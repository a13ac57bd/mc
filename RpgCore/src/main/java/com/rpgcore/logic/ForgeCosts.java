package com.rpgcore.logic;

/** Forge table costs and salvage yields (TECH_SPEC §6). All counts are of the item's tier material. */
public final class ForgeCosts {
    private ForgeCosts() {}

    /** Upgrade tier t -> t+1: 3 + 2t materials of tier t. -1 when already max tier. */
    public static int upgradeCost(int tier) {
        if (tier >= LootMath.MAX_TIER) return -1;
        return 3 + 2 * LootMath.clampTier(tier);
    }

    /** Reroll one trait: 2 + previous rerolls, capped at 12. Only rare and above. */
    public static int rerollCost(int rerolls) {
        return Math.min(12, 2 + Math.max(0, rerolls));
    }

    public static boolean canReroll(Rarity rarity) {
        return rarity != null && rarity.ordinal() >= Rarity.RARE.ordinal();
    }

    /** Materials of the item's tier returned by salvaging. */
    public static int salvageYield(Rarity rarity, int upgrades) {
        int base = switch (rarity == null ? Rarity.COMMON : rarity) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 4;
            case LEGENDARY -> 8;
            case ARTIFACT -> 12;
        };
        return base + Math.max(0, upgrades);
    }

    /** Auto-salvage thresholds: 0 off, 1 common, 2 uncommon, 3 rare. */
    public static boolean autoSalvage(int threshold, Rarity rarity) {
        if (threshold <= 0 || rarity == null || rarity.isUnique()) return false;
        return rarity.ordinal() < threshold;
    }
}
