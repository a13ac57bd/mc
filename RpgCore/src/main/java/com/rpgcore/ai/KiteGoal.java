package com.rpgcore.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Archer/support (§12): when the target comes within {@code min} blocks, move away until {@code max}. */
public class KiteGoal extends TimedGoal {
    private final double min;
    private final double max;

    public KiteGoal(PathfinderMob mob, double min, double max) {
        super(mob);
        this.min = min;
        this.max = max;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    protected boolean doCanUse() {
        LivingEntity t = target();
        return t != null && mob.distanceToSqr(t) < min * min;
    }

    @Override
    protected boolean doCanContinue() {
        LivingEntity t = target();
        return t != null && !mob.getNavigation().isDone() && mob.distanceToSqr(t) < max * max;
    }

    @Override
    public void start() {
        LivingEntity t = target();
        if (t == null) return;
        Vec3 away = DefaultRandomPos.getPosAway(mob, (int) max, 7, t.position());
        if (away != null) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.25);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    protected void doTick() {
        LivingEntity t = target();
        if (t != null) mob.getLookControl().setLookAt(t, 30F, 30F);
    }
}
