package com.rpgcore.rift;

import com.rpgcore.danger.DangerMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Rift beacon (§11, 0.5% from elites): opens an entrance in front of you at local danger; sneak-use = local + 1. */
public class RiftBeaconItem extends Item {
    public RiftBeaconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel sl)) return InteractionResultHolder.success(stack);
        if (level.dimension().equals(RiftManager.RIFT)) {
            player.displayClientMessage(Component.translatable("item.rpgcore.rift_beacon.inside"), true);
            return InteractionResultHolder.fail(stack);
        }
        Vec3 look = player.getLookAngle();
        BlockPos pos = BlockPos.containing(player.getX() + look.x * 3, player.getY(), player.getZ() + look.z * 3);
        int tier = DangerMap.get(level, pos) + (player.isShiftKeyDown() ? 1 : 0);
        RiftPortalEntity.spawn(sl, pos, tier, false);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.consume(stack);
    }
}
