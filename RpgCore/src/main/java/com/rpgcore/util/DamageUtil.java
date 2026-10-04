package com.rpgcore.util;

import com.rpgcore.RpgCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** rpgcore damage types (data/rpgcore/damage_type/*.json). */
public final class DamageUtil {
    private DamageUtil() {}

    /** Damage caused by trait effects (aoe_damage...). Not classified as melee/bow/gun/spell. */
    public static final ResourceKey<DamageType> TRAIT = ResourceKey.create(Registries.DAMAGE_TYPE, RpgCore.id("trait"));
    /** Boss moves and dungeon mechanisms without an attacker. */
    public static final ResourceKey<DamageType> MECHANISM = ResourceKey.create(Registries.DAMAGE_TYPE, RpgCore.id("mechanism"));

    public static DamageSource source(Level level, ResourceKey<DamageType> type, Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
    }

    public static DamageSource trait(Entity attacker) {
        return source(attacker.level(), TRAIT, attacker);
    }
}
