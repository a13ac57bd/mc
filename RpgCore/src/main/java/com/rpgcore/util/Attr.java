package com.rpgcore.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.RegistryObject;

import java.util.UUID;

/** Attribute helpers that never throw for entities lacking an attribute. */
public final class Attr {
    private Attr() {}

    public static double value(LivingEntity entity, Attribute attribute, double def) {
        AttributeInstance inst = entity.getAttribute(attribute);
        return inst == null ? def : inst.getValue();
    }

    public static double value(LivingEntity entity, RegistryObject<Attribute> attribute, double def) {
        return attribute.isPresent() ? value(entity, attribute.get(), def) : def;
    }

    /** Adds or replaces a transient modifier. */
    public static void setTransient(LivingEntity entity, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    /** Adds or replaces a permanent (saved) modifier. */
    public static void setPermanent(LivingEntity entity, Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, name, amount, op));
    }

    public static void remove(LivingEntity entity, Attribute attribute, UUID id) {
        AttributeInstance inst = entity.getAttribute(attribute);
        if (inst != null) inst.removeModifier(id);
    }

    public static boolean has(LivingEntity entity, Attribute attribute, UUID id) {
        AttributeInstance inst = entity.getAttribute(attribute);
        return inst != null && inst.getModifier(id) != null;
    }

    public static AttributeModifier.Operation op(String name) {
        if (name == null) return AttributeModifier.Operation.ADDITION;
        return switch (name) {
            case "multiply_base" -> AttributeModifier.Operation.MULTIPLY_BASE;
            case "multiply_total" -> AttributeModifier.Operation.MULTIPLY_TOTAL;
            default -> AttributeModifier.Operation.ADDITION;
        };
    }
}
