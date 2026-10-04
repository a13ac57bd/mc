package com.rpgcore.logic;

/** Vanilla experience curve, used by the death rule (#27: keep half the experience). */
public final class XpMath {
    private XpMath() {}

    /** Points needed to go from {@code level} to {@code level + 1} (vanilla Player#getXpNeededForNextLevel). */
    public static int neededForNext(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    /** Total points represented by a level and the progress bar (0..1). */
    public static int total(int level, float progress) {
        long sum = 0;
        for (int l = 0; l < level; l++) sum += neededForNext(l);
        sum += Math.round(progress * neededForNext(level));
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }
}
