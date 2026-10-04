package com.rpgcore.item;

import com.rpgcore.logic.LootMath;
import com.rpgcore.logic.Rarity;
import com.rpgcore.logic.School;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code rpgcore} sub-tag of an item (TECH_SPEC §4):
 * rarity, tier, upgrade, unique, traits[{id, roll, locked}], inscription, rerolls, school.
 */
public final class ItemData {
    private ItemData() {}

    public static final String ROOT = "rpgcore";

    public record TraitRoll(ResourceLocation id, double roll, boolean locked) {
        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("id", id.toString());
            t.putDouble("roll", roll);
            t.putBoolean("locked", locked);
            return t;
        }

        public static TraitRoll load(CompoundTag t) {
            ResourceLocation id = ResourceLocation.tryParse(t.getString("id"));
            return id == null ? null : new TraitRoll(id, t.getDouble("roll"), t.getBoolean("locked"));
        }
    }

    public static boolean has(ItemStack stack) {
        return !stack.isEmpty() && stack.getTag() != null && stack.getTag().contains(ROOT, Tag.TAG_COMPOUND);
    }

    /** Read-only view; empty tag when absent. */
    public static CompoundTag get(ItemStack stack) {
        return has(stack) ? stack.getTag().getCompound(ROOT) : new CompoundTag();
    }

    public static CompoundTag getOrCreate(ItemStack stack) {
        CompoundTag root = stack.getOrCreateTag();
        if (!root.contains(ROOT, Tag.TAG_COMPOUND)) root.put(ROOT, new CompoundTag());
        return root.getCompound(ROOT);
    }

    public static Rarity rarity(ItemStack stack) {
        return Rarity.byId(get(stack).getString("rarity"));
    }

    public static void setRarity(ItemStack stack, Rarity rarity) {
        getOrCreate(stack).putString("rarity", rarity.id());
    }

    /** 1..5; items without rpgcore data count as tier 1. */
    public static int tier(ItemStack stack) {
        CompoundTag t = get(stack);
        return t.contains("tier") ? LootMath.clampTier(t.getInt("tier")) : 1;
    }

    public static void setTier(ItemStack stack, int tier) {
        getOrCreate(stack).putInt("tier", LootMath.clampTier(tier));
    }

    public static int upgrade(ItemStack stack) {
        return get(stack).getInt("upgrade");
    }

    public static void setUpgrade(ItemStack stack, int upgrade) {
        getOrCreate(stack).putInt("upgrade", upgrade);
    }

    public static ResourceLocation unique(ItemStack stack) {
        CompoundTag t = get(stack);
        return t.contains("unique") ? ResourceLocation.tryParse(t.getString("unique")) : null;
    }

    public static void setUnique(ItemStack stack, ResourceLocation id) {
        getOrCreate(stack).putString("unique", id.toString());
    }

    public static School school(ItemStack stack) {
        School s = School.byId(get(stack).getString("school"));
        return s == null ? School.ANY : s;
    }

    public static void setSchool(ItemStack stack, School school) {
        getOrCreate(stack).putString("school", school.id());
    }

    public static List<TraitRoll> traits(ItemStack stack) {
        List<TraitRoll> out = new ArrayList<>();
        ListTag list = get(stack).getList("traits", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            TraitRoll r = TraitRoll.load(list.getCompound(i));
            if (r != null) out.add(r);
        }
        return out;
    }

    public static void setTraits(ItemStack stack, List<TraitRoll> traits) {
        ListTag list = new ListTag();
        for (TraitRoll r : traits) list.add(r.save());
        getOrCreate(stack).put("traits", list);
    }

    public static ResourceLocation inscription(ItemStack stack) {
        CompoundTag t = get(stack);
        return t.contains("inscription") ? ResourceLocation.tryParse(t.getString("inscription")) : null;
    }

    public static void setInscription(ItemStack stack, ResourceLocation id) {
        getOrCreate(stack).putString("inscription", id.toString());
    }

    public static int rerolls(ItemStack stack) {
        return get(stack).getInt("rerolls");
    }

    public static void setRerolls(ItemStack stack, int rerolls) {
        getOrCreate(stack).putInt("rerolls", rerolls);
    }

    /** Effective trait value: stored roll x tier multiplier (#45). */
    public static double effectiveRoll(ItemStack stack, TraitRoll roll) {
        return roll.roll() * LootMath.tierMultiplier(tier(stack));
    }
}
