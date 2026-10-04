package com.rpgcore.home;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.data.RpgData;
import com.rpgcore.util.Json;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Blueprints (data/&lt;ns&gt;/rpgcore/blueprints/*.json, §16). Either inline layers (bottom to top, rows along z, chars
 * along x, palette maps chars to block states, ' ' = keep, '_' = clear to air) or a structure template id
 * ("structure": "ns:path", e.g. saved with a structure block). Placed one layer per second by the build table.
 * aibuildbridge blueprints are not ported; this own format replaces them.
 */
public final class Blueprints {
    private Blueprints() {}

    public record Placement(BlockPos pos, BlockState state) {}

    public record BlueprintDef(ResourceLocation id, String name, ResourceLocation civilization, ResourceLocation structure,
                               List<List<Placement>> inlineLayers) {}

    private static Map<ResourceLocation, BlueprintDef> defs = Map.of();
    private static final Map<ResourceLocation, List<List<Placement>>> TEMPLATE_CACHE = new HashMap<>();

    public static BlueprintDef get(ResourceLocation id) {
        return id == null ? null : defs.get(id);
    }

    public static Map<ResourceLocation, BlueprintDef> all() {
        return defs;
    }

    public static void load(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, BlueprintDef> loaded = new LinkedHashMap<>();
        RpgData.each("blueprint", files, (id, json) -> loaded.put(id, parse(id, json.getAsJsonObject())));
        defs = loaded;
        TEMPLATE_CACHE.clear();
    }

    private static BlueprintDef parse(ResourceLocation id, JsonObject j) {
        ResourceLocation civ = Json.id(j, "civilization");
        if (civ == null) civ = Unlocks.BASIC;
        String name = Json.str(j, "name", "blueprint." + id.getNamespace() + "." + id.getPath());
        ResourceLocation structure = Json.id(j, "structure");
        List<List<Placement>> layers = new ArrayList<>();
        if (structure == null) {
            Map<Character, BlockState> palette = new HashMap<>();
            palette.put('_', Blocks.AIR.defaultBlockState());
            for (Map.Entry<String, JsonElement> e : Json.obj(j, "palette").entrySet()) {
                ResourceLocation bid = new ResourceLocation(e.getValue().getAsString());
                if (!ForgeRegistries.BLOCKS.containsKey(bid)) throw new IllegalArgumentException("unknown block " + bid);
                Block block = ForgeRegistries.BLOCKS.getValue(bid);
                palette.put(e.getKey().charAt(0), block.defaultBlockState());
            }
            int y = 0;
            for (JsonElement le : Json.arr(j, "layers")) {
                List<Placement> layer = new ArrayList<>();
                int z = 0;
                for (JsonElement row : le.getAsJsonArray()) {
                    String r = row.getAsString();
                    for (int x = 0; x < r.length(); x++) {
                        char c = r.charAt(x);
                        if (c == ' ' || c == '.') continue;
                        BlockState s = palette.get(c);
                        if (s == null) throw new IllegalArgumentException("layer " + y + ": char '" + c + "' not in palette");
                        layer.add(new Placement(new BlockPos(x, y, z), s));
                    }
                    z++;
                }
                layers.add(layer);
                y++;
            }
            if (layers.isEmpty()) throw new IllegalArgumentException("blueprint has no layers");
        }
        return new BlueprintDef(id, name, civ, structure, List.copyOf(layers));
    }

    /** Layers bottom to top. Template blueprints are read from the structure manager once and cached. */
    public static List<List<Placement>> layers(ServerLevel level, BlueprintDef def) {
        if (def.structure() == null) return def.inlineLayers();
        return TEMPLATE_CACHE.computeIfAbsent(def.id(), k -> readTemplate(level, def.structure()));
    }

    private static List<List<Placement>> readTemplate(ServerLevel level, ResourceLocation structure) {
        Optional<StructureTemplate> template = level.getStructureManager().get(structure);
        if (template.isEmpty()) return List.of();
        CompoundTag tag = template.get().save(new CompoundTag());
        ListTag paletteTag = tag.getList("palette", Tag.TAG_COMPOUND);
        List<BlockState> palette = new ArrayList<>();
        for (int i = 0; i < paletteTag.size(); i++) palette.add(NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), paletteTag.getCompound(i)));
        TreeMap<Integer, List<Placement>> byY = new TreeMap<>();
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag b = blocks.getCompound(i);
            ListTag p = b.getList("pos", Tag.TAG_INT);
            BlockPos pos = new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2));
            BlockState state = palette.get(b.getInt("state"));
            if (state.is(Blocks.STRUCTURE_VOID)) continue;
            byY.computeIfAbsent(pos.getY(), y -> new ArrayList<>()).add(new Placement(pos, state));
        }
        return List.copyOf(byY.values());
    }

    /** Footprint size (x, y, z) for the preview outline. */
    public static BlockPos size(List<List<Placement>> layers) {
        int mx = 0, my = 0, mz = 0;
        for (List<Placement> layer : layers) {
            for (Placement p : layer) {
                mx = Math.max(mx, p.pos().getX());
                my = Math.max(my, p.pos().getY());
                mz = Math.max(mz, p.pos().getZ());
            }
        }
        return new BlockPos(mx + 1, my + 1, mz + 1);
    }
}
