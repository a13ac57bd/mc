package com.rpgcore.registry;

import com.rpgcore.RpgCore;
import com.rpgcore.home.BlueprintItem;
import com.rpgcore.home.CivilizationScrollItem;
import com.rpgcore.home.TrophyItem;
import com.rpgcore.rift.RiftBeaconItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class RpgItems {
    private RpgItems() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, RpgCore.MODID);

    /** Tier materials 1..5 ("怪物素材", §6). Index 0 = tier 1. */
    public static final List<RegistryObject<Item>> ESSENCES = new ArrayList<>();

    static {
        for (int t = 1; t <= 5; t++) {
            ESSENCES.add(ITEMS.register("essence_t" + t, () -> new Item(new Item.Properties())));
        }
    }

    // inscription materials (#47)
    public static final RegistryObject<Item> FROST_CORE = simple("frost_core", Rarity.UNCOMMON);
    public static final RegistryObject<Item> EMBER_CORE = simple("ember_core", Rarity.UNCOMMON);
    public static final RegistryObject<Item> VOID_CORE = simple("void_core", Rarity.RARE);
    public static final RegistryObject<Item> STORM_CORE = simple("storm_core", Rarity.UNCOMMON);
    public static final RegistryObject<Item> VENOM_CORE = simple("venom_core", Rarity.UNCOMMON);

    public static final RegistryObject<Item> RIFT_BEACON = ITEMS.register("rift_beacon",
            () -> new RiftBeaconItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> BLUEPRINT = ITEMS.register("blueprint",
            () -> new BlueprintItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CIVILIZATION_SCROLL = ITEMS.register("civilization_scroll",
            () -> new CivilizationScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    /** Demo quest item (quests/lost_ring.json). */
    public static final RegistryObject<Item> OLD_RING = simple("old_ring", Rarity.UNCOMMON);

    // block items
    public static final RegistryObject<Item> FORGE_TABLE = block("forge_table", RpgBlocks.FORGE_TABLE);
    public static final RegistryObject<Item> SHORTCUT_DOOR = block("shortcut_door", RpgBlocks.SHORTCUT_DOOR);
    public static final RegistryObject<Item> SECRET_WALL = block("secret_wall", RpgBlocks.SECRET_WALL);
    public static final RegistryObject<Item> OATH_STONE = block("oath_stone", RpgBlocks.OATH_STONE);
    public static final RegistryObject<Item> POWDER_KEG = block("powder_keg", RpgBlocks.POWDER_KEG);
    public static final RegistryObject<Item> FRAGILE_CHAIN = block("fragile_chain", RpgBlocks.FRAGILE_CHAIN);
    public static final RegistryObject<Item> CHANDELIER = block("chandelier", RpgBlocks.CHANDELIER);
    public static final RegistryObject<Item> OIL = block("oil", RpgBlocks.OIL);
    public static final RegistryObject<Item> ARENA_GATE = block("arena_gate", RpgBlocks.ARENA_GATE);
    public static final RegistryObject<Item> BOSS_ALTAR = block("boss_altar", RpgBlocks.BOSS_ALTAR);
    public static final RegistryObject<Item> RIFT_DOOR_CONTINUE = block("rift_door_continue", RpgBlocks.RIFT_DOOR_CONTINUE);
    public static final RegistryObject<Item> RIFT_DOOR_EXIT = block("rift_door_exit", RpgBlocks.RIFT_DOOR_EXIT);
    public static final RegistryObject<Item> INN_BED = block("inn_bed", RpgBlocks.INN_BED);
    public static final RegistryObject<Item> BUILD_TABLE = block("build_table", RpgBlocks.BUILD_TABLE);
    public static final RegistryObject<Item> TROPHY = ITEMS.register("trophy",
            () -> new TrophyItem(RpgBlocks.TROPHY.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static Item essence(int tier) {
        int t = Math.max(1, Math.min(5, tier));
        return ESSENCES.get(t - 1).get();
    }

    private static RegistryObject<Item> simple(String name, Rarity rarity) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().rarity(rarity)));
    }

    private static RegistryObject<Item> block(String name, Supplier<? extends Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
