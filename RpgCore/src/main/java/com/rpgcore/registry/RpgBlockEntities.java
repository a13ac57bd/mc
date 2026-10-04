package com.rpgcore.registry;

import com.rpgcore.RpgCore;
import com.rpgcore.boss.BossAltarBlockEntity;
import com.rpgcore.dungeon.SecretWallBlockEntity;
import com.rpgcore.home.BuildTableBlockEntity;
import com.rpgcore.home.TrophyBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class RpgBlockEntities {
    private RpgBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, RpgCore.MODID);

    public static final RegistryObject<BlockEntityType<SecretWallBlockEntity>> SECRET_WALL = BLOCK_ENTITIES.register("secret_wall",
            () -> BlockEntityType.Builder.of(SecretWallBlockEntity::new, RpgBlocks.SECRET_WALL.get()).build(null));
    public static final RegistryObject<BlockEntityType<BossAltarBlockEntity>> BOSS_ALTAR = BLOCK_ENTITIES.register("boss_altar",
            () -> BlockEntityType.Builder.of(BossAltarBlockEntity::new, RpgBlocks.BOSS_ALTAR.get()).build(null));
    public static final RegistryObject<BlockEntityType<BuildTableBlockEntity>> BUILD_TABLE = BLOCK_ENTITIES.register("build_table",
            () -> BlockEntityType.Builder.of(BuildTableBlockEntity::new, RpgBlocks.BUILD_TABLE.get()).build(null));
    public static final RegistryObject<BlockEntityType<TrophyBlockEntity>> TROPHY = BLOCK_ENTITIES.register("trophy",
            () -> BlockEntityType.Builder.of(TrophyBlockEntity::new, RpgBlocks.TROPHY.get()).build(null));
}
