package com.rpgcore.boss;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.data.RpgData;
import com.rpgcore.util.Json;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loaded boss definitions. */
public final class BossDefs {
    private BossDefs() {}

    private static Map<ResourceLocation, BossDef> defs = Map.of();

    public static BossDef get(ResourceLocation id) {
        return id == null ? null : defs.get(id);
    }

    public static Map<ResourceLocation, BossDef> all() {
        return defs;
    }

    public static void load(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, BossDef> loaded = new LinkedHashMap<>();
        RpgData.each("boss", files, (id, json) -> loaded.put(id, parse(id, json.getAsJsonObject())));
        defs = loaded;
    }

    public static BossDef parse(ResourceLocation id, JsonObject j) {
        List<BossDef.Move> moves = new ArrayList<>();
        for (JsonElement me : Json.arr(j, "moves")) {
            JsonObject m = me.getAsJsonObject();
            List<Integer> phases = new ArrayList<>();
            for (JsonElement p : Json.arr(m, "phases")) phases.add(p.getAsInt());
            String name = Json.str(m, "name", "move" + moves.size());
            moves.add(new BossDef.Move(name, Json.str(m, "animation", name), BossDef.Shape.byId(Json.str(m, "shape", "circle")),
                    Json.num(m, "range", 4), Json.num(m, "angle", 90), Json.num(m, "width", 1.5), Json.num(m, "windup", 1.0),
                    Json.num(m, "damage", 8), Json.num(m, "cooldown", 4), List.copyOf(phases)));
        }
        if (moves.isEmpty()) throw new IllegalArgumentException("boss has no moves");
        List<BossDef.Phase> phases = new ArrayList<>();
        for (JsonElement pe : Json.arr(j, "phases")) {
            JsonObject p = pe.getAsJsonObject();
            phases.add(new BossDef.Phase(Json.num(p, "threshold", 0.5), Json.num(p, "transition", 2.0)));
        }
        phases.sort(Comparator.comparingDouble(BossDef.Phase::threshold).reversed());
        String name = Json.str(j, "name", "boss." + id.getNamespace() + "." + id.getPath());
        return new BossDef(id, name, Json.num(j, "health", 80), Json.num(j, "damage", 8), Json.num(j, "speed", 0.28),
                Json.num(j, "armor", 4), List.copyOf(moves), List.copyOf(phases));
    }
}
