package com.rpgcore.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * The random decisions of one loot source roll (TECH_SPEC §6), separated from item creation so the
 * distribution and pity can be simulated without Minecraft.
 */
public final class LootRoller {
    private LootRoller() {}

    /**
     * @param equipmentChance chance per roll to drop a random piece of gear
     * @param rarityWeights   weights for COMMON, UNCOMMON, RARE (index = ordinal)
     * @param legendaryChance base chance for a legendary from the source's pool (pity applies)
     * @param rolls           gear rolls per kill/chest
     */
    public record Spec(double equipmentChance, double[] rarityWeights, double legendaryChance, int rolls) {}

    public record Result(List<Rarity> equipment, boolean legendary, int misses) {}

    public static Result roll(Spec spec, int misses, boolean forceLegendary, RandomGenerator rng) {
        List<Rarity> gear = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (double w : spec.rarityWeights()) weights.add(w);
        for (int i = 0; i < spec.rolls(); i++) {
            if (rng.nextDouble() < spec.equipmentChance()) {
                int idx = LootMath.pickWeighted(weights, rng);
                if (idx >= 0) gear.add(Rarity.values()[idx]);
            }
        }
        boolean legendary;
        int newMisses;
        if (forceLegendary) {
            legendary = true;
            newMisses = 0;
        } else if (spec.legendaryChance() > 0) {
            boolean[] hit = new boolean[1];
            newMisses = LootMath.rollWithPity(spec.legendaryChance(), misses, rng, hit);
            legendary = hit[0];
        } else {
            legendary = false;
            newMisses = misses;
        }
        return new Result(gear, legendary, newMisses);
    }
}
