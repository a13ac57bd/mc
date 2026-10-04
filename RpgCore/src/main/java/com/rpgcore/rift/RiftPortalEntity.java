package com.rpgcore.rift;

import com.rpgcore.registry.RpgEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Rift entrance (§11): particles only. Walking into it takes the player and everyone within 4 blocks into a rift of
 * its tier. Natural entrances vanish at dawn.
 */
public class RiftPortalEntity extends Entity {
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(RiftPortalEntity.class, EntityDataSerializers.INT);

    private boolean natural;

    public RiftPortalEntity(EntityType<? extends RiftPortalEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static RiftPortalEntity spawn(ServerLevel level, BlockPos pos, int tier, boolean natural) {
        RiftPortalEntity portal = RpgEntities.RIFT_PORTAL.get().create(level);
        if (portal == null) return null;
        portal.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0F, 0F);
        portal.entityData.set(TIER, Math.max(1, Math.min(5, tier)));
        portal.natural = natural;
        level.addFreshEntity(portal);
        return portal;
    }

    public int tier() {
        return entityData.get(TIER);
    }

    public boolean natural() {
        return natural;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TIER, 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            for (int i = 0; i < 4; i++) {
                double a = (tickCount * 0.2 + i * Math.PI / 2);
                level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + Math.cos(a) * 0.6, getY() + 0.2 + (tickCount % 20) * 0.1, getZ() + Math.sin(a) * 0.6, 0, 0.02, 0);
            }
            level().addParticle(ParticleTypes.PORTAL, getX(), getY() + 1.2, getZ(), (random.nextDouble() - 0.5), (random.nextDouble() - 0.5), (random.nextDouble() - 0.5));
            return;
        }
        if (natural && tickCount % 40 == 0 && level().isDay()) {
            discard();
            return;
        }
        if (tickCount % 5 != 0) return;
        List<ServerPlayer> touching = level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox(), p -> p.isAlive() && !p.isSpectator());
        if (touching.isEmpty()) return;
        List<ServerPlayer> party = level().getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(4), p -> p.isAlive() && !p.isSpectator());
        if (RiftManager.open(party, tier()) != null) discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(TIER, Math.max(1, tag.getInt("Tier")));
        natural = tag.getBoolean("Natural");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Tier", tier());
        tag.putBoolean("Natural", natural);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }
}
