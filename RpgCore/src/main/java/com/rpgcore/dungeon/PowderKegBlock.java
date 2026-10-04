package com.rpgcore.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Powder keg (§15): any damage (punch, projectile, explosion, fire) blows it up. */
public class PowderKegBlock extends Block {
    public PowderKegBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        Mechanisms.explodeKeg(level, pos, player);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        Mechanisms.explodeKeg(level, hit.getBlockPos(), projectile.getOwner());
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        // the block is already gone; put it back for one moment and blow it up shortly after
        if (level instanceof ServerLevel sl) {
            sl.setBlock(pos, defaultBlockState(), 3);
            LivingEntity blame = explosion.getIndirectSourceEntity();
            Mechanisms.queueKeg(sl, pos, blame, 2);
        }
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 100;
    }

    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, Direction direction, LivingEntity igniter) {
        if (level instanceof ServerLevel sl) {
            sl.setBlock(pos, defaultBlockState(), 3);
            Mechanisms.queueKeg(sl, pos, igniter, 1);
        }
    }
}
