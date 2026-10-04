package com.rpgcore.loot;

import com.rpgcore.item.ItemData;
import com.rpgcore.logic.LootMath;
import com.rpgcore.logic.Rarity;
import com.rpgcore.logic.School;
import com.rpgcore.trait.TraitDef;
import com.rpgcore.trait.TraitRegistry;
import com.rpgcore.util.Rng;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Creates random gear (§6): rarity decides the minor count (#23), all traits share one school. */
public final class Generator {
    private Generator() {}

    public static ItemStack gear(Rarity rarity, int tier, String category, RandomSource random) {
        Bases.BaseDef base = Bases.pick(category, tier, random);
        if (base == null) return ItemStack.EMPTY;
        ItemStack stack = base.template().copy();
        School school = base.school() != null ? base.school() : School.GEAR[random.nextInt(School.GEAR.length)];
        ItemData.setRarity(stack, rarity);
        ItemData.setTier(stack, tier);
        ItemData.setSchool(stack, school);
        int count = rarity.minorCount(random.nextDouble());
        ItemData.setTraits(stack, rollMinors(school, count, new HashSet<>(), random));
        return stack;
    }

    /** {@code count} distinct minor traits of {@code school} (plus "any"), not in {@code exclude}. */
    public static List<ItemData.TraitRoll> rollMinors(School school, int count, Set<ResourceLocation> exclude, RandomSource random) {
        List<ItemData.TraitRoll> out = new ArrayList<>();
        List<TraitDef> pool = new ArrayList<>(TraitRegistry.minors(school));
        pool.removeIf(d -> exclude.contains(d.id()));
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            TraitDef def = pool.remove(random.nextInt(pool.size()));
            out.add(roll(def, random, false));
        }
        return out;
    }

    public static ItemData.TraitRoll roll(TraitDef def, RandomSource random, boolean locked) {
        double value = def.hasRoll() ? LootMath.roll(def.rollMin(), def.rollMax(), Rng.of(random)) : 0.0;
        return new ItemData.TraitRoll(def.id(), Math.round(value * 1000.0) / 1000.0, locked);
    }
}
