package com.rpgcore.trait.effect;

import com.rpgcore.trait.ActiveTrait;
import com.rpgcore.trait.TraitContext;

/** A rule effect. Implementations are created from JSON by {@link Effects}. */
public interface Effect {
    String type();

    void apply(TraitContext ctx, ActiveTrait trait);
}
