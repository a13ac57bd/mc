package com.rpgcore.stat;

import com.rpgcore.RpgCore;
import com.rpgcore.logic.CombatMath;
import com.rpgcore.registry.RpgAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** Attaches the rpgcore attributes to players and sets the base health (§MECH 2, #16). */
public final class Stats {
    private Stats() {}

    public static final List<RegistryObject<Attribute>> ALL = List.of(
            RpgAttributes.MELEE_POWER, RpgAttributes.RANGED_POWER, RpgAttributes.CRIT_CHANCE, RpgAttributes.CRIT_DAMAGE,
            RpgAttributes.DASH_CHARGES, RpgAttributes.DASH_COOLDOWN, RpgAttributes.DASH_DISTANCE, RpgAttributes.DASH_AIR);

    /** Apothic Attributes crit applies to every damage with an attacker, spells included (breaks #3). */
    private static final ResourceLocation APOTHIC_CRIT = new ResourceLocation("attributeslib", "crit_chance");

    @Mod.EventBusSubscriber(modid = RpgCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onAttributes(EntityAttributeModificationEvent event) {
            for (RegistryObject<Attribute> a : ALL) {
                if (!event.has(EntityType.PLAYER, a.get())) event.add(EntityType.PLAYER, a.get());
            }
            event.add(EntityType.PLAYER, Attributes.MAX_HEALTH, CombatMath.PLAYER_BASE_HEALTH);
        }
    }

    @Mod.EventBusSubscriber(modid = RpgCore.MODID)
    public static final class ForgeEvents {
        private ForgeEvents() {}

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            fixForeignAttributes(event.getEntity());
        }

        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
            fixForeignAttributes(event.getEntity());
        }
    }

    /** Disables Apothic's crit; rpgcore handles crits for melee/bow/gun only (§8). */
    public static void fixForeignAttributes(Player player) {
        Attribute apothic = ForgeRegistries.ATTRIBUTES.getValue(APOTHIC_CRIT);
        if (apothic == null) return;
        AttributeInstance inst = player.getAttribute(apothic);
        if (inst != null && inst.getBaseValue() != 0.0) inst.setBaseValue(0.0);
    }
}
