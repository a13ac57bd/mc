package com.rpgcore.forge;

import com.rpgcore.item.ItemData;
import com.rpgcore.logic.ForgeCosts;
import com.rpgcore.logic.Rarity;
import com.rpgcore.loot.Generator;
import com.rpgcore.loot.LootEvents;
import com.rpgcore.loot.PlayerLootData;
import com.rpgcore.registry.RpgItems;
import com.rpgcore.trait.TraitDef;
import com.rpgcore.trait.TraitRegistry;
import com.rpgcore.util.Inv;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Forge table operations (§6). Costs are paid from the player's inventory. Pure rules live in logic.ForgeCosts. */
public final class ForgeOps {
    private ForgeOps() {}

    public static final int UPGRADE = 0;
    public static final int REROLL = 1;
    public static final int INSCRIBE = 2;
    public static final int SALVAGE = 3;
    public static final int SET_THRESHOLD = 4;

    public enum Result { OK, NO_ITEM, MAX_TIER, NOT_ENOUGH, NOT_ALLOWED, LOCKED, NO_INSCRIPTION }

    public static void handle(ServerPlayer player, int action, int arg) {
        if (!(player.containerMenu instanceof ForgeMenu menu) || !menu.stillValid(player)) return;
        Result r = switch (action) {
            case UPGRADE -> upgrade(player, menu.equipment());
            case REROLL -> reroll(player, menu.equipment(), arg);
            case INSCRIBE -> inscribe(player, menu.equipment(), menu.material());
            case SALVAGE -> salvage(player, menu);
            case SET_THRESHOLD -> {
                PlayerLootData.setSalvageThreshold(player, arg);
                menu.setSalvageThreshold(PlayerLootData.salvageThreshold(player));
                yield Result.OK;
            }
            default -> Result.NOT_ALLOWED;
        };
        if (r == Result.OK) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else {
            player.displayClientMessage(Component.translatable("forge.rpgcore.result." + r.name().toLowerCase(java.util.Locale.ROOT)), true);
        }
        menu.broadcastChanges();
    }

    /** Tier +1; all trait values scale with the tier multiplier. */
    public static Result upgrade(Player player, ItemStack stack) {
        if (!ItemData.has(stack) || ItemData.rarity(stack) == null) return Result.NO_ITEM;
        int tier = ItemData.tier(stack);
        int cost = ForgeCosts.upgradeCost(tier);
        if (cost < 0) return Result.MAX_TIER;
        if (!Inv.take(player, RpgItems.essence(tier), cost)) return Result.NOT_ENOUGH;
        ItemData.setTier(stack, tier + 1);
        ItemData.setUpgrade(stack, ItemData.upgrade(stack) + 1);
        return Result.OK;
    }

    /** Replaces one unlocked trait with another of the same school; rare and above only. */
    public static Result reroll(Player player, ItemStack stack, int index) {
        if (!ItemData.has(stack)) return Result.NO_ITEM;
        Rarity rarity = ItemData.rarity(stack);
        if (!ForgeCosts.canReroll(rarity)) return Result.NOT_ALLOWED;
        List<ItemData.TraitRoll> traits = new java.util.ArrayList<>(ItemData.traits(stack));
        if (index < 0 || index >= traits.size()) return Result.NOT_ALLOWED;
        if (traits.get(index).locked()) return Result.LOCKED;
        int cost = ForgeCosts.rerollCost(ItemData.rerolls(stack));
        Set<ResourceLocation> exclude = new HashSet<>();
        for (ItemData.TraitRoll t : traits) exclude.add(t.id());
        List<ItemData.TraitRoll> fresh = Generator.rollMinors(ItemData.school(stack), 1, exclude, player.getRandom());
        if (fresh.isEmpty()) return Result.NOT_ALLOWED;
        if (!Inv.take(player, RpgItems.essence(ItemData.tier(stack)), cost)) return Result.NOT_ENOUGH;
        traits.set(index, fresh.get(0));
        ItemData.setTraits(stack, traits);
        ItemData.setRerolls(stack, ItemData.rerolls(stack) + 1);
        return Result.OK;
    }

    /** Consumes one special material and sets the matching inscription (replacing an older one). */
    public static Result inscribe(Player player, ItemStack stack, ItemStack material) {
        if (!ItemData.has(stack) || ItemData.rarity(stack) == null) return Result.NO_ITEM;
        Inscriptions.InscriptionDef def = Inscriptions.forMaterial(material);
        if (def == null) return Result.NO_INSCRIPTION;
        TraitDef trait = TraitRegistry.get(def.trait());
        if (trait == null) return Result.NO_INSCRIPTION;
        if (!player.getAbilities().instabuild) material.shrink(1);
        ItemData.setInscription(stack, def.trait());
        return Result.OK;
    }

    public static Result salvage(Player player, ForgeMenu menu) {
        ItemStack stack = menu.equipment();
        if (!ItemData.has(stack) || ItemData.rarity(stack) == null) return Result.NO_ITEM;
        ItemStack mats = LootEvents.salvage(stack);
        menu.setEquipment(ItemStack.EMPTY);
        Inv.give(player, mats);
        return Result.OK;
    }
}
