package com.rpgcore.logic;

import java.util.Locale;

/** Trait triggers (TECH_SPEC §5.2). */
public enum Trigger {
    MELEE_HIT(Family.MELEE, Kind.HIT),
    MELEE_DEALT(Family.MELEE, Kind.DEALT),
    BOW_HIT(Family.BOW, Kind.HIT),
    BOW_DEALT(Family.BOW, Kind.DEALT),
    GUN_HIT(Family.GUN, Kind.HIT),
    GUN_DEALT(Family.GUN, Kind.DEALT),
    GUN_SHOOT(Family.GUN, Kind.OTHER),
    GUN_KILL(Family.GUN, Kind.OTHER),
    GUN_RELOAD(Family.GUN, Kind.OTHER),
    SPELL_CAST(Family.SPELL, Kind.OTHER),
    SPELL_HIT(Family.SPELL, Kind.HIT),
    SPELL_DEALT(Family.SPELL, Kind.DEALT),
    DASH(Family.UNIVERSAL, Kind.OTHER),
    KILL(Family.UNIVERSAL, Kind.OTHER),
    HURT(Family.UNIVERSAL, Kind.OTHER),
    PASSIVE(Family.UNIVERSAL, Kind.OTHER),
    /** Any school's hit. Only stateless effects/conditions are allowed (§6). */
    ANY_HIT(Family.UNIVERSAL, Kind.HIT);

    public enum Family { GUN, SPELL, MELEE, BOW, UNIVERSAL }

    /** HIT runs before damage is final (may change it); DEALT runs after. */
    public enum Kind { HIT, DEALT, OTHER }

    private final Family family;
    private final Kind kind;

    Trigger(Family family, Kind kind) {
        this.family = family;
        this.kind = kind;
    }

    public Family family() {
        return family;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isHitLike() {
        return kind == Kind.HIT || kind == Kind.DEALT;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Trigger byId(String id) {
        if (id == null) return null;
        for (Trigger t : values()) if (t.id().equals(id)) return t;
        return null;
    }

    /** The HIT trigger of a family, used to fan out ANY_HIT. */
    public static Trigger hitOf(Family f) {
        return switch (f) {
            case GUN -> GUN_HIT;
            case SPELL -> SPELL_HIT;
            case MELEE -> MELEE_HIT;
            case BOW -> BOW_HIT;
            case UNIVERSAL -> ANY_HIT;
        };
    }

    public static Trigger dealtOf(Family f) {
        return switch (f) {
            case GUN -> GUN_DEALT;
            case SPELL -> SPELL_DEALT;
            case MELEE -> MELEE_DEALT;
            case BOW -> BOW_DEALT;
            case UNIVERSAL -> null;
        };
    }
}
