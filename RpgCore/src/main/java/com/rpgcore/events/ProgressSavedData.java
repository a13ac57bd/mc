package com.rpgcore.events;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * World-level progress flags (§10 #14): boss_killed:&lt;id&gt;, chapter_done:&lt;id&gt;, location_discovered:&lt;id&gt;,
 * quest_done:&lt;id&gt;, plus the main-story chapter choices. Setting a new flag notifies world events.
 */
public class ProgressSavedData extends SavedData {
    public static final String NAME = "rpgcore_progress";

    private final Set<String> flags = new LinkedHashSet<>();
    private final Map<String, String> choices = new LinkedHashMap<>();

    public static ProgressSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ProgressSavedData::load, ProgressSavedData::new, NAME);
    }

    public static String bossKilled(ResourceLocation id) {
        return "boss_killed:" + id;
    }

    public static String chapterDone(ResourceLocation id) {
        return "chapter_done:" + id;
    }

    public static String locationDiscovered(ResourceLocation id) {
        return "location_discovered:" + id;
    }

    public static String questDone(ResourceLocation id) {
        return "quest_done:" + id;
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }

    public Set<String> flags() {
        return Collections.unmodifiableSet(flags);
    }

    /** Sets a flag; returns false if it was already set. New flags start/advance world events. */
    public boolean set(MinecraftServer server, String flag) {
        if (!flags.add(flag)) return false;
        setDirty();
        WorldEvents.onProgress(server, flag);
        return true;
    }

    public void clear(String flag) {
        if (flags.remove(flag)) setDirty();
    }

    public Map<String, String> choices() {
        return Collections.unmodifiableMap(choices);
    }

    public void setChoice(String chapter, String choice) {
        choices.put(chapter, choice);
        setDirty();
    }

    public int chaptersDone() {
        int n = 0;
        for (String f : flags) if (f.startsWith("chapter_done:")) n++;
        return n;
    }

    public static ProgressSavedData load(CompoundTag tag) {
        ProgressSavedData d = new ProgressSavedData();
        ListTag list = tag.getList("flags", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) d.flags.add(list.getString(i));
        CompoundTag c = tag.getCompound("choices");
        for (String k : c.getAllKeys()) d.choices.put(k, c.getString(k));
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (String f : flags) list.add(StringTag.valueOf(f));
        tag.put("flags", list);
        CompoundTag c = new CompoundTag();
        choices.forEach(c::putString);
        tag.put("choices", c);
        return tag;
    }
}
