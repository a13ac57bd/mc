package com.rpgcore.forge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.data.RpgData;
import com.rpgcore.trait.TraitDef;
import com.rpgcore.trait.TraitRegistry;
import com.rpgcore.util.Json;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/** Inscriptions (#47, data/&lt;ns&gt;/rpgcore/inscriptions/*.json): special material -> fixed inscription trait. */
public final class Inscriptions {
    private Inscriptions() {}

    public record InscriptionDef(ResourceLocation id, Item material, ResourceLocation trait) {}

    private static Map<Item, InscriptionDef> byMaterial = Map.of();

    public static InscriptionDef forMaterial(ItemStack stack) {
        return stack.isEmpty() ? null : byMaterial.get(stack.getItem());
    }

    public static Map<Item, InscriptionDef> all() {
        return byMaterial;
    }

    public static void load(Map<ResourceLocation, JsonElement> files) {
        Map<Item, InscriptionDef> loaded = new LinkedHashMap<>();
        RpgData.each("inscription", files, (id, json) -> {
            JsonObject j = json.getAsJsonObject();
            Item material = Json.item(Json.str(j, "material", ""));
            if (material == null) throw new IllegalArgumentException("unknown material");
            ResourceLocation trait = Json.id(j, "trait");
            TraitDef def = TraitRegistry.get(trait);
            if (def == null || def.type() != TraitDef.Type.INSCRIPTION) throw new IllegalArgumentException("trait " + trait + " is not an inscription trait");
            loaded.put(material, new InscriptionDef(id, material, trait));
        });
        byMaterial = loaded;
    }
}
