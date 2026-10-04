package com.rpgcore.trait.condition;

import com.google.gson.JsonObject;
import com.rpgcore.trait.ActiveTrait;
import com.rpgcore.trait.Num;
import com.rpgcore.trait.TraitContext;
import com.rpgcore.trait.TraitStacks;
import com.rpgcore.util.Json;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/** Condition registry (TECH_SPEC §5.1). Compat code may {@link #register} more types. */
public final class Conditions {
    private Conditions() {}

    @FunctionalInterface
    public interface Factory {
        Condition create(JsonObject json);
    }

    private record Simple(String type, BiPredicate<TraitContext, ActiveTrait> predicate) implements Condition {
        @Override
        public boolean test(TraitContext ctx, ActiveTrait trait) {
            return predicate.test(ctx, trait);
        }
    }

    private static final Map<String, Factory> FACTORIES = new HashMap<>();

    public static void register(String type, Factory factory) {
        FACTORIES.put(type, factory);
    }

    public static Condition simple(String type, BiPredicate<TraitContext, ActiveTrait> predicate) {
        return new Simple(type, predicate);
    }

    public static Condition parse(JsonObject json) {
        String type = Json.str(json, "type", "");
        Factory f = FACTORIES.get(type);
        if (f == null) throw new IllegalArgumentException("unknown condition type '" + type + "'");
        return f.create(json);
    }

    /** Iron's "frozen" style effects that count as frozen for target_frozen. */
    private static final ResourceLocation[] FROZEN_EFFECTS = {
            new ResourceLocation("irons_spellbooks", "chilled"),
            new ResourceLocation("irons_spellbooks", "frozen")
    };

    public static boolean isFrozen(LivingEntity e) {
        if (e.getTicksFrozen() > 0 || e.isFullyFrozen()) return true;
        for (ResourceLocation id : FROZEN_EFFECTS) {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(id);
            if (effect != null && e.hasEffect(effect)) return true;
        }
        return false;
    }

    static {
        register("distance", j -> {
            double min = Json.num(j, "min", 0), max = Json.num(j, "max", Double.MAX_VALUE);
            return simple("distance", (c, t) -> c.target != null && c.distance >= min && c.distance <= max);
        });
        register("hit_index", j -> {
            int eq = Json.integer(j, "eq", -1), min = Json.integer(j, "min", 1), max = Json.integer(j, "max", Integer.MAX_VALUE);
            return simple("hit_index", (c, t) -> eq >= 0 ? c.hitIndex == eq : c.hitIndex >= min && c.hitIndex <= max);
        });
        register("pellets", j -> {
            int eq = Json.integer(j, "eq", Json.integer(j, "value", 1));
            return simple("pellets", (c, t) -> c.pellets == eq);
        });
        register("counter", j -> {
            String name = Json.str(j, "name", "count");
            double min = Json.num(j, "min", Double.NEGATIVE_INFINITY), max = Json.num(j, "max", Double.MAX_VALUE);
            return simple("counter", (c, t) -> {
                double v = TraitStacks.counter(c.player, t.key(name));
                return v >= min && v <= max;
            });
        });
        register("flag", j -> {
            String name = Json.str(j, "name", "flag");
            return simple("flag", (c, t) -> TraitStacks.flag(c.player, t.key(name)));
        });
        register("not_flag", j -> {
            String name = Json.str(j, "name", "flag");
            return simple("not_flag", (c, t) -> !TraitStacks.flag(c.player, t.key(name)));
        });
        register("spell", j -> {
            Set<ResourceLocation> ids = new HashSet<>(Json.ids(j, "ids"));
            ids.addAll(Json.ids(j, "spells"));
            return simple("spell", (c, t) -> c.spell != null && ids.contains(c.spell));
        });
        register("spell_school", j -> {
            String school = Json.str(j, "school", "").toLowerCase(Locale.ROOT);
            return simple("spell_school", (c, t) -> c.spellSchool != null && matchesSchool(c.spellSchool, school));
        });
        register("target_frozen", j -> simple("target_frozen", (c, t) -> c.target != null && isFrozen(c.target)));
        register("crit", j -> simple("crit", (c, t) -> c.isCrit()));
        register("headshot", j -> simple("headshot", (c, t) -> c.isHeadshot()));
        register("chance", j -> {
            Num chance = Num.parse(j, "value", 0.5);
            return simple("chance", (c, t) -> c.player.getRandom().nextDouble() < chance.get(t));
        });
    }

    /** "fire" matches "irons_spellbooks:fire" and vice versa. */
    static boolean matchesSchool(String actual, String wanted) {
        String a = actual.toLowerCase(Locale.ROOT);
        if (a.equals(wanted)) return true;
        String ap = a.contains(":") ? a.substring(a.indexOf(':') + 1) : a;
        String wp = wanted.contains(":") ? wanted.substring(wanted.indexOf(':') + 1) : wanted;
        return ap.equals(wp);
    }
}
