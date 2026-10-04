package com.rpgcore.boss;

import com.rpgcore.RpgCore;
import com.rpgcore.danger.Elites;
import com.rpgcore.events.ProgressSavedData;
import com.rpgcore.events.WorldEvents;
import com.rpgcore.home.Trophies;
import com.rpgcore.quest.Quests;
import com.rpgcore.registry.RpgTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Named kills (bosses and elites): progress flag boss_killed (#14), quest kill_specific, world event goals, trophies.
 * Everyone within 48 blocks counts as a participant.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class BossEvents {
    private BossEvents() {}

    public static ResourceLocation idOf(LivingEntity e) {
        if (e instanceof RpgBoss b && b.bossId() != null) return b.bossId();
        return ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        boolean boss = victim.getType().is(RpgTags.BOSS) || victim instanceof RpgBoss;
        if (!boss && !Elites.isElite(victim)) return;
        ResourceLocation id = idOf(victim);
        List<ServerPlayer> participants = new ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class, victim.getBoundingBox().inflate(48)));
        if (event.getSource().getEntity() instanceof ServerPlayer killer && !participants.contains(killer)) participants.add(killer);
        if (boss) ProgressSavedData.get(level.getServer()).set(level.getServer(), ProgressSavedData.bossKilled(id));
        for (ServerPlayer p : participants) {
            Quests.onKill(p, victim, id);
            if (boss) Trophies.grant(p, id);
        }
        WorldEvents.onKill(level, victim, id);
    }
}
