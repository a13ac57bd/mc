package com.rpgcore.town;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Inn bed (§13): right-click sets the respawn point here, day or night, without sleeping. */
public class InnBedBlock extends Block {
    public InnBedBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            // forced = true: respawn here even though this is not a vanilla bed (like /spawnpoint)
            sp.setRespawnPosition(level.dimension(), pos.above(), sp.getYRot(), true, false);
            sp.displayClientMessage(Component.translatable("block.rpgcore.inn_bed.set"), true);
        }
        return InteractionResult.CONSUME;
    }
}
