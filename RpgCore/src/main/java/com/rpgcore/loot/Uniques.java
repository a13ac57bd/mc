package com.rpgcore.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.RpgCore;
import com.rpgcore.item.ItemData;
import com.rpgcore.logic.Rarity;
import com.rpgcore.logic.School;
import com.rpgcore.trait.TraitDef;
import com.rpgcore.trait.TraitRegistry;
import com.rpgcore.util.Json;
import com.rpgcore.util.Rng;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Legendary/artifact definitions (data/&lt;ns&gt;/rpgcore/uniques/*.json, §5.1): item, nbt (TACZ guns: {GunId:"rpg:..."}),
 * rarity, fixed traits, pool. Name: unique.&lt;ns&gt;.&lt;path&gt;, description: .desc
 */
public final class Uniques {
    private Uniques() {}

    public record UniqueDef(ResourceLocation id, ItemStack template, Rarity rarity, List<ResourceLocation> traits, String pool, School school) {
        public String nameKey() {
            return "unique." + id.getNamespace() + "." + id.getPath();
        }
    }

    private static Map<ResourceLocation, UniqueDef> defs = Map.of();

    public static UniqueDef get(ResourceLocation id) {
        return defs.get(id);
    }

    public static Map<ResourceLocation, UniqueDef> all() {
        return defs;
    }

    public static void load(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, UniqueDef> loaded = new LinkedHashMap<>();
        int skipped = 0;
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            try {
                UniqueDef d = parse(e.getKey(), e.getValue().getAsJsonObject());
                if (d == null) skipped++;
                else loaded.put(d.id(), d);
            } catch (Exception ex) {
                RpgCore.LOG.error("rpgcore: bad unique {}: {}", e.getKey(), ex.toString());
            }
        }
        defs = loaded;
        RpgCore.LOG.info("rpgcore: {} uniques ({} skipped, base item missing)", loaded.size(), skipped);
    }

    private static UniqueDef parse(ResourceLocation id, JsonObject j) {
        ItemStack stack = Json.stack(j);
        if (stack.isEmpty()) return null;
        Rarity rarity = Rarity.byId(Json.str(j, "rarity", "legendary"));
        if (rarity == null || !rarity.isUnique()) throw new IllegalArgumentException("rarity must be legendary or artifact");
        List<ResourceLocation> traits = Json.ids(j, "traits");
        School school = School.ANY;
        for (ResourceLocation t : traits) {
            TraitDef def = TraitRegistry.get(t);
            if (def == null) throw new IllegalArgumentException("unknown trait " + t);
            if (def.school() != School.ANY) school = def.school();
        }
        return new UniqueDef(id, stack, rarity, List.copyOf(traits), Json.str(j, "pool", "world"), school);
    }

    public static List<UniqueDef> pool(String pool) {
        List<UniqueDef> out = new ArrayList<>();
        for (UniqueDef d : defs.values()) if (d.pool().equals(pool)) out.add(d);
        return out;
    }

    public static UniqueDef pickFromPool(String pool, RandomSource random) {
        return Rng.pick(pool(pool), random);
    }

    /** A unique at {@code tier}: fixed traits locked, plus 0-1 minors of the same school (50%, #23). */
    public static ItemStack create(UniqueDef def, int tier, RandomSource random) {
        ItemStack stack = def.template().copy();
        ItemData.setRarity(stack, def.rarity());
        ItemData.setTier(stack, tier);
        ItemData.setUnique(stack, def.id());
        ItemData.setSchool(stack, def.school());
        List<ItemData.TraitRoll> traits = new ArrayList<>();
        Set<ResourceLocation> used = new HashSet<>();
        for (ResourceLocation t : def.traits()) {
            TraitDef td = TraitRegistry.get(t);
            if (td == null) continue;
            traits.add(Generator.roll(td, random, true));
            used.add(t);
        }
        traits.addAll(Generator.rollMinors(def.school(), def.rarity().minorCount(random.nextDouble()), used, random));
        ItemData.setTraits(stack, traits);
        stack.setHoverName(Component.translatable(def.nameKey()).withStyle(s -> s.withItalic(false).withColor(def.rarity().color())));
        return stack;
    }
}
