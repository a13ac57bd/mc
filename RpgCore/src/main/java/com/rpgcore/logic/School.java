package com.rpgcore.logic;

import java.util.Locale;

/** Trait school (#3, #20). */
public enum School {
    GUN, SPELL, MELEE, BOW, ANY;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static School byId(String id) {
        if (id == null) return null;
        for (School s : values()) if (s.id().equals(id)) return s;
        return null;
    }

    /** Schools a random piece of gear can roll minors from. */
    public static final School[] GEAR = {GUN, SPELL, MELEE, BOW};
}
