package com.rpgcore.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Boss arena gate (§14): open = passable and invisible, closed = solid. Driven by the boss altar. */
public class ArenaGateBlock extends Block {
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private static final VoxelShape OPEN_SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public ArenaGateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? OPEN_SHAPE : Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    public static void set(Level level, BlockPos pos, boolean open) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof ArenaGateBlock && s.getValue(OPEN) != open) level.setBlock(pos, s.setValue(OPEN, open), Block.UPDATE_ALL);
    }
}
