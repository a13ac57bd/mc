package com.rpgcore.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Fragile chain (§15): a punch or a projectile snaps it, dropping the chandelier below. Cannot be mined. */
public class FragileChainBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(6.5, 0, 6.5, 9.5, 16, 9.5);

    public FragileChainBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) snap(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide) snap(level, hit.getBlockPos());
    }

    /** Breaks this link; the chandelier below notices on its next shape update. */
    public static void snap(Level level, BlockPos pos) {
        level.destroyBlock(pos, false);
    }
}
