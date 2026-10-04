package com.rpgcore.ecology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.RpgCore;
import com.rpgcore.danger.DangerMap;
import com.rpgcore.data.RpgData;
import com.rpgcore.util.Json;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Spawn tables (data/&lt;ns&gt;/rpgcore/spawns/*.json, §12): by dimension, danger range and day/night, "add" entries to
 * the monster list or "replace" it. World events add tables at runtime through {@link #EXTRA}.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class SpawnTables {
    private SpawnTables() {}

    public record Entry(EntityType<?> type, int weight, int min, int max) {}

    public record TableDef(ResourceLocation id, ResourceLocation dimension, int minDanger, int maxDanger, String time, boolean replace, List<Entry> entries) {}

    private static List<TableDef> tables = List.of();
    /** Runtime tables (world events). */
    public static final List<TableDef> EXTRA = new CopyOnWriteArrayList<>();

    public static void load(Map<ResourceLocation, JsonElement> files) {
        List<TableDef> loaded = new ArrayList<>();
        RpgData.each("spawn table", files, (id, json) -> loaded.add(parse(id, json.getAsJsonObject())));
        tables = loaded;
    }

    public static TableDef parse(ResourceLocation id, JsonObject j) {
        int[] danger = Json.range(j, "danger", 1, 5);
        List<Entry> entries = new ArrayList<>();
        for (JsonElement e : Json.arr(j, "add")) {
            JsonObject o = e.getAsJsonObject();
            ResourceLocation typeId = Json.id(o, "type");
            // the entity registry returns pig for unknown ids, so check membership first
            if (typeId == null || !ForgeRegistries.ENTITY_TYPES.containsKey(typeId)) continue;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(typeId);
            entries.add(new Entry(type, Json.integer(o, "weight", 10), Json.integer(o, "min", 1), Json.integer(o, "max", 1)));
        }
        return new TableDef(id, Json.id(j, "dimension"), danger[0], danger[1], Json.str(j, "time", "any"), Json.bool(j, "replace", false), entries);
    }

    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER || !(event.getLevel() instanceof ServerLevel level)) return;
        if (tables.isEmpty() && EXTRA.isEmpty()) return;
        int tier = -1;
        boolean day = level.isDay();
        List<TableDef> all = new ArrayList<>(tables);
        all.addAll(EXTRA);
        for (TableDef t : all) {
            if (t.dimension() != null && !t.dimension().equals(level.dimension().location())) continue;
            if (t.time().equals("day") && !day || t.time().equals("night") && day) continue;
            if (tier < 0) tier = DangerMap.get(level, event.getPos());
            if (tier < t.minDanger() || tier > t.maxDanger()) continue;
            if (t.replace()) {
                for (MobSpawnSettings.SpawnerData d : new ArrayList<>(event.getSpawnerDataList())) event.removeSpawnerData(d);
            }
            for (Entry e : t.entries()) event.addSpawnerData(new MobSpawnSettings.SpawnerData(e.type(), e.weight(), e.min(), e.max()));
        }
    }
}
