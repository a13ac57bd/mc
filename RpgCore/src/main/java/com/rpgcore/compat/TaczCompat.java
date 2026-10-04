package com.rpgcore.compat;

import com.rpgcore.item.ItemData;
import com.rpgcore.logic.CombatMath;
import com.rpgcore.logic.LootMath;
import com.rpgcore.logic.Trigger;
import com.rpgcore.registry.RpgAttributes;
import com.rpgcore.trait.TraitContext;
import com.rpgcore.trait.TraitEngine;
import com.rpgcore.util.Attr;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * TACZ: gun damage (ranged_power x gun tier, crits) and the gun_* triggers (§5.2, §8).
 * EntityHurtByGunEvent.Pre changes the bullet's base damage and headshot before TACZ applies it.
 */
public final class TaczCompat {
    private TaczCompat() {}

    private static final String EVENTS = "com.tacz.guns.api.event.common.";
    /** Hits per bullet entity, for the pierce index. */
    private static final Map<Entity, Integer> BULLET_HITS = new WeakHashMap<>();
    /** (attacker, target) -> {gameTime, count}, for pellets per shot. */
    private static final Map<Long, long[]> PELLETS = new HashMap<>();

    public static void setup() {
        Ref.listen(EVENTS + "EntityHurtByGunEvent$Pre", EventPriority.NORMAL, TaczCompat::onHurtPre);
        Ref.listen(EVENTS + "EntityHurtByGunEvent$Post", EventPriority.NORMAL, TaczCompat::onHurtPost);
        Ref.listen(EVENTS + "GunFireEvent", EventPriority.NORMAL, TaczCompat::onFire);
        Ref.listen(EVENTS + "GunReloadEvent", EventPriority.NORMAL, TaczCompat::onReload);
        Ref.listen(EVENTS + "EntityKillByGunEvent", EventPriority.NORMAL, TaczCompat::onKill);
    }

    private static ServerPlayer attacker(Object event) {
        return Ref.call(event, "getAttacker") instanceof ServerPlayer p ? p : null;
    }

    private static LivingEntity hurt(Object event) {
        return Ref.call(event, "getHurtEntity") instanceof LivingEntity e ? e : null;
    }

    private static void onHurtPre(Object event) {
        ServerPlayer p = attacker(event);
        LivingEntity target = hurt(event);
        if (p == null || target == null) return;
        float base = Ref.asFloat(Ref.call(event, "getBaseAmount"), 0F);
        int hitIndex = 1;
        if (Ref.call(event, "getBullet") instanceof Entity bullet) {
            hitIndex = BULLET_HITS.merge(bullet, 1, Integer::sum);
        }
        double power = Attr.value(p, RpgAttributes.RANGED_POWER, 1.0) * LootMath.tierMultiplier(ItemData.tier(p.getMainHandItem()));
        TraitContext ctx = new TraitContext(p, Trigger.GUN_HIT).target(target).damage((float) (base * power));
        ctx.headshot = Ref.asBool(Ref.call(event, "isHeadShot"));
        ctx.hitIndex = hitIndex;
        if (TraitEngine.has(p, Trigger.GUN_HIT)) TraitEngine.fire(ctx);
        boolean crit = ctx.forceCrit || p.getRandom().nextDouble() < Attr.value(p, RpgAttributes.CRIT_CHANCE, 0.0);
        float result = ctx.finalDamage() * (float) CombatMath.crit(crit, Attr.value(p, RpgAttributes.CRIT_DAMAGE, 1.5));
        Ref.call(event, "setBaseAmount", result);
        if (ctx.forceHeadshot && !ctx.headshot) Ref.call(event, "setHeadshot", true);
        if (crit) p.crit(target);
    }

    private static void onHurtPost(Object event) {
        ServerPlayer p = attacker(event);
        LivingEntity target = hurt(event);
        if (p == null || target == null || !TraitEngine.has(p, Trigger.GUN_DEALT)) return;
        long key = ((long) p.getId() << 32) | (target.getId() & 0xFFFFFFFFL);
        long now = p.level().getGameTime();
        long[] entry = PELLETS.get(key);
        if (entry == null || entry[0] != now) {
            if (PELLETS.size() > 256) PELLETS.clear();
            entry = new long[]{now, 0};
            PELLETS.put(key, entry);
        }
        entry[1]++;
        TraitContext ctx = new TraitContext(p, Trigger.GUN_DEALT).target(target).damage(Ref.asFloat(Ref.call(event, "getBaseAmount"), 0F));
        ctx.headshot = Ref.asBool(Ref.call(event, "isHeadShot"));
        ctx.pellets = (int) entry[1];
        TraitEngine.fire(ctx);
    }

    private static void onFire(Object event) {
        if (Ref.call(event, "getShooter") instanceof ServerPlayer p && TraitEngine.has(p, Trigger.GUN_SHOOT)) {
            TraitEngine.fire(new TraitContext(p, Trigger.GUN_SHOOT));
        }
    }

    private static void onReload(Object event) {
        if (Ref.call(event, "getEntity") instanceof ServerPlayer p && TraitEngine.has(p, Trigger.GUN_RELOAD)) {
            TraitEngine.fire(new TraitContext(p, Trigger.GUN_RELOAD));
        }
    }

    private static void onKill(Object event) {
        ServerPlayer p = attacker(event);
        if (p == null || !TraitEngine.has(p, Trigger.GUN_KILL)) return;
        TraitContext ctx = new TraitContext(p, Trigger.GUN_KILL);
        if (Ref.call(event, "getKilledEntity") instanceof LivingEntity killed) ctx.target(killed);
        ctx.headshot = Ref.asBool(Ref.call(event, "isHeadShot"));
        TraitEngine.fire(ctx);
    }

    /** refill_magazine: {@code amount} rounds, or a full magazine when amount &lt;= 0. */
    public static void refillMagazine(ServerPlayer p, int amount) {
        if (!Compat.tacz) return;
        ItemStack gun = p.getMainHandItem();
        Class<?> iGunType = Ref.type("com.tacz.guns.api.item.IGun");
        Object iGun = Ref.callStatic(iGunType, "getIGunOrNull", gun);
        if (iGun == null) return;
        int current = Ref.asInt(Ref.call(iGun, "getCurrentAmmoCount", gun), 0);
        int max = magazineSize(iGun, gun);
        int target = amount <= 0 ? max : Math.min(max, current + amount);
        if (target > current) Ref.call(iGun, "setCurrentAmmoCount", gun, target);
    }

    private static int magazineSize(Object iGun, ItemStack gun) {
        Object gunId = Ref.call(iGun, "getGunId", gun);
        if (!(gunId instanceof ResourceLocation)) return 30;
        Object index = Ref.callStatic(Ref.type("com.tacz.guns.api.TimelessAPI"), "getCommonGunIndex", gunId);
        if (index instanceof Optional<?> opt && opt.isPresent()) {
            Object data = Ref.call(opt.get(), "getGunData");
            return Ref.asInt(Ref.call(data, "getAmmoAmount"), 30);
        }
        return 30;
    }
}
