package com.rpgcore.ecology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.RpgCore;
import com.rpgcore.danger.MobScaling;
import com.rpgcore.data.RpgData;
import com.rpgcore.util.Json;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Squads (data/&lt;ns&gt;/rpgcore/squads/*.json, §12): a naturally spawned leader may bring members (same danger tier).
 * When any member finds a target, idle squad mates within 32 blocks join in.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Squads {
    private Squads() {}

    public static final String TAG = "rpgcore_squad";

    public record Member(ResourceLocation type, int count) {}

    public record SquadDef(ResourceLocation id, ResourceLocation leader, double chance, List<Member> members) {}

    private static List<SquadDef> squads = List.of();
    private static boolean alerting;

    public static void load(Map<ResourceLocation, JsonElement> files) {
        List<SquadDef> loaded = new ArrayList<>();
        RpgData.each("squad", files, (id, json) -> {
            JsonObject j = json.getAsJsonObject();
            List<Member> members = new ArrayList<>();
            for (JsonElement m : Json.arr(j, "members")) {
                JsonObject mo = m.getAsJsonObject();
                members.add(new Member(Json.id(mo, "type"), Math.max(1, Json.integer(mo, "count", 1))));
            }
            loaded.add(new SquadDef(id, Json.id(j, "leader"), Json.num(j, "chance", 0.1), members));
        });
        squads = loaded;
    }

    public static boolean sameSquad(Entity a, Entity b) {
        String sa = a.getPersistentData().getString(TAG);
        return !sa.isEmpty() && sa.equals(b.getPersistentData().getString(TAG));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !(event.getLevel() instanceof ServerLevel level)) return;
        Mob leader = event.getEntity();
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(leader.getType());
        for (SquadDef def : squads) {
            if (!def.leader().equals(type) || leader.getRandom().nextDouble() >= def.chance()) continue;
            spawnSquad(level, leader, def);
            return;
        }
    }

    private static void spawnSquad(ServerLevel level, Mob leader, SquadDef def) {
        String id = UUID.randomUUID().toString();
        leader.getPersistentData().putString(TAG, id);
        int tier = MobScaling.tier(leader);
        for (Member m : def.members()) {
            if (m.type() == null || !ForgeRegistries.ENTITY_TYPES.containsKey(m.type())) continue;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(m.type());
            for (int i = 0; i < m.count(); i++) {
                Entity e = type.create(level);
                if (!(e instanceof Mob mob)) continue;
                double dx = (leader.getRandom().nextDouble() - 0.5) * 4.0, dz = (leader.getRandom().nextDouble() - 0.5) * 4.0;
                mob.moveTo(leader.getX() + dx, leader.getY(), leader.getZ() + dz, leader.getRandom().nextFloat() * 360F, 0F);
                ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.REINFORCEMENT, null, null);
                MobScaling.apply(mob, tier, 1.0);
                mob.getPersistentData().putString(TAG, id);
                level.addFreshEntityWithPassengers(mob);
            }
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (alerting || event.isCanceled() || !(event.getEntity() instanceof Mob mob)) return;
        LivingEntity target = event.getNewTarget();
        String squad = mob.getPersistentData().getString(TAG);
        if (target == null || squad.isEmpty()) return;
        alerting = true;
        try {
            for (Mob mate : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(32),
                    m -> m != mob && m.getTarget() == null && squad.equals(m.getPersistentData().getString(TAG)))) {
                mate.setTarget(target);
            }
        } finally {
            alerting = false;
        }
    }
}
