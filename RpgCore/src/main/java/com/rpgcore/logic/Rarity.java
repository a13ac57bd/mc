package com.rpgcore.logic;

import java.util.Locale;

/** Item rarity (MECH §4). Pure logic: no Minecraft imports allowed in com.rpgcore.logic. */
public enum Rarity {
    COMMON(0xFFFFFF, 0, 0),
    UNCOMMON(0x55FF55, 1, 1),
    RARE(0x5599FF, 2, 2),
    LEGENDARY(0xFFAA00, 0, 1),
    ARTIFACT(0xFF5555, 0, 1);

    private final int color;
    private final int minMinor;
    private final int maxMinor;

    Rarity(int color, int minMinor, int maxMinor) {
        this.color = color;
        this.minMinor = minMinor;
        this.maxMinor = maxMinor;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int color() {
        return color;
    }

    /** Number of random minor traits on top of fixed ones (#23). Legendary/artifact: 0 or 1 (50%). */
    public int minorCount(double random01) {
        if (minMinor == maxMinor) return minMinor;
        return random01 < 0.5 ? minMinor : maxMinor;
    }

    public boolean isUnique() {
        return this == LEGENDARY || this == ARTIFACT;
    }

    public static Rarity byId(String id) {
        if (id == null) return null;
        for (Rarity r : values()) if (r.id().equals(id)) return r;
        return null;
    }
}
