package com.rpgcore.quest;

import com.rpgcore.RpgCore;
import com.rpgcore.danger.DangerMap;
import com.rpgcore.events.ProgressSavedData;
import com.rpgcore.loot.LootSources;
import com.rpgcore.net.RpgNetwork;
import com.rpgcore.net.RpgPackets;
import com.rpgcore.util.Inv;
import com.rpgcore.util.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Quest runtime (§13): starts quests, checks objectives, applies choices, rewards, journal and navigation.
 * Finishing a quest sets quest_done:&lt;id&gt;; finishing a chapter quest also sets chapter_done and records the choice.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Quests {
    private Quests() {}

    public static boolean start(ServerPlayer p, ResourceLocation questId) {
        QuestDef def = QuestDefs.get(questId);
        if (def == null || def.steps().isEmpty() || QuestLog.isActive(p, questId) || QuestLog.isDone(p, questId)) return false;
        QuestLog.start(p, questId, def.steps().get(0).id());
        p.displayClientMessage(Component.translatable("quest.rpgcore.started", Component.translatable(def.titleKey())), true);
        sync(p);
        return true;
    }

    public static QuestDef.Step currentStep(ServerPlayer p, ResourceLocation questId) {
        QuestDef def = QuestDefs.get(questId);
        return def == null ? null : def.step(QuestLog.step(p, questId));
    }

    /** Completes the current step (with an optional choice) and moves on, finishing the quest when the flow ends. */
    public static void advance(ServerPlayer p, ResourceLocation questId, String choice) {
        QuestDef def = QuestDefs.get(questId);
        if (def == null || !QuestLog.isActive(p, questId)) return;
        String step = QuestLog.step(p, questId);
        QuestDef.Step s = def.step(step);
        if (s == null) return;
        if (!s.choices().isEmpty()) {
            if (choice == null || !s.choices().containsKey(choice)) return;
            QuestLog.recordChoice(p, questId, step, choice);
        }
        if (s.objective().consume() && s.objective().type().equals("find_item")) {
            Item item = Json.item(s.objective().target());
            if (item != null) Inv.take(p, item, s.objective().count());
        }
        String next = def.flow().next(step, choice);
        if (next == null) finish(p, def);
        else QuestLog.setStep(p, questId, next);
        sync(p);
    }

    private static void finish(ServerPlayer p, QuestDef def) {
        QuestLog.complete(p, def.id());
        for (ItemStack s : def.rewards().items()) Inv.give(p, s.copy());
        if (def.rewards().xp() > 0) p.giveExperiencePoints(def.rewards().xp());
        if (def.rewards().lootSource() != null) {
            for (ItemStack s : LootSources.roll(def.rewards().lootSource(), p, DangerMap.get(p.level(), p.blockPosition()), p.getRandom(), null)) Inv.give(p, s);
        }
        p.displayClientMessage(Component.translatable("quest.rpgcore.completed", Component.translatable(def.titleKey())), true);
        MinecraftServer server = p.getServer();
        if (server == null) return;
        ProgressSavedData progress = ProgressSavedData.get(server);
        progress.set(server, ProgressSavedData.questDone(def.id()));
        Chapters.onQuestDone(p, def);
    }

    // ---------- objective hooks ----------

    /** A talk objective is completed by the dialogue action; returns true if a step was advanced. */
    public static boolean talk(ServerPlayer p, String npcId, String choice) {
        boolean any = false;
        for (ResourceLocation q : QuestLog.activeQuests(p)) {
            QuestDef.Step s = currentStep(p, q);
            if (s != null && s.objective().type().equals("talk") && s.objective().target().equals(npcId)) {
                if (!s.choices().isEmpty() && choice == null) continue;
                advance(p, q, choice);
                any = true;
            }
        }
        return any;
    }

    /** kill_specific: named bosses/elites (boss id or entity type id). */
    public static void onKill(ServerPlayer p, LivingEntity victim, ResourceLocation id) {
        if (id == null) return;
        for (ResourceLocation q : QuestLog.activeQuests(p)) {
            QuestDef.Step s = currentStep(p, q);
            if (s != null && s.objective().type().equals("kill_specific") && s.objective().target().equals(id.toString())) advance(p, q, null);
        }
    }

    public static void onEventOutcome(MinecraftServer server, ResourceLocation event, String outcome) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            for (ResourceLocation q : QuestLog.activeQuests(p)) {
                QuestDef.Step s = currentStep(p, q);
                if (s == null || !s.objective().type().equals("event_outcome") || !s.objective().target().equals(event.toString())) continue;
                if (s.objective().value() == null || s.objective().value().equals(outcome)) advance(p, q, null);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        BlockPos pos = event.getPos();
        ResourceLocation block = ForgeRegistries.BLOCKS.getKey(p.level().getBlockState(pos).getBlock());
        for (ResourceLocation q : QuestLog.activeQuests(p)) {
            QuestDef.Step s = currentStep(p, q);
            if (s == null || !s.objective().type().equals("interact_block") || block == null) continue;
            if (!s.objective().target().equals(block.toString())) continue;
            if (s.objective().pos() != null && !s.objective().pos().equals(pos)) continue;
            advance(p, q, null);
        }
    }

    /** reach and find_item are polled once a second. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        for (ResourceLocation q : QuestLog.activeQuests(p)) {
            QuestDef.Step s = currentStep(p, q);
            if (s == null) continue;
            QuestDef.Objective o = s.objective();
            switch (o.type()) {
                case "reach" -> {
                    if (o.pos() != null && p.blockPosition().distSqr(o.pos()) <= (double) o.radius() * o.radius()) {
                        ResourceLocation loc = ResourceLocation.tryParse(o.target());
                        if (loc != null && p.getServer() != null) {
                            ProgressSavedData.get(p.getServer()).set(p.getServer(), ProgressSavedData.locationDiscovered(loc));
                        }
                        advance(p, q, null);
                    }
                }
                case "find_item" -> {
                    Item item = Json.item(o.target());
                    if (item != null && Inv.count(p, item) >= o.count()) advance(p, q, null);
                }
                default -> {
                }
            }
        }
        if (p.tickCount % 100 == 0) sendNav(p);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) sync(p);
    }

    // ---------- journal & navigation ----------

    public static void sync(ServerPlayer p) {
        sendJournal(p, false);
        sendNav(p);
    }

    public static void sendJournal(ServerPlayer p, boolean open) {
        List<RpgPackets.JournalEntry> entries = new ArrayList<>();
        for (ResourceLocation q : QuestLog.activeQuests(p)) {
            QuestDef def = QuestDefs.get(q);
            QuestDef.Step s = currentStep(p, q);
            if (def != null && s != null) entries.add(new RpgPackets.JournalEntry(def.titleKey(), s.text(), false));
        }
        for (QuestDef def : QuestDefs.all().values()) {
            if (QuestLog.isDone(p, def.id())) entries.add(new RpgPackets.JournalEntry(def.titleKey(), "quest.rpgcore.done", true));
        }
        RpgNetwork.toPlayer(p, new RpgPackets.Journal(entries, QuestLog.clues(p), QuestLog.navEnabled(p), open));
    }

    public static void toggleNav(ServerPlayer p) {
        QuestLog.setNav(p, !QuestLog.navEnabled(p));
        sync(p);
    }

    public static void sendNav(ServerPlayer p) {
        ResourceLocation tracked = QuestLog.tracked(p);
        QuestDef.Step s = tracked == null || !QuestLog.navEnabled(p) ? null : currentStep(p, tracked);
        BlockPos nav = s == null ? null : s.nav() != null ? s.nav() : s.objective().pos();
        if (nav == null) {
            RpgNetwork.toPlayer(p, new RpgPackets.NavTarget(false, 0, 0, 0, ""));
        } else {
            RpgNetwork.toPlayer(p, new RpgPackets.NavTarget(true, nav.getX() + 0.5, nav.getY(), nav.getZ() + 0.5, s.text()));
        }
    }
}
