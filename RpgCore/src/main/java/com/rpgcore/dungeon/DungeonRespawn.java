package com.rpgcore.dungeon;

import com.rpgcore.RpgCore;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Respawn at the oath stone when the player died inside a dungeon they swore in (§9). */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class DungeonRespawn {
    private DungeonRespawn() {}

    private record DeathSpot(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final Map<UUID, DeathSpot> DEATHS = new HashMap<>();

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) DEATHS.put(p.getUUID(), new DeathSpot(p.level().dimension(), p.blockPosition()));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer p)) return;
        DeathSpot spot = DEATHS.remove(p.getUUID());
        if (spot == null || p.getServer() == null) return;
        ServerLevel level = p.getServer().getLevel(spot.dimension());
        if (level == null) return;
        DungeonData.Instance inst = DungeonData.get(level).findFor(spot.pos(), p.getUUID());
        if (inst == null) return;
        BlockPos oath = inst.oath;
        p.teleportTo(level, oath.getX() + 0.5, oath.getY() + 1.0, oath.getZ() + 0.5, p.getYRot(), p.getXRot());
    }
}
