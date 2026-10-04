package com.rpgcore.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.RpgCore;
import com.rpgcore.data.RpgData;
import com.rpgcore.logic.LootMath;
import com.rpgcore.logic.LootRoller;
import com.rpgcore.logic.Rarity;
import com.rpgcore.registry.RpgItems;
import com.rpgcore.util.Json;
import com.rpgcore.util.Rng;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loot sources (data/&lt;ns&gt;/rpgcore/loot_sources/*.json, §6, numbers per MECHANICS §5 #37):
 * fixed drops ("#material" = tier material, "#ammo" = one of {@code options}), gear chance and rarity weights,
 * legendary chance / pool / first-kill guarantee.
 */
public final class LootSources {
    private LootSources() {}

    public static final ResourceLocation MOB = RpgCore.id("mob");
    public static final ResourceLocation ELITE = RpgCore.id("elite");
    public static final ResourceLocation BOSS = RpgCore.id("boss");
    public static final ResourceLocation CHEST = RpgCore.id("chest");
    public static final ResourceLocation RIFT = RpgCore.id("rift");

    public record Fixed(String item, int min, int max, double chance, List<ItemStack> options) {}

    public record SourceDef(ResourceLocation id, List<Fixed> fixed, double equipmentChance, double[] rarityWeights,
                            double legendaryChance, String pool, boolean firstKill, int rolls, String category) {
        LootRoller.Spec spec() {
            return new LootRoller.Spec(equipmentChance, rarityWeights, legendaryChance, rolls);
        }
    }

    private static Map<ResourceLocation, SourceDef> sources = Map.of();

    public static SourceDef get(ResourceLocation id) {
        return sources.get(id);
    }

    public static Map<ResourceLocation, SourceDef> all() {
        return sources;
    }

    public static void load(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, SourceDef> loaded = new LinkedHashMap<>();
        RpgData.each("loot source", files, (id, json) -> loaded.put(id, parse(id, json.getAsJsonObject())));
        sources = loaded;
    }

    private static SourceDef parse(ResourceLocation id, JsonObject j) {
        List<Fixed> fixed = new ArrayList<>();
        for (JsonElement fe : Json.arr(j, "fixed")) {
            JsonObject f = fe.getAsJsonObject();
            int[] count = Json.range(f, "count", 1, 1);
            List<ItemStack> options = new ArrayList<>();
            for (JsonElement oe : Json.arr(f, "options")) {
                ItemStack s = Json.stack(oe.getAsJsonObject());
                if (!s.isEmpty()) options.add(s);
            }
            fixed.add(new Fixed(Json.str(f, "item", "#material"), count[0], count[1], Json.num(f, "chance", 1.0), options));
        }
        JsonObject eq = Json.obj(j, "equipment");
        JsonObject weights = Json.obj(eq, "rarity");
        double[] w = {Json.num(weights, "common", 60), Json.num(weights, "uncommon", 30), Json.num(weights, "rare", 10)};
        JsonObject leg = Json.obj(j, "legendary");
        return new SourceDef(id, fixed, Json.num(eq, "chance", 0.0), w,
                Json.num(leg, "chance", 0.0), Json.str(leg, "pool", "world"), Json.bool(leg, "first_kill", false),
                Math.max(1, Json.integer(eq, "rolls", 1)), Json.str(eq, "category", null));
    }

    /**
     * Rolls a source for a player at a tier. Updates the player's pity counter; {@code bossId} enables the
     * first-kill guarantee (once per player per boss).
     */
    public static List<ItemStack> roll(ResourceLocation sourceId, ServerPlayer player, int tier, RandomSource random, ResourceLocation bossId) {
        List<ItemStack> out = new ArrayList<>();
        SourceDef src = sources.get(sourceId);
        if (src == null) return out;
        tier = LootMath.clampTier(tier);
        for (Fixed f : src.fixed()) {
            if (random.nextDouble() >= f.chance()) continue;
            int count = LootMath.rollInt(f.min(), f.max(), Rng.of(random));
            if (count <= 0) continue;
            ItemStack stack = switch (f.item()) {
                case "#material" -> new ItemStack(RpgItems.essence(tier), count);
                case "#ammo" -> {
                    ItemStack opt = Rng.pick(f.options(), random);
                    yield opt == null ? ItemStack.EMPTY : opt.copyWithCount(count);
                }
                default -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("item", f.item());
                    o.addProperty("count", count);
                    yield Json.stack(o);
                }
            };
            if (!stack.isEmpty()) out.add(stack);
        }

        boolean force = false;
        if (src.firstKill() && bossId != null && player != null && !PlayerLootData.killedBoss(player, bossId)) {
            force = true;
            PlayerLootData.markBoss(player, bossId);
        }
        int misses = player == null ? 0 : PlayerLootData.misses(player, sourceId);
        LootRoller.Result result = LootRoller.roll(src.spec(), misses, force, Rng.of(random));
        if (player != null && src.legendaryChance() > 0) PlayerLootData.setMisses(player, sourceId, result.misses());
        for (Rarity r : result.equipment()) {
            ItemStack gear = Generator.gear(r, tier, src.category(), random);
            if (!gear.isEmpty()) out.add(gear);
        }
        if (result.legendary()) {
            Uniques.UniqueDef def = Uniques.pickFromPool(src.pool(), random);
            if (def != null) out.add(Uniques.create(def, tier, random));
        }
        return out;
    }
}
