package com.rpgcore.dungeon;

import com.rpgcore.logic.FloodFill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Flammable oil (§15): a thin layer lit by burning entities or projectiles moving through it, or by adjacent fire/lava.
 * The whole connected pool (up to 256 blocks) bursts into fire. Note: vanilla only checks blocks for entities that
 * move, so a mob standing perfectly still will not light the oil.
 */
public class OilBlock extends Block {
    public static final int LIMIT = 256;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public OilBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity.isOnFire()) ignite(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (!level.isClientSide && isHot(level.getBlockState(from))) ignite(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (level.isClientSide) return;
        for (Direction d : Direction.values()) {
            if (isHot(level.getBlockState(pos.relative(d)))) {
                ignite(level, pos);
                return;
            }
        }
    }

    private static boolean isHot(BlockState state) {
        return state.is(BlockTags.FIRE) || state.getFluidState().is(Fluids.LAVA) || state.getFluidState().is(Fluids.FLOWING_LAVA);
    }

    /** Sets the connected oil pool on fire. Returns the number of oil blocks burnt. */
    public static int ignite(Level level, BlockPos start) {
        List<BlockPos> pool = FloodFill.fill(start, p -> {
            List<BlockPos> n = new ArrayList<>(8);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                n.add(p.relative(d));
                n.add(p.relative(d).above());
                n.add(p.relative(d).below());
            }
            return n;
        }, p -> level.getBlockState(p).getBlock() instanceof OilBlock, LIMIT);
        for (BlockPos p : pool) level.setBlock(p, BaseFireBlock.getState(level, p), Block.UPDATE_ALL);
        if (!pool.isEmpty()) level.playSound(null, start, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
        return pool.size();
    }
}
