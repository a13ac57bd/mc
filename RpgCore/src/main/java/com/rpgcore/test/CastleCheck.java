package com.rpgcore.test;

import com.rpgcore.RpgCore;
import com.rpgcore.registry.RpgTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Castle self-test (§9): generates rpgcore:castle at a position (like /place structure), then counts rpgcore blocks,
 * leftover jigsaw blocks, chests and dispensers, and writes top and side views as PNG.
 */
public final class CastleCheck {
    private CastleCheck() {}

    public record Result(int pieces, BoundingBox box, Map<String, Integer> counts) {
        public int count(String id) {
            return counts.getOrDefault(id, 0);
        }
    }

    public static Result placeAndScan(ServerLevel level, BlockPos origin, File imageDir) {
        Structure castle = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RpgCore.id("castle"));
        if (castle == null) return null;
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        StructureStart start = castle.generate(level.registryAccess(), gen, gen.getBiomeSource(), level.getChunkSource().randomState(),
                level.getStructureManager(), level.getSeed(), new ChunkPos(origin), 0, level, b -> true);
        if (!start.isValid()) return new Result(0, null, Map.of());
        BoundingBox box = start.getBoundingBox();
        ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
        ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        ChunkPos.rangeClosed(min, max).forEach(cp -> {
            level.getChunk(cp.x, cp.z);
            start.placeInChunk(level, level.structureManager(), gen, level.getRandom(),
                    new BoundingBox(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX(), level.getMaxBuildHeight(), cp.getMaxBlockZ()), cp);
        });
        return scan(level, start, imageDir, "castle");
    }

    /**
     * Finds the nearest naturally generated castle, generates every chunk it touches (terrain adaptation included) and
     * scans it. Returns null when no castle is within {@code radiusChunks}.
     */
    public static Result locateAndScan(ServerLevel level, BlockPos from, int radiusChunks, File imageDir) {
        Structure castle = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RpgCore.id("castle"));
        BlockPos found = level.findNearestMapStructure(RpgTags.DUNGEON, from, radiusChunks, false);
        if (castle == null || found == null) return null;
        ChunkPos startChunk = new ChunkPos(found);
        StructureStart start = level.getChunk(startChunk.x, startChunk.z).getStartForStructure(castle);
        if (start == null || !start.isValid()) return new Result(0, null, Map.of());
        BoundingBox box = start.getBoundingBox();
        ChunkPos.rangeClosed(new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ())),
                new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()))).forEach(cp -> level.getChunk(cp.x, cp.z));
        return scan(level, start, imageDir, "castle_natural");
    }

    private static Result scan(ServerLevel level, StructureStart start, File imageDir, String name) {
        BoundingBox box = start.getBoundingBox();
        Map<String, Integer> counts = new TreeMap<>();
        int w = box.getXSpan(), d = box.getZSpan(), h = box.getYSpan();
        BufferedImage top = new BufferedImage(w, d, BufferedImage.TYPE_INT_RGB);
        BufferedImage side = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean topDone = false;
                for (int y = h - 1; y >= 0; y--) {
                    BlockPos p = new BlockPos(box.minX() + x, box.minY() + y, box.minZ() + z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir()) continue;
                    ResourceLocation id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
                    String key = id == null ? "?" : id.toString();
                    if (key.startsWith("rpgcore:") || is(s, "jigsaw", "chest", "dispenser")) counts.merge(key, 1, Integer::sum);
                    int color = color(level, p, s);
                    if (!topDone) {
                        top.setRGB(x, z, color);
                        topDone = true;
                    }
                    if (z == d / 2) side.setRGB(x, h - 1 - y, color);
                }
            }
        }
        if (imageDir != null) {
            try {
                imageDir.mkdirs();
                ImageIO.write(top, "png", new File(imageDir, name + "_top.png"));
                ImageIO.write(side, "png", new File(imageDir, name + "_side.png"));
            } catch (IOException e) {
                RpgCore.LOG.warn("rpgcore: castle images not written: {}", e.toString());
            }
        }
        return new Result(start.getPieces().size(), box, counts);
    }

    private static boolean is(BlockState s, String... vanilla) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
        if (id == null || !id.getNamespace().equals("minecraft")) return false;
        for (String v : vanilla) if (id.getPath().equals(v)) return true;
        return false;
    }

    private static int color(ServerLevel level, BlockPos p, BlockState s) {
        Block b = s.getBlock();
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(b);
        if (id != null && id.getNamespace().equals(RpgCore.MODID)) return 0xFF00FF;
        MapColor c = s.getMapColor(level, p);
        return c == MapColor.NONE ? 0x000000 : c.col;
    }
}
