package com.rpgcore.ai;

import com.rpgcore.RpgCore;
import com.rpgcore.ecology.Factions;
import com.rpgcore.ecology.Squads;
import com.rpgcore.logic.CombatMath;
import com.rpgcore.registry.RpgTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Role goals by entity tag #rpgcore:role/&lt;role&gt; (§12, #8 #10), added when the mob joins the world.
 * Bosses and other mods' monsters are not tagged and keep their own AI.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Roles {
    private Roles() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof PathfinderMob mob)) return;
        if (mob.getType().is(RpgTags.BOSS)) return;
        if (mob.getType().is(RpgTags.ROLE_CHARGER)) mob.goalSelector.addGoal(2, new ChargerGoal(mob));
        if (mob.getType().is(RpgTags.ROLE_ARCHER)) mob.goalSelector.addGoal(1, new KiteGoal(mob, 10, 16));
        if (mob.getType().is(RpgTags.ROLE_TANK)) mob.goalSelector.addGoal(2, new ShieldWallGoal(mob));
        if (mob.getType().is(RpgTags.ROLE_FLANKER)) mob.goalSelector.addGoal(2, new FlankGoal(mob));
        if (mob.getType().is(RpgTags.ROLE_SUPPORT)) {
            mob.goalSelector.addGoal(1, new KiteGoal(mob, 8, 14));
            mob.goalSelector.addGoal(1, new SupportGoal(mob));
        }
    }

    /** Teammates: same faction or same squad. */
    public static boolean allied(Entity a, Entity b) {
        return Factions.allied(a, b) || Squads.sameSquad(a, b);
    }

    /** Tank: damage from the front 60° is halved. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (!victim.getType().is(RpgTags.ROLE_TANK)) return;
        Entity attacker = event.getSource().getDirectEntity() != null ? event.getSource().getDirectEntity() : event.getSource().getEntity();
        if (attacker == null) return;
        Vec3 look = victim.getLookAngle();
        if (CombatMath.inFrontCone(look.x, look.z, attacker.getX() - victim.getX(), attacker.getZ() - victim.getZ(), 60)) {
            event.setAmount(event.getAmount() * 0.5F);
        }
    }
}
