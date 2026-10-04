package com.rpgcore.compat;

import com.rpgcore.RpgCore;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Cached reflection helpers for compat bindings. Failures are logged once and return null. */
public final class Ref {
    private Ref() {}

    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Set<String> REPORTED = new HashSet<>();

    public static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException | LinkageError e) {
            warn("class " + name, e);
            return null;
        }
    }

    /** Subscribes {@code handler} to an event class known only by name. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean listen(String eventClass, EventPriority priority, Consumer<Object> handler) {
        Class c = type(eventClass);
        if (c == null || !Event.class.isAssignableFrom(c)) return false;
        Consumer wrapped = ev -> {
            try {
                handler.accept(ev);
            } catch (RuntimeException e) {
                warn("handler " + eventClass, e);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(priority, false, c, wrapped);
        return true;
    }

    private static Method find(Class<?> c, String name, int argc) {
        String key = c.getName() + "#" + name + "/" + argc;
        Method m = METHODS.get(key);
        if (m != null) return m;
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            for (Method cand : k.getDeclaredMethods()) {
                if (cand.getName().equals(name) && cand.getParameterCount() == argc) {
                    try {
                        cand.setAccessible(true);
                    } catch (RuntimeException ignored) {
                        // public methods still work without it
                    }
                    METHODS.put(key, cand);
                    return cand;
                }
            }
        }
        for (Class<?> i : c.getInterfaces()) {
            Method im = find(i, name, argc);
            if (im != null) return im;
        }
        return null;
    }

    /** Instance call; returns null (and logs once) when the method is missing or throws. */
    public static Object call(Object target, String method, Object... args) {
        if (target == null) return null;
        Method m = find(target.getClass(), method, args.length);
        if (m == null) {
            warn(target.getClass().getName() + "#" + method, null);
            return null;
        }
        try {
            return m.invoke(target, args);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            warn(target.getClass().getName() + "#" + method, e);
            return null;
        }
    }

    public static Object callStatic(Class<?> type, String method, Object... args) {
        if (type == null) return null;
        Method m = find(type, method, args.length);
        if (m == null) {
            warn(type.getName() + "#" + method, null);
            return null;
        }
        try {
            return m.invoke(null, args);
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            warn(type.getName() + "#" + method, e);
            return null;
        }
    }

    public static float asFloat(Object o, float def) {
        return o instanceof Number n ? n.floatValue() : def;
    }

    public static int asInt(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    public static boolean asBool(Object o) {
        return o instanceof Boolean b && b;
    }

    public static void warn(String what, Throwable e) {
        synchronized (REPORTED) {
            if (!REPORTED.add(what)) return;
        }
        if (e == null) RpgCore.LOG.warn("rpgcore compat: missing {}", what);
        else RpgCore.LOG.warn("rpgcore compat: {} failed: {}", what, e.toString());
    }
}
