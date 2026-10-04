package com.rpgcore.combat;

import com.rpgcore.RpgCore;
import com.rpgcore.logic.XpMath;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Death rule (#27, §8): keepInventory on (inventory, armor and Curios stay, no experience orbs);
 * the respawned player gets half of the experience they had.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Death {
    private Death() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        event.getServer().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, event.getServer());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        Player old = event.getOriginal();
        Player now = event.getEntity();
        int total = XpMath.total(old.experienceLevel, old.experienceProgress);
        now.experienceLevel = 0;
        now.experienceProgress = 0.0F;
        now.totalExperience = 0;
        now.giveExperiencePoints(total / 2);
    }
}
