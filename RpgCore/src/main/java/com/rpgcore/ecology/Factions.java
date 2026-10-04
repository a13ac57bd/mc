package com.rpgcore.ecology;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rpgcore.RpgCore;
import com.rpgcore.data.RpgData;
import com.rpgcore.util.Json;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Factions (data/&lt;ns&gt;/rpgcore/factions/*.json + entity tags #rpgcore:faction/*, §12): undead, beasts, bandits,
 * corrupted. Hostile factions attack each other (target selector priority 3); the same faction is a team.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class Factions {
    private Factions() {}

    public record FactionDef(ResourceLocation id, TagKey<EntityType<?>> tag, Set<ResourceLocation> hostile) {}

    private static Map<ResourceLocation, FactionDef> factions = Map.of();
    private static final Map<EntityType<?>, ResourceLocation> BY_TYPE = new HashMap<>();

    public static void load(Map<ResourceLocation, JsonElement> files) {
        Map<ResourceLocation, FactionDef> loaded = new LinkedHashMap<>();
        RpgData.each("faction", files, (id, json) -> {
            JsonObject j = json.getAsJsonObject();
            ResourceLocation tag = Json.id(j, "tag");
            if (tag == null) tag = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "faction/" + id.getPath());
            loaded.put(id, new FactionDef(id, TagKey.create(Registries.ENTITY_TYPE, tag), new HashSet<>(Json.ids(j, "hostile"))));
        });
        factions = loaded;
        BY_TYPE.clear();
    }

    public static ResourceLocation factionOf(Entity e) {
        if (e == null || e instanceof Player) return null;
        EntityType<?> type = e.getType();
        if (BY_TYPE.containsKey(type)) return BY_TYPE.get(type);
        ResourceLocation found = null;
        for (FactionDef f : factions.values()) {
            if (type.is(f.tag())) {
                found = f.id();
                break;
            }
        }
        BY_TYPE.put(type, found);
        return found;
    }

    public static boolean allied(Entity a, Entity b) {
        ResourceLocation fa = factionOf(a);
        return fa != null && fa.equals(factionOf(b));
    }

    public static boolean hostile(Entity a, Entity b) {
        ResourceLocation fa = factionOf(a), fb = factionOf(b);
        if (fa == null || fb == null || fa.equals(fb)) return false;
        FactionDef da = factions.get(fa), db = factions.get(fb);
        return (da != null && da.hostile().contains(fb)) || (db != null && db.hostile().contains(fa));
    }

    private static boolean hasEnemies(ResourceLocation faction) {
        FactionDef def = factions.get(faction);
        if (def == null) return false;
        if (!def.hostile().isEmpty()) return true;
        for (FactionDef other : factions.values()) if (other.hostile().contains(faction)) return true;
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        ResourceLocation faction = factionOf(mob);
        if (faction == null || !hasEnemies(faction)) return;
        mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, e -> hostile(mob, e)));
    }

    /** Never switch to a teammate (e.g. after a stray arrow). */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewTarget();
        if (target != null && target != event.getEntity() && allied(event.getEntity(), target)) event.setCanceled(true);
    }
}
