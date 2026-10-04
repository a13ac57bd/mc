package com.rpgcore.rift;

import com.rpgcore.RpgCore;
import com.rpgcore.boss.BossDef;
import com.rpgcore.boss.BossDefs;
import com.rpgcore.boss.RpgBoss;
import com.rpgcore.danger.DangerMap;
import com.rpgcore.danger.MobScaling;
import com.rpgcore.logic.RiftMath;
import com.rpgcore.loot.LootSources;
import com.rpgcore.registry.RpgBlocks;
import com.rpgcore.registry.RpgEntities;
import com.rpgcore.util.Inv;
import com.rpgcore.util.PlayerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rifts (§11 #15): dimension rpgcore:rift (void, fixed midnight). Each run takes a 1024-block grid cell; the room is
 * built at y=100 (radius 10, placeholder until the room pool), torn down afterwards and its chunks un-forced.
 * Clearing a floor adds rewards (source rpgcore:rift x floor multiplier) and opens "continue" / "leave" doors.
 * Leaving keeps everything; timeout or everyone dead halves the rewards.
 */
@Mod.EventBusSubscriber(modid = RpgCore.MODID)
public final class RiftManager {
    private RiftManager() {}

    public static final ResourceKey<Level> RIFT = ResourceKey.create(Registries.DIMENSION, RpgCore.id("rift"));
    public static final int CELL = 1024;
    public static final int Y = 100;
    private static final String PENDING = "rift_rewards";

    private static final Map<Integer, RiftInstance> INSTANCES = new HashMap<>();

    public static Map<Integer, RiftInstance> instances() {
        return INSTANCES;
    }

    public static RiftInstance at(BlockPos pos) {
        int cell = Math.floorDiv(pos.getX() + CELL / 2, CELL);
        return INSTANCES.get(cell);
    }

    public static RiftInstance of(ServerPlayer p) {
        for (RiftInstance i : INSTANCES.values()) if (i.players.containsKey(p.getUUID())) return i;
        return null;
    }

    // ---------- open ----------

    public static RiftInstance open(List<ServerPlayer> party, int tier) {
        if (party.isEmpty()) return null;
        MinecraftServer server = party.get(0).getServer();
        ServerLevel level = server == null ? null : server.getLevel(RIFT);
        if (level == null) {
            RpgCore.LOG.error("rpgcore: rift dimension missing");
            return null;
        }
        int cell = 0;
        while (INSTANCES.containsKey(cell)) cell++;
        BlockPos center = new BlockPos(cell * CELL, Y, 0);
        RiftInstance inst = new RiftInstance(cell, Mth.clamp(tier, 1, 5), center, RiftConfig.roomRadius, RiftConfig.seconds * 20);
        INSTANCES.put(cell, inst);
        forceChunks(level, inst, true);
        buildRoom(level, inst, false);
        DangerMap.override(RIFT, inst.box, inst.tier);
        for (ServerPlayer p : party) {
            if (of(p) != null) continue;
            inst.players.put(p.getUUID(), new RiftInstance.Origin(p.level().dimension(), p.getX(), p.getY(), p.getZ()));
            p.teleportTo(level, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, p.getYRot(), p.getXRot());
            p.displayClientMessage(Component.translatable("rift.rpgcore.enter", inst.tier, RiftConfig.seconds / 60), true);
        }
        spawnFloor(level, inst);
        return inst;
    }

    private static void forceChunks(ServerLevel level, RiftInstance inst, boolean force) {
        int r = (RiftConfig.roomRadius + 2 >> 4) + 1;
        int cx = inst.center.getX() >> 4, cz = inst.center.getZ() >> 4;
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) level.setChunkForced(x, z, force);
        }
    }

    private static void buildRoom(ServerLevel level, RiftInstance inst, boolean clear) {
        int r = RiftConfig.roomRadius;
        BlockState floor = clear ? Blocks.AIR.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState light = clear ? Blocks.AIR.defaultBlockState() : Blocks.SEA_LANTERN.defaultBlockState();
        BlockState wall = clear ? Blocks.AIR.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState();
        BlockPos c = inst.center;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                BlockPos base = c.offset(dx, -1, dz);
                if (d <= r) {
                    level.setBlock(base, (dx % 4 == 0 && dz % 4 == 0) ? light : floor, 2);
                    for (int y = 1; y <= 4; y++) level.setBlock(base.above(y), Blocks.AIR.defaultBlockState(), 2);
                } else if (d <= r + 1.5) {
                    level.setBlock(base, floor, 2);
                    for (int y = 1; y <= 4; y++) level.setBlock(base.above(y), wall, 2);
                }
            }
        }
        if (clear) {
            level.setBlock(inst.continueDoor(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(inst.exitDoor(), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static void spawnFloor(ServerLevel level, RiftInstance inst) {
        inst.phase = RiftInstance.Phase.FIGHTING;
        inst.mobs.clear();
        double strength = RiftMath.strength(inst.floor, RiftConfig.strengthPerFloor);
        int count = RiftMath.mobCount(inst.floor, RiftConfig.baseMobs, RiftConfig.mobsPerFloor);
        for (int i = 0; i < count; i++) {
            ResourceLocation id = RiftConfig.mobs.get(level.random.nextInt(RiftConfig.mobs.size()));
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
            Entity e = type == null ? null : type.create(level);
            if (!(e instanceof Mob mob)) continue;
            place(level, inst, mob);
            ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null, null);
            MobScaling.apply(mob, inst.tier, strength);
            mob.setPersistenceRequired();
            level.addFreshEntity(mob);
            inst.mobs.add(mob);
        }
        if (level.random.nextDouble() < RiftMath.bossChance(inst.floor, RiftConfig.bossStartFloor, RiftConfig.bossChance)) {
            BossDef def = BossDefs.get(RiftConfig.boss);
            RpgBoss boss = def == null ? null : RpgEntities.BOSS.get().create(level);
            if (boss != null) {
                place(level, inst, boss);
                boss.setBoss(def, null);
                level.addFreshEntity(boss);
                inst.mobs.add(boss);
            }
        }
    }

    private static void place(ServerLevel level, RiftInstance inst, Mob mob) {
        double a = level.random.nextDouble() * Math.PI * 2;
        double d = 3 + level.random.nextDouble() * (RiftConfig.roomRadius - 4);
        mob.moveTo(inst.center.getX() + 0.5 + Math.cos(a) * d, inst.center.getY(), inst.center.getZ() + 0.5 + Math.sin(a) * d, level.random.nextFloat() * 360F, 0F);
    }

    // ---------- floors / doors ----------

    private static void floorCleared(ServerLevel level, RiftInstance inst) {
        inst.phase = RiftInstance.Phase.CLEARED;
        ServerPlayer leader = firstPlayer(level.getServer(), inst);
        double mult = RiftMath.rewardMultiplier(inst.floor, RiftConfig.rewardPerFloor);
        int rolls = RiftMath.rewardRolls(mult, level.random.nextDouble());
        for (int i = 0; i < rolls; i++) inst.rewards.addAll(LootSources.roll(LootSources.RIFT, leader, inst.tier, level.random, null));
        level.setBlock(inst.continueDoor(), RpgBlocks.RIFT_DOOR_CONTINUE.get().defaultBlockState(), 3);
        level.setBlock(inst.exitDoor(), RpgBlocks.RIFT_DOOR_EXIT.get().defaultBlockState(), 3);
        broadcast(level.getServer(), inst, Component.translatable("rift.rpgcore.cleared", inst.floor, inst.rewards.size()));
    }

    /** Door "continue": next floor. */
    public static void descend(ServerLevel level, RiftInstance inst) {
        if (inst.phase != RiftInstance.Phase.CLEARED) return;
        level.setBlock(inst.continueDoor(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(inst.exitDoor(), Blocks.AIR.defaultBlockState(), 3);
        inst.floor++;
        spawnFloor(level, inst);
        broadcast(level.getServer(), inst, Component.translatable("rift.rpgcore.floor", inst.floor));
    }

    /** Ends a run: {@code failed} halves the rewards (timeout or everyone dead). */
    public static void end(MinecraftServer server, RiftInstance inst, boolean failed) {
        INSTANCES.remove(inst.cell);
        ServerLevel level = server.getLevel(RIFT);
        List<ItemStack> rewards = inst.rewards;
        if (failed) {
            List<Integer> counts = new ArrayList<>();
            for (ItemStack s : rewards) counts.add(s.getCount());
            List<Integer> kept = RiftMath.halve(counts);
            List<ItemStack> halved = new ArrayList<>();
            for (int i = 0; i < rewards.size(); i++) if (kept.get(i) > 0) halved.add(rewards.get(i).copyWithCount(kept.get(i)));
            rewards = halved;
        }
        List<UUID> ids = new ArrayList<>(inst.players.keySet());
        for (int i = 0; i < rewards.size() && !ids.isEmpty(); i++) give(server, ids.get(i % ids.size()), rewards.get(i));
        for (Map.Entry<UUID, RiftInstance.Origin> e : inst.players.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.level().dimension().equals(RIFT) || !p.isAlive()) continue;
            RiftInstance.Origin o = e.getValue();
            ServerLevel back = server.getLevel(o.dimension());
            if (back == null) back = server.overworld();
            p.teleportTo(back, o.x(), o.y(), o.z(), p.getYRot(), p.getXRot());
            p.displayClientMessage(Component.translatable(failed ? "rift.rpgcore.failed" : "rift.rpgcore.left"), true);
        }
        if (level != null) {
            for (Mob m : inst.mobs) if (m.isAlive()) m.discard();
            buildRoom(level, inst, true);
            forceChunks(level, inst, false);
        }
        DangerMap.removeOverride(RIFT, inst.box);
    }

    private static void give(MinecraftServer server, UUID id, ItemStack stack) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null && p.isAlive()) {
            Inv.give(p, stack);
            return;
        }
        // dead or offline: delivered on respawn/login
        if (p != null) {
            CompoundTag root = PlayerData.root(p);
            ListTag list = root.getList(PENDING, Tag.TAG_COMPOUND);
            list.add(stack.save(new CompoundTag()));
            root.put(PENDING, list);
        }
    }

    private static void deliverPending(ServerPlayer p) {
        CompoundTag root = PlayerData.root(p);
        if (!root.contains(PENDING)) return;
        ListTag list = root.getList(PENDING, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) Inv.give(p, ItemStack.of(list.getCompound(i)));
        root.remove(PENDING);
    }

    private static ServerPlayer firstPlayer(MinecraftServer server, RiftInstance inst) {
        for (UUID id : inst.players.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) return p;
        }
        return null;
    }

    private static void broadcast(MinecraftServer server, RiftInstance inst, Component msg) {
        for (UUID id : inst.players.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.displayClientMessage(msg, false);
        }
    }

    // ---------- ticking ----------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        if (server == null) return;
        if (!INSTANCES.isEmpty()) {
            ServerLevel level = server.getLevel(RIFT);
            for (RiftInstance inst : new ArrayList<>(INSTANCES.values())) {
                inst.ticksLeft--;
                if (inst.ticksLeft <= 0) {
                    end(server, inst, true);
                    continue;
                }
                if (server.getTickCount() % 10 != 0 || level == null) continue;
                if (!anyoneInside(server, inst)) {
                    end(server, inst, true);
                } else if (inst.phase == RiftInstance.Phase.FIGHTING && inst.mobsDead()) {
                    floorCleared(level, inst);
                }
            }
        }
        if (server.getTickCount() % 20 == 0) naturalPortals(server);
    }

    private static boolean anyoneInside(MinecraftServer server, RiftInstance inst) {
        for (UUID id : inst.players.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.isAlive() && p.level().dimension().equals(RIFT)) return true;
        }
        return false;
    }

    /** At nightfall each overworld player rolls for a natural rift nearby (average every 3 days); they vanish at dawn. */
    private static void naturalPortals(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long time = overworld.getDayTime() % 24000L;
        if (time < 13000 || time >= 13020) return;
        for (ServerPlayer p : overworld.players()) {
            if (p.getRandom().nextDouble() >= RiftConfig.portalChance) continue;
            for (int attempt = 0; attempt < 8; attempt++) {
                double a = p.getRandom().nextDouble() * Math.PI * 2;
                double d = RiftConfig.portalMin + p.getRandom().nextDouble() * (RiftConfig.portalMax - RiftConfig.portalMin);
                int x = (int) (p.getX() + Math.cos(a) * d), z = (int) (p.getZ() + Math.sin(a) * d);
                if (!overworld.isLoaded(new BlockPos(x, 64, z))) continue;
                int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (!overworld.getFluidState(pos.below()).isEmpty()) continue;
                RiftPortalEntity.spawn(overworld, pos, DangerMap.get(overworld, pos), true);
                break;
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        deliverPending(p);
        // a run cannot survive a restart: send stragglers home
        if (p.level().dimension().equals(RIFT) && of(p) == null && p.getServer() != null) {
            ServerLevel ow = p.getServer().overworld();
            BlockPos spawn = ow.getSharedSpawnPos();
            p.teleportTo(ow, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, p.getYRot(), p.getXRot());
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) deliverPending(p);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        for (RiftInstance inst : new ArrayList<>(INSTANCES.values())) end(event.getServer(), inst, false);
    }
}
