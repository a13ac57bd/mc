package com.rpgcore.home;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** Trophy item; the boss id rides in BlockEntityTag so the placed block keeps it. */
public class TrophyItem extends BlockItem {
    public TrophyItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static ResourceLocation boss(ItemStack stack) {
        CompoundTag be = stack.getTagElement("BlockEntityTag");
        return be == null ? null : ResourceLocation.tryParse(be.getString("boss"));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation boss = boss(stack);
        if (boss != null) tooltip.add(Trophies.bossName(boss));
    }
}
