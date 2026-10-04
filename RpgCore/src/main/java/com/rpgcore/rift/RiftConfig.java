package com.rpgcore.rift;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.util.Json;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** data/&lt;ns&gt;/rpgcore/rift/config.json (§11): every rift number lives here. */
public final class RiftConfig {
    private RiftConfig() {}

    public static int seconds = 600;
    public static double strengthPerFloor = 0.15;
    public static double rewardPerFloor = 0.20;
    public static int bossStartFloor = 5;
    public static double bossChance = 0.2;
    public static ResourceLocation boss = new ResourceLocation("rpgcore", "test_boss");
    public static int baseMobs = 4;
    public static int mobsPerFloor = 1;
    public static List<ResourceLocation> mobs = List.of(new ResourceLocation("minecraft", "zombie"), new ResourceLocation("minecraft", "skeleton"));
    /** Chance per player per night; 1/3 = on average every 3 days. */
    public static double portalChance = 1.0 / 3.0;
    public static int portalMin = 50;
    public static int portalMax = 150;
    public static int roomRadius = 10;

    public static void load(Map<ResourceLocation, JsonElement> files) {
        for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
            if (!e.getKey().getPath().equals("config")) continue;
            JsonObject j = e.getValue().getAsJsonObject();
            seconds = Json.integer(j, "seconds", seconds);
            strengthPerFloor = Json.num(j, "strength_per_floor", strengthPerFloor);
            rewardPerFloor = Json.num(j, "reward_per_floor", rewardPerFloor);
            bossStartFloor = Json.integer(j, "boss_start_floor", bossStartFloor);
            bossChance = Json.num(j, "boss_chance", bossChance);
            ResourceLocation b = Json.id(j, "boss");
            if (b != null) boss = b;
            baseMobs = Json.integer(j, "base_mobs", baseMobs);
            mobsPerFloor = Json.integer(j, "mobs_per_floor", mobsPerFloor);
            List<ResourceLocation> m = new ArrayList<>();
            for (ResourceLocation id : Json.ids(j, "mobs")) if (ForgeRegistries.ENTITY_TYPES.containsKey(id)) m.add(id);
            if (!m.isEmpty()) mobs = m;
            portalChance = Json.num(j, "portal_chance", portalChance);
            portalMin = Json.integer(j, "portal_min", portalMin);
            portalMax = Json.integer(j, "portal_max", portalMax);
            roomRadius = Json.integer(j, "room_radius", roomRadius);
        }
    }
}
