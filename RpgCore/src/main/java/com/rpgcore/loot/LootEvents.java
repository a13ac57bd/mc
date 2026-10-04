package com.rpgcore.loot;

import com.rpgcore.RpgCore;
import com.rpgcore.item.ItemData;
import com.rpgcore.logic.ForgeCosts;
import com.rpgcore.logic.LootMath;
import com.rpgcore.logic.Rarity;
import com.rpgcore.registry.RpgItems;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Auto-salvage on pickup and tier scaling of melee weapons and armor (§6, #45). */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class LootEvents {
    private LootEvents() {}

    /**
     * Items at or below the player's threshold (never legendary/artifact) turn into materials. The pickup is cancelled
     * and the item entity becomes the materials, which the player picks up on the next tick.
     */
    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        ItemEntity entity = event.getItem();
        ItemStack stack = entity.getItem();
        if (!ItemData.has(stack)) return;
        Rarity rarity = ItemData.rarity(stack);
        if (!ForgeCosts.autoSalvage(PlayerLootData.salvageThreshold(event.getEntity()), rarity)) return;
        event.setCanceled(true);
        entity.setItem(salvage(stack));
    }

    public static ItemStack salvage(ItemStack stack) {
        int count = ForgeCosts.salvageYield(ItemData.rarity(stack), ItemData.upgrade(stack)) * stack.getCount();
        return new ItemStack(RpgItems.essence(ItemData.tier(stack)), count);
    }

    /** Melee weapons and armor: scale the item's own attack damage, armor and toughness by the tier multiplier. */
    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!ItemData.has(stack)) return;
        int tier = ItemData.tier(stack);
        if (tier <= 1) return;
        double mult = LootMath.tierMultiplier(tier);
        scale(event, Attributes.ATTACK_DAMAGE, mult);
        scale(event, Attributes.ARMOR, mult);
        scale(event, Attributes.ARMOR_TOUGHNESS, mult);
    }

    private static void scale(ItemAttributeModifierEvent event, Attribute attribute, double mult) {
        List<AttributeModifier> mods = new ArrayList<>(event.getModifiers().get(attribute));
        for (AttributeModifier m : mods) {
            if (m.getOperation() != AttributeModifier.Operation.ADDITION || m.getAmount() <= 0) continue;
            event.removeModifier(attribute, m);
            event.addModifier(attribute, new AttributeModifier(m.getId(), m.getName(), m.getAmount() * mult, m.getOperation()));
        }
    }
}
