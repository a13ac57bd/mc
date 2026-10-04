package com.rpgcore.dungeon;

import com.rpgcore.registry.RpgEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Chandelier (§15): hangs under a fragile chain (or any solid block). When the chain breaks, it loses support or is
 * shot, it falls: 3 damage per block fallen (vanilla falling-block damage, max 40) and 2 s stun where it lands.
 */
public class ChandelierBlock extends Block implements Fallable {
    public static final float DAMAGE_PER_BLOCK = 3.0F;
    public static final int STUN_TICKS = 40;
    private static final VoxelShape SHAPE = Block.box(2, 2, 2, 14, 16, 14);

    public ChandelierBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    private static boolean supported(LevelAccessor level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.getBlock() instanceof FragileChainBlock || Block.canSupportCenter(level, pos.above(), Direction.DOWN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        level.scheduleTick(pos, this, 2);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (dir == Direction.UP) level.scheduleTick(pos, this, 1);
        return state;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!supported(level, pos) && FallingBlock.isFree(level.getBlockState(pos.below()))) drop(level, pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (level instanceof ServerLevel sl && FallingBlock.isFree(level.getBlockState(hit.getBlockPos().below()))) {
            drop(sl, hit.getBlockPos(), state);
        }
    }

    public static void drop(ServerLevel level, BlockPos pos, BlockState state) {
        FallingBlockEntity falling = FallingBlockEntity.fall(level, pos, state);
        falling.setHurtsEntities(DAMAGE_PER_BLOCK, 40);
    }

    @Override
    public void onLand(Level level, BlockPos pos, BlockState state, BlockState replaced, FallingBlockEntity entity) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(1.0))) {
            e.addEffect(new MobEffectInstance(RpgEffects.STUN.get(), STUN_TICKS, 0, false, true));
        }
    }
}
