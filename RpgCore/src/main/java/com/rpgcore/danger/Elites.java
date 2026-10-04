package com.rpgcore.danger;

import com.rpgcore.RpgCore;
import com.rpgcore.logic.DangerMath;
import com.rpgcore.util.Attr;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Elites (#26 #38, §7). rpgcore rolls the chance by danger (2-10%) when a hostile mob first joins the world and gives
 * 1/1/2/2/3 affixes. Native affixes replace Champions (whose Forge 1.20.1 build never makes champions on its own).
 * "Is elite" = has affixes. Elite loot comes from the rpgcore:elite loot source.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Elites {
    private Elites() {}

    public static final String TAG = "rpgcore_elite";
    /** Self-tests turn natural elites off so random elites do not change damage numbers. */
    public static boolean suppressed;

    public enum Affix {
        HARDY, SWIFT, BRUTAL, ARMORED, VAMPIRIC, MOLTEN, KNOCKBACK;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public UUID modifierId() {
            return UUID.nameUUIDFromBytes(("rpgcore:elite/" + id()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (suppressed || event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (!(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy) || MobScaling.skip(mob) || isElite(mob)) return;
        int tier = MobScaling.tier(mob);
        if (mob.getRandom().nextDouble() < DangerMath.eliteChance(tier)) makeElite(mob, DangerMath.eliteAffixes(tier), mob.getRandom());
    }

    public static boolean isElite(LivingEntity entity) {
        return !entity.getPersistentData().getList(TAG, Tag.TAG_STRING).isEmpty();
    }

    public static List<Affix> affixes(LivingEntity entity) {
        List<Affix> out = new ArrayList<>();
        ListTag list = entity.getPersistentData().getList(TAG, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            try {
                out.add(Affix.valueOf(list.getString(i).toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // affix removed in a newer version
            }
        }
        return out;
    }

    public static boolean has(LivingEntity entity, Affix affix) {
        return affixes(entity).contains(affix);
    }

    public static void makeElite(Mob mob, int count, RandomSource random) {
        List<Affix> pool = new ArrayList<>(Arrays.asList(Affix.values()));
        ListTag list = new ListTag();
        MutableComponent name = Component.empty();
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            Affix a = pool.remove(random.nextInt(pool.size()));
            list.add(StringTag.valueOf(a.id()));
            applyAffix(mob, a);
            name.append(Component.translatable("elite.rpgcore." + a.id())).append(" ");
        }
        mob.getPersistentData().put(TAG, list);
        mob.setCustomName(name.append(mob.getType().getDescription()).withStyle(s -> s.withColor(0xFFAA00)));
        mob.setCustomNameVisible(true);
        mob.setPersistenceRequired();
        mob.setHealth(mob.getMaxHealth());
    }

    private static void applyAffix(Mob mob, Affix a) {
        switch (a) {
            case HARDY -> Attr.setPermanent(mob, Attributes.MAX_HEALTH, a.modifierId(), "rpgcore elite", 0.5, AttributeModifier.Operation.MULTIPLY_TOTAL);
            case SWIFT -> Attr.setPermanent(mob, Attributes.MOVEMENT_SPEED, a.modifierId(), "rpgcore elite", 0.3, AttributeModifier.Operation.MULTIPLY_BASE);
            case ARMORED -> Attr.setPermanent(mob, Attributes.ARMOR, a.modifierId(), "rpgcore elite", 6.0, AttributeModifier.Operation.ADDITION);
            case MOLTEN -> mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != victim && isElite(attacker)) {
            List<Affix> affixes = affixes(attacker);
            if (affixes.contains(Affix.BRUTAL)) event.setAmount(event.getAmount() * 1.4F);
            if (affixes.contains(Affix.VAMPIRIC)) attacker.heal(event.getAmount() * 0.2F);
            if (affixes.contains(Affix.KNOCKBACK) && event.getSource().getDirectEntity() == attacker) {
                victim.knockback(1.2, attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ());
            }
        }
        if (isElite(victim) && has(victim, Affix.MOLTEN) && event.getSource().getDirectEntity() instanceof LivingEntity melee && melee != victim) {
            melee.setSecondsOnFire(3);
        }
    }
}
