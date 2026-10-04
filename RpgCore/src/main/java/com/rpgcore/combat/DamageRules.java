package com.rpgcore.combat;

import com.rpgcore.RpgCore;
import com.rpgcore.item.ItemData;
import com.rpgcore.logic.CombatMath;
import com.rpgcore.logic.LootMath;
import com.rpgcore.logic.Trigger;
import com.rpgcore.registry.RpgAttributes;
import com.rpgcore.registry.RpgTags;
import com.rpgcore.trait.TraitContext;
import com.rpgcore.trait.TraitEngine;
import com.rpgcore.util.Attr;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * School damage (§8): melee x melee_power, bow x ranged_power x bow tier; crits for melee/bow (guns in TaczCompat).
 * Spells get nothing here (#3). Also the melee/bow trait triggers (§5.2).
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class DamageRules {
    private DamageRules() {}

    public enum Kind { MELEE, BOW, GUN, SPELL, OTHER }

    public static final String ARROW_TIER = "rpgcore_tier";

    /** Hit info handed from LivingHurtEvent to the following LivingDamageEvent of the same victim. */
    private record Pending(LivingEntity target, boolean crit) {}

    private static Pending pending;

    public static Kind classify(DamageSource source) {
        if (source.is(RpgTags.SPELL)) return Kind.SPELL;
        Entity direct = source.getDirectEntity();
        if (source.is(RpgTags.GUN) || (direct != null && direct.getType().is(RpgTags.GUN_PROJECTILE))) return Kind.GUN;
        if (direct instanceof AbstractArrow) return Kind.BOW;
        if (source.getEntity() != null && direct == source.getEntity() && (source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK))) {
            return Kind.MELEE;
        }
        return Kind.OTHER;
    }

    /** Remembers the bow's tier on arrows shot by players (bow damage x weapon tier, #45). */
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getOwner() instanceof ServerPlayer p)) return;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = p.getItemInHand(hand);
            if (held.getItem() instanceof ProjectileWeaponItem) {
                arrow.getPersistentData().putInt(ARROW_TIER, ItemData.tier(held));
                return;
            }
        }
    }

    private static int arrowTier(Entity direct) {
        if (direct == null || !direct.getPersistentData().contains(ARROW_TIER)) return 1;
        return direct.getPersistentData().getInt(ARROW_TIER);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        pending = null;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        Kind kind = classify(event.getSource());
        if (kind != Kind.MELEE && kind != Kind.BOW) return;
        LivingEntity target = event.getEntity();
        double power;
        Trigger hit;
        if (kind == Kind.MELEE) {
            power = Attr.value(p, RpgAttributes.MELEE_POWER, 1.0);
            hit = Trigger.MELEE_HIT;
        } else {
            power = Attr.value(p, RpgAttributes.RANGED_POWER, 1.0) * LootMath.tierMultiplier(arrowTier(event.getSource().getDirectEntity()));
            hit = Trigger.BOW_HIT;
        }
        TraitContext ctx = new TraitContext(p, hit).target(target).source(event.getSource()).damage((float) (event.getAmount() * power));
        if (TraitEngine.has(p, hit)) TraitEngine.fire(ctx);
        boolean crit = ctx.forceCrit || p.getRandom().nextDouble() < Attr.value(p, RpgAttributes.CRIT_CHANCE, 0.0);
        ctx.crit = crit;
        float result = ctx.finalDamage() * (float) CombatMath.crit(crit, Attr.value(p, RpgAttributes.CRIT_DAMAGE, 1.5));
        event.setAmount(result);
        if (crit) p.crit(target);
        pending = new Pending(target, crit);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent event) {
        Pending pend = pending;
        pending = null;
        if (!(event.getSource().getEntity() instanceof ServerPlayer p)) return;
        Kind kind = classify(event.getSource());
        if (kind != Kind.MELEE && kind != Kind.BOW) return;
        Trigger dealt = kind == Kind.MELEE ? Trigger.MELEE_DEALT : Trigger.BOW_DEALT;
        if (!TraitEngine.has(p, dealt)) return;
        TraitContext ctx = new TraitContext(p, dealt).target(event.getEntity()).source(event.getSource()).damage(event.getAmount());
        if (pend != null && pend.target() == event.getEntity()) ctx.crit = pend.crit();
        TraitEngine.fire(ctx);
    }
}
