package com.rpgcore.home;

import com.rpgcore.registry.RpgBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Which boss a trophy is for (§16). The model comes with the art pass. */
public class TrophyBlockEntity extends BlockEntity {
    private ResourceLocation boss;

    public TrophyBlockEntity(BlockPos pos, BlockState state) {
        super(RpgBlockEntities.TROPHY.get(), pos, state);
    }

    public ResourceLocation boss() {
        return boss;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boss = tag.contains("boss") ? ResourceLocation.tryParse(tag.getString("boss")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (boss != null) tag.putString("boss", boss.toString());
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
