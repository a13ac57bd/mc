package com.rpgcore.trait;

import com.rpgcore.logic.School;
import com.rpgcore.logic.Trigger;
import com.rpgcore.trait.condition.Condition;
import com.rpgcore.trait.effect.Effect;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

/**
 * A trait definition from data/&lt;ns&gt;/rpgcore/traits/*.json (TECH_SPEC §5.1).
 *
 * @param rollMin  minor traits: lower bound of the item roll (0 when not rolled)
 * @param tooltip  translation key; %s receives the effective roll in percent
 */
public record TraitDef(ResourceLocation id, Type type, School school, double rollMin, double rollMax, String tooltip, List<Rule> rules) {

    public enum Type {
        MINOR, UNIQUE, INSCRIPTION;

        public static Type byId(String id) {
            for (Type t : values()) if (t.name().toLowerCase(Locale.ROOT).equals(id)) return t;
            return null;
        }
    }

    /** One trigger with its conditions and effects. {@code recursive}: also runs inside trait-caused damage. */
    public record Rule(Trigger trigger, List<Condition> conditions, List<Effect> effects, boolean recursive) {}

    public boolean hasRoll() {
        return rollMax > 0;
    }

    public String tooltipKey() {
        return tooltip != null ? tooltip : "trait." + id.getNamespace() + "." + id.getPath();
    }
}
