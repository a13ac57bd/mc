package com.rpgcore.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Charger (§12): at 4-14 blocks dash straight in (speed x1.8, max 2 s); after a hit back off for 1 s; 4 s cooldown. */
public class ChargerGoal extends TimedGoal {
    private enum Phase { CHARGE, RETREAT, DONE }

    private Phase phase = Phase.DONE;
    private int ticks;
    private long cooldownUntil;

    public ChargerGoal(PathfinderMob mob) {
        super(mob);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    protected boolean doCanUse() {
        LivingEntity t = target();
        if (t == null || now() < cooldownUntil) return false;
        double d = mob.distanceTo(t);
        return d >= 4 && d <= 14 && mob.getSensing().hasLineOfSight(t);
    }

    @Override
    protected boolean doCanContinue() {
        return phase != Phase.DONE && target() != null;
    }

    @Override
    public void start() {
        phase = Phase.CHARGE;
        ticks = 0;
    }

    @Override
    public void stop() {
        phase = Phase.DONE;
        cooldownUntil = now() + 80;
        mob.getNavigation().stop();
    }

    @Override
    protected void doTick() {
        LivingEntity t = target();
        if (t == null) {
            phase = Phase.DONE;
            return;
        }
        ticks++;
        if (phase == Phase.CHARGE) {
            mob.getLookControl().setLookAt(t, 30F, 30F);
            mob.getNavigation().moveTo(t, 1.8);
            double reach = mob.getBbWidth() * 2.0F * mob.getBbWidth() * 2.0F + t.getBbWidth();
            if (mob.distanceToSqr(t) <= reach) {
                mob.swing(mob.getUsedItemHand());
                mob.doHurtTarget(t);
                phase = Phase.RETREAT;
                ticks = 0;
                Vec3 away = DefaultRandomPos.getPosAway(mob, 8, 4, t.position());
                if (away != null) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
            } else if (ticks > 40) {
                phase = Phase.DONE;
            }
        } else if (phase == Phase.RETREAT && ticks > 20) {
            phase = Phase.DONE;
        }
    }
}
