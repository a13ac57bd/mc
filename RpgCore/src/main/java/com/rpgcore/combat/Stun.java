package com.rpgcore.combat;

import com.rpgcore.RpgCore;
import com.rpgcore.registry.RpgEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** rpgcore:stun — movement is removed by the effect's attribute modifier; attacks and pathing are blocked here. */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Stun {
    private Stun() {}

    public static boolean stunned(LivingEntity e) {
        return e.hasEffect(RpgEffects.STUN.get());
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != event.getEntity() && stunned(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof Mob mob && !mob.level().isClientSide() && mob.tickCount % 5 == 0 && stunned(mob)) {
            mob.getNavigation().stop();
        }
    }
}
