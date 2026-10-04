package com.rpgcore.boss;

import com.rpgcore.registry.RpgBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Boss altar: block entity stores the boss id and arena radius (14). */
public class BossAltarBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public BossAltarBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BossAltarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != RpgBlockEntities.BOSS_ALTAR.get()) return null;
        return (lvl, pos, st, be) -> BossAltarBlockEntity.serverTick(lvl, pos, st, (BossAltarBlockEntity) be);
    }
}
