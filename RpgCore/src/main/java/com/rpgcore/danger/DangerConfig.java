package com.rpgcore.danger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.logic.DangerMath;
import com.rpgcore.util.Json;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * data/&lt;ns&gt;/rpgcore/danger/config.json (§7): ring distances, fixed tiers per dimension, biome modifiers
 * ("#tag" or id) and fixed tiers per structure. Several files merge; later namespaces override.
 */
public final class DangerConfig {
    private DangerConfig() {}

    public record BiomeMod(TagKey<Biome> tag, ResourceLocation id, int modifier) {
        public boolean matches(Holder<Biome> biome) {
            return tag != null ? biome.is(tag) : biome.is(id);
        }
    }

    public static int[] rings = DangerMath.DEFAULT_RINGS.clone();
    public static Map<ResourceLocation, Integer> dimensions = Map.of();
    public static List<BiomeMod> biomes = List.of();
    public static Map<ResourceLocation, Integer> structures = Map.of();

    public static void load(Map<ResourceLocation, JsonElement> files) {
        int[] r = DangerMath.DEFAULT_RINGS.clone();
        Map<ResourceLocation, Integer> dims = new LinkedHashMap<>();
        List<BiomeMod> bio = new ArrayList<>();
        Map<ResourceLocation, Integer> structs = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            if (!e.getKey().getPath().equals("config")) continue;
            JsonObject j = e.getValue().getAsJsonObject();
            if (j.has("rings")) {
                var arr = Json.arr(j, "rings");
                r = new int[arr.size()];
                for (int i = 0; i < arr.size(); i++) r[i] = arr.get(i).getAsInt();
            }
            Json.obj(j, "dimensions").entrySet().forEach(d -> dims.put(ResourceLocation.parse(d.getKey()), d.getValue().getAsInt()));
            Json.obj(j, "biomes").entrySet().forEach(b -> {
                String key = b.getKey();
                int mod = b.getValue().getAsInt();
                if (key.startsWith("#")) bio.add(new BiomeMod(TagKey.create(Registries.BIOME, ResourceLocation.parse(key.substring(1))), null, mod));
                else bio.add(new BiomeMod(null, ResourceLocation.parse(key), mod));
            });
            Json.obj(j, "structures").entrySet().forEach(s -> structs.put(ResourceLocation.parse(s.getKey()), s.getValue().getAsInt()));
        }
        rings = r;
        dimensions = dims;
        biomes = bio;
        structures = structs;
        DangerMap.clearCache();
    }
}
