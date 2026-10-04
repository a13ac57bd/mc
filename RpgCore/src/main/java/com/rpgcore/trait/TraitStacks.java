package com.rpgcore.trait;

import com.rpgcore.util.Attr;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Seconds-scale combat state per player: flags, counters, hit streaks and temporary attributes (§5.3 step 6).
 * Lives in memory only and is cleared on logout. Keys are already prefixed with the trait id.
 */
public final class TraitStacks {
    private TraitStacks() {}

    private static final class Counter {
        double value;
        long lastTick;
        int resetTicks;
    }

    private record Streak(UUID target, int count, long lastTick) {}

    private record Temp(Attribute attribute, long expiry) {}

    private static final class State {
        final Map<String, Long> flags = new HashMap<>();
        final Map<String, Counter> counters = new HashMap<>();
        final Map<String, Streak> streaks = new HashMap<>();
        final Map<UUID, Temp> temps = new HashMap<>();
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), u -> new State());
    }

    private static long now(ServerPlayer p) {
        return p.level().getGameTime();
    }

    // ---- flags ----

    public static boolean flag(ServerPlayer p, String key) {
        State s = STATES.get(p.getUUID());
        if (s == null) return false;
        Long expiry = s.flags.get(key);
        if (expiry == null) return false;
        if (expiry >= 0 && now(p) >= expiry) {
            s.flags.remove(key);
            return false;
        }
        return true;
    }

    /** {@code ticks} &lt; 0 keeps the flag until cleared or logout. */
    public static void setFlag(ServerPlayer p, String key, int ticks) {
        state(p).flags.put(key, ticks < 0 ? -1L : now(p) + ticks);
    }

    public static void clearFlag(ServerPlayer p, String key) {
        State s = STATES.get(p.getUUID());
        if (s != null) s.flags.remove(key);
    }

    // ---- counters: reset to 0 when not added to for resetTicks ----

    public static double counter(ServerPlayer p, String key) {
        State s = STATES.get(p.getUUID());
        if (s == null) return 0;
        Counter c = s.counters.get(key);
        if (c == null) return 0;
        if (now(p) - c.lastTick > c.resetTicks) {
            s.counters.remove(key);
            return 0;
        }
        return c.value;
    }

    public static double addCounter(ServerPlayer p, String key, double amount, int resetTicks, double max) {
        double current = counter(p, key);
        Counter c = state(p).counters.computeIfAbsent(key, k -> new Counter());
        c.value = Math.min(max, current + amount);
        c.lastTick = now(p);
        c.resetTicks = resetTicks;
        return c.value;
    }

    // ---- consecutive hits on the same target ----

    /** Registers a hit and returns the streak length including this hit. */
    public static int streak(ServerPlayer p, String key, UUID target, int resetTicks) {
        State s = state(p);
        long t = now(p);
        Streak old = s.streaks.get(key);
        int count = old != null && old.target().equals(target) && t - old.lastTick() <= resetTicks ? old.count() + 1 : 1;
        s.streaks.put(key, new Streak(target, count, t));
        return count;
    }

    // ---- temporary attributes ----

    public static UUID modifierId(String key) {
        return UUID.nameUUIDFromBytes(("rpgcore:" + key).getBytes(StandardCharsets.UTF_8));
    }

    /** Applies or refreshes a temporary modifier (re-trigger refreshes duration, does not stack). */
    public static void tempAttribute(ServerPlayer p, String key, Attribute attribute, double amount, AttributeModifier.Operation op, int ticks) {
        UUID id = modifierId(key);
        Attr.setTransient(p, attribute, id, "rpgcore trait", amount, op);
        state(p).temps.put(id, new Temp(attribute, now(p) + ticks));
    }

    /** Called every few ticks: expires temporary modifiers. */
    public static void tick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null || s.temps.isEmpty()) return;
        long t = now(p);
        Iterator<Map.Entry<UUID, Temp>> it = s.temps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Temp> e = it.next();
            if (t >= e.getValue().expiry()) {
                Attr.remove(p, e.getValue().attribute(), e.getKey());
                it.remove();
            }
        }
    }

    /** Logout: drop everything and remove temporary modifiers. */
    public static void clear(ServerPlayer p) {
        State s = STATES.remove(p.getUUID());
        if (s == null) return;
        s.temps.forEach((id, temp) -> Attr.remove(p, temp.attribute(), id));
    }
}
