package com.rpgcore.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * rpgcore's part of Forge's PlayerPersisted tag: survives death and respawn (§6). Holds pity counters, killed bosses,
 * auto-salvage threshold, quest log, unlocked civilizations...
 */
public final class PlayerData {
    private PlayerData() {}

    public static CompoundTag root(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains("rpgcore", Tag.TAG_COMPOUND)) persisted.put("rpgcore", new CompoundTag());
        return persisted.getCompound("rpgcore");
    }

    /** A named compound under the rpgcore root, created on demand. */
    public static CompoundTag section(Player player, String name) {
        CompoundTag root = root(player);
        if (!root.contains(name, Tag.TAG_COMPOUND)) root.put(name, new CompoundTag());
        return root.getCompound(name);
    }
}
