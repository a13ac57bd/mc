package com.rpgcore.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Step ordering for hand-made quests (TECH_SPEC §13). */
public final class QuestFlow {

    /**
     * {@code next}: explicit next step id ("end" finishes the quest), or null for the following step.
     * {@code choices}: choice id -> next step id ("end" finishes).
     */
    public record Step(String id, String next, Map<String, String> choices) {}

    public static final String END = "end";

    private final List<Step> steps;

    public QuestFlow(List<Step> steps) {
        this.steps = List.copyOf(steps);
    }

    public List<Step> steps() {
        return steps;
    }

    public int indexOf(String stepId) {
        for (int i = 0; i < steps.size(); i++) if (steps.get(i).id().equals(stepId)) return i;
        return -1;
    }

    public Step first() {
        return steps.isEmpty() ? null : steps.get(0);
    }

    /** Next step id after completing {@code stepId} (with an optional choice), or null when the quest is complete. */
    public String next(String stepId, String choiceId) {
        int i = indexOf(stepId);
        if (i < 0) return null;
        Step s = steps.get(i);
        String target;
        if (!s.choices().isEmpty()) {
            target = choiceId == null ? null : s.choices().get(choiceId);
            if (target == null) return stepId; // invalid choice: stay
        } else if (s.next() != null) {
            target = s.next();
        } else {
            target = i + 1 < steps.size() ? steps.get(i + 1).id() : END;
        }
        return END.equals(target) ? null : target;
    }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (steps.isEmpty()) errors.add("quest has no steps");
        for (Step s : steps) {
            if (s.next() != null && !END.equals(s.next()) && indexOf(s.next()) < 0) errors.add(s.id() + ": next " + s.next() + " missing");
            for (Map.Entry<String, String> c : s.choices().entrySet()) {
                if (!END.equals(c.getValue()) && indexOf(c.getValue()) < 0) errors.add(s.id() + ": choice " + c.getKey() + " -> " + c.getValue() + " missing");
            }
        }
        return errors;
    }
}
