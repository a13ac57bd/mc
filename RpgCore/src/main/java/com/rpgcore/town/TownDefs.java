package com.rpgcore.town;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.data.RpgData;
import com.rpgcore.util.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Town templates (data/&lt;ns&gt;/rpgcore/towns/*.json, §13): display name, radius and service NPCs placed relative to
 * the town center. Where a town is lives in TownSavedData (/rpgcore town create).
 */
public final class TownDefs {
    private TownDefs() {}

    public record NpcSpec(String role, String name, ResourceLocation dialogue, BlockPos offset) {}

    public record TownDef(ResourceLocation id, String name, int radius, List<NpcSpec> npcs) {}

    private static Map<ResourceLocation, TownDef> defs = Map.of();

    public static TownDef get(ResourceLocation id) {
        return defs.get(id);
    }

    public static Map<ResourceLocation, TownDef> all() {
        return defs;
    }

    public static void load(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, TownDef> loaded = new LinkedHashMap<>();
        RpgData.each("town", files, (id, json) -> {
            JsonObject j = json.getAsJsonObject();
            List<NpcSpec> npcs = new ArrayList<>();
            for (JsonElement e : Json.arr(j, "npcs")) {
                JsonObject n = e.getAsJsonObject();
                int[] off = new int[]{0, 0, 0};
                var arr = Json.arr(n, "offset");
                for (int i = 0; i < Math.min(3, arr.size()); i++) off[i] = arr.get(i).getAsInt();
                String role = Json.str(n, "role", "villager");
                npcs.add(new NpcSpec(role, Json.str(n, "name", "npc.rpgcore." + role), Json.id(n, "dialogue"), new BlockPos(off[0], off[1], off[2])));
            }
            loaded.put(id, new TownDef(id, Json.str(j, "name", "town." + id.getNamespace() + "." + id.getPath()), Json.integer(j, "radius", 64), npcs));
        });
        defs = loaded;
    }
}
