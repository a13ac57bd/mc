package com.rpgcore.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Oath stone (§9): right-click to swear in; dying inside this dungeon respawns you here (DungeonRespawn). */
public class OathStoneBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public OathStoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level instanceof ServerLevel sl) {
            DungeonData.get(sl).activate(sl, pos, player.getUUID());
            if (!state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
            sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
            level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("block.rpgcore.oath_stone.sworn"), true);
        }
        return InteractionResult.CONSUME;
    }
}
