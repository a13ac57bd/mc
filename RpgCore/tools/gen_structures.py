#!/usr/bin/env python3
"""Generates placeholder structure templates (TECH_SPEC §9): the castle Jigsaw rooms and the GameTest template.

Rooms are stone bricks + vanilla/rpgcore blocks; the art pass rebuilds them in game and saves over these files.
Pure standard library (gzip + struct), no NBT package needed.

    python3 tools/gen_structures.py
"""
import gzip
import os
import random
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "rpgcore", "structures")
DATA_VERSION = 3465  # 1.20.1

# ---------------------------------------------------------------- NBT writer

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG, TAG_FLOAT, TAG_DOUBLE = 0, 1, 2, 3, 4, 5, 6
TAG_STRING, TAG_LIST, TAG_COMPOUND = 8, 9, 10


class Byte(int):
    pass


class IntList(list):
    pass


def _name(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def _type(v):
    if isinstance(v, Byte):
        return TAG_BYTE
    if isinstance(v, bool):
        return TAG_BYTE
    if isinstance(v, int):
        return TAG_INT
    if isinstance(v, float):
        return TAG_DOUBLE
    if isinstance(v, str):
        return TAG_STRING
    if isinstance(v, (list, IntList)):
        return TAG_LIST
    if isinstance(v, dict):
        return TAG_COMPOUND
    raise TypeError(type(v))


def _payload(v):
    t = _type(v)
    if t == TAG_BYTE:
        return struct.pack(">b", int(v))
    if t == TAG_INT:
        return struct.pack(">i", v)
    if t == TAG_DOUBLE:
        return struct.pack(">d", v)
    if t == TAG_STRING:
        return _name(v)
    if t == TAG_LIST:
        if not v:
            return struct.pack(">bi", TAG_END, 0)
        et = _type(v[0])
        return struct.pack(">bi", et, len(v)) + b"".join(_payload(e) for e in v)
    if t == TAG_COMPOUND:
        out = b""
        for k, e in v.items():
            out += struct.pack(">b", _type(e)) + _name(k) + _payload(e)
        return out + struct.pack(">b", TAG_END)
    raise TypeError(t)


def write_nbt(path, root):
    data = struct.pack(">b", TAG_COMPOUND) + _name("") + _payload(root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, "wb") as f:
        f.write(data)


# ---------------------------------------------------------------- template builder

class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}  # (x,y,z) -> (state_key, nbt)

    def set(self, x, y, z, name, props=None, nbt=None):
        key = (name, tuple(sorted((props or {}).items())))
        self.blocks[(x, y, z)] = (key, nbt)

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def save(self, rel):
        palette, index, blocks = [], {}, []
        for pos in sorted(self.blocks):
            key, nbt = self.blocks[pos]
            if key not in index:
                index[key] = len(palette)
                entry = {"Name": key[0]}
                if key[1]:
                    entry["Properties"] = {k: str(v) for k, v in key[1]}
                palette.append(entry)
            b = {"pos": [pos[0], pos[1], pos[2]], "state": index[key]}
            if nbt is not None:
                b["nbt"] = nbt
            blocks.append(b)
        write_nbt(os.path.join(OUT, rel + ".nbt"), {
            "DataVersion": DATA_VERSION,
            "size": list(self.size),
            "palette": palette,
            "blocks": blocks,
            "entities": [],
        })


rng = random.Random(1234)
WALLS = ["minecraft:stone_bricks"] * 6 + ["minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks"]


def room(sx, sy, sz):
    """Hollow box: floor, walls, ceiling; air inside (explicit, so terrain is carved out)."""
    t = Template(sx, sy, sz)
    for x in range(sx):
        for y in range(sy):
            for z in range(sz):
                edge = x in (0, sx - 1) or y in (0, sy - 1) or z in (0, sz - 1)
                if edge:
                    t.set(x, y, z, "minecraft:polished_andesite" if y == 0 else rng.choice(WALLS))
                else:
                    t.set(x, y, z, "minecraft:air")
    # lights in the ceiling
    for x in range(2, sx - 2, 4):
        for z in range(2, sz - 2, 4):
            t.set(x, sy - 1, z, "minecraft:glowstone")
    return t


def jigsaw(t, x, y, z, facing, name, target="minecraft:empty", pool="minecraft:empty"):
    """A connector in a wall. facing points out of the room. The block above becomes a doorway."""
    t.set(x, y, z, "minecraft:jigsaw", {"orientation": f"{facing}_up"}, {
        "id": "minecraft:jigsaw", "name": name, "target": target, "pool": pool,
        "final_state": "minecraft:air", "joint": "aligned"})
    t.set(x, y + 1, z, "minecraft:air")


def door_in(t, x, y, z, facing):
    jigsaw(t, x, y, z, facing, "rpgcore:door_in")


def door_out(t, x, y, z, facing, pool):
    jigsaw(t, x, y, z, facing, "rpgcore:door_out", "rpgcore:door_in", pool)


def chest(t, x, y, z, facing="north", table="minecraft:chests/simple_dungeon"):
    t.set(x, y, z, "minecraft:chest", {"facing": facing, "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": table})


# ---------------------------------------------------------------- castle rooms
# Local convention: door_in on the north wall (z=0, facing north); exits elsewhere.

