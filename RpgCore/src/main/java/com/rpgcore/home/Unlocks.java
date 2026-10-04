package com.rpgcore.home;

import com.rpgcore.RpgCore;
import com.rpgcore.util.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Unlocked building civilizations per player (§16), kept through death. rpgcore:basic is always unlocked.
 * Recipe conditions in Forge 1.20.1 are evaluated at load, not per player, so unlocks gate blueprints and the
 * build table instead of recipes.
 */
public final class Unlocks {
    private Unlocks() {}

    public static final ResourceLocation BASIC = RpgCore.id("basic");

    public static boolean has(Player p, ResourceLocation civ) {
        if (civ == null || BASIC.equals(civ)) return true;
        return all(p).contains(civ.toString());
    }

    public static List<String> all(Player p) {
        List<String> out = new ArrayList<>();
        ListTag l = PlayerData.root(p).getList("unlocks", Tag.TAG_STRING);
        for (int i = 0; i < l.size(); i++) out.add(l.getString(i));
        return out;
    }

    public static boolean unlock(Player p, ResourceLocation civ) {
        if (has(p, civ)) return false;
        CompoundTag root = PlayerData.root(p);
        ListTag l = root.getList("unlocks", Tag.TAG_STRING);
        l.add(StringTag.valueOf(civ.toString()));
        root.put("unlocks", l);
        return true;
    }
}
