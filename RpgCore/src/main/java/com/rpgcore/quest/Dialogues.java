package com.rpgcore.quest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.RpgCore;
import com.rpgcore.data.RpgData;
import com.rpgcore.events.ProgressSavedData;
import com.rpgcore.events.WorldEvents;
import com.rpgcore.forge.ForgeMenu;
import com.rpgcore.forge.ForgeTableBlock;
import com.rpgcore.net.RpgNetwork;
import com.rpgcore.net.RpgPackets;
import com.rpgcore.town.NpcEntity;
import com.rpgcore.town.TownSavedData;
import com.rpgcore.util.Inv;
import com.rpgcore.util.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * NPC dialogue (§13): a small box with the NPC's name, 2-4 lines and at most 3 options. All text lives in the lang files.
 * Dialogue trees come from data/&lt;ns&gt;/rpgcore/dialogues/*.json; quest "talk" steps add their own options automatically,
 * and an active world event can add a line (hints instead of notifications, §10).
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Dialogues {
    private Dialogues() {}

    public static final int MAX_OPTIONS = 3;

    public record Cond(String type, String a, String b) {}

    public record Action(String type, String a, String b, int count) {}

    public record Option(String text, List<Cond> conditions, List<Action> actions, String next) {}

    public record Node(List<String> lines, List<Option> options) {}

    public record DialogueDef(ResourceLocation id, String start, Map<String, Node> nodes) {}

    /** What an on-screen option does: a static option, a quest choice, or completing a talk step. */
    private record Visible(Option option, ResourceLocation quest, String choice, String text) {}

    private record Session(int entityId, ResourceLocation dialogue, List<Visible> visible) {}

    private static Map<ResourceLocation, DialogueDef> defs = Map.of();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static DialogueDef get(ResourceLocation id) {
        return id == null ? null : defs.get(id);
    }

    public static void load(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, DialogueDef> loaded = new LinkedHashMap<>();
        RpgData.each("dialogue", files, (id, json) -> loaded.put(id, parse(id, json.getAsJsonObject())));
        defs = loaded;
    }

    private static DialogueDef parse(ResourceLocation id, JsonObject j) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : Json.obj(j, "nodes").entrySet()) {
            JsonObject n = e.getValue().getAsJsonObject();
            List<Option> options = new ArrayList<>();
            for (JsonElement oe : Json.arr(n, "options")) {
                JsonObject o = oe.getAsJsonObject();
                List<Cond> conds = new ArrayList<>();
                for (JsonElement ce : Json.arr(o, "conditions")) {
                    JsonObject c = ce.getAsJsonObject();
                    conds.add(new Cond(Json.str(c, "type", ""), Json.str(c, "quest", Json.str(c, "flag", Json.str(c, "clue", ""))), Json.str(c, "step", "")));
                }
                List<Action> actions = new ArrayList<>();
                for (JsonElement ae : Json.arr(o, "actions")) {
                    JsonObject a = ae.getAsJsonObject();
                    actions.add(new Action(Json.str(a, "type", ""), Json.str(a, "quest", Json.str(a, "flag", Json.str(a, "clue", Json.str(a, "item", "")))),
                            Json.str(a, "choice", ""), Json.integer(a, "count", 1)));
                }
                options.add(new Option(Json.str(o, "text", "dialogue.rpgcore.bye"), conds, actions, Json.str(o, "next", null)));
            }
            List<String> lines = Json.strings(n, "lines");
            if (lines.size() > 4) throw new IllegalArgumentException("node " + e.getKey() + " has more than 4 lines");
            nodes.put(e.getKey(), new Node(lines, options));
        }
        String start = Json.str(j, "start", nodes.isEmpty() ? "" : nodes.keySet().iterator().next());
        if (!nodes.containsKey(start)) throw new IllegalArgumentException("start node " + start + " missing");
        return new DialogueDef(id, start, nodes);
    }

    // ---------- runtime ----------

    public static void open(ServerPlayer p, NpcEntity npc) {
        DialogueDef def = get(npc.dialogue());
        show(p, npc, def, def == null ? null : def.start());
    }

    private static void show(ServerPlayer p, NpcEntity npc, DialogueDef def, String nodeId) {
        Node node = def == null ? null : def.nodes().get(nodeId);
        List<String> lines = new ArrayList<>();
        if (npc.town() != null && (nodeId == null || def == null || nodeId.equals(def.start()))) {
            String eventLine = WorldEvents.dialogueFor(npc.town());
            if (eventLine != null) lines.add(eventLine);
        }
        if (node != null) lines.addAll(node.lines());
        if (lines.isEmpty()) lines.add("dialogue.rpgcore.default");

        List<Visible> visible = new ArrayList<>();
        // quest options first: talk steps aimed at this NPC
        for (ResourceLocation q : QuestLog.activeQuests(p)) {
            QuestDef.Step s = Quests.currentStep(p, q);
            if (s == null || !s.objective().type().equals("talk") || !s.objective().target().equals(npc.npcId())) continue;
            if (s.choices().isEmpty()) {
                visible.add(new Visible(null, q, null, s.text()));
            } else {
                for (String choice : s.choices().keySet()) visible.add(new Visible(null, q, choice, s.choiceTexts().get(choice)));
            }
        }
        if (node != null) {
            for (Option o : node.options()) if (passes(p, o.conditions())) visible.add(new Visible(o, null, null, o.text()));
        }
        if (visible.isEmpty()) visible.add(new Visible(new Option("dialogue.rpgcore.bye", List.of(), List.of(), null), null, null, "dialogue.rpgcore.bye"));
        if (visible.size() > MAX_OPTIONS) visible = new ArrayList<>(visible.subList(0, MAX_OPTIONS));

        SESSIONS.put(p.getUUID(), new Session(npc.getId(), def == null ? null : def.id(), visible));
        List<String> texts = new ArrayList<>();
        for (Visible v : visible) texts.add(v.text());
        RpgNetwork.toPlayer(p, new RpgPackets.Dialogue(npc.getId(), npc.nameKey(), lines, texts));
    }

    public static void choose(ServerPlayer p, int entityId, int index) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.entityId() != entityId || index < 0 || index >= s.visible().size()) {
            SESSIONS.remove(p.getUUID());
            return;
        }
        Entity e = p.level().getEntity(entityId);
        if (!(e instanceof NpcEntity npc) || p.distanceToSqr(npc) > 64) {
            close(p, entityId);
            return;
        }
        Visible v = s.visible().get(index);
        if (v.quest() != null) {
            Quests.talk(p, npc.npcId(), v.choice());
            show(p, npc, get(s.dialogue()), get(s.dialogue()) == null ? null : get(s.dialogue()).start());
            return;
        }
        boolean keepOpen = true;
        for (Action a : v.option().actions()) keepOpen &= run(p, npc, a);
        if (keepOpen && v.option().next() != null) show(p, npc, get(s.dialogue()), v.option().next());
        else close(p, entityId);
    }

    private static void close(ServerPlayer p, int entityId) {
        SESSIONS.remove(p.getUUID());
        RpgNetwork.toPlayer(p, new RpgPackets.Dialogue(entityId, "", List.of(), List.of()));
    }

    private static boolean passes(ServerPlayer p, List<Cond> conds) {
        for (Cond c : conds) if (!test(p, c)) return false;
        return true;
    }

    private static boolean test(ServerPlayer p, Cond c) {
        ResourceLocation quest = ResourceLocation.tryParse(c.a());
        return switch (c.type()) {
            case "quest_not_started" -> quest != null && !QuestLog.isActive(p, quest) && !QuestLog.isDone(p, quest);
            case "quest_active" -> quest != null && QuestLog.isActive(p, quest);
            case "quest_step" -> quest != null && QuestLog.isActive(p, quest) && QuestLog.step(p, quest).equals(c.b());
            case "quest_done" -> quest != null && QuestLog.isDone(p, quest);
            case "flag" -> p.getServer() != null && ProgressSavedData.get(p.getServer()).has(c.a());
            case "not_flag" -> p.getServer() == null || !ProgressSavedData.get(p.getServer()).has(c.a());
            case "clue" -> QuestLog.clues(p).contains(c.a());
            default -> false;
        };
    }

    /** Runs one action; returns false when the dialogue should close (e.g. a screen was opened). */
    private static boolean run(ServerPlayer p, NpcEntity npc, Action a) {
        ResourceLocation id = ResourceLocation.tryParse(a.a());
        switch (a.type()) {
            case "start_quest" -> {
                if (id != null) Quests.start(p, id);
            }
            case "complete_talk" -> Quests.talk(p, npc.npcId(), a.b().isEmpty() ? null : a.b());
            case "choose" -> {
                if (id != null) Quests.advance(p, id, a.b());
            }
            case "track" -> {
                if (id != null) {
                    QuestLog.setTracked(p, id);
                    Quests.sync(p);
                }
            }
            case "set_flag" -> {
                if (p.getServer() != null) ProgressSavedData.get(p.getServer()).set(p.getServer(), a.a());
            }
            case "add_clue" -> {
                if (QuestLog.addClue(p, a.a())) p.displayClientMessage(Component.translatable("quest.rpgcore.clue", Component.translatable(a.a())), true);
            }
            case "give" -> {
                JsonObject o = new JsonObject();
                o.addProperty("item", a.a());
                o.addProperty("count", a.count());
                ItemStack stack = Json.stack(o);
                Inv.give(p, stack);
            }
            case "rest" -> {
                BlockPos at = npc.blockPosition();
                if (npc.town() != null && p.getServer() != null) {
                    TownSavedData.Town town = TownSavedData.get(p.getServer()).get(npc.town());
                    if (town != null && town.inn != null) at = town.inn.above();
                }
                p.setRespawnPosition(p.level().dimension(), at, p.getYRot(), true, false);
                p.displayClientMessage(Component.translatable("block.rpgcore.inn_bed.set"), true);
            }
            case "open_forge" -> {
                NetworkHooks.openScreen(p, new SimpleMenuProvider((wid, inv, pl) -> new ForgeMenu(wid, inv, ContainerLevelAccess.NULL), ForgeTableBlock.TITLE));
                return false;
            }
            default -> RpgCore.LOG.warn("rpgcore: unknown dialogue action {}", a.type());
        }
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }
}
