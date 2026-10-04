package com.rpgcore.dungeon;

import com.rpgcore.registry.RpgTags;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Dungeon instances per dimension (§9): bounding box (the structure's, or 48 blocks around the oath stone), oath stone
 * position and the players who activated it.
 */
public class DungeonData extends SavedData {
    public static final String NAME = "rpgcore_dungeons";
    public static final int FALLBACK_RADIUS = 48;

    public static final class Instance {
        public final BoundingBox box;
        public BlockPos oath;
        public final Set<UUID> players = new HashSet<>();

        Instance(BoundingBox box, BlockPos oath) {
            this.box = box;
            this.oath = oath;
        }
    }

    private final List<Instance> instances = new ArrayList<>();

    public static DungeonData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(DungeonData::load, DungeonData::new, NAME);
    }

    public List<Instance> instances() {
        return instances;
    }

    /** Box of the dungeon structure at {@code pos}, or a cube around it. */
    public static BoundingBox boxAt(ServerLevel level, BlockPos pos) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, RpgTags.DUNGEON);
        if (start.isValid()) return start.getBoundingBox();
        return new BoundingBox(pos.getX() - FALLBACK_RADIUS, pos.getY() - FALLBACK_RADIUS, pos.getZ() - FALLBACK_RADIUS,
                pos.getX() + FALLBACK_RADIUS, pos.getY() + FALLBACK_RADIUS, pos.getZ() + FALLBACK_RADIUS);
    }

    public Instance activate(ServerLevel level, BlockPos oath, UUID player) {
        Instance inst = find(oath);
        if (inst == null) {
            inst = new Instance(boxAt(level, oath), oath);
            instances.add(inst);
        }
        inst.oath = oath;
        inst.players.add(player);
        setDirty();
        return inst;
    }

    public Instance find(BlockPos pos) {
        for (Instance i : instances) if (i.box.isInside(pos)) return i;
        return null;
    }

    /** The instance containing {@code pos} that {@code player} activated, or null. */
    public Instance findFor(BlockPos pos, UUID player) {
        Instance i = find(pos);
        return i != null && i.players.contains(player) ? i : null;
    }

    public static DungeonData load(CompoundTag tag) {
        DungeonData data = new DungeonData();
        ListTag list = tag.getList("instances", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            int[] b = t.getIntArray("box");
            if (b.length != 6) continue;
            Instance inst = new Instance(new BoundingBox(b[0], b[1], b[2], b[3], b[4], b[5]), NbtUtils.readBlockPos(t.getCompound("oath")));
            ListTag players = t.getList("players", Tag.TAG_STRING);
            for (int j = 0; j < players.size(); j++) {
                try {
                    inst.players.add(UUID.fromString(players.getString(j)));
                } catch (IllegalArgumentException ignored) {
                    // corrupted entry
                }
            }
            data.instances.add(inst);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Instance i : instances) {
            CompoundTag t = new CompoundTag();
            t.putIntArray("box", new int[]{i.box.minX(), i.box.minY(), i.box.minZ(), i.box.maxX(), i.box.maxY(), i.box.maxZ()});
            t.put("oath", NbtUtils.writeBlockPos(i.oath));
            ListTag players = new ListTag();
            for (UUID u : i.players) players.add(StringTag.valueOf(u.toString()));
            t.put("players", players);
            list.add(t);
        }
        tag.put("instances", list);
        return tag;
    }
}
