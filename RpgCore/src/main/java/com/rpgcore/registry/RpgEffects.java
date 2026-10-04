package com.rpgcore.registry;

import com.rpgcore.RpgCore;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class RpgEffects {
    private RpgEffects() {}

    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, RpgCore.MODID);

    /** rpgcore:stun — cannot move or attack (attacks are cancelled in combat/Stun). */
    public static final RegistryObject<MobEffect> STUN = EFFECTS.register("stun", () -> new RpgEffect(MobEffectCategory.HARMFUL, 0x9E9E9E)
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, "3f1c9a52-6a5e-4b8e-9a63-2c1d5e7b0a01", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));

    /** rpgcore:corrupted — +20% health, hits wither the target (ecology/Corruption). Purple particles come from the color. */
    public static final RegistryObject<MobEffect> CORRUPTED = EFFECTS.register("corrupted", () -> new RpgEffect(MobEffectCategory.HARMFUL, 0x7A1FA2)
            .addAttributeModifier(Attributes.MAX_HEALTH, "8d0e4b8c-2a64-4b3c-8f0e-5b3c7d2a9e02", 0.2, AttributeModifier.Operation.MULTIPLY_TOTAL));

    public static class RpgEffect extends MobEffect {
        public RpgEffect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
