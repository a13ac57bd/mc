package com.rpgcore.trait.trigger;

import com.rpgcore.RpgCore;
import com.rpgcore.combat.DashEvent;
import com.rpgcore.logic.Trigger;
import com.rpgcore.trait.TraitCache;
import com.rpgcore.trait.TraitContext;
import com.rpgcore.trait.TraitEngine;
import com.rpgcore.trait.TraitStacks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cache maintenance and the generic triggers (kill, hurt, dash). Melee/bow hits come from combat/DamageRules,
 * gun and spell hits from compat.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class TraitEvents {
    private TraitEvents() {}

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) TraitCache.markDirty(p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) TraitCache.markDirty(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) TraitCache.markDirty(p);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) TraitCache.markDirty(p);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            TraitCache.remove(p);
            TraitStacks.clear(p);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        if (TraitCache.isDirty(p)) TraitCache.recompute(p);
        if (p.tickCount % 10 == 0) TraitStacks.tick(p);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer p) || event.getEntity() == p) return;
        if (!TraitEngine.has(p, Trigger.KILL)) return;
        TraitEngine.fire(new TraitContext(p, Trigger.KILL).target(event.getEntity()).source(event.getSource()));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !TraitEngine.has(p, Trigger.HURT)) return;
        TraitContext ctx = new TraitContext(p, Trigger.HURT).source(event.getSource()).damage(event.getAmount());
        if (event.getSource().getEntity() instanceof LivingEntity attacker) ctx.target(attacker);
        TraitEngine.fire(ctx);
    }

    @SubscribeEvent
    public static void onDash(DashEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && TraitEngine.has(p, Trigger.DASH)) {
            TraitEngine.fire(new TraitContext(p, Trigger.DASH));
        }
    }
}
