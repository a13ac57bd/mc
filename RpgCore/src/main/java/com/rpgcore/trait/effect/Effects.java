package com.rpgcore.trait.effect;

import com.google.gson.JsonObject;
import com.rpgcore.compat.IronsCompat;
import com.rpgcore.compat.TaczCompat;
import com.rpgcore.registry.RpgEffects;
import com.rpgcore.trait.ActiveTrait;
import com.rpgcore.trait.Num;
import com.rpgcore.trait.TraitContext;
import com.rpgcore.trait.TraitEngine;
import com.rpgcore.trait.TraitStacks;
import com.rpgcore.util.Attr;
import com.rpgcore.util.DamageUtil;
import com.rpgcore.util.Json;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Effect registry (TECH_SPEC §5.1). */
public final class Effects {
    private Effects() {}

    @FunctionalInterface
    public interface Factory {
        Effect create(JsonObject json);
    }

    private record Simple(String type, BiConsumer<TraitContext, ActiveTrait> action) implements Effect {
        @Override
        public void apply(TraitContext ctx, ActiveTrait trait) {
            action.accept(ctx, trait);
        }
    }

    /** Equip-time attribute modifier; applied by TraitCache, never at trigger time. */
    public record AttributeEffect(Attribute attribute, Num amount, AttributeModifier.Operation operation) implements Effect {
        @Override
        public String type() {
            return "attribute";
        }

        @Override
        public void apply(TraitContext ctx, ActiveTrait trait) {
        }
    }

    private static final Map<String, Factory> FACTORIES = new HashMap<>();

    public static void register(String type, Factory factory) {
        FACTORIES.put(type, factory);
    }

    public static Effect simple(String type, BiConsumer<TraitContext, ActiveTrait> action) {
        return new Simple(type, action);
    }

    public static Effect parse(JsonObject json) {
        String type = Json.str(json, "type", "");
        Factory f = FACTORIES.get(type);
        if (f == null) throw new IllegalArgumentException("unknown effect type '" + type + "'");
        return f.create(json);
    }

    public static Attribute attribute(JsonObject j) {
        ResourceLocation id = Json.id(j, "attribute");
        Attribute a = id == null ? null : ForgeRegistries.ATTRIBUTES.getValue(id);
        if (a == null) throw new IllegalArgumentException("unknown attribute " + id);
        return a;
    }

