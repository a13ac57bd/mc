package com.rpgcore.trait;

import com.rpgcore.RpgCore;
import com.rpgcore.logic.Trigger;
import com.rpgcore.trait.condition.Condition;
import com.rpgcore.trait.effect.Effect;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the rules of one trigger (§5.3). Damage caused by trait effects runs inside {@link #nested}; while nested,
 * hit-like triggers only run rules marked {@code "recursive": true}, and nesting stops at depth 2 (§5.3 step 4).
 */
public final class TraitEngine {
    private TraitEngine() {}

    public static final int MAX_DEPTH = 2;
    private static int depth;
    private static final Set<String> REPORTED = new HashSet<>();

    public static int depth() {
        return depth;
    }

    /** Runs {@code action} one level deeper; does nothing once {@link #MAX_DEPTH} is reached. */
    public static void nested(Runnable action) {
        if (depth >= MAX_DEPTH) return;
        depth++;
        try {
            action.run();
        } finally {
            depth--;
        }
    }

    /** True when the player has any rule for this trigger (cheap pre-check for event sources). */
    public static boolean has(ServerPlayer player, Trigger trigger) {
        TraitCache.PlayerTraits pt = TraitCache.get(player);
        if (pt.isEmpty()) return false;
        if (!pt.rules(trigger).isEmpty()) return true;
        return trigger.kind() == Trigger.Kind.HIT && !pt.rules(Trigger.ANY_HIT).isEmpty();
    }

    public static void fire(TraitContext ctx) {
        TraitCache.PlayerTraits pt = TraitCache.get(ctx.player);
        if (pt.isEmpty()) return;
        run(pt.rules(ctx.trigger), ctx);
        if (ctx.trigger.kind() == Trigger.Kind.HIT && ctx.trigger != Trigger.ANY_HIT) run(pt.rules(Trigger.ANY_HIT), ctx);
    }

    private static void run(List<TraitCache.ActiveRule> rules, TraitContext ctx) {
        if (rules.isEmpty()) return;
        boolean inNested = depth > 0;
        for (TraitCache.ActiveRule ar : rules) {
            if (inNested && ctx.trigger.isHitLike() && !ar.rule().recursive()) continue;
            if (!passes(ar, ctx)) continue;
            for (Effect effect : ar.rule().effects()) {
                try {
                    effect.apply(ctx, ar.trait());
                } catch (RuntimeException e) {
                    String key = ar.trait().def().id() + "/" + effect.type();
                    if (REPORTED.add(key)) RpgCore.LOG.error("rpgcore: trait effect {} failed", key, e);
                }
            }
        }
    }

    private static boolean passes(TraitCache.ActiveRule ar, TraitContext ctx) {
        for (Condition c : ar.rule().conditions()) {
            if (!c.test(ctx, ar.trait())) return false;
        }
        return true;
    }
}
