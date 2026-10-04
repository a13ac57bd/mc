package com.rpgcore.events;

import com.rpgcore.RpgCore;
import com.rpgcore.boss.BossDef;
import com.rpgcore.boss.BossDefs;
import com.rpgcore.boss.RpgBoss;
import com.rpgcore.danger.DangerMap;
import com.rpgcore.danger.MobScaling;
import com.rpgcore.ecology.Corruption;
import com.rpgcore.ecology.SpawnTables;
import com.rpgcore.logic.EventMachine;
import com.rpgcore.loot.LootSources;
import com.rpgcore.quest.Quests;
import com.rpgcore.registry.RpgEntities;
import com.rpgcore.town.TownSavedData;
import com.rpgcore.util.Inv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * World event runtime (§10): started only by progress flags (#14), advanced once per in-game day, at most 2 at once
 * (the rest queue), "held" and "fallen then reclaimed" endings with separate rewards. No notifications: hints come
 * from NPC dialogue, distant smoke columns and refugees.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class WorldEvents {
    private WorldEvents() {}

    public static final int MAX_CONCURRENT = 2;

    /** An active state's effects with its resolved region. */
    public record Active(EventDef def, String state, EventDef.Effects effects, ResourceKey<Level> dimension, int x, int z, int radius) {
        boolean contains(ResourceKey<Level> dim, BlockPos pos) {
            double dx = pos.getX() - x, dz = pos.getZ() - z;
            return dimension.equals(dim) && dx * dx + dz * dz <= (double) radius * radius;
        }
    }

    private static MinecraftServer server;
    private static EventMachine machine;
    private static List<Active> active = List.of();
    private static final List<SpawnTables.TableDef> OUR_TABLES = new ArrayList<>();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        rebind();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SpawnTables.EXTRA.removeAll(OUR_TABLES);
        OUR_TABLES.clear();
        Corruption.ZONES.removeIf(z -> z.source().startsWith("event:"));
        server = null;
        machine = null;
        active = List.of();
    }

    /** Rebuilds the machine from the current definitions and saved state (server start, /reload). */
    public static void rebind() {
        if (server == null) return;
        Map<String, EventMachine.Def> defs = new LinkedHashMap<>();
        EventDefs.all().forEach((id, def) -> defs.put(id.toString(), def.machine()));
        machine = new EventMachine(defs, MAX_CONCURRENT);
        WorldEventSavedData data = WorldEventSavedData.get(server);
        machine.restore(data.active, data.queue, data.finished);
        refreshEffects();
    }

    public static EventMachine machine() {
        return machine;
    }

    public static List<Active> active() {
        return active;
    }

    // ---------- inputs ----------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || server == null || machine == null) return;
        int tick = server.getTickCount();
        if (tick % 20 == 0) {
            WorldEventSavedData data = WorldEventSavedData.get(server);
            long day = server.overworld().getDayTime() / 24000L;
            if (data.lastDay < 0) {
                data.lastDay = day;
                data.setDirty();
            }
            while (data.lastDay < day) {
                data.lastDay++;
                data.setDirty();
                apply(machine.dailyTick());
            }
        }
        if (tick % 40 == 0) hints();
    }

    /** A progress flag was set: start matching events, complete quest_done goals. */
    public static void onProgress(MinecraftServer s, String flag) {
        if (machine == null) return;
        for (EventDef def : EventDefs.all().values()) {
            if (def.trigger().equals(flag)) {
                List<EventMachine.Transition> out = new ArrayList<>();
                machine.trigger(def.id().toString(), out);
                apply(out);
            }
        }
        if (flag.startsWith("quest_done:")) {
            ResourceLocation quest = ResourceLocation.tryParse(flag.substring("quest_done:".length()));
            for (EventMachine.Instance inst : new ArrayList<>(machine.active())) {
                EventDef def = EventDefs.get(ResourceLocation.parse(inst.defId));
                EventDef.Goal goal = def == null ? null : def.goals().get(inst.state);
                if (goal != null && goal.type().equals("quest_done") && goal.id().equals(quest)) apply(machine.goalMet(inst.defId));
            }
        }
    }

    /** kill_specific goals: the boss/elite (or entity type) {@code id} died inside the event region. */
    public static void onKill(ServerLevel level, LivingEntity victim, ResourceLocation id) {
        if (machine == null || id == null) return;
        for (EventMachine.Instance inst : new ArrayList<>(machine.active())) {
            EventDef def = EventDefs.get(ResourceLocation.parse(inst.defId));
            if (def == null) continue;
            EventDef.Goal goal = def.goals().get(inst.state);
            if (goal == null || !goal.type().equals("kill_specific") || !id.equals(goal.id())) continue;
            Active region = resolve(def, inst.state);
            if (region != null && region.contains(level.dimension(), victim.blockPosition())) apply(machine.goalMet(inst.defId));
        }
    }

    // ---------- commands ----------

    public static EventMachine.StartResult start(ResourceLocation id) {
        if (machine == null) return EventMachine.StartResult.UNKNOWN;
        List<EventMachine.Transition> out = new ArrayList<>();
        EventMachine.StartResult r = machine.trigger(id.toString(), out);
        apply(out);
        return r;
    }

    public static void advanceDays(int days) {
        if (machine == null) return;
        for (int i = 0; i < days; i++) apply(machine.dailyTick());
    }

    public static void force(ResourceLocation id, String state) {
        if (machine != null) apply(machine.force(id.toString(), state));
    }

    public static void goal(ResourceLocation id) {
        if (machine != null) apply(machine.goalMet(id.toString()));
    }

    // ---------- transitions ----------

    private static void apply(List<EventMachine.Transition> out) {
        if (out.isEmpty() || server == null) return;
        WorldEventSavedData data = WorldEventSavedData.get(server);
        data.capture(machine);
        for (EventMachine.Transition t : out) {
            EventDef def = EventDefs.get(ResourceLocation.parse(t.defId()));
            if (def == null) continue;
            if (t.to() != null) onEnter(data, def, t.to());
            if (t.outcome() != null) onFinish(def, t.from(), t.outcome());
        }
        refreshEffects();
    }

    private static void onEnter(WorldEventSavedData data, EventDef def, String state) {
        if (!data.entered.add(def.id() + "/" + state)) return;
        data.setDirty();
        EventDef.Effects fx = def.effects().get(state);
        Active region = resolve(def, state);
        if (fx == null || region == null) return;
        ServerLevel level = server.getLevel(region.dimension());
        if (level == null) return;
        BlockPos center = surface(level, region.x(), region.z());
        if (fx.boss() != null) spawnEventBoss(level, center, fx.boss());
        for (int i = 0; i < fx.refugees(); i++) spawnRefugee(level, center.offset(level.random.nextInt(9) - 4, 0, level.random.nextInt(9) - 4));
    }

    private static void onFinish(EventDef def, String terminalState, String outcome) {
        ProgressSavedData.get(server).set(server, "event:" + def.id() + "=" + outcome);
        Quests.onEventOutcome(server, def.id(), outcome);
        ResourceLocation reward = def.rewards().get(terminalState);
        Active region = resolve(def, terminalState);
        if (reward == null || region == null) return;
        ServerLevel level = server.getLevel(region.dimension());
        if (level == null) return;
        for (ServerPlayer p : level.players()) {
            if (!region.contains(level.dimension(), p.blockPosition())) continue;
            for (ItemStack stack : LootSources.roll(reward, p, DangerMap.get(level, p.blockPosition()), p.getRandom(), null)) Inv.give(p, stack);
        }
    }

    private static void spawnEventBoss(ServerLevel level, BlockPos pos, ResourceLocation bossId) {
        BossDef def = BossDefs.get(bossId);
        if (def == null) return;
        level.getChunk(pos);
        RpgBoss boss = RpgEntities.BOSS.get().create(level);
        if (boss == null) return;
        boss.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0F, 0F);
        boss.setBoss(def, null);
        boss.getPersistentData().putBoolean(MobScaling.EVENT_MOB, true);
        level.addFreshEntity(boss);
    }

    private static void spawnRefugee(ServerLevel level, BlockPos pos) {
        level.getChunk(pos);
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) return;
        BlockPos at = surface(level, pos.getX(), pos.getZ());
        v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        v.setCustomName(Component.translatable("entity.rpgcore.refugee"));
        v.setPersistenceRequired();
        v.getPersistentData().putBoolean(MobScaling.EVENT_MOB, true);
        level.addFreshEntity(v);
    }

    private static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    // ---------- effects ----------

    private static Active resolve(EventDef def, String state) {
        EventDef.Effects fx = def.effects().getOrDefault(state, EventDef.Effects.NONE);
        EventDef.Region r = def.region();
        if (r.town() != null) {
            if (server == null) return null;
            TownSavedData.Town town = TownSavedData.get(server).get(r.town());
            if (town == null) return null;
            int radius = r.radius() > 0 ? Math.max(r.radius(), town.radius) : town.radius;
            return new Active(def, state, fx, town.dimension, town.center.getX(), town.center.getZ(), radius);
        }
        ResourceKey<Level> dim = r.dimension() == null ? Level.OVERWORLD : ResourceKey.create(Registries.DIMENSION, r.dimension());
        return new Active(def, state, fx, dim, r.x(), r.z(), r.radius());
    }

    private static void refreshEffects() {
        List<Active> list = new ArrayList<>();
        if (machine != null) {
            for (EventMachine.Instance inst : machine.active()) {
                EventDef def = EventDefs.get(ResourceLocation.parse(inst.defId));
                if (def == null) continue;
                Active a = resolve(def, inst.state);
                if (a != null) list.add(a);
            }
        }
        active = List.copyOf(list);

        SpawnTables.EXTRA.removeAll(OUR_TABLES);
        OUR_TABLES.clear();
        Corruption.ZONES.removeIf(z -> z.source().startsWith("event:"));
        Map<ResourceLocation, Boolean> townHidden = new HashMap<>();
        for (EventDef def : EventDefs.all().values()) {
            if (def.region().town() != null) townHidden.putIfAbsent(def.region().town(), false);
        }
        for (Active a : active) {
            EventDef.Effects fx = a.effects();
            if (fx.spawns() != null) OUR_TABLES.add(fx.spawns());
            if (fx.corruption()) Corruption.ZONES.add(new Corruption.Zone("event:" + a.def().id(), a.dimension(), new BlockPos(a.x(), 64, a.z()), a.radius()));
            if (fx.hideNpcs() && a.def().region().town() != null) townHidden.put(a.def().region().town(), true);
        }
        SpawnTables.EXTRA.addAll(OUR_TABLES);
        if (server != null) {
            TownSavedData towns = TownSavedData.get(server);
            townHidden.forEach((town, hidden) -> towns.setNpcsHidden(server, town, hidden));
        }
        DangerMap.clearCache();
    }

    /** Sum of event danger modifiers at a position (used by DangerMap). */
    public static int dangerModifier(ServerLevel level, BlockPos pos) {
        int sum = 0;
        for (Active a : active) if (a.effects().danger() != 0 && a.contains(level.dimension(), pos)) sum += a.effects().danger();
        return sum;
    }

    /** Dialogue key NPCs of {@code town} use while an event state overrides it, or null. */
    public static String dialogueFor(ResourceLocation town) {
        for (Active a : active) {
            if (town.equals(a.def().region().town()) && a.effects().dialogue() != null) return a.effects().dialogue();
        }
        return null;
    }

    private static void hints() {
        Set<String> done = new HashSet<>();
        for (Active a : active) {
            if (!a.effects().smoke() || !done.add(a.def().id().toString())) continue;
            ServerLevel level = server.getLevel(a.dimension());
            if (level == null) continue;
            int y = level.isLoaded(new BlockPos(a.x(), 64, a.z())) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, a.x(), a.z()) : 80;
            for (ServerPlayer p : level.players()) {
                double dx = p.getX() - a.x(), dz = p.getZ() - a.z();
                if (dx * dx + dz * dz > 256.0 * 256.0) continue;
                level.sendParticles(p, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, a.x() + 0.5, y + 1, a.z() + 0.5, 6, 0.4, 3.0, 0.4, 0.02);
            }
        }
    }
}