def entrance():
    t = room(9, 7, 9)
    t.fill(3, 1, 8, 5, 3, 8, "minecraft:air")  # gate to the outside (south)
    door_out(t, 4, 1, 0, "north", "rpgcore:castle/corridors")
    t.set(1, 1, 1, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    t.set(7, 1, 1, "minecraft:lantern", {"hanging": "false", "waterlogged": "false"})
    return t


def corridor(variant):
    t = room(5, 6, 11)
    door_in(t, 2, 1, 10, "south")
    door_out(t, 2, 1, 0, "north", "rpgcore:castle/hubs")
    if variant == "b":
        for z in (3, 7):
            t.set(1, 1, z, "rpgcore:powder_keg")
            t.set(3, 1, z, "rpgcore:powder_keg")
        t.set(2, 4, 5, "rpgcore:fragile_chain")
        t.set(2, 3, 5, "rpgcore:chandelier")
    else:
        for z in (2, 5, 8):
            t.set(1, 1, z, "minecraft:cobweb")
    return t


def hub():
    t = room(15, 8, 15)
    door_in(t, 7, 1, 14, "south")
    door_out(t, 14, 1, 7, "east", "rpgcore:castle/branches")
    door_out(t, 0, 1, 7, "west", "rpgcore:castle/branches")
    door_out(t, 7, 1, 0, "north", "rpgcore:castle/oath")
    for x, z in ((4, 4), (10, 4), (4, 10), (10, 10)):
        t.fill(x, 1, z, x, 6, z, "minecraft:chiseled_stone_bricks")
    # a shortcut door back toward the entrance; FACING points inside the hub
    t.fill(11, 1, 14, 11, 2, 14, "rpgcore:shortcut_door", {"facing": "north", "open": "false"})
    return t


def branch_treasure():
    t = room(9, 6, 9)
    door_in(t, 4, 1, 0, "north")
    t.fill(1, 1, 6, 7, 3, 6, "rpgcore:secret_wall")
    for x in range(1, 8):
        for y in range(1, 4):
            t.blocks[(x, y, 6)] = (("rpgcore:secret_wall", ()), {"id": "rpgcore:secret_wall", "hits_required": 3, "hits": 0})
    chest(t, 4, 1, 7, "north")
    chest(t, 2, 1, 7, "north")
    return t


def branch_trap():
    t = room(9, 6, 9)
    door_in(t, 4, 1, 0, "north")
    # vanilla trap: dispenser -> redstone -> pressure plate; the arrow spawns outside the target's hitbox
    t.set(4, 1, 8, "minecraft:dispenser", {"facing": "north", "triggered": "false"},
          {"id": "minecraft:dispenser", "Items": [{"Slot": Byte(0), "id": "minecraft:arrow", "Count": Byte(64)}]})
    t.set(4, 1, 7, "minecraft:redstone_wire")
    t.set(4, 1, 6, "minecraft:redstone_wire")
    t.set(4, 1, 5, "minecraft:stone_pressure_plate", {"powered": "false"})
    for x in range(1, 4):
        for z in range(2, 5):
            t.set(x, 1, z, "rpgcore:oil")
    t.set(6, 1, 3, "rpgcore:powder_keg")
    chest(t, 7, 1, 7, "west")
    return t


def branch_dead_end():
    t = room(9, 6, 9)
    door_in(t, 4, 1, 0, "north")
    t.set(4, 4, 4, "rpgcore:fragile_chain")
    t.set(4, 3, 4, "rpgcore:chandelier")
    t.set(4, 1, 7, "minecraft:skeleton_skull", {"rotation": "0"})
    return t


def oath_hall():
    t = room(11, 7, 11)
    door_in(t, 5, 1, 10, "south")
    door_out(t, 5, 1, 0, "north", "rpgcore:castle/boss")
    t.set(5, 1, 5, "rpgcore:oath_stone", {"lit": "false"})
    for x, z in ((3, 3), (7, 3), (3, 7), (7, 7)):
        t.set(x, 1, z, "minecraft:candle", {"candles": "3", "lit": "true", "waterlogged": "false"})
    return t


def boss_room():
    t = room(32, 12, 32)
    door_in(t, 16, 1, 31, "south")
    # arena gate just inside the doorway (open until the fight starts)
    t.fill(15, 1, 30, 17, 3, 30, "rpgcore:arena_gate", {"open": "true"})
    t.set(16, 1, 16, "rpgcore:boss_altar", None, {"id": "rpgcore:boss_altar", "boss": "rpgcore:test_boss", "radius": 14})
    for x, z in ((8, 8), (23, 8), (8, 23), (23, 23)):
        t.set(x, 10, z, "rpgcore:fragile_chain")
        t.set(x, 9, z, "rpgcore:chandelier")
    for x, z in ((2, 2), (29, 2), (2, 29), (29, 29)):
        t.set(x, 1, z, "rpgcore:powder_keg")
    for x in range(4, 28, 6):
        t.fill(x, 1, 4, x, 10, 4, "minecraft:chiseled_stone_bricks")
        t.fill(x, 1, 27, x, 10, 27, "minecraft:chiseled_stone_bricks")
    return t


def gametest_empty():
    t = Template(8, 8, 8)
    t.fill(0, 0, 0, 7, 0, 7, "minecraft:stone")
    return t


def main():
    entrance().save("castle/entrance")
    corridor("a").save("castle/corridor_a")
    corridor("b").save("castle/corridor_b")
    hub().save("castle/hub")
    branch_treasure().save("castle/branch_treasure")
    branch_trap().save("castle/branch_trap")
    branch_dead_end().save("castle/branch_dead_end")
    oath_hall().save("castle/oath_hall")
    boss_room().save("castle/boss_room")
    gametest_empty().save("empty")
    print("structures written to", OUT)


if __name__ == "__main__":
    main()
