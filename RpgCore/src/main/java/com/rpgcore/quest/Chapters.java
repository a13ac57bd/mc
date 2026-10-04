package com.rpgcore.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.data.RpgData;
import com.rpgcore.events.ProgressSavedData;
import com.rpgcore.logic.EndingTable;
import com.rpgcore.util.Json;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main story (§13): 4 chapters, each a ChapterDef whose quest line ends in a choice written to ProgressSavedData.
 * The endgame needs at least 3 chapters; the ending is looked up from the choices (endings.json).
 */
public final class Chapters {
    private Chapters() {}

    /** @param choiceStep the quest step whose choice is the chapter's choice; {@code key} is its name in endings.json */
    public record ChapterDef(ResourceLocation id, ResourceLocation quest, int order, String choiceStep, String key) {}

    private static Map<ResourceLocation, ChapterDef> chapters = Map.of();
    private static List<EndingTable.Rule> endingRules = List.of();
    private static String defaultEnding = "ending.rpgcore.default";

    public static Map<ResourceLocation, ChapterDef> all() {
        return chapters;
    }

    public static void load(Map<ResourceLocation, JsonElement> chapterFiles, Map<ResourceLocation, JsonElement> endingFiles) {
        Map<ResourceLocation, ChapterDef> loaded = new LinkedHashMap<>();
        RpgData.each("chapter", chapterFiles, (id, json) -> {
            JsonObject j = json.getAsJsonObject();
            loaded.put(id, new ChapterDef(id, Json.id(j, "quest"), Json.integer(j, "order", loaded.size() + 1),
                    Json.str(j, "choice_step", ""), Json.str(j, "key", id.getPath())));
        });
        chapters = loaded;
        List<EndingTable.Rule> rules = new ArrayList<>();
        String fallback = "ending.rpgcore.default";
        for (JsonElement e : endingFiles.values()) {
            JsonObject j = e.getAsJsonObject();
            fallback = Json.str(j, "default", fallback);
            for (JsonElement re : Json.arr(j, "rules")) {
                JsonObject r = re.getAsJsonObject();
                Map<String, String> when = new LinkedHashMap<>();
                Json.obj(r, "when").entrySet().forEach(w -> when.put(w.getKey(), w.getValue().getAsString()));
                rules.add(new EndingTable.Rule(when, Json.str(r, "ending", fallback)));
            }
        }
        endingRules = rules;
        defaultEnding = fallback;
    }

    public static ChapterDef forQuest(ResourceLocation quest) {
        for (ChapterDef c : chapters.values()) if (quest.equals(c.quest())) return c;
        return null;
    }

    /** Called when any quest finishes: marks the chapter done and stores its choice. */
    public static void onQuestDone(ServerPlayer p, QuestDef def) {
        ChapterDef chapter = forQuest(def.id());
        MinecraftServer server = p.getServer();
        if (chapter == null || server == null) return;
        ProgressSavedData progress = ProgressSavedData.get(server);
        String choice = chapter.choiceStep().isEmpty() ? null : QuestLog.choiceMade(p, def.id(), chapter.choiceStep());
        if (choice != null) progress.setChoice(chapter.key(), choice);
        progress.set(server, ProgressSavedData.chapterDone(chapter.id()));
        if (EndingTable.endgameReady(progress.chaptersDone())) {
            p.displayClientMessage(Component.translatable("story.rpgcore.endgame_ready"), false);
        }
    }

    /** The ending for the world's choices, or null before the endgame (fewer than 3 chapters). */
    public static String ending(MinecraftServer server) {
        ProgressSavedData progress = ProgressSavedData.get(server);
        if (!EndingTable.endgameReady(progress.chaptersDone())) return null;
        return EndingTable.resolve(endingRules, defaultEnding, progress.choices());
    }
}
