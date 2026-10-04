package com.rpgcore.registry;

import com.rpgcore.RpgCore;
import com.rpgcore.boss.RpgBoss;
import com.rpgcore.rift.RiftPortalEntity;
import com.rpgcore.town.NpcEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = RpgCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RpgEntities {
    private RpgEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, RpgCore.MODID);

    /** Shared boss entity; behaviour comes from data/&lt;ns&gt;/rpgcore/bosses/*.json (§14). Placeholder: scaled zombie model. */
    public static final RegistryObject<EntityType<RpgBoss>> BOSS = ENTITIES.register("rpg_boss",
            () -> EntityType.Builder.<RpgBoss>of(RpgBoss::new, MobCategory.MONSTER)
                    .sized(0.6F * RpgBoss.SCALE, 1.95F * RpgBoss.SCALE)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build(RpgCore.id("rpg_boss").toString()));

    /** Town service NPC (§13). */
    public static final RegistryObject<EntityType<NpcEntity>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(RpgCore.id("npc").toString()));

    /** Rift entrance: particles only (§11). */
    public static final RegistryObject<EntityType<RiftPortalEntity>> RIFT_PORTAL = ENTITIES.register("rift_portal",
            () -> EntityType.Builder.<RiftPortalEntity>of(RiftPortalEntity::new, MobCategory.MISC)
                    .sized(1.2F, 2.4F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .fireImmune()
                    .build(RpgCore.id("rift_portal").toString()));

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(BOSS.get(), RpgBoss.createAttributes().build());
        event.put(NPC.get(), NpcEntity.createAttributes().build());
    }
}
