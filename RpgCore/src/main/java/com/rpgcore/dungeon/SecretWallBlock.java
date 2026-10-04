package com.rpgcore.dungeon;

import com.rpgcore.logic.FloodFill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Secret wall (§9): looks like stone bricks; opens after N punches or with an item, together with up to 64 connected walls. */
public class SecretWallBlock extends Block implements EntityBlock {
    public static final int LIMIT = 64;

    public SecretWallBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecretWallBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof SecretWallBlockEntity be)) return;
        if (matchesItem(be, player.getMainHandItem()) || be.hit()) reveal(level, pos);
        else level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.8F, 0.6F);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SecretWallBlockEntity be) || !matchesItem(be, player.getItemInHand(hand))) return InteractionResult.PASS;
        if (!level.isClientSide) reveal(level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean matchesItem(SecretWallBlockEntity be, ItemStack stack) {
        ResourceLocation id = be.item();
        return id != null && !stack.isEmpty() && id.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    /** Removes the connected secret walls (6-neighbour, at most 64). */
    public static int reveal(Level level, BlockPos pos) {
        List<BlockPos> walls = FloodFill.fill(pos, p -> {
            List<BlockPos> n = new ArrayList<>(6);
            for (Direction d : Direction.values()) n.add(p.relative(d));
            return n;
        }, p -> level.getBlockState(p).getBlock() instanceof SecretWallBlock, LIMIT);
        for (BlockPos p : walls) level.destroyBlock(p, false);
        level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 1.0F, 0.6F);
        return walls.size();
    }
}
