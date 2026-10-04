package com.rpgcore.ai;

import com.rpgcore.registry.RpgTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Tank (§12): stands between the target and the nearest archer/support ally (3 blocks in front of the ally), facing
 * the target. Frontal damage reduction (60°, x0.5) is applied in Roles.
 */
public class ShieldWallGoal extends TimedGoal {
    private Mob ally;
    private long nextRepath;

    public ShieldWallGoal(PathfinderMob mob) {
        super(mob);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    protected boolean doCanUse() {
        if (target() == null) return false;
        ally = findAlly();
        return ally != null;
    }

    @Override
    protected boolean doCanContinue() {
        return target() != null && ally != null && ally.isAlive() && mob.distanceToSqr(ally) < 24 * 24;
    }

    private Mob findAlly() {
        List<Mob> near = mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(16),
                m -> m != mob && m.isAlive() && (m.getType().is(RpgTags.ROLE_ARCHER) || m.getType().is(RpgTags.ROLE_SUPPORT)) && Roles.allied(mob, m));
        Mob best = null;
        double bestD = Double.MAX_VALUE;
        for (Mob m : near) {
            double d = m.distanceToSqr(mob);
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        return best;
    }

    @Override
    public void stop() {
        ally = null;
        mob.getNavigation().stop();
    }

    @Override
    protected void doTick() {
        LivingEntity t = target();
        if (t == null || ally == null) return;
        mob.getLookControl().setLookAt(t, 30F, 30F);
        if (now() < nextRepath) return;
        nextRepath = now() + 10;
        Vec3 a = ally.position();
        Vec3 dir = t.position().subtract(a);
        dir = new Vec3(dir.x, 0, dir.z);
        if (dir.lengthSqr() < 1.0E-4) return;
        Vec3 guard = a.add(dir.normalize().scale(3.0));
        mob.getNavigation().moveTo(guard.x, guard.y, guard.z, 1.0);
    }
}
