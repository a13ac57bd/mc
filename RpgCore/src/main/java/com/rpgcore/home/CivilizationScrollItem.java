package com.rpgcore.home;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Reading it unlocks the civilization in its NBT ("civilization": "ns:id"). */
public class CivilizationScrollItem extends Item {
    public CivilizationScrollItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Item item, ResourceLocation civ) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString("civilization", civ.toString());
        return stack;
    }

    public static ResourceLocation civilization(ItemStack stack) {
        return stack.getTag() == null ? null : ResourceLocation.tryParse(stack.getTag().getString("civilization"));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ResourceLocation civ = civilization(stack);
        if (civ == null) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) {
            if (Unlocks.unlock(player, civ)) {
                player.displayClientMessage(Component.translatable("item.rpgcore.civilization_scroll.unlocked", civName(civ)), false);
                if (!player.getAbilities().instabuild) stack.shrink(1);
            } else {
                player.displayClientMessage(Component.translatable("item.rpgcore.civilization_scroll.known", civName(civ)), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static Component civName(ResourceLocation civ) {
        return Component.translatable("civilization." + civ.getNamespace() + "." + civ.getPath());
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation civ = civilization(stack);
        if (civ != null) tooltip.add(civName(civ));
    }
}
