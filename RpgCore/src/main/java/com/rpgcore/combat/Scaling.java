package com.rpgcore.combat;

import com.rpgcore.RpgCore;
import com.rpgcore.logic.CombatMath;
import com.rpgcore.registry.RpgTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Number scale x5 (#16 #17, §8): everything is computed in vanilla units; only the final damage and heal are x5.
 * Players get base health 100 (stat/Stats); other living entities get a x5 MULTIPLY_TOTAL modifier.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Scaling {
    private Scaling() {}

    public static final UUID HEALTH_ID = UUID.fromString("5b0a6f8e-2c1d-4d3e-9f8a-7b6c5d4e3f21");

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof LivingEntity living) || living instanceof Player) return;
        apply(living);
    }

    /** Adds the x5 health modifier once (fixed UUID); refills health if the entity was at full health. */
    public static void apply(LivingEntity living) {
        if (living.getType().is(RpgTags.NO_HEALTH_SCALE)) return;
        AttributeInstance inst = living.getAttribute(Attributes.MAX_HEALTH);
        if (inst == null || inst.getModifier(HEALTH_ID) != null) return;
        boolean full = living.getHealth() >= living.getMaxHealth() - 1.0E-3F;
        inst.addPermanentModifier(new AttributeModifier(HEALTH_ID, "rpgcore x5 health", CombatMath.HEALTH_MODIFIER, AttributeModifier.Operation.MULTIPLY_TOTAL));
        if (full) living.setHealth(living.getMaxHealth());
    }

    /** After armor, enchantments and absorption (vanilla armor math depends on the absolute damage). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent event) {
        if (event.getSource().is(RpgTags.NO_SCALE)) return;
        event.setAmount(event.getAmount() * CombatMath.SCALE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        event.setAmount(event.getAmount() * CombatMath.SCALE);
    }
}
