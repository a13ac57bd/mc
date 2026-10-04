package com.rpgcore.trait.condition;

import com.rpgcore.trait.ActiveTrait;
import com.rpgcore.trait.TraitContext;

/** A rule condition. Implementations are created from JSON by {@link Conditions}. */
public interface Condition {
    String type();

    boolean test(TraitContext ctx, ActiveTrait trait);
}
