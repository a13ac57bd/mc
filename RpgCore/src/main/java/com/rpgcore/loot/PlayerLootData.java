package com.rpgcore.loot;

import com.rpgcore.util.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/** Per-player loot state kept through death (§6): pity counters per source, killed bosses, auto-salvage threshold. */
public final class PlayerLootData {
    private PlayerLootData() {}

    private static CompoundTag loot(Player p) {
        return PlayerData.section(p, "loot");
    }

    public static int misses(Player p, ResourceLocation source) {
        return loot(p).getCompound("pity").getInt(source.toString());
    }

    public static void setMisses(Player p, ResourceLocation source, int misses) {
        CompoundTag l = loot(p);
        CompoundTag pity = l.getCompound("pity");
        pity.putInt(source.toString(), misses);
        l.put("pity", pity);
    }

    public static boolean killedBoss(Player p, ResourceLocation boss) {
        ListTag list = loot(p).getList("bosses", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (list.getString(i).equals(boss.toString())) return true;
        return false;
    }

    public static void markBoss(Player p, ResourceLocation boss) {
        if (killedBoss(p, boss)) return;
        CompoundTag l = loot(p);
        ListTag list = l.getList("bosses", Tag.TAG_STRING);
        list.add(StringTag.valueOf(boss.toString()));
        l.put("bosses", list);
    }

    /** 0 off, 1 common, 2 uncommon, 3 rare (§6). */
    public static int salvageThreshold(Player p) {
        return loot(p).getInt("salvage");
    }

    public static void setSalvageThreshold(Player p, int threshold) {
        loot(p).putInt("salvage", Math.max(0, Math.min(3, threshold)));
    }
}
