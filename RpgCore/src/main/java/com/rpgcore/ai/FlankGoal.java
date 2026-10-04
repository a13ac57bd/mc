package com.rpgcore.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Flanker (§12): first circle to the target's side-rear (4 blocks), then hand back to the normal melee goal. */
public class FlankGoal extends TimedGoal {
    private int side = 1;
    private int ticks;
    private boolean done;
    private long cooldownUntil;
    private long nextRepath;

    public FlankGoal(PathfinderMob mob) {
        super(mob);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    protected boolean doCanUse() {
        LivingEntity t = target();
        if (t == null || now() < cooldownUntil) return false;
        double d = mob.distanceToSqr(t);
        return d > 9 && d < 400;
    }

    @Override
    protected boolean doCanContinue() {
        return !done && ticks < 100 && target() != null;
    }

    @Override
    public void start() {
        side = mob.getRandom().nextBoolean() ? 1 : -1;
        ticks = 0;
        done = false;
        nextRepath = 0;
    }

    @Override
    public void stop() {
        cooldownUntil = now() + 100;
    }

    /** Point 4 blocks from the target at 135° from where it is looking. */
    private Vec3 flankPoint(LivingEntity t) {
        float yaw = (t.getYRot() + side * 135F) * Mth.DEG_TO_RAD;
        return t.position().add(-Mth.sin(yaw) * 4.0, 0, Mth.cos(yaw) * 4.0);
    }

    @Override
    protected void doTick() {
        LivingEntity t = target();
        if (t == null) {
            done = true;
            return;
        }
        ticks += 2;
        Vec3 p = flankPoint(t);
        if (mob.position().distanceToSqr(p) < 2.25) {
            done = true;
            return;
        }
        if (now() >= nextRepath) {
            nextRepath = now() + 10;
            mob.getNavigation().moveTo(p.x, p.y, p.z, 1.2);
        }
    }
}
