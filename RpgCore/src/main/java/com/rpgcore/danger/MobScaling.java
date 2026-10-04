package com.rpgcore.danger;

import com.rpgcore.RpgCore;
import com.rpgcore.logic.DangerMath;
import com.rpgcore.registry.RpgTags;
import com.rpgcore.util.Attr;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Hostile mobs scale with danger at FinalizeSpawn (§7): health +35%/tier (MULTIPLY_TOTAL), damage +25%/tier
 * (applied in LivingHurtEvent; arrows count for the shooter). Bosses and event mobs are skipped.
 * Note: FinalizeSpawn is fired by the spawning caller (ForgeEventFactory.onFinalizeSpawn); rpgcore always uses it.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class MobScaling {
    private MobScaling() {}

    public static final String TIER = "rpgcore_danger";
    public static final String STRENGTH = "rpgcore_strength";
    /** Marks mobs spawned by world events; they keep their own numbers. */
    public static final String EVENT_MOB = "rpgcore_event_mob";
    public static final UUID HEALTH_ID = UUID.fromString("a7c3e1d2-4b5f-4e6a-8c9d-0e1f2a3b4c5d");

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        Mob mob = event.getEntity();
        if (!(mob instanceof Enemy) || skip(mob)) return;
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        // during chunk generation the accessor is a WorldGenRegion: stay away from chunk lookups
        int tier = event.getLevel() instanceof ServerLevel level ? DangerMap.get(level, pos) : DangerMap.ringOnly(event.getLevel().getLevel(), pos);
        apply(mob, tier, 1.0);
    }

    public static boolean skip(LivingEntity mob) {
        return mob.getType().is(RpgTags.BOSS) || mob.getPersistentData().getBoolean(EVENT_MOB);
    }

    /** Sets tier and extra strength (rift floors) and refills health. */
    public static void apply(Mob mob, int tier, double strength) {
        mob.getPersistentData().putInt(TIER, DangerMath.clamp(tier));
        mob.getPersistentData().putDouble(STRENGTH, strength);
        double bonus = (1.0 + DangerMath.healthBonus(tier)) * strength - 1.0;
        if (bonus > 0) Attr.setPermanent(mob, Attributes.MAX_HEALTH, HEALTH_ID, "rpgcore danger", bonus, AttributeModifier.Operation.MULTIPLY_TOTAL);
        else Attr.remove(mob, Attributes.MAX_HEALTH, HEALTH_ID);
        mob.setHealth(mob.getMaxHealth());
    }

    /** Tier stored on the mob, or the danger at its position. */
    public static int tier(LivingEntity entity) {
        if (entity.getPersistentData().contains(TIER)) return entity.getPersistentData().getInt(TIER);
        return DangerMap.get(entity.level(), entity.blockPosition());
    }

    public static double damageMultiplier(LivingEntity entity) {
        if (!entity.getPersistentData().contains(TIER)) return 1.0;
        double strength = entity.getPersistentData().contains(STRENGTH) ? entity.getPersistentData().getDouble(STRENGTH) : 1.0;
        return DangerMath.damageMultiplier(entity.getPersistentData().getInt(TIER)) * strength;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof Mob attacker && attacker.getPersistentData().contains(TIER)) {
            event.setAmount(event.getAmount() * (float) damageMultiplier(attacker));
        }
    }
}
