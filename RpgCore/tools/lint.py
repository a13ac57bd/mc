#!/usr/bin/env python3
"""Cross-file consistency check without Minecraft/Forge jars.

Compiles src/main/java with the Eclipse compiler (ecj) using only the JDK, then drops errors that are caused by
the missing external libraries (unresolved net.minecraft / net.minecraftforge / com.mojang ... types). What is left
are real mistakes between rpgcore's own classes: wrong names, missing members, bad arity, syntax errors.

Usage: python3 tools/lint.py [path/to/ecj.jar]
"""
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "java")
EXTERNAL = ("net.minecraft", "net.minecraftforge", "com.mojang", "org.slf4j", "com.google", "it.unimi",
            "org.joml", "org.apache", "io.netty", "org.lwjgl", "org.jetbrains", "javax.annotation",
            "org.spongepowered", "cpw.mods")


def find_ecj():
    if len(sys.argv) > 1:
        return sys.argv[1]
    env = os.environ.get("ECJ_JAR")
    if env:
        return env
    sys.exit("pass the path to ecj.jar (Maven Central: org.eclipse.jdt:ecj)")


def sources():
    out = []
    for d, _, files in os.walk(SRC):
        for f in files:
            if f.endswith(".java"):
                out.append(os.path.join(d, f))
    return out


IMPORT = re.compile(r"^import\s+(static\s+)?([\w.]+)(\.\*)?;", re.M)
EXTENDS = re.compile(r"\b(?:class|record|enum|interface)\s+(\w+)[^{]*?\b(?:extends|implements)\s+([\w.<>, ?]+)")


def file_info(path):
    text = open(path, encoding="utf-8").read()
    external_names = set()
    for m in IMPORT.finditer(text):
        name = m.group(2)
        if name.startswith(EXTERNAL):
            external_names.add(name.split(".")[-1])
            if m.group(1):  # static import: also the class name
                external_names.add(name.split(".")[-2])
    return text, external_names


def main():
    ecj = find_ecj()
    files = sources()
    info = {f: file_info(f) for f in files}
    # rpgcore types whose supertype is external: calls to inherited members cannot be checked
    opaque = set()
    for f, (text, ext) in info.items():
        for m in EXTENDS.finditer(text):
            supers = re.findall(r"\w+", m.group(2))
            if any(s in ext for s in supers):
                opaque.add(m.group(1))
    # transitive: extends an opaque rpgcore type
    changed = True
    while changed:
        changed = False
        for f, (text, ext) in info.items():
            for m in EXTENDS.finditer(text):
                if m.group(1) in opaque:
                    continue
                if any(s in opaque for s in re.findall(r"\w+", m.group(2))):
                    opaque.add(m.group(1))
                    changed = True

    own = set()
    for f, (text, ext) in info.items():
        own.update(re.findall(r"\b(?:class|record|enum|interface)\s+(\w+)", text))
    global_ext = set()
    for f, (text, ext) in info.items():
        global_ext |= ext
    global_ext -= own

    with tempfile.TemporaryDirectory() as out:
        argfile = os.path.join(out, "files.txt")
        log = os.path.join(out, "log.xml")
        with open(argfile, "w") as fh:
            fh.write("\n".join(files))
        subprocess.run(["java", "-jar", ecj, "-17", "-proceedOnError", "-nowarn", "-encoding", "UTF-8",
                        "-d", "none", "-log", log, "@" + argfile], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        tree = ET.parse(log)
    problems = []
    for source in tree.iter("source"):
        path = source.get("path")
        for prob in source.iter("problem"):
            if prob.get("severity") != "ERROR":
                continue
            msg = prob.find("message").get("value")
            ctx = prob.find("source_context")
            problems.append((path, prob.get("line"), msg, ctx.get("value") if ctx is not None else ""))
    kept = []
    for path, line, msg, context in problems:
        ext = info.get(path, ("", set()))[1] | global_ext
        if "Internal compiler error" in msg:
            continue
        text = info.get(path, ("", set()))[0]
        file_types = set(re.findall(r"\b(?:class|record|enum|interface)\s+(\w+)", text))
        if file_types & opaque:
            # members and nested types inherited from an external supertype cannot be checked here
            m2 = re.match(r"(.+?) cannot be resolved( to a type| to a variable| or is not a field)?$", msg)
            if m2 and re.split(r"[.<]", m2.group(1))[0] not in own:
                continue
        block = msg + "\n" + context
        if is_noise(msg, ext, opaque, block):
            continue
        kept.append(f"{os.path.relpath(path, ROOT)}:{line}: {msg}\n    {context.strip()}")
    for k in kept:
        print(k)
    total = len(problems)
    print(f"\n{len(kept)} problem(s) after filtering external-library noise ({total} raw errors)")
    return 1 if kept else 0


def mentions_external(text, ext):
    words = set(re.findall(r"[A-Za-z_]\w*", text))
    return bool(words & ext)


def is_noise(msg, ext, opaque, block):
    if msg.startswith("The import ") and "cannot be resolved" in msg:
        src = re.search(r"import\s+(?:static\s+)?([\w.]+)", block)
        return bool(src) and src.group(1).startswith(EXTERNAL)
    m = re.match(r"(.+?) cannot be resolved( to a type| or is not a field)?$", msg)
    if m:
        name = re.split(r"[.<]", m.group(1))[0]
        return name in ext or mentions_external(m.group(1), ext)
    if msg.startswith("Cannot infer type arguments"):
        return mentions_external(block, ext)
    if "hierarchy of the type" in msg and "is inconsistent" in msg:
        return True
    if ("is undefined for the type Object" in msg or "refers to the missing type" in msg
            or "The target type of this expression must be a functional interface" in msg):
        return True
    m = re.search(r"is undefined for the type (\w+)", msg)
    if m and (m.group(1) in opaque or m.group(1) in ext):
        return True
    m = re.search(r"cannot be resolved or is not a field", msg)
    if m:
        return True if mentions_external(block, ext) else False
    if "must implement the inherited abstract method" in msg and mentions_external(msg, ext):
        return True
    if "must override or implement a supertype method" in msg:
        return True
    if "is not applicable for the arguments" in msg and mentions_external(msg, ext):
        return True
    if "The method" in msg and "is undefined" in msg and mentions_external(block, ext):
        return True
    if ("cannot be resolved to a variable" in msg or "Type mismatch" in msg or "cannot convert" in msg
            or "is not visible" in msg or "is ambiguous" in msg or "Unhandled exception" in msg
            or "incompatible" in msg.lower() or "missing type" in msg or "indirectly referenced" in msg
            or "cannot infer" in msg or "is not a functional interface" in msg or "is not generic" in msg
            or "has to be" in msg or "Duplicate methods named" in msg or "references the missing type" in msg
            or "The target type of this expression" in msg or "operator" in msg):
        return mentions_external(block, ext)
    return False


if __name__ == "__main__":
    sys.exit(main())
