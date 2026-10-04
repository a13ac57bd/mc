package com.rpgcore.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Inventory helpers for costs paid from the player's inventory. */
public final class Inv {
    private Inv() {}

    public static int count(Player player, Item item) {
        int n = 0;
        for (ItemStack s : player.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** Removes {@code amount} items if available; returns false (and removes nothing) otherwise. */
    public static boolean take(Player player, Item item, int amount) {
        if (amount <= 0) return true;
        if (player.getAbilities().instabuild) return true;
        if (count(player, item) < amount) return false;
        int left = amount;
        for (ItemStack s : player.getInventory().items) {
            if (left <= 0) break;
            if (!s.is(item)) continue;
            int n = Math.min(left, s.getCount());
            s.shrink(n);
            left -= n;
        }
        player.getInventory().setChanged();
        return true;
    }

    /** Adds to the inventory, dropping what does not fit. */
    public static void give(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack) && !stack.isEmpty()) player.drop(stack, false);
    }
}
