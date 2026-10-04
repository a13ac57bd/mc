package com.rpgcore.trait;

import com.rpgcore.logic.Trigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mutable data of one trigger. For *_hit triggers effects add to {@link #bonus} or force crits; the caller applies
 * {@link #finalDamage()}. For *_dealt triggers {@link #damage} is the final (vanilla-unit) damage.
 */
public final class TraitContext {
    public final ServerPlayer player;
    public final Trigger trigger;
    public LivingEntity target;
    public DamageSource source;
    public float damage;
    /** Additive damage bonus from damage_mult effects (0.6 = +60%). */
    public double bonus;
    public boolean crit;
    public boolean headshot;
    public boolean forceCrit;
    public boolean forceHeadshot;
    /** Pierce order: 1 = first target this bullet hit. */
    public int hitIndex = 1;
    /** Pellets of the same shot that hit the same target this tick. */
    public int pellets = 1;
    public double distance;
    public ResourceLocation spell;
    public String spellSchool;
    /** Set by the Iron's compat while replaying an extra cast, so extra_casts cannot chain. */
    public boolean extraCast;

    public TraitContext(ServerPlayer player, Trigger trigger) {
        this.player = player;
        this.trigger = trigger;
    }

    public TraitContext target(LivingEntity target) {
        this.target = target;
        if (target != null) this.distance = player.distanceTo(target);
        return this;
    }

    public TraitContext damage(float damage) {
        this.damage = damage;
        return this;
    }

    public TraitContext source(DamageSource source) {
        this.source = source;
        return this;
    }

    public float finalDamage() {
        return (float) (damage * Math.max(0.0, 1.0 + bonus));
    }

    public boolean isCrit() {
        return crit || forceCrit;
    }

    public boolean isHeadshot() {
        return headshot || forceHeadshot;
    }
}
