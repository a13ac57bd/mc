package com.rpgcore.registry;

import com.rpgcore.RpgCore;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The 8 rpgcore player attributes (MECH §2). Gun and spell power are separate: guns use ranged_power,
 * spells use Iron's own spell power (#3).
 */
public final class RpgAttributes {
    private RpgAttributes() {}

    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, RpgCore.MODID);

    /** Multiplier for melee damage. */
    public static final RegistryObject<Attribute> MELEE_POWER = reg("melee_power", 1.0, 0.0, 100.0);
    /** Multiplier for bow and gun damage. */
    public static final RegistryObject<Attribute> RANGED_POWER = reg("ranged_power", 1.0, 0.0, 100.0);
    /** Crit chance for melee, bow and gun (0..1). */
    public static final RegistryObject<Attribute> CRIT_CHANCE = reg("crit_chance", 0.05, 0.0, 1.0);
    /** Crit damage multiplier. */
    public static final RegistryObject<Attribute> CRIT_DAMAGE = reg("crit_damage", 1.5, 1.0, 100.0);
    public static final RegistryObject<Attribute> DASH_CHARGES = reg("dash_charges", 1.0, 0.0, 10.0);
    /** Seconds to recover one dash charge. */
    public static final RegistryObject<Attribute> DASH_COOLDOWN = reg("dash_cooldown", 2.5, 0.1, 60.0);
    /** Blocks travelled per dash. */
    public static final RegistryObject<Attribute> DASH_DISTANCE = reg("dash_distance", 4.0, 0.0, 32.0);
    /** &gt;= 1 allows dashing in the air. */
    public static final RegistryObject<Attribute> DASH_AIR = reg("dash_air", 0.0, 0.0, 1.0);

    private static RegistryObject<Attribute> reg(String name, double def, double min, double max) {
        return ATTRIBUTES.register(name, () -> new RangedAttribute("attribute.name." + RpgCore.MODID + "." + name, def, min, max).setSyncable(true));
    }
}
