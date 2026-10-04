package com.rpgcore.registry;

import net.minecraftforge.eventbus.api.IEventBus;

/** Registers every DeferredRegister on the mod bus. */
public final class RpgRegistries {
    private RpgRegistries() {}

    public static void register(IEventBus modBus) {
        RpgAttributes.ATTRIBUTES.register(modBus);
        RpgEffects.EFFECTS.register(modBus);
        RpgBlocks.BLOCKS.register(modBus);
        RpgItems.ITEMS.register(modBus);
        RpgBlockEntities.BLOCK_ENTITIES.register(modBus);
        RpgEntities.ENTITIES.register(modBus);
        RpgMenus.MENUS.register(modBus);
        RpgLoot.SERIALIZERS.register(modBus);
        RpgTabs.TABS.register(modBus);
    }
}
