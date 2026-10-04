package com.rpgcore.rift;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One running rift (§11 RiftInstance): grid cell, floor, timer, mobs, pending rewards and where everyone came from. */
public final class RiftInstance {
    public enum Phase { FIGHTING, CLEARED }

    public record Origin(ResourceKey<Level> dimension, double x, double y, double z) {}

    public final int cell;
    public final int tier;
    public final BlockPos center;
    public final BoundingBox box;
    public int floor = 1;
    public int ticksLeft;
    public Phase phase = Phase.FIGHTING;
    /** Direct references: entities in freshly force-loaded chunks cannot be found by UUID until the next tick. */
    public final List<Mob> mobs = new ArrayList<>();
    public final List<ItemStack> rewards = new ArrayList<>();
    public final Map<UUID, Origin> players = new LinkedHashMap<>();

    public RiftInstance(int cell, int tier, BlockPos center, int radius, int ticks) {
        this.cell = cell;
        this.tier = tier;
        this.center = center;
        this.box = new BoundingBox(center.getX() - radius - 2, center.getY() - 4, center.getZ() - radius - 2,
                center.getX() + radius + 2, center.getY() + 12, center.getZ() + radius + 2);
        this.ticksLeft = ticks;
    }

    public BlockPos continueDoor() {
        return center.offset(3, 0, 0);
    }

    public BlockPos exitDoor() {
        return center.offset(-3, 0, 0);
    }

    public boolean mobsDead() {
        for (Mob m : mobs) if (m.isAlive() && !m.isRemoved()) return false;
        return true;
    }
}
