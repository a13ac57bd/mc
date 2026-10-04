package com.rpgcore.ai;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;

import java.util.EnumSet;
import java.util.List;

/**
 * Support (§12): every 8 s heal the weakest hurt ally within 12 blocks (Regeneration II, 5 s); if nobody is hurt,
 * give an ally that is fighting Strength for 8 s.
 */
public class SupportGoal extends TimedGoal {
    private long next;

    public SupportGoal(PathfinderMob mob) {
        super(mob);
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    protected boolean doCanUse() {
        return now() >= next && target() != null;
    }

    @Override
    protected boolean doCanContinue() {
        return false;
    }

    @Override
    public void start() {
        next = now() + 160;
        List<Mob> allies = mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(12),
                m -> m != mob && m.isAlive() && Roles.allied(mob, m));
        Mob weakest = null;
        float lowest = 1.0F;
        for (Mob m : allies) {
            float ratio = m.getHealth() / m.getMaxHealth();
            if (ratio < lowest) {
                lowest = ratio;
                weakest = m;
            }
        }
        LivingEntity buffed = null;
        if (weakest != null) {
            weakest.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1), mob);
            buffed = weakest;
        } else {
            for (Mob m : allies) {
                if (m.getTarget() != null) {
                    m.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 160, 0), mob);
                    buffed = m;
                    break;
                }
            }
        }
        if (buffed != null && mob.level() instanceof ServerLevel sl) {
            mob.swing(mob.getUsedItemHand());
            sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, buffed.getX(), buffed.getY() + buffed.getBbHeight(), buffed.getZ(), 8, 0.4, 0.4, 0.4, 0.0);
        }
    }
}
