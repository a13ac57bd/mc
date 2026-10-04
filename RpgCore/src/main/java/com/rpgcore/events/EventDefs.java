package com.rpgcore.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.data.RpgData;
import com.rpgcore.ecology.SpawnTables;
import com.rpgcore.logic.EventMachine;
import com.rpgcore.util.Json;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loaded world event definitions. */
public final class EventDefs {
    private EventDefs() {}

    private static final Set<String> TRIGGERS = Set.of("boss_killed", "chapter_done", "location_discovered", "quest_done");

    private static Map<ResourceLocation, EventDef> defs = Map.of();

    public static EventDef get(ResourceLocation id) {
        return defs.get(id);
    }

    public static Map<ResourceLocation, EventDef> all() {
        return defs;
    }

    public static void load(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, EventDef> loaded = new LinkedHashMap<>();
        RpgData.each("event", files, (id, json) -> loaded.put(id, parse(id, json.getAsJsonObject())));
        defs = loaded;
        WorldEvents.rebind();
    }

    public static EventDef parse(ResourceLocation id, JsonObject j) {
        JsonObject trig = Json.obj(j, "trigger");
        String type = Json.str(trig, "type", "");
        if (!TRIGGERS.contains(type)) throw new IllegalArgumentException("trigger must be one of " + TRIGGERS + " (#14), got " + type);
        String trigger = type + ":" + Json.str(trig, "id", "");

        JsonObject r = Json.obj(j, "region");
        int[] center = Json.range(r, "center", 0, 0);
        EventDef.Region region = new EventDef.Region(Json.id(r, "town"), Json.id(r, "dimension"), center[0], center[1], Json.integer(r, "radius", 192));

        Map<String, EventMachine.State> states = new LinkedHashMap<>();
        Map<String, EventDef.Effects> effects = new LinkedHashMap<>();
        Map<String, EventDef.Goal> goals = new LinkedHashMap<>();
        Map<String, ResourceLocation> rewards = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : Json.obj(j, "states").entrySet()) {
            String sid = e.getKey();
            JsonObject s = e.getValue().getAsJsonObject();
            states.put(sid, new EventMachine.State(sid, Json.integer(s, "days", 0), Json.str(s, "next", null),
                    Json.str(s, "on_success", null), Json.str(s, "outcome", null)));
            if (s.has("effects")) effects.put(sid, parseEffects(id, sid, Json.obj(s, "effects")));
            if (s.has("goal")) {
                JsonObject g = Json.obj(s, "goal");
                goals.put(sid, new EventDef.Goal(Json.str(g, "type", "kill_specific"), Json.id(g, "id")));
            }
            ResourceLocation reward = Json.id(s, "rewards");
            if (reward != null) rewards.put(sid, reward);
        }
        EventMachine.Def machine = new EventMachine.Def(id.toString(), Json.str(j, "initial", ""), states);
        List<String> errors = EventMachine.validate(machine);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        return new EventDef(id, trigger, region, machine, effects, goals, rewards);
    }

    private static EventDef.Effects parseEffects(ResourceLocation id, String state, JsonObject o) {
        SpawnTables.TableDef spawns = o.has("spawns")
                ? SpawnTables.parse(new ResourceLocation(id.getNamespace(), id.getPath() + "/" + state), Json.obj(o, "spawns"))
                : null;
        return new EventDef.Effects(Json.integer(o, "danger", 0), spawns, Json.bool(o, "hide_npcs", false), Json.id(o, "boss"),
                Json.bool(o, "smoke", false), Json.integer(o, "refugees", 0), Json.bool(o, "corruption", false), Json.str(o, "dialogue", null));
    }
}
