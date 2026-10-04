package com.rpgcore.trait;

/**
 * A trait equipped right now.
 *
 * @param roll  effective roll (item roll x tier multiplier), 0 for unrolled traits
 * @param slot  where it comes from ("mainhand", "head", "curios:ring/0"...) — used for stable modifier ids
 */
public record ActiveTrait(TraitDef def, double roll, int tier, String slot) {

    /** State keys are prefixed by the trait id so traits never share state (#3). */
    public String key(String name) {
        return def.id() + "/" + name;
    }
}
