package com.rpgcore.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The two doors that appear after a rift floor is cleared: continue down, or leave with everything (§11). */
public class RiftDoorBlock extends Block {
    private final boolean exit;

    public RiftDoorBlock(Properties properties, boolean exit) {
        super(properties);
        this.exit = exit;
    }

    public boolean isExit() {
        return exit;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;
        RiftInstance inst = RiftManager.at(pos);
        if (inst == null) {
            sl.removeBlock(pos, false);
            return InteractionResult.CONSUME;
        }
        if (exit) RiftManager.end(sl.getServer(), inst, false);
        else RiftManager.descend(sl, inst);
        return InteractionResult.CONSUME;
    }
}
