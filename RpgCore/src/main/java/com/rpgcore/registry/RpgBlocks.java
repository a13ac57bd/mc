package com.rpgcore.registry;

import com.rpgcore.RpgCore;
import com.rpgcore.boss.BossAltarBlock;
import com.rpgcore.dungeon.ArenaGateBlock;
import com.rpgcore.dungeon.ChandelierBlock;
import com.rpgcore.dungeon.FragileChainBlock;
import com.rpgcore.dungeon.OathStoneBlock;
import com.rpgcore.dungeon.OilBlock;
import com.rpgcore.dungeon.PowderKegBlock;
import com.rpgcore.dungeon.SecretWallBlock;
import com.rpgcore.dungeon.ShortcutDoorBlock;
import com.rpgcore.forge.ForgeTableBlock;
import com.rpgcore.home.BuildTableBlock;
import com.rpgcore.home.TrophyBlock;
import com.rpgcore.rift.RiftDoorBlock;
import com.rpgcore.town.InnBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class RpgBlocks {
    private RpgBlocks() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, RpgCore.MODID);

    /** Dungeon blocks cannot be broken in survival (§9). */
    public static BlockBehaviour.Properties unbreakable(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(-1.0F, 3600000.0F).noLootTable().pushReaction(PushReaction.BLOCK);
    }

    public static final RegistryObject<Block> FORGE_TABLE = BLOCKS.register("forge_table",
            () -> new ForgeTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F).requiresCorrectToolForDrops().sound(SoundType.ANVIL)));

    public static final RegistryObject<Block> SHORTCUT_DOOR = BLOCKS.register("shortcut_door",
            () -> new ShortcutDoorBlock(unbreakable(MapColor.COLOR_BLACK).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> SECRET_WALL = BLOCKS.register("secret_wall",
            () -> new SecretWallBlock(unbreakable(MapColor.STONE).sound(SoundType.STONE)));
    public static final RegistryObject<Block> OATH_STONE = BLOCKS.register("oath_stone",
            () -> new OathStoneBlock(unbreakable(MapColor.METAL).sound(SoundType.LODESTONE).lightLevel(s -> s.getValue(OathStoneBlock.LIT) ? 12 : 4)));
    public static final RegistryObject<Block> POWDER_KEG = BLOCKS.register("powder_keg",
            () -> new PowderKegBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0F, 0.0F).noLootTable().sound(SoundType.WOOD)));
    public static final RegistryObject<Block> FRAGILE_CHAIN = BLOCKS.register("fragile_chain",
            () -> new FragileChainBlock(unbreakable(MapColor.METAL).sound(SoundType.CHAIN).noOcclusion()));
    public static final RegistryObject<Block> CHANDELIER = BLOCKS.register("chandelier",
            () -> new ChandelierBlock(unbreakable(MapColor.METAL).sound(SoundType.LANTERN).noOcclusion().lightLevel(s -> 15)));
    public static final RegistryObject<Block> OIL = BLOCKS.register("oil",
            () -> new OilBlock(unbreakable(MapColor.COLOR_BLACK).noCollission().noOcclusion().sound(SoundType.HONEY_BLOCK)));
    public static final RegistryObject<Block> ARENA_GATE = BLOCKS.register("arena_gate",
            () -> new ArenaGateBlock(unbreakable(MapColor.COLOR_BLACK).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> BOSS_ALTAR = BLOCKS.register("boss_altar",
            () -> new BossAltarBlock(unbreakable(MapColor.COLOR_RED).sound(SoundType.STONE).noOcclusion().lightLevel(s -> 7)));

    public static final RegistryObject<Block> RIFT_DOOR_CONTINUE = BLOCKS.register("rift_door_continue",
            () -> new RiftDoorBlock(unbreakable(MapColor.COLOR_PURPLE).sound(SoundType.AMETHYST).lightLevel(s -> 12), false));
    public static final RegistryObject<Block> RIFT_DOOR_EXIT = BLOCKS.register("rift_door_exit",
            () -> new RiftDoorBlock(unbreakable(MapColor.EMERALD).sound(SoundType.AMETHYST).lightLevel(s -> 12), true));

    public static final RegistryObject<Block> INN_BED = BLOCKS.register("inn_bed",
            () -> new InnBedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.8F).sound(SoundType.WOOL)));

    public static final RegistryObject<Block> BUILD_TABLE = BLOCKS.register("build_table",
            () -> new BuildTableBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> TROPHY = BLOCKS.register("trophy",
            () -> new TrophyBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(1.5F).sound(SoundType.METAL).noOcclusion()));
}
