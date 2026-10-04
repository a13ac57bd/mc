package com.rpgcore.town;

import com.rpgcore.registry.RpgEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Town state (§13): where each town is, its inn, and NPCs stored away while hidden (world events). NPCs have no
 * show/hide API, so hiding saves them as NBT and removes them; showing puts them back.
 */
public class TownSavedData extends SavedData {
    public static final String NAME = "rpgcore_towns";

    public static final class Town {
        public final ResourceLocation id;
        public ResourceKey<Level> dimension;
        public BlockPos center;
        public int radius;
        public BlockPos inn;
        public boolean hidden;
        public final ListTag storedNpcs = new ListTag();

        Town(ResourceLocation id, ResourceKey<Level> dimension, BlockPos center, int radius) {
            this.id = id;
            this.dimension = dimension;
            this.center = center;
            this.radius = radius;
        }

        public boolean contains(ResourceKey<Level> dim, BlockPos pos) {
            double dx = pos.getX() - center.getX(), dz = pos.getZ() - center.getZ();
            return dimension.equals(dim) && dx * dx + dz * dz <= (double) radius * radius;
        }
    }

    private final Map<ResourceLocation, Town> towns = new LinkedHashMap<>();

    public static TownSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TownSavedData::load, TownSavedData::new, NAME);
    }

    public Town get(ResourceLocation id) {
        return towns.get(id);
    }

    public Collection<Town> all() {
        return towns.values();
    }

    public Town townAt(ResourceKey<Level> dim, BlockPos pos) {
        for (Town t : towns.values()) if (t.contains(dim, pos)) return t;
        return null;
    }

    public Town create(ResourceLocation id, ResourceKey<Level> dim, BlockPos center, int radius) {
        Town t = towns.computeIfAbsent(id, k -> new Town(id, dim, center, radius));
        t.dimension = dim;
        t.center = center;
        t.radius = radius;
        setDirty();
        return t;
    }

    public void setInn(ResourceLocation id, BlockPos pos) {
        Town t = towns.get(id);
        if (t != null) {
            t.inn = pos;
            setDirty();
        }
    }

    /** Stores away (hidden) or restores the town's NPCs. */
    public void setNpcsHidden(MinecraftServer server, ResourceLocation id, boolean hidden) {
        Town t = towns.get(id);
        if (t == null || t.hidden == hidden) return;
        t.hidden = hidden;
        setDirty();
        ServerLevel level = server.getLevel(t.dimension);
        if (level == null) return;
        if (hidden) {
            AABB box = new AABB(t.center).inflate(t.radius, 64, t.radius);
            for (NpcEntity npc : level.getEntitiesOfClass(NpcEntity.class, box, n -> id.equals(n.town()))) store(npc);
        } else {
            restore(level, t);
        }
    }

    /** Saves an NPC of a hidden town and removes it (also called by NPCs that were in unloaded chunks). */
    public void store(NpcEntity npc) {
        Town t = towns.get(npc.town());
        if (t == null) return;
        CompoundTag tag = new CompoundTag();
        if (npc.save(tag)) {
            t.storedNpcs.add(tag);
            setDirty();
        }
        npc.discard();
    }

    private void restore(ServerLevel level, Town t) {
        for (int i = 0; i < t.storedNpcs.size(); i++) {
            CompoundTag tag = t.storedNpcs.getCompound(i);
            if (tag.contains("Pos", Tag.TAG_LIST)) {
                ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
                level.getChunk(BlockPos.containing(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2)));
            }
            Optional<Entity> e = EntityType.create(tag, level);
            e.ifPresent(level::addFreshEntity);
        }
        t.storedNpcs.clear();
        setDirty();
    }

    public static TownSavedData load(CompoundTag tag) {
        TownSavedData d = new TownSavedData();
        CompoundTag all = tag.getCompound("towns");
        for (String key : all.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) continue;
            CompoundTag t = all.getCompound(key);
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(t.getString("dimension")));
            Town town = new Town(id, dim, NbtUtils.readBlockPos(t.getCompound("center")), t.getInt("radius"));
            if (t.contains("inn")) town.inn = NbtUtils.readBlockPos(t.getCompound("inn"));
            town.hidden = t.getBoolean("hidden");
            town.storedNpcs.addAll(t.getList("npcs", Tag.TAG_COMPOUND));
            d.towns.put(id, town);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Town town : towns.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("dimension", town.dimension.location().toString());
            t.put("center", NbtUtils.writeBlockPos(town.center));
            t.putInt("radius", town.radius);
            if (town.inn != null) t.put("inn", NbtUtils.writeBlockPos(town.inn));
            t.putBoolean("hidden", town.hidden);
            t.put("npcs", town.storedNpcs.copy());
            all.put(town.id.toString(), t);
        }
        tag.put("towns", all);
        return tag;
    }

    /** Spawns the template NPCs of a town def around its center. */
    public static int spawnTemplateNpcs(ServerLevel level, Town town, TownDefs.TownDef def) {
        int n = 0;
        for (TownDefs.NpcSpec spec : def.npcs()) {
            NpcEntity npc = RpgEntities.NPC.get().create(level);
            if (npc == null) continue;
            BlockPos p = town.center.offset(spec.offset());
            npc.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0F, 0F);
            npc.configure(spec.role(), spec.name(), spec.dialogue(), town.id);
            level.addFreshEntity(npc);
            n++;
        }
        return n;
    }
}
