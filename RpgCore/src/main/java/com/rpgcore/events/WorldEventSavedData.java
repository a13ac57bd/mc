package com.rpgcore.events;

import com.rpgcore.logic.EventMachine;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Saved state of world events (§10): active instances, queue, finished outcomes, last processed day, spawned once-only bosses. */
public class WorldEventSavedData extends SavedData {
    public static final String NAME = "rpgcore_world_events";

    public final List<EventMachine.Instance> active = new ArrayList<>();
    public final List<String> queue = new ArrayList<>();
    public final Map<String, String> finished = new LinkedHashMap<>();
    /** "event/state" keys whose entry effects (boss, refugees) already ran. */
    public final Set<String> entered = new LinkedHashSet<>();
    public long lastDay = -1;

    public static WorldEventSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(WorldEventSavedData::load, WorldEventSavedData::new, NAME);
    }

    /** Copies the machine's state into this object. */
    public void capture(EventMachine machine) {
        active.clear();
        for (EventMachine.Instance i : machine.active()) active.add(new EventMachine.Instance(i.defId, i.state, i.daysInState));
        queue.clear();
        queue.addAll(machine.queue());
        finished.clear();
        finished.putAll(machine.finished());
        setDirty();
    }

    public static WorldEventSavedData load(CompoundTag tag) {
        WorldEventSavedData d = new WorldEventSavedData();
        ListTag a = tag.getList("active", Tag.TAG_COMPOUND);
        for (int i = 0; i < a.size(); i++) {
            CompoundTag t = a.getCompound(i);
            d.active.add(new EventMachine.Instance(t.getString("id"), t.getString("state"), t.getInt("days")));
        }
        ListTag q = tag.getList("queue", Tag.TAG_STRING);
        for (int i = 0; i < q.size(); i++) d.queue.add(q.getString(i));
        CompoundTag f = tag.getCompound("finished");
        for (String k : f.getAllKeys()) d.finished.put(k, f.getString(k));
        ListTag e = tag.getList("entered", Tag.TAG_STRING);
        for (int i = 0; i < e.size(); i++) d.entered.add(e.getString(i));
        d.lastDay = tag.contains("last_day") ? tag.getLong("last_day") : -1;
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag a = new ListTag();
        for (EventMachine.Instance i : active) {
            CompoundTag t = new CompoundTag();
            t.putString("id", i.defId);
            t.putString("state", i.state);
            t.putInt("days", i.daysInState);
            a.add(t);
        }
        tag.put("active", a);
        ListTag q = new ListTag();
        for (String s : queue) q.add(StringTag.valueOf(s));
        tag.put("queue", q);
        CompoundTag f = new CompoundTag();
        finished.forEach(f::putString);
        tag.put("finished", f);
        ListTag e = new ListTag();
        for (String s : entered) e.add(StringTag.valueOf(s));
        tag.put("entered", e);
        tag.putLong("last_day", lastDay);
        return tag;
    }
}
