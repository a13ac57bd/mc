package com.rpgcore.danger;

import com.rpgcore.events.WorldEvents;
import com.rpgcore.logic.DangerMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Danger tier 1..5 of a position (§7): ring distance from spawn + biome modifier (cached per chunk) -> structure
 * override (generated chunks only) -> world event modifier, clamped. Dimension fixed values replace the rings;
 * {@link #override} boxes (rifts) win over everything.
 */
public final class DangerMap {
    private DangerMap() {}

    public record Zone(ResourceKey<Level> dimension, BoundingBox box, int tier) {}

    private static final Map<ResourceKey<Level>, Map<Long, Integer>> CACHE = new HashMap<>();
    private static final List<Zone> OVERRIDES = new ArrayList<>();

    public static void clearCache() {
        CACHE.clear();
    }

    public static void override(ResourceKey<Level> dimension, BoundingBox box, int tier) {
        OVERRIDES.add(new Zone(dimension, box, tier));
    }

    public static void removeOverride(ResourceKey<Level> dimension, BoundingBox box) {
        OVERRIDES.removeIf(o -> o.dimension().equals(dimension) && o.box().equals(box));
    }

    public static int get(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sl)) return 1;
        for (Zone o : OVERRIDES) {
            if (o.dimension().equals(level.dimension()) && o.box().isInside(pos)) return DangerMath.clamp(o.tier());
        }
        int tier = base(sl, pos);
        Integer structure = structureTier(sl, pos);
        if (structure != null) tier = structure;
        tier += WorldEvents.dangerModifier(sl, pos);
        return DangerMath.clamp(tier);
    }

    /**
     * Rings (or the dimension's fixed value) only: no chunk, biome or cache access. Safe during chunk generation,
     * where FinalizeSpawn runs with a WorldGenRegion.
     */
    public static int ringOnly(ServerLevel level, BlockPos pos) {
        Integer fixed = DangerConfig.dimensions.get(level.dimension().location());
        if (fixed != null && fixed > 0) return DangerMath.clamp(fixed);
        BlockPos spawn = level.getSharedSpawnPos();
        double dx = pos.getX() - spawn.getX(), dz = pos.getZ() - spawn.getZ();
        return DangerMath.ringTier(Math.sqrt(dx * dx + dz * dz), DangerConfig.rings);
    }

    /** Ring + biome (or the dimension's fixed value), cached per chunk. */
    public static int base(ServerLevel level, BlockPos pos) {
        Integer fixed = DangerConfig.dimensions.get(level.dimension().location());
        if (fixed != null && fixed > 0) return fixed;
        Map<Long, Integer> cache = CACHE.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Integer cached = cache.get(key);
        if (cached != null) return cached;
        BlockPos spawn = level.getSharedSpawnPos();
        double dx = pos.getX() - spawn.getX(), dz = pos.getZ() - spawn.getZ();
        int tier = DangerMath.ringTier(Math.sqrt(dx * dx + dz * dz), DangerConfig.rings);
        Holder<Biome> biome = level.getBiome(pos);
        for (DangerConfig.BiomeMod mod : DangerConfig.biomes) {
            if (mod.matches(biome)) {
                tier += mod.modifier();
                break;
            }
        }
        tier = DangerMath.clamp(tier);
        if (cache.size() > 65536) cache.clear();
        cache.put(key, tier);
        return tier;
    }

    /** Fixed tier of a configured structure containing {@code pos}; only looks at loaded chunks. */
    public static Integer structureTier(ServerLevel level, BlockPos pos) {
        if (DangerConfig.structures.isEmpty() || !level.isLoaded(pos)) return null;
        ChunkAccess chunk = level.getChunk(pos);
        if (chunk.getAllReferences().isEmpty()) return null;
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<ResourceLocation, Integer> e : DangerConfig.structures.entrySet()) {
            Structure structure = registry.get(e.getKey());
            if (structure == null || !chunk.getAllReferences().containsKey(structure)) continue;
            if (level.structureManager().getStructureWithPieceAt(pos, structure).isValid()) return e.getValue();
        }
        return null;
    }
}
