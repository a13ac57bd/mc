package com.rpgcore.boss;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * A boss from data/&lt;ns&gt;/rpgcore/bosses/*.json (§14): stats, move table and phases.
 *
 * @param name translation key of the display name
 */
public record BossDef(ResourceLocation id, String name, double health, double damage, double speed, double armor,
                      List<Move> moves, List<Phase> phases) {

    public enum Shape {
        CIRCLE, CONE, CHARGE;

        public static Shape byId(String id) {
            for (Shape s : values()) if (s.name().toLowerCase(Locale.ROOT).equals(id)) return s;
            return CIRCLE;
        }
    }

    /**
     * @param angle   full cone angle in degrees (cone only)
     * @param width   half-width of the charge lane
     * @param windup  seconds of telegraph before the hit
     * @param phases  1-based phases in which the move may be used
     */
    public record Move(String name, String animation, Shape shape, double range, double angle, double width,
                       double windup, double damage, double cooldown, List<Integer> phases) {
        public boolean usableIn(int phase) {
            return phases.isEmpty() || phases.contains(phase);
        }
    }

    /** Entering phase index+2 when health falls to {@code threshold} (fraction); {@code transition} seconds invulnerable. */
    public record Phase(double threshold, double transition) {}
}
