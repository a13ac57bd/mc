package com.rpgcore.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.data.RpgData;
import com.rpgcore.logic.QuestFlow;
import com.rpgcore.util.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loaded quest definitions. */
public final class QuestDefs {
    private QuestDefs() {}

    public static final Set<String> OBJECTIVES = Set.of("talk", "reach", "kill_specific", "find_item", "interact_block", "event_outcome");

    private static Map<ResourceLocation, QuestDef> defs = Map.of();

    public static QuestDef get(ResourceLocation id) {
        return id == null ? null : defs.get(id);
    }

    public static Map<ResourceLocation, QuestDef> all() {
        return defs;
    }

    public static void load(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, QuestDef> loaded = new LinkedHashMap<>();
        RpgData.each("quest", files, (id, json) -> loaded.put(id, parse(id, json.getAsJsonObject())));
        defs = loaded;
    }

    private static BlockPos pos(JsonObject o, String key) {
        var arr = Json.arr(o, key);
        if (arr.size() < 3) return null;
        return new BlockPos(arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt());
    }

    public static QuestDef parse(ResourceLocation id, JsonObject j) {
        String base = "quest." + id.getNamespace() + "." + id.getPath();
        List<QuestDef.Step> steps = new ArrayList<>();
        List<QuestFlow.Step> flowSteps = new ArrayList<>();
        for (JsonElement se : Json.arr(j, "steps")) {
            JsonObject s = se.getAsJsonObject();
            String sid = Json.str(s, "id", "s" + steps.size());
            JsonObject o = Json.obj(s, "objective");
            String type = Json.str(o, "type", "talk");
            if (!OBJECTIVES.contains(type)) throw new IllegalArgumentException("step " + sid + ": unknown objective " + type);
            if (type.equals("kill_specific") && o.has("count")) throw new IllegalArgumentException("step " + sid + ": kill_specific has no count (no 'kill N', §13)");
            QuestDef.Objective objective = new QuestDef.Objective(type, Json.str(o, "target", ""), pos(o, "pos"),
                    Json.integer(o, "radius", 8), Math.max(1, Json.integer(o, "count", 1)), Json.str(o, "value", null), Json.bool(o, "consume", false));
            Map<String, String> choices = new LinkedHashMap<>();
            Map<String, String> choiceTexts = new LinkedHashMap<>();
            for (JsonElement ce : Json.arr(s, "choices")) {
                JsonObject c = ce.getAsJsonObject();
                String cid = Json.str(c, "id", "c" + choices.size());
                choices.put(cid, Json.str(c, "next", QuestFlow.END));
                choiceTexts.put(cid, Json.str(c, "text", base + "." + sid + "." + cid));
            }
            String next = Json.str(s, "next", null);
            steps.add(new QuestDef.Step(sid, objective, Json.str(s, "text", base + "." + sid), pos(s, "nav"), next, choices, choiceTexts));
            flowSteps.add(new QuestFlow.Step(sid, next, choices));
        }
        QuestFlow flow = new QuestFlow(flowSteps);
        List<String> errors = flow.validate();
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        JsonObject r = Json.obj(j, "rewards");
        List<ItemStack> items = new ArrayList<>();
        for (JsonElement ie : Json.arr(r, "items")) {
            ItemStack stack = Json.stack(ie.getAsJsonObject());
            if (!stack.isEmpty()) items.add(stack);
        }
        QuestDef.Rewards rewards = new QuestDef.Rewards(items, Json.integer(r, "xp", 0), Json.id(r, "loot_source"));
        return new QuestDef(id, Json.str(j, "title", base + ".title"), List.copyOf(steps), flow, rewards);
    }
}
