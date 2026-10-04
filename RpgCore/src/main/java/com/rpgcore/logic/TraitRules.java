package com.rpgcore.logic;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Load-time validation of a trait definition (TECH_SPEC §5.3 step 3, cross-school filter #3).
 * A trait that fails validation is rejected and logged.
 */
public final class TraitRules {
    private TraitRules() {}

    /** Effects that keep state between events. Not allowed on any_hit (§6). */
    public static final Set<String> STATEFUL_EFFECTS = Set.of(
            "counter_add", "set_flag", "clear_flag", "damage_mult_per_hit", "temp_attribute");
    /** Conditions that read state. Not allowed on any_hit. */
    public static final Set<String> STATEFUL_CONDITIONS = Set.of("counter", "flag", "not_flag");
    /** Effects that only make sense before damage is final. */
    public static final Set<String> PRE_DAMAGE_EFFECTS = Set.of(
            "damage_mult", "damage_mult_per_hit", "force_crit", "force_headshot");

    /** Minimal view of one rule for validation. */
    public record RuleView(Trigger trigger, List<String> conditionTypes, List<String> effectTypes) {}

    /** True if a trait of {@code school} may use {@code trigger}. */
    public static boolean allowed(School school, Trigger trigger) {
        Trigger.Family f = trigger.family();
        if (f == Trigger.Family.UNIVERSAL) return true;
        return switch (school) {
            case GUN -> f == Trigger.Family.GUN;
            case SPELL -> f == Trigger.Family.SPELL;
            // melee and bow are interchangeable (#20)
            case MELEE, BOW -> f == Trigger.Family.MELEE || f == Trigger.Family.BOW;
            case ANY -> true;
        };
    }

    /** Returns a list of human readable errors; empty means valid. */
    public static List<String> validate(School school, List<RuleView> rules) {
        List<String> errors = new ArrayList<>();
        if (rules.isEmpty()) errors.add("trait has no rules");
        EnumSet<Trigger.Family> families = EnumSet.noneOf(Trigger.Family.class);
        for (int i = 0; i < rules.size(); i++) {
            RuleView r = rules.get(i);
            if (r.trigger() == null) {
                errors.add("rule " + i + ": unknown trigger");
                continue;
            }
            families.add(r.trigger().family());
            if (!allowed(school, r.trigger())) {
                errors.add("rule " + i + ": trigger " + r.trigger().id() + " not allowed for school " + school.id());
            }
            if (r.trigger() == Trigger.ANY_HIT) {
                for (String e : r.effectTypes()) {
                    if (STATEFUL_EFFECTS.contains(e)) errors.add("rule " + i + ": any_hit cannot use stateful effect " + e);
                }
                for (String c : r.conditionTypes()) {
                    if (STATEFUL_CONDITIONS.contains(c)) errors.add("rule " + i + ": any_hit cannot use stateful condition " + c);
                }
            }
            if (r.trigger() == Trigger.PASSIVE) {
                for (String e : r.effectTypes()) {
                    if (!e.equals("attribute")) errors.add("rule " + i + ": passive only allows attribute effects, got " + e);
                }
                if (!r.conditionTypes().isEmpty()) errors.add("rule " + i + ": passive rules cannot have conditions");
            } else {
                for (String e : r.effectTypes()) {
                    if (e.equals("attribute")) errors.add("rule " + i + ": attribute effect only allowed on passive");
                }
            }
            if (r.trigger().kind() != Trigger.Kind.HIT) {
                for (String e : r.effectTypes()) {
                    if (PRE_DAMAGE_EFFECTS.contains(e)) {
                        errors.add("rule " + i + ": " + e + " only works on *_hit triggers, not " + r.trigger().id());
                    }
                }
            }
        }
        if (school == School.ANY && families.contains(Trigger.Family.GUN) && families.contains(Trigger.Family.SPELL)) {
            errors.add("school any cannot mix gun and spell triggers");
        }
        return errors;
    }
}
