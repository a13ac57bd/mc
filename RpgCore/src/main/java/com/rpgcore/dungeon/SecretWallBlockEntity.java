package com.rpgcore.dungeon;

import com.rpgcore.registry.RpgBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Reveal condition of a secret wall (§9): {@code hits} punches (default 3), or holding {@code item}. */
public class SecretWallBlockEntity extends BlockEntity {
    private int hitsRequired = 3;
    private ResourceLocation item;
    private int hits;

    public SecretWallBlockEntity(BlockPos pos, BlockState state) {
        super(RpgBlockEntities.SECRET_WALL.get(), pos, state);
    }

    public int hitsRequired() {
        return hitsRequired;
    }

    public ResourceLocation item() {
        return item;
    }

    /** Counts a punch; returns true when the wall should open. */
    public boolean hit() {
        hits++;
        setChanged();
        return item == null && hits >= hitsRequired;
    }

    public void configure(int hitsRequired, ResourceLocation item) {
        this.hitsRequired = Math.max(1, hitsRequired);
        this.item = item;
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        hitsRequired = tag.contains("hits_required") ? Math.max(1, tag.getInt("hits_required")) : 3;
        item = tag.contains("item") ? ResourceLocation.tryParse(tag.getString("item")) : null;
        hits = tag.getInt("hits");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("hits_required", hitsRequired);
        if (item != null) tag.putString("item", item.toString());
        tag.putInt("hits", hits);
    }
}
