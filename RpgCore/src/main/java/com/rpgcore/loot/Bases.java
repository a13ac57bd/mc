package com.rpgcore.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.RpgCore;
import com.rpgcore.data.RpgData;
import com.rpgcore.logic.LootMath;
import com.rpgcore.logic.School;
import com.rpgcore.util.Json;
import com.rpgcore.util.Rng;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gear bases (data/&lt;ns&gt;/rpgcore/bases/*.json, §6 #46): melee, bow, gun, spell, armor, accessory.
 * Bases whose item is not installed are skipped at load.
 */
public final class Bases {
    private Bases() {}

    /** {@code school == null}: armor/accessories roll a random gear school. */
    public record BaseDef(ResourceLocation id, ItemStack template, String category, School school, int minTier, int maxTier, int weight) {
        public boolean fits(int tier) {
            return tier >= minTier && tier <= maxTier;
        }
    }

    private static List<BaseDef> bases = List.of();

    public static List<BaseDef> all() {
        return bases;
    }

    public static void load(Map<ResourceLocation, JsonElement> files) {
        List<BaseDef> loaded = new ArrayList<>();
        int skipped = 0;
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            try {
                BaseDef d = parse(e.getKey(), e.getValue().getAsJsonObject());
                if (d == null) skipped++;
                else loaded.add(d);
            } catch (Exception ex) {
                RpgCore.LOG.error("rpgcore: bad base {}: {}", e.getKey(), ex.toString());
            }
        }
        bases = List.copyOf(loaded);
        RpgCore.LOG.info("rpgcore: {} gear bases ({} skipped, mod missing)", loaded.size(), skipped);
    }

    private static BaseDef parse(ResourceLocation id, JsonObject j) {
        ItemStack stack = Json.stack(j);
        if (stack.isEmpty()) return null;
        String category = Json.str(j, "category", "melee");
        School school = School.byId(Json.str(j, "school", defaultSchool(category)));
        int[] tiers = Json.range(j, "tiers", LootMath.MIN_TIER, LootMath.MAX_TIER);
        return new BaseDef(id, stack, category, school, tiers[0], tiers[1], Math.max(1, Json.integer(j, "weight", 10)));
    }

    private static String defaultSchool(String category) {
        return switch (category) {
            case "melee" -> "melee";
            case "bow" -> "bow";
            case "gun" -> "gun";
            case "spell" -> "spell";
            default -> "random";
        };
    }

    /** Weighted pick among bases that allow {@code tier}; {@code category} null = any. */
    public static BaseDef pick(String category, int tier, RandomSource random) {
        List<BaseDef> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (BaseDef b : bases) {
            if (b.fits(tier) && (category == null || b.category().equals(category))) {
                pool.add(b);
                weights.add(b.weight());
            }
        }
        if (pool.isEmpty()) return null;
        int idx = LootMath.pickWeighted(weights, Rng.of(random));
        return idx < 0 ? Rng.pick(pool, random) : pool.get(idx);
    }

    public static BaseDef get(ResourceLocation id) {
        for (BaseDef b : bases) if (b.id().equals(id)) return b;
        return null;
    }
}
