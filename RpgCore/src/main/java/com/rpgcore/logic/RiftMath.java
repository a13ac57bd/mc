package com.rpgcore.logic;

import java.util.ArrayList;
import java.util.List;

/** Rift numbers (TECH_SPEC §11). Defaults match rift/config.json. */
public final class RiftMath {
    private RiftMath() {}

    /** Mob strength multiplier for a floor (floor 1 = 1.0). */
    public static double strength(int floor, double perFloor) {
        return 1.0 + perFloor * (Math.max(1, floor) - 1);
    }

    /** Reward multiplier for a floor (floor 1 = 1.0). */
    public static double rewardMultiplier(int floor, double perFloor) {
        return 1.0 + perFloor * (Math.max(1, floor) - 1);
    }

    public static int mobCount(int floor, int base, int perFloor) {
        return base + perFloor * (Math.max(1, floor) - 1);
    }

    public static double bossChance(int floor, int startFloor, double chance) {
        return floor >= startFloor ? chance : 0.0;
    }

    /** Number of reward rolls for a floor: the multiplier's integer part plus a chance for the fraction. */
    public static int rewardRolls(double multiplier, double random01) {
        int whole = (int) Math.floor(multiplier);
        return whole + (random01 < multiplier - whole ? 1 : 0);
    }

    /**
     * Timeout / wipe penalty: every stack is halved (rounded down); single items alternate,
     * keeping the first, dropping the second, and so on. Returns the kept count per input stack.
     */
    public static List<Integer> halve(List<Integer> counts) {
        List<Integer> out = new ArrayList<>(counts.size());
        boolean keepNextSingle = true;
        for (int c : counts) {
            if (c <= 0) {
                out.add(0);
            } else if (c == 1) {
                out.add(keepNextSingle ? 1 : 0);
                keepNextSingle = !keepNextSingle;
            } else {
                out.add(c / 2);
            }
        }
        return out;
    }
}
