package com.rpgcore.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/** Small, forgiving JSON accessors for data files. */
public final class Json {
    private Json() {}

    public static String str(JsonObject o, String key, String def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    public static double num(JsonObject o, String key, double def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : def;
    }

    public static int integer(JsonObject o, String key, int def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : def;
    }

    public static boolean bool(JsonObject o, String key, boolean def) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
    }

    public static JsonArray arr(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null) return new JsonArray();
        if (e.isJsonArray()) return e.getAsJsonArray();
        JsonArray a = new JsonArray();
        a.add(e);
        return a;
    }

    public static JsonObject obj(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    public static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : arr(o, key)) if (e.isJsonPrimitive()) out.add(e.getAsString());
        return out;
    }

    public static List<ResourceLocation> ids(JsonObject o, String key) {
        List<ResourceLocation> out = new ArrayList<>();
        for (String s : strings(o, key)) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id != null) out.add(id);
        }
        return out;
    }

    public static ResourceLocation id(JsonObject o, String key) {
        String s = str(o, key, null);
        return s == null ? null : ResourceLocation.tryParse(s);
    }

    /** "[min, max]" or a single number. */
    public static int[] range(JsonObject o, String key, int defMin, int defMax) {
        JsonElement e = o.get(key);
        if (e == null) return new int[]{defMin, defMax};
        if (e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
            return new int[]{e.getAsJsonArray().get(0).getAsInt(), e.getAsJsonArray().get(1).getAsInt()};
        }
        if (e.isJsonPrimitive()) {
            int v = e.getAsInt();
            return new int[]{v, v};
        }
        return new int[]{defMin, defMax};
    }

    public static double[] rangeD(JsonObject o, String key, double defMin, double defMax) {
        JsonElement e = o.get(key);
        if (e == null) return new double[]{defMin, defMax};
        if (e.isJsonArray() && e.getAsJsonArray().size() >= 2) {
            return new double[]{e.getAsJsonArray().get(0).getAsDouble(), e.getAsJsonArray().get(1).getAsDouble()};
        }
        if (e.isJsonObject()) {
            JsonObject r = e.getAsJsonObject();
            return new double[]{num(r, "min", defMin), num(r, "max", defMax)};
        }
        if (e.isJsonPrimitive()) {
            double v = e.getAsDouble();
            return new double[]{v, v};
        }
        return new double[]{defMin, defMax};
    }

    public static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) return null;
        return ForgeRegistries.ITEMS.getValue(rl);
    }

    /** {"item": "...", "count": n, "nbt": "{...}"}; EMPTY if the item does not exist (mod not installed). */
    public static ItemStack stack(JsonObject o) {
        Item item = item(str(o, "item", ""));
        if (item == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(item, Math.max(1, integer(o, "count", 1)));
        String nbt = str(o, "nbt", null);
        if (nbt != null) {
            CompoundTag tag = nbt(nbt);
            if (tag != null) stack.getOrCreateTag().merge(tag);
        }
        return stack;
    }

    public static CompoundTag nbt(String snbt) {
        try {
            return TagParser.parseTag(snbt);
        } catch (CommandSyntaxException e) {
            return null;
        }
    }
}
