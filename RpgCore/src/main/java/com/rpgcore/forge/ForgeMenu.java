package com.rpgcore.forge;

import com.rpgcore.loot.PlayerLootData;
import com.rpgcore.registry.RpgBlocks;
import com.rpgcore.registry.RpgMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Forge table menu (§6): one equipment slot, one material slot (inscriptions) and the player inventory.
 * The four tabs (upgrade, reroll, inscribe, salvage) live in the screen; actions go through ForgeOps.
 */
public class ForgeMenu extends AbstractContainerMenu {
    public static final int EQUIPMENT = 0;
    public static final int MATERIAL = 1;

    private final Container container = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            ForgeMenu.this.slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    private final Player player;
    private final DataSlot threshold = DataSlot.standalone();

    public static ForgeMenu fromNetwork(int id, Inventory inv, FriendlyByteBuf buf) {
        return new ForgeMenu(id, inv, ContainerLevelAccess.NULL);
    }

    public ForgeMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(RpgMenus.FORGE.get(), id);
        this.access = access;
        this.player = inv.player;
        addSlot(new Slot(container, EQUIPMENT, 44, 20));
        addSlot(new Slot(container, MATERIAL, 44, 50));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 142));
        addDataSlot(threshold);
        if (!inv.player.level().isClientSide) threshold.set(PlayerLootData.salvageThreshold(inv.player));
    }

    public ItemStack equipment() {
        return container.getItem(EQUIPMENT);
    }

    public void setEquipment(ItemStack stack) {
        container.setItem(EQUIPMENT, stack);
    }

    public ItemStack material() {
        return container.getItem(MATERIAL);
    }

    public int salvageThreshold() {
        return threshold.get();
    }

    public void setSalvageThreshold(int value) {
        threshold.set(value);
    }

    public Player player() {
        return player;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, RpgBlocks.FORGE_TABLE.get());
    }

    /** Items go back to the player on close (also when opened by a smith NPC, where access is NULL). */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, container);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = Inscriptions.forMaterial(stack) != null ? MATERIAL : EQUIPMENT;
            if (!moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}
