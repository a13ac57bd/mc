package com.rpgcore.dungeon;

import com.rpgcore.logic.FloodFill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Shortcut door (§9): FACING points to the inside; it only opens from the inside and stays open (saved in the block
 * state). Vertically stacked doors open together. Unbreakable in survival.
 */
public class ShortcutDoorBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    private static final VoxelShape OPEN_SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public ShortcutDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    /** The placer stands on the inside, looking out. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
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

    /** True when {@code player} is on the inside half-space of the door. */
    public static boolean isInside(BlockState state, BlockPos pos, Player player) {
        Direction in = state.getValue(FACING);
        double dx = player.getX() - (pos.getX() + 0.5), dz = player.getZ() - (pos.getZ() + 0.5);
        return dx * in.getStepX() + dz * in.getStepZ() > 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(OPEN)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!isInside(state, pos, player)) {
            player.displayClientMessage(Component.translatable("block.rpgcore.shortcut_door.locked"), true);
            return InteractionResult.CONSUME;
        }
        open(level, pos);
        return InteractionResult.CONSUME;
    }

    /** Opens the door column at {@code pos}. */
    public static void open(Level level, BlockPos pos) {
        BlockState start = level.getBlockState(pos);
        if (!(start.getBlock() instanceof ShortcutDoorBlock)) return;
        List<BlockPos> column = FloodFill.fill(pos, p -> List.of(p.above(), p.below()),
                p -> level.getBlockState(p).getBlock() instanceof ShortcutDoorBlock, 16);
        for (BlockPos p : column) level.setBlock(p, level.getBlockState(p).setValue(OPEN, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
    }
}
