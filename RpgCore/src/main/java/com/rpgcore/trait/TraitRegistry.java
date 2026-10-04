package com.rpgcore.trait;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.RpgCore;
import com.rpgcore.data.RpgData;
import com.rpgcore.logic.School;
import com.rpgcore.logic.TraitRules;
import com.rpgcore.logic.Trigger;
import com.rpgcore.trait.condition.Condition;
import com.rpgcore.trait.condition.Conditions;
import com.rpgcore.trait.effect.Effect;
import com.rpgcore.trait.effect.Effects;
import com.rpgcore.util.Json;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loaded trait definitions. Invalid traits (bad JSON or cross-school, #3) are rejected and logged. */
public final class TraitRegistry {
    private TraitRegistry() {}

    private static Map<ResourceLocation, TraitDef> defs = Map.of();
    private static Map<School, List<TraitDef>> minors = Map.of();

    public static TraitDef get(ResourceLocation id) {
        return id == null ? null : defs.get(id);
    }

    public static Collection<TraitDef> all() {
        return defs.values();
    }

    /** Minor traits a piece of gear of {@code school} can roll: that school plus "any". */
    public static List<TraitDef> minors(School school) {
        List<TraitDef> out = new ArrayList<>(minors.getOrDefault(school, List.of()));
        if (school != School.ANY) out.addAll(minors.getOrDefault(School.ANY, List.of()));
        return out;
    }

    public static void load(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, TraitDef> loaded = new LinkedHashMap<>();
        RpgData.each("trait", files, (id, json) -> loaded.put(id, parse(id, json.getAsJsonObject())));
        Map<School, List<TraitDef>> bySchool = new EnumMap<>(School.class);
        for (TraitDef d : loaded.values()) {
            if (d.type() == TraitDef.Type.MINOR) bySchoolList(bySchool, d.school()).add(d);
        }
        defs = loaded;
        minors = bySchool;
        TraitCache.invalidateAll();
        RpgCore.LOG.info("rpgcore: {} traits loaded", loaded.size());
    }

    private static List<TraitDef> bySchoolList(Map<School, List<TraitDef>> map, School s) {
        return map.computeIfAbsent(s, k -> new ArrayList<>());
    }

    public static TraitDef parse(ResourceLocation id, JsonObject json) {
        TraitDef.Type type = TraitDef.Type.byId(Json.str(json, "type", "minor"));
        if (type == null) throw new IllegalArgumentException("unknown type " + Json.str(json, "type", ""));
        School school = School.byId(Json.str(json, "school", "any"));
        if (school == null) throw new IllegalArgumentException("unknown school " + Json.str(json, "school", ""));
        double[] roll = Json.rangeD(json, "roll", 0, 0);
        String tooltip = Json.str(json, "tooltip", null);

        JsonArray rulesJson;
        if (json.has("rules")) {
            rulesJson = Json.arr(json, "rules");
        } else {
            // shorthand: a single rule at top level
            rulesJson = new JsonArray();
            rulesJson.add(json);
        }
        List<TraitDef.Rule> rules = new ArrayList<>();
        List<TraitRules.RuleView> views = new ArrayList<>();
        for (JsonElement re : rulesJson) {
            JsonObject r = re.getAsJsonObject();
            Trigger trigger = Trigger.byId(Json.str(r, "trigger", ""));
            List<Condition> conditions = new ArrayList<>();
            List<String> conditionTypes = new ArrayList<>();
            for (JsonElement ce : Json.arr(r, "conditions")) {
                Condition c = Conditions.parse(ce.getAsJsonObject());
                conditions.add(c);
                conditionTypes.add(c.type());
            }
            List<Effect> effects = new ArrayList<>();
            List<String> effectTypes = new ArrayList<>();
            for (JsonElement ee : Json.arr(r, "effects")) {
                Effect e = Effects.parse(ee.getAsJsonObject());
                effects.add(e);
                effectTypes.add(e.type());
            }
            views.add(new TraitRules.RuleView(trigger, conditionTypes, effectTypes));
            rules.add(new TraitDef.Rule(trigger, List.copyOf(conditions), List.copyOf(effects), Json.bool(r, "recursive", false)));
        }
        List<String> errors = TraitRules.validate(school, views);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        if (type == TraitDef.Type.MINOR && roll[1] <= 0) {
            RpgCore.LOG.warn("rpgcore: minor trait {} has no roll range", id);
        }
        return new TraitDef(id, type, school, roll[0], roll[1], tooltip, List.copyOf(rules));
    }
}
