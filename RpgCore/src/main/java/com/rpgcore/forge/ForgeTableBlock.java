package com.rpgcore.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/** rpgcore:forge_table (§6). Recipe: iron ingot + smithing table. Stateless: items go back to the player on close. */
public class ForgeTableBlock extends Block {
    public static final Component TITLE = Component.translatable("container.rpgcore.forge_table");

    public ForgeTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, new SimpleMenuProvider(
                    (id, inv, p) -> new ForgeMenu(id, inv, ContainerLevelAccess.create(level, pos)), TITLE));
        }
        return InteractionResult.CONSUME;
    }
}
