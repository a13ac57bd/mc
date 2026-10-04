package com.rpgcore.quest;

import com.rpgcore.util.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-player quest state (§13 QuestLogCapability), stored in PlayerPersisted so it survives death:
 * active quests (current step + choices made), completed quests, clues, navigation on/off.
 */
public final class QuestLog {
    private QuestLog() {}

    private static CompoundTag root(Player p) {
        return PlayerData.section(p, "quests");
    }

    private static CompoundTag active(Player p) {
        CompoundTag r = root(p);
        if (!r.contains("active", Tag.TAG_COMPOUND)) r.put("active", new CompoundTag());
        return r.getCompound("active");
    }

    public static List<ResourceLocation> activeQuests(Player p) {
        List<ResourceLocation> out = new ArrayList<>();
        for (String k : active(p).getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(k);
            if (id != null) out.add(id);
        }
        return out;
    }

    public static boolean isActive(Player p, ResourceLocation quest) {
        return active(p).contains(quest.toString());
    }

    public static String step(Player p, ResourceLocation quest) {
        return active(p).getCompound(quest.toString()).getString("step");
    }

    public static void setStep(Player p, ResourceLocation quest, String step) {
        CompoundTag a = active(p);
        CompoundTag q = a.getCompound(quest.toString());
        q.putString("step", step);
        q.putInt("progress", 0);
        a.put(quest.toString(), q);
    }

    public static int progress(Player p, ResourceLocation quest) {
        return active(p).getCompound(quest.toString()).getInt("progress");
    }

    public static void setProgress(Player p, ResourceLocation quest, int value) {
        CompoundTag a = active(p);
        CompoundTag q = a.getCompound(quest.toString());
        q.putInt("progress", value);
        a.put(quest.toString(), q);
    }

    public static Map<String, String> choices(Player p, ResourceLocation quest) {
        Map<String, String> out = new LinkedHashMap<>();
        CompoundTag c = active(p).getCompound(quest.toString()).getCompound("choices");
        for (String k : c.getAllKeys()) out.put(k, c.getString(k));
        return out;
    }

    public static void recordChoice(Player p, ResourceLocation quest, String step, String choice) {
        CompoundTag a = active(p);
        CompoundTag q = a.getCompound(quest.toString());
        CompoundTag c = q.getCompound("choices");
        c.putString(step, choice);
        q.put("choices", c);
        a.put(quest.toString(), q);
        // choices are also kept after completion (§13: choice record)
        CompoundTag hist = root(p).getCompound("history");
        hist.putString(quest + "#" + step, choice);
        root(p).put("history", hist);
    }

    /** Choice made at {@code step} of {@code quest}, active or completed. */
    public static String choiceMade(Player p, ResourceLocation quest, String step) {
        String key = quest + "#" + step;
        CompoundTag hist = root(p).getCompound("history");
        return hist.contains(key) ? hist.getString(key) : null;
    }

    public static void start(Player p, ResourceLocation quest, String firstStep) {
        setStep(p, quest, firstStep);
        if (tracked(p) == null) setTracked(p, quest);
    }

    public static void complete(Player p, ResourceLocation quest) {
        active(p).remove(quest.toString());
        CompoundTag r = root(p);
        ListTag done = r.getList("done", Tag.TAG_STRING);
        done.add(StringTag.valueOf(quest.toString()));
        r.put("done", done);
        if (quest.equals(tracked(p))) {
            List<ResourceLocation> rest = activeQuests(p);
            setTracked(p, rest.isEmpty() ? null : rest.get(0));
        }
    }

    public static boolean isDone(Player p, ResourceLocation quest) {
        ListTag done = root(p).getList("done", Tag.TAG_STRING);
        for (int i = 0; i < done.size(); i++) if (done.getString(i).equals(quest.toString())) return true;
        return false;
    }

    public static List<String> clues(Player p) {
        List<String> out = new ArrayList<>();
        ListTag l = root(p).getList("clues", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    public static boolean addClue(Player p, String clueKey) {
        if (clues(p).contains(clueKey)) return false;
        CompoundTag r = root(p);
        ListTag l = r.getList("clues", Tag.TAG_STRING);
        l.add(StringTag.valueOf(clueKey));
        r.put("clues", l);
        return true;
    }

    /** Navigation hints are on by default and can be turned off (§13). */
    public static boolean navEnabled(Player p) {
        CompoundTag r = root(p);
        return !r.contains("nav") || r.getBoolean("nav");
    }

    public static void setNav(Player p, boolean on) {
        root(p).putBoolean("nav", on);
    }

    public static ResourceLocation tracked(Player p) {
        CompoundTag r = root(p);
        return r.contains("tracked") ? ResourceLocation.tryParse(r.getString("tracked")) : null;
    }

    public static void setTracked(Player p, ResourceLocation quest) {
        if (quest == null) root(p).remove("tracked");
        else root(p).putString("tracked", quest.toString());
    }

    public static void reset(Player p) {
        PlayerData.root(p).remove("quests");
    }
}
