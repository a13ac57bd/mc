package com.rpgcore.logic;

/** Number scale x5 (#16 #17) and dash physics (#19). */
public final class CombatMath {
    private CombatMath() {}

    /** All computations use vanilla units; only the final damage/heal is multiplied. */
    public static final float SCALE = 5.0f;
    public static final double PLAYER_BASE_HEALTH = 100.0;
    /** MULTIPLY_TOTAL amount that turns x1 into x5. */
    public static final double HEALTH_MODIFIER = SCALE - 1.0;

    /** Vanilla ground drag: block friction x 0.91. Air drag is 0.91. */
    public static double horizontalDrag(float blockFriction, boolean onGround) {
        return onGround ? blockFriction * 0.91 : 0.91;
    }

    /**
     * Initial horizontal speed so the total travel equals {@code distance}:
     * sum of v0 * drag^n = v0 / (1 - drag)  =>  v0 = distance * (1 - drag).
     */
    public static double dashSpeed(double distance, double drag) {
        return distance * (1.0 - drag);
    }

    /** Total distance covered by speed v0 under geometric drag. */
    public static double travel(double v0, double drag) {
        return v0 / (1.0 - drag);
    }

    /** Charge recovery: ticks per charge from seconds. */
    public static int ticksPerCharge(double cooldownSeconds) {
        return Math.max(1, (int) Math.round(cooldownSeconds * 20.0));
    }

    /** Crit multiplier: returns crit damage if the roll passed, else 1. */
    public static double crit(boolean crit, double critDamage) {
        return crit ? Math.max(1.0, critDamage) : 1.0;
    }

    /** Is {@code toAttacker} within the front cone of {@code facing}? Vectors on the XZ plane. */
    public static boolean inFrontCone(double fx, double fz, double ax, double az, double fullAngleDegrees) {
        double lf = Math.sqrt(fx * fx + fz * fz);
        double la = Math.sqrt(ax * ax + az * az);
        if (lf < 1e-6 || la < 1e-6) return false;
        double cos = (fx * ax + fz * az) / (lf * la);
        return cos >= Math.cos(Math.toRadians(fullAngleDegrees / 2.0));
    }
}
