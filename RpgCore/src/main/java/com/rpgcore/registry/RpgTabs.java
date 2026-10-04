package com.rpgcore.registry;

import com.rpgcore.RpgCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class RpgTabs {
    private RpgTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, RpgCore.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.rpgcore"))
            .icon(() -> new ItemStack(RpgItems.FORGE_TABLE.get()))
            .displayItems((params, output) -> RpgItems.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());
}
