package com.rpgcore.registry;

import com.mojang.serialization.Codec;
import com.rpgcore.RpgCore;
import com.rpgcore.loot.RpgLootModifier;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class RpgLoot {
    private RpgLoot() {}

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, RpgCore.MODID);

    /** The single GLM that adds all rpgcore loot (§6). */
    public static final RegistryObject<Codec<RpgLootModifier>> RPG_LOOT = SERIALIZERS.register("rpg_loot", RpgLootModifier.CODEC);
}
