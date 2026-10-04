package com.rpgcore.logic;

import java.util.List;
import java.util.random.RandomGenerator;

/** Tier multiplier, pity and weighted picks (TECH_SPEC §6, #45 #48). */
public final class LootMath {
    private LootMath() {}

    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 5;

    /** #45: 1 + 0.15 x (tier - 1). */
    public static double tierMultiplier(int tier) {
        return 1.0 + 0.15 * (clampTier(tier) - 1);
    }

    public static int clampTier(int tier) {
        return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
    }

    /** #48: base x (1 + 0.1 x consecutive misses), capped at 1. */
    public static double pityChance(double base, int misses) {
        return Math.min(1.0, base * (1.0 + 0.1 * Math.max(0, misses)));
    }

    /** Index of a weighted pick, or -1 if all weights are zero. */
    public static int pickWeighted(List<? extends Number> weights, RandomGenerator random) {
        double total = 0;
        for (Number w : weights) total += Math.max(0, w.doubleValue());
        if (total <= 0) return -1;
        double r = random.nextDouble() * total;
        for (int i = 0; i < weights.size(); i++) {
            double w = Math.max(0, weights.get(i).doubleValue());
            if (r < w) return i;
            r -= w;
        }
        return weights.size() - 1;
    }

    /** Uniform roll in [min, max]. */
    public static double roll(double min, double max, RandomGenerator random) {
        if (max <= min) return min;
        return min + random.nextDouble() * (max - min);
    }

    /** Uniform int in [min, max]. */
    public static int rollInt(int min, int max, RandomGenerator random) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }

    /**
     * One legendary roll with pity. Returns the new miss counter: 0 on success, misses + 1 on failure.
     * {@code hit[0]} receives the result.
     */
    public static int rollWithPity(double base, int misses, RandomGenerator random, boolean[] hit) {
        boolean ok = random.nextDouble() < pityChance(base, misses);
        hit[0] = ok;
        return ok ? 0 : misses + 1;
    }
}
