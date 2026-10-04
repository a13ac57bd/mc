package com.rpgcore.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Base for every rpgcore goal: measures the time spent in rpgcore AI (self-test budget 2 ms/tick, §12).
 * Goals without requiresUpdateEveryTick only tick every second tick, so periods must use game time, not tickCount.
 */
public abstract class TimedGoal extends Goal {
    private static long nanos;

    protected final PathfinderMob mob;

    protected TimedGoal(PathfinderMob mob) {
        this.mob = mob;
    }

    /** Milliseconds spent since the last call, then resets. */
    public static double takeMillis() {
        double ms = nanos / 1.0e6;
        nanos = 0;
        return ms;
    }

    protected abstract boolean doCanUse();

    protected boolean doCanContinue() {
        return doCanUse();
    }

    protected void doTick() {
    }

    @Override
    public final boolean canUse() {
        long t = System.nanoTime();
        try {
            return doCanUse();
        } finally {
            nanos += System.nanoTime() - t;
        }
    }

    @Override
    public final boolean canContinueToUse() {
        long t = System.nanoTime();
        try {
            return doCanContinue();
        } finally {
            nanos += System.nanoTime() - t;
        }
    }

    @Override
    public final void tick() {
        long t = System.nanoTime();
        try {
            doTick();
        } finally {
            nanos += System.nanoTime() - t;
        }
    }

    protected LivingEntity target() {
        LivingEntity t = mob.getTarget();
        return t != null && t.isAlive() ? t : null;
    }

    protected long now() {
        return mob.level().getGameTime();
    }
}