    static int ticks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }

    static {
        register("damage_mult", j -> {
            Num v = Num.parse(j, "value", 0.0);
            return simple("damage_mult", (c, t) -> c.bonus += v.get(t));
        });
        register("damage_mult_per_hit", j -> {
            Num per = Num.parse(j, "per", 0.1);
            int max = Json.integer(j, "max", 10);
            int reset = ticks(Json.num(j, "reset", 3.0));
            return simple("damage_mult_per_hit", (c, t) -> {
                if (c.target == null) return;
                int count = TraitStacks.streak(c.player, t.key("streak"), c.target.getUUID(), reset);
                c.bonus += per.get(t) * Math.min(max, count - 1);
            });
        });
        register("force_crit", j -> simple("force_crit", (c, t) -> c.forceCrit = true));
        register("force_headshot", j -> simple("force_headshot", (c, t) -> c.forceHeadshot = true));
        register("set_flag", j -> {
            String name = Json.str(j, "name", "flag");
            double seconds = Json.num(j, "seconds", 0);
            return simple("set_flag", (c, t) -> TraitStacks.setFlag(c.player, t.key(name), seconds <= 0 ? -1 : ticks(seconds)));
        });
        register("clear_flag", j -> {
            String name = Json.str(j, "name", "flag");
            return simple("clear_flag", (c, t) -> TraitStacks.clearFlag(c.player, t.key(name)));
        });
        register("counter_add", j -> {
            String name = Json.str(j, "name", "count");
            Num amount = Num.parse(j, "amount", 1);
            int reset = ticks(Json.num(j, "reset", 5.0));
            double max = Json.num(j, "max", Double.MAX_VALUE);
            return simple("counter_add", (c, t) -> TraitStacks.addCounter(c.player, t.key(name), amount.get(t), reset, max));
        });
        register("ignite", j -> {
            Num seconds = Num.parse(j, "seconds", 3);
            return simple("ignite", (c, t) -> {
                if (c.target != null) c.target.setSecondsOnFire((int) Math.ceil(seconds.get(t)));
            });
        });
        register("knockback", j -> {
            Num strength = Num.parse(j, "strength", 0.6);
            return simple("knockback", (c, t) -> {
                if (c.target != null) {
                    c.target.knockback(strength.get(t), c.player.getX() - c.target.getX(), c.player.getZ() - c.target.getZ());
                    c.target.hurtMarked = true;
                }
            });
        });
        register("stun", j -> {
            Num seconds = Num.parse(j, "seconds", 1);
            return simple("stun", (c, t) -> {
                if (c.target != null) c.target.addEffect(new MobEffectInstance(RpgEffects.STUN.get(), ticks(seconds.get(t)), 0, false, true), c.player);
            });
        });
        register("aoe_damage", j -> {
            double radius = Json.num(j, "radius", 3.0);
            Num fraction = Num.parse(j, "fraction", 0.0);
            Num value = Num.parse(j, "value", 0.0);
            return simple("aoe_damage", (c, t) -> {
                if (c.target == null) return;
                float amount = (float) (fraction.get(t) * c.damage + value.get(t));
                if (amount <= 0) return;
                LivingEntity center = c.target;
                List<LivingEntity> victims = center.level().getEntitiesOfClass(LivingEntity.class, center.getBoundingBox().inflate(radius),
                        e -> e != c.player && e != center && e.isAlive() && !e.isAlliedTo(c.player));
                TraitEngine.nested(() -> {
                    for (LivingEntity v : victims) v.hurt(DamageUtil.trait(c.player), amount);
                });
            });
        });
        register("unfreeze", j -> simple("unfreeze", (c, t) -> {
            if (c.target == null) return;
            c.target.setTicksFrozen(0);
            for (String id : new String[]{"irons_spellbooks:chilled", "irons_spellbooks:frozen"}) {
                MobEffect e = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(id));
                if (e != null) c.target.removeEffect(e);
            }
        }));
        register("heal_fraction", j -> {
            Num fraction = Num.parse(j, "fraction", 0.1);
            return simple("heal_fraction", (c, t) -> {
                float amount = (float) (c.damage * fraction.get(t));
                if (amount > 0) c.player.heal(amount);
            });
        });
        register("attribute", j -> new AttributeEffect(attribute(j), Num.parse(j, "amount", 0), Attr.op(Json.str(j, "operation", "addition"))));
        register("temp_attribute", j -> {
            Attribute attribute = attribute(j);
            Num amount = Num.parse(j, "amount", 0);
            AttributeModifier.Operation op = Attr.op(Json.str(j, "operation", "addition"));
            int duration = ticks(Json.num(j, "seconds", 5));
            ResourceLocation attrId = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            return simple("temp_attribute", (c, t) -> TraitStacks.tempAttribute(c.player, t.key("attr/" + attrId), attribute, amount.get(t), op, duration));
        });
        // compat effects: no-ops when the mod is missing
        register("refill_magazine", j -> {
            Num amount = Num.parse(j, "amount", 0);
            return simple("refill_magazine", (c, t) -> TaczCompat.refillMagazine(c.player, (int) Math.round(amount.get(t))));
        });
        register("extra_casts", j -> {
            int count = Json.integer(j, "count", 2);
            double spread = Json.num(j, "spread", 15);
            return simple("extra_casts", (c, t) -> IronsCompat.extraCasts(c, count, spread));
        });
    }
}
