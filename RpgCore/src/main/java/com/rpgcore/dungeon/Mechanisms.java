package com.rpgcore.dungeon;

import com.rpgcore.RpgCore;
import com.rpgcore.registry.RpgBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Dungeon mechanisms helper (§15, numbers are proposals): powder keg explosions (radius 3, blamed on whoever hit the
 * keg, chain to nearby kegs a few ticks later).
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Mechanisms {
    private Mechanisms() {}

    public static final float KEG_RADIUS = 3.0F;
    private static final int CHAIN_DELAY = 4;

    private record Pending(ServerLevel level, BlockPos pos, Entity blame, long at) {}

    private static final List<Pending> PENDING = new ArrayList<>();

    /** Explodes the keg at {@code pos} now; neighbours within the radius follow after a short delay. */
    public static void explodeKeg(Level level, BlockPos pos, Entity blame) {
        if (!(level instanceof ServerLevel sl) || !sl.getBlockState(pos).is(RpgBlocks.POWDER_KEG.get())) return;
        sl.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        // source entity null: nobody is excluded, the attacker gets the kill credit (#9: hurts friend and foe)
        DamageSource source = sl.damageSources().explosion(null, blame);
        sl.explode(null, source, null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, KEG_RADIUS, false, Level.ExplosionInteraction.NONE);
        int r = (int) Math.ceil(KEG_RADIUS);
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -r, -r), pos.offset(r, r, r))) {
            if (sl.getBlockState(p).is(RpgBlocks.POWDER_KEG.get()) && p.distSqr(pos) <= KEG_RADIUS * KEG_RADIUS) {
                PENDING.add(new Pending(sl, p.immutable(), blame, sl.getGameTime() + CHAIN_DELAY));
            }
        }
    }

    public static void queueKeg(ServerLevel level, BlockPos pos, Entity blame, int delay) {
        PENDING.add(new Pending(level, pos.immutable(), blame, level.getGameTime() + delay));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        List<Pending> due = new ArrayList<>();
        PENDING.removeIf(p -> {
            if (p.level().getGameTime() >= p.at()) {
                due.add(p);
                return true;
            }
            return false;
        });
        for (Pending p : due) explodeKeg(p.level(), p.pos(), p.blame());
    }
}
