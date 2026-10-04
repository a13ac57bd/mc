package com.rpgcore.home;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Boss trophy: one placeable block type; the block entity says which boss (§16). */
public class TrophyBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public TrophyBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrophyBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TrophyBlockEntity be && be.boss() != null) {
            player.displayClientMessage(Component.translatable("block.rpgcore.trophy.of", Trophies.bossName(be.boss())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
