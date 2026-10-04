package com.rpgcore.town;

import com.rpgcore.quest.Dialogues;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Town service NPC (§13): innkeeper, smith, quartermaster, quest giver... Right-click opens its dialogue (quest/Dialogues).
 * Stands still and cannot be hurt. Replaces Easy NPC so the pack has no hard dependency for towns.
 */
public class NpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> ROLE = SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);

    private ResourceLocation dialogue;
    private ResourceLocation town;
    private String nameKey = "npc.rpgcore.villager";

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ROLE, "villager");
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    public void configure(String role, String nameKey, ResourceLocation dialogue, ResourceLocation town) {
        entityData.set(ROLE, role);
        this.nameKey = nameKey;
        this.dialogue = dialogue;
        this.town = town;
        setCustomName(Component.translatable(nameKey));
        setCustomNameVisible(true);
    }

    public String role() {
        return entityData.get(ROLE);
    }

    public ResourceLocation dialogue() {
        return dialogue;
    }

    public ResourceLocation town() {
        return town;
    }

    public String nameKey() {
        return nameKey;
    }

    /** NPC id used by quest "talk" objectives: &lt;town&gt;/&lt;role&gt;, or the role alone outside towns. */
    public String npcId() {
        return town == null ? role() : town + "/" + role();
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) Dialogues.open(sp, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // NPCs of a hidden town that were in unloaded chunks put themselves away when they load
        if (!level().isClientSide && tickCount % 100 == 0 && town != null && level() instanceof ServerLevel sl) {
            TownSavedData data = TownSavedData.get(sl.getServer());
            TownSavedData.Town t = data.get(town);
            if (t != null && t.hidden) data.store(this);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.getEntity() instanceof Player p && p.isCreative() && super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Role", role());
        tag.putString("NameKey", nameKey);
        if (dialogue != null) tag.putString("Dialogue", dialogue.toString());
        if (town != null) tag.putString("Town", town.toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ROLE, tag.getString("Role"));
        if (tag.contains("NameKey")) nameKey = tag.getString("NameKey");
        dialogue = tag.contains("Dialogue") ? ResourceLocation.tryParse(tag.getString("Dialogue")) : null;
        town = tag.contains("Town") ? ResourceLocation.tryParse(tag.getString("Town")) : null;
    }
}
