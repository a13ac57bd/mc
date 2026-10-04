package com.rpgcore.registry;

import com.rpgcore.RpgCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class RpgTags {
    private RpgTags() {}

    // damage types
    public static final TagKey<DamageType> SPELL = TagKey.create(Registries.DAMAGE_TYPE, RpgCore.id("spell"));
    public static final TagKey<DamageType> GUN = TagKey.create(Registries.DAMAGE_TYPE, RpgCore.id("gun"));
    public static final TagKey<DamageType> NO_SCALE = TagKey.create(Registries.DAMAGE_TYPE, RpgCore.id("no_scale"));

    // entity types
    public static final TagKey<EntityType<?>> GUN_PROJECTILE = entity("gun_projectile");
    public static final TagKey<EntityType<?>> BOSS = entity("boss");
    public static final TagKey<EntityType<?>> NO_HEALTH_SCALE = entity("no_health_scale");
    public static final TagKey<EntityType<?>> ROLE_CHARGER = entity("role/charger");
    public static final TagKey<EntityType<?>> ROLE_ARCHER = entity("role/archer");
    public static final TagKey<EntityType<?>> ROLE_TANK = entity("role/tank");
    public static final TagKey<EntityType<?>> ROLE_FLANKER = entity("role/flanker");
    public static final TagKey<EntityType<?>> ROLE_SUPPORT = entity("role/support");

    // structures
    public static final TagKey<Structure> DUNGEON = TagKey.create(Registries.STRUCTURE, RpgCore.id("dungeon"));

    public static TagKey<EntityType<?>> entity(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, RpgCore.id(path));
    }
}
