package com.rpgcore.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared boss entity (§14 M5): state machine idle -> windup (ground particles draw the hit area) -> strike -> recover.
 * Moves are picked in table order by phase, cooldown and distance. Invulnerable during phase transitions.
 * The current move/animation name is synced for the renderer (GeckoLib comes with the art pass).
 */
public class RpgBoss extends Monster {
    public static final float SCALE = 1.6F;

    private static final EntityDataAccessor<String> BOSS_ID = SynchedEntityData.defineId(RpgBoss.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> ANIMATION = SynchedEntityData.defineId(RpgBoss.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(RpgBoss.class, EntityDataSerializers.INT);

    public enum State { IDLE, WINDUP, STRIKE, RECOVER, TRANSITION }

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.literal("Boss"), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);
    private BossDef def;
    private State state = State.IDLE;
    private int stateTicks;
    private int transitionTicks;
    private BossDef.Move move;
    private final Map<String, Long> cooldowns = new HashMap<>();
    private int phase = 1;
    private BlockPos altar;
    private Vec3 aimOrigin = Vec3.ZERO;
    private Vec3 aimDir = new Vec3(0, 0, 1);
    private final Set<Integer> hit = new HashSet<>();

    public RpgBoss(EntityType<? extends RpgBoss> type, Level level) {
        super(type, level);
        this.xpReward = 100;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(BOSS_ID, "");
        entityData.define(ANIMATION, "");
        entityData.define(STATE, 0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---------- setup ----------

    /** Call before adding to the level; the x5 health scale is applied on join. */
    public void setBoss(BossDef def, BlockPos altar) {
        this.def = def;
        this.altar = altar;
        entityData.set(BOSS_ID, def.id().toString());
        applyStats();
        setHealth(getMaxHealth());
    }

    private void applyStats() {
        setBase(Attributes.MAX_HEALTH, def.health());
        setBase(Attributes.ATTACK_DAMAGE, def.damage());
        setBase(Attributes.MOVEMENT_SPEED, def.speed());
        setBase(Attributes.ARMOR, def.armor());
        bossEvent.setName(Component.translatable(def.name()));
        setCustomName(Component.translatable(def.name()));
    }

    private void setBase(net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        AttributeInstance inst = getAttribute(attribute);
        if (inst != null) inst.setBaseValue(value);
    }

    public ResourceLocation bossId() {
        return ResourceLocation.tryParse(entityData.get(BOSS_ID));
    }

    public BossDef def() {
        return def;
    }

    public String animation() {
        return entityData.get(ANIMATION);
    }

    public State state() {
        return State.values()[Mth.clamp(entityData.get(STATE), 0, State.values().length - 1)];
    }

    public int phase() {
        return phase;
    }

    public BlockPos altar() {
        return altar;
    }

    // ---------- AI ----------

    private void setState(State s) {
        state = s;
        stateTicks = 0;
        entityData.set(STATE, s.ordinal());
        if (s == State.IDLE || s == State.RECOVER) entityData.set(ANIMATION, s == State.IDLE ? "" : "recover");
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (def == null) {
            def = BossDefs.get(bossId());
            if (def == null) return;
            applyStats();
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());
        stateTicks++;
        LivingEntity target = getTarget();
        switch (state) {
            case TRANSITION -> {
                getNavigation().stop();
                if (stateTicks % 5 == 0) burst(ParticleTypes.SOUL_FIRE_FLAME, 2.0, 16);
                if (stateTicks >= transitionTicks) setState(State.IDLE);
            }
            case IDLE -> {
                if (target == null || !target.isAlive()) return;
                BossDef.Move m = chooseMove(target);
                if (m != null) startMove(m, target);
                else if (distanceTo(target) > 2.5) getNavigation().moveTo(target, 1.0);
            }
            case WINDUP -> {
                getNavigation().stop();
                getLookControl().setLookAt(aimOrigin.add(aimDir.scale(4)));
                if (stateTicks % 4 == 1) telegraph();
                if (stateTicks >= Math.max(1, (int) Math.round(move.windup() * 20))) {
                    setState(State.STRIKE);
                    hit.clear();
                }
            }
            case STRIKE -> strikeTick();
            case RECOVER -> {
                if (stateTicks >= 20) setState(State.IDLE);
            }
        }
    }

    private BossDef.Move chooseMove(LivingEntity target) {
        long now = level().getGameTime();
        double d = distanceTo(target);
        for (BossDef.Move m : def.moves()) {
            if (!m.usableIn(phase) || cooldowns.getOrDefault(m.name(), 0L) > now) continue;
            boolean inRange = m.shape() == BossDef.Shape.CHARGE ? d >= 3.0 && d <= m.range() : d <= m.range() + 0.5;
            if (inRange) return m;
        }
        return null;
    }

    private void startMove(BossDef.Move m, LivingEntity target) {
        move = m;
        aimOrigin = position();
        Vec3 d = target.position().subtract(aimOrigin);
        d = new Vec3(d.x, 0, d.z);
        aimDir = d.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, getYRot()) : d.normalize();
        cooldowns.put(m.name(), level().getGameTime() + Math.round((m.windup() + m.cooldown()) * 20));
        entityData.set(ANIMATION, m.animation());
        getNavigation().stop();
        setState(State.WINDUP);
    }

    private void strikeTick() {
        if (move.shape() == BossDef.Shape.CHARGE) {
            int duration = Math.max(1, (int) Math.ceil(move.range() / 0.8));
            setDeltaMovement(aimDir.x * 0.8, getDeltaMovement().y, aimDir.z * 0.8);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(move.width() - getBbWidth() / 2))) {
                if (e != this && hit.add(e.getId())) damage(e);
            }
            if (stateTicks >= duration) {
                setDeltaMovement(Vec3.ZERO);
                setState(State.RECOVER);
            }
            return;
        }
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(move.range() + 1, 3, move.range() + 1))) {
            if (e != this && inArea(e.position())) damage(e);
        }
        burst(ParticleTypes.EXPLOSION, 0.5, 2);
        setState(State.RECOVER);
    }

    /** True when {@code p} lies inside the current move's area (circle or cone around the aim origin). */
    public boolean inArea(Vec3 p) {
        double dx = p.x - aimOrigin.x, dz = p.z - aimOrigin.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > move.range()) return false;
        if (move.shape() == BossDef.Shape.CIRCLE || dist < 0.5) return true;
        double cos = (dx * aimDir.x + dz * aimDir.z) / dist;
        return cos >= Math.cos(Math.toRadians(move.angle() / 2.0));
    }

    private void damage(LivingEntity e) {
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        e.hurt(damageSources().mobAttack(this), (float) move.damage());
    }

    private void telegraph() {
        if (!(level() instanceof ServerLevel sl)) return;
        double y = aimOrigin.y + 0.1;
        switch (move.shape()) {
            case CIRCLE -> {
                int n = (int) (move.range() * 12);
                for (int i = 0; i < n; i++) {
                    double a = 2 * Math.PI * i / n;
                    sl.sendParticles(ParticleTypes.FLAME, aimOrigin.x + Math.cos(a) * move.range(), y, aimOrigin.z + Math.sin(a) * move.range(), 1, 0, 0, 0, 0);
                }
            }
            case CONE -> {
                double half = Math.toRadians(move.angle() / 2.0);
                double base = Math.atan2(aimDir.z, aimDir.x);
                int n = (int) (move.range() * 8);
                for (int i = 0; i <= n; i++) {
                    double a = base - half + 2 * half * i / n;
                    sl.sendParticles(ParticleTypes.FLAME, aimOrigin.x + Math.cos(a) * move.range(), y, aimOrigin.z + Math.sin(a) * move.range(), 1, 0, 0, 0, 0);
                }
                for (double r = 0.5; r <= move.range(); r += 0.5) {
                    for (double a : new double[]{base - half, base + half}) {
                        sl.sendParticles(ParticleTypes.FLAME, aimOrigin.x + Math.cos(a) * r, y, aimOrigin.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                    }
                }
            }
            case CHARGE -> {
                Vec3 side = new Vec3(-aimDir.z, 0, aimDir.x).scale(move.width());
                for (double r = 0; r <= move.range(); r += 0.5) {
                    Vec3 c = aimOrigin.add(aimDir.scale(r));
                    sl.sendParticles(ParticleTypes.FLAME, c.x + side.x, y, c.z + side.z, 1, 0, 0, 0, 0);
                    sl.sendParticles(ParticleTypes.FLAME, c.x - side.x, y, c.z - side.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private void burst(ParticleOptions particle, double spread, int count) {
        if (level() instanceof ServerLevel sl) sl.sendParticles(particle, getX(), getY() + getBbHeight() / 2, getZ(), count, spread, spread, spread, 0.05);
    }

    // ---------- damage / phases ----------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (state == State.TRANSITION && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        boolean result = super.hurt(source, amount);
        if (result && def != null && isAlive()) checkPhase();
        return result;
    }

    private void checkPhase() {
        int idx = phase - 1;
        if (idx >= def.phases().size()) return;
        BossDef.Phase next = def.phases().get(idx);
        if (getHealth() / getMaxHealth() > next.threshold()) return;
        phase++;
        transitionTicks = Math.max(1, (int) Math.round(next.transition() * 20));
        move = null;
        entityData.set(ANIMATION, "transition");
        setState(State.TRANSITION);
    }

    /** Arena reset: full health, phase 1, back to the altar. */
    public void resetTo(BlockPos pos) {
        setHealth(getMaxHealth());
        phase = 1;
        cooldowns.clear();
        move = null;
        setTarget(null);
        getNavigation().stop();
        setState(State.IDLE);
        teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && altar != null) {
            BlockEntity be = level().getBlockEntity(altar);
            if (be instanceof BossAltarBlockEntity altarBe) altarBe.onBossDefeated();
        }
    }

    // ---------- tracking / save ----------

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Boss", entityData.get(BOSS_ID));
        tag.putInt("Phase", phase);
        if (altar != null) tag.put("Altar", NbtUtils.writeBlockPos(altar));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(BOSS_ID, tag.getString("Boss"));
        phase = Math.max(1, tag.getInt("Phase"));
        altar = tag.contains("Altar") ? NbtUtils.readBlockPos(tag.getCompound("Altar")) : null;
        def = BossDefs.get(bossId());
        if (def != null) bossEvent.setName(Component.translatable(def.name()));
    }
}
