package com.rpgcore.ecology;

import com.rpgcore.RpgCore;
import com.rpgcore.registry.RpgEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Corruption (§12): hostile mobs spawning inside a corruption zone (set by world events) get rpgcore:corrupted with
 * 50% chance: +20% health, hits wither for 2 s, purple particles.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Corruption {
    private Corruption() {}

    public record Zone(String source, ResourceKey<Level> dimension, BlockPos center, int radius) {
        boolean contains(ResourceKey<Level> dim, double x, double z) {
            double dx = x - center.getX(), dz = z - center.getZ();
            return dimension.equals(dim) && dx * dx + dz * dz <= (double) radius * radius;
        }
    }

    public static final List<Zone> ZONES = new CopyOnWriteArrayList<>();

    public static boolean inZone(ResourceKey<Level> dim, double x, double z) {
        for (Zone zone : ZONES) if (zone.contains(dim, x, z)) return true;
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        Mob mob = event.getEntity();
        if (!(mob instanceof Enemy) || ZONES.isEmpty()) return;
        if (!inZone(event.getLevel().getLevel().dimension(), event.getX(), event.getZ())) return;
        if (mob.getRandom().nextFloat() >= 0.5F) return;
        corrupt(mob);
    }

    public static void corrupt(LivingEntity mob) {
        mob.addEffect(new MobEffectInstance(RpgEffects.CORRUPTED.get(), MobEffectInstance.INFINITE_DURATION, 0, false, true));
        mob.setHealth(mob.getMaxHealth());
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != event.getEntity()
                && attacker.hasEffect(RpgEffects.CORRUPTED.get())) {
            event.getEntity().addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), attacker);
        }
    }
}
