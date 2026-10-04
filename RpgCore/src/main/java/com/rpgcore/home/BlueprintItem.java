package com.rpgcore.home;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Blueprint (§16): NBT "blueprint" points at a blueprint id. Use it on a build table. */
public class BlueprintItem extends Item {
    public BlueprintItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(Item item, ResourceLocation blueprint) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString("blueprint", blueprint.toString());
        return stack;
    }

    public static ResourceLocation blueprint(ItemStack stack) {
        return stack.getTag() == null ? null : ResourceLocation.tryParse(stack.getTag().getString("blueprint"));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = blueprint(stack);
        if (id != null) tooltip.add(Component.translatable("blueprint." + id.getNamespace() + "." + id.getPath()));
    }
}
