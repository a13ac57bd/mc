package com.rpgcore.combat;

import com.rpgcore.RpgCore;
import com.rpgcore.logic.CombatMath;
import com.rpgcore.net.RpgNetwork;
import com.rpgcore.net.RpgPackets;
import com.rpgcore.registry.RpgAttributes;
import com.rpgcore.registry.RpgEffects;
import com.rpgcore.util.Attr;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dash (#19, §8): the client only sends "pressed + movement input"; the server checks charges and ground, sets the
 * velocity and syncs it with hurtMarked. Initial speed = distance x (1 - drag), so every surface gives the same
 * distance. No invulnerability frames.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Dash {
    private Dash() {}

    private static final class State {
        int charges = -1;
        int elapsed;
        int sentCharges = -1;
        int sentMax = -1;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static int maxCharges(ServerPlayer p) {
        return (int) Math.floor(Attr.value(p, RpgAttributes.DASH_CHARGES, 1.0));
    }

    private static State state(ServerPlayer p) {
        State s = STATES.computeIfAbsent(p.getUUID(), u -> new State());
        int max = maxCharges(p);
        if (s.charges < 0 || s.charges > max) s.charges = max;
        return s;
    }

    public static int charges(ServerPlayer p) {
        return state(p).charges;
    }

    /** Refills all charges (tests, rift floors). */
    public static void refill(ServerPlayer p) {
        State s = state(p);
        s.charges = maxCharges(p);
        s.elapsed = 0;
        sync(p, s, true);
    }

    public static boolean tryDash(ServerPlayer p, float forward, float strafe) {
        if (p.isSpectator() || p.isPassenger() || p.isFallFlying() || p.isSleeping() || p.isDeadOrDying()) return false;
        if (p.hasEffect(RpgEffects.STUN.get())) return false;
        State s = state(p);
        if (s.charges <= 0) return false;
        boolean air = Attr.value(p, RpgAttributes.DASH_AIR, 0.0) >= 1.0;
        if (!p.onGround() && !air) return false;

        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        // left of the facing direction (leftImpulse is positive to the left)
        double lx = fz, lz = -fx;
        double dx = fx * forward + lx * strafe;
        double dz = fz * forward + lz * strafe;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) {
            dx = fx;
            dz = fz;
            len = 1.0;
        }
        dx /= len;
        dz /= len;

        BlockPos below = BlockPos.containing(p.getX(), p.getBoundingBox().minY - 0.5000001, p.getZ());
        float friction = p.level().getBlockState(below).getFriction(p.level(), below, p);
        double drag = CombatMath.horizontalDrag(friction, p.onGround());
        double speed = CombatMath.dashSpeed(Attr.value(p, RpgAttributes.DASH_DISTANCE, 4.0), drag);
        double vy = p.onGround() ? 0.0 : Math.max(0.0, p.getDeltaMovement().y);
        p.setDeltaMovement(dx * speed, vy, dz * speed);
        p.hurtMarked = true;
        p.fallDistance = 0;

        s.charges--;
        sync(p, s, true);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.7F);
        MinecraftForge.EVENT_BUS.post(new DashEvent(p));
        return true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p)) return;
        State s = state(p);
        int max = maxCharges(p);
        if (s.charges < max) {
            int needed = CombatMath.ticksPerCharge(Attr.value(p, RpgAttributes.DASH_COOLDOWN, 2.5));
            if (++s.elapsed >= needed) {
                s.charges++;
                s.elapsed = 0;
            }
        } else {
            s.elapsed = 0;
        }
        sync(p, s, false);
    }

    private static void sync(ServerPlayer p, State s, boolean force) {
        int max = maxCharges(p);
        if (!force && s.sentCharges == s.charges && s.sentMax == max) return;
        s.sentCharges = s.charges;
        s.sentMax = max;
        int needed = CombatMath.ticksPerCharge(Attr.value(p, RpgAttributes.DASH_COOLDOWN, 2.5));
        RpgNetwork.toPlayer(p, new RpgPackets.DashSync(s.charges, max, needed, s.elapsed));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }
}
