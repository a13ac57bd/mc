package com.rpgcore.logic;

/** Danger tier helpers (TECH_SPEC §7) and mob scaling numbers. */
public final class DangerMath {
    private DangerMath() {}

    public static final int[] DEFAULT_RINGS = {1000, 2500, 5000, 8000};

    /** Tier 1 inside the first ring, +1 for each ring passed. */
    public static int ringTier(double distance, int[] rings) {
        int tier = 1;
        for (int r : rings) if (distance >= r) tier++;
        return clamp(tier);
    }

    public static int clamp(int tier) {
        return LootMath.clampTier(tier);
    }

    /** Health bonus per tier above 1 (MULTIPLY_TOTAL amount). */
    public static double healthBonus(int tier) {
        return 0.35 * (clamp(tier) - 1);
    }

    /** Damage multiplier for a mob of the given tier. */
    public static double damageMultiplier(int tier) {
        return 1.0 + 0.25 * (clamp(tier) - 1);
    }

    /** Elite chance by tier: 2%, 4%, 6%, 8%, 10%. */
    public static double eliteChance(int tier) {
        return 0.02 * clamp(tier);
    }

    /** Elite affix count by tier: 1/1/2/2/3. */
    public static int eliteAffixes(int tier) {
        return switch (clamp(tier)) {
            case 1, 2 -> 1;
            case 3, 4 -> 2;
            default -> 3;
        };
    }
}
