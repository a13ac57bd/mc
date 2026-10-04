package com.rpgcore.boss;

import com.rpgcore.dungeon.ArenaGateBlock;
import com.rpgcore.registry.RpgBlockEntities;
import com.rpgcore.registry.RpgEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Boss arena (§14 BossArena): spawns the boss when a player comes within {@code radius} and closes the arena gates;
 * if no player is inside for 100 ticks the boss heals fully, returns to the altar and the gates open; when the boss
 * dies the altar is marked defeated, the gates open and it never spawns again.
 */
public class BossAltarBlockEntity extends BlockEntity {
    public static final int EMPTY_RESET_TICKS = 100;

    private ResourceLocation bossId = new ResourceLocation("rpgcore", "test_boss");
    private int radius = 14;
    private UUID bossUuid;
    private boolean defeated;
    private int emptyTicks;
    private boolean gatesClosed;
    private List<BlockPos> gates;

    public BossAltarBlockEntity(BlockPos pos, BlockState state) {
        super(RpgBlockEntities.BOSS_ALTAR.get(), pos, state);
    }

    public ResourceLocation bossId() {
        return bossId;
    }

    public void configure(ResourceLocation bossId, int radius) {
        this.bossId = bossId;
        this.radius = radius;
        setChanged();
    }

    public boolean defeated() {
        return defeated;
    }

    public UUID bossUuid() {
        return bossUuid;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BossAltarBlockEntity be) {
        if (level.getGameTime() % 10 != 0 || !(level instanceof ServerLevel sl)) return;
        be.tick(sl);
    }

    private void tick(ServerLevel level) {
        if (defeated) return;
        AABB arena = new AABB(worldPosition).inflate(radius, 8, radius);
        List<Player> players = level.getEntitiesOfClass(Player.class, arena, p -> p.isAlive() && !p.isSpectator() && !p.isCreative());
        Entity entity = bossUuid == null ? null : level.getEntity(bossUuid);
        RpgBoss boss = entity instanceof RpgBoss b && b.isAlive() ? b : null;
        if (boss == null) {
            if (!players.isEmpty()) {
                spawnBoss(level);
                setGates(level, false);
            } else if (gatesClosed) {
                setGates(level, true);
            }
            return;
        }
        if (players.isEmpty()) {
            emptyTicks += 10;
            if (emptyTicks >= EMPTY_RESET_TICKS) {
                boss.resetTo(worldPosition.above());
                setGates(level, true);
                emptyTicks = 0;
            }
        } else {
            emptyTicks = 0;
            if (!gatesClosed) setGates(level, false);
        }
    }

    private void spawnBoss(ServerLevel level) {
        BossDef def = BossDefs.get(bossId);
        if (def == null) return;
        RpgBoss boss = RpgEntities.BOSS.get().create(level);
        if (boss == null) return;
        boss.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, 0F, 0F);
        boss.setBoss(def, worldPosition);
        level.addFreshEntity(boss);
        bossUuid = boss.getUUID();
        setChanged();
    }

    /** Called by the boss when it dies. */
    public void onBossDefeated() {
        defeated = true;
        bossUuid = null;
        if (level instanceof ServerLevel sl) setGates(sl, true);
        setChanged();
    }

    private void setGates(ServerLevel level, boolean open) {
        if (gates == null) gates = scanGates(level);
        for (BlockPos p : gates) ArenaGateBlock.set(level, p, open);
        gatesClosed = !open;
        setChanged();
    }

    private List<BlockPos> scanGates(ServerLevel level) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-radius - 2, -4, -radius - 2), worldPosition.offset(radius + 2, 10, radius + 2))) {
            if (level.getBlockState(p).getBlock() instanceof ArenaGateBlock) out.add(p.immutable());
        }
        return out;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("boss")) bossId = ResourceLocation.tryParse(tag.getString("boss"));
        if (tag.contains("radius")) radius = tag.getInt("radius");
        bossUuid = tag.hasUUID("boss_uuid") ? tag.getUUID("boss_uuid") : null;
        defeated = tag.getBoolean("defeated");
        gatesClosed = tag.getBoolean("gates_closed");
        if (tag.contains("gates")) {
            gates = new ArrayList<>();
            ListTag list = tag.getList("gates", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) gates.add(NbtUtils.readBlockPos(list.getCompound(i)));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (bossId != null) tag.putString("boss", bossId.toString());
        tag.putInt("radius", radius);
        if (bossUuid != null) tag.putUUID("boss_uuid", bossUuid);
        tag.putBoolean("defeated", defeated);
        tag.putBoolean("gates_closed", gatesClosed);
        if (gates != null) {
            ListTag list = new ListTag();
            for (BlockPos p : gates) list.add(NbtUtils.writeBlockPos(p));
            tag.put("gates", list);
        }
    }
}
