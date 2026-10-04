package com.rpgcore.logic;

import java.util.List;
import java.util.Map;

/** Ending lookup from chapter choices (TECH_SPEC §13, endings.json). */
public final class EndingTable {
    private EndingTable() {}

    public static final int CHAPTERS_FOR_ENDGAME = 3;

    /** All entries of {@code when} must match the recorded choices; "*" matches any recorded choice. */
    public record Rule(Map<String, String> when, String ending) {}

    public static boolean endgameReady(int chaptersDone) {
        return chaptersDone >= CHAPTERS_FOR_ENDGAME;
    }

    public static String resolve(List<Rule> rules, String fallback, Map<String, String> choices) {
        for (Rule r : rules) {
            boolean ok = true;
            for (Map.Entry<String, String> w : r.when().entrySet()) {
                String got = choices.get(w.getKey());
                if (got == null || (!"*".equals(w.getValue()) && !w.getValue().equals(got))) {
                    ok = false;
                    break;
                }
            }
            if (ok) return r.ending();
        }
        return fallback;
    }
}
