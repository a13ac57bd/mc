package com.rpgcore.registry;

import com.rpgcore.RpgCore;
import com.rpgcore.forge.ForgeMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class RpgMenus {
    private RpgMenus() {}

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, RpgCore.MODID);

    public static final RegistryObject<MenuType<ForgeMenu>> FORGE = MENUS.register("forge_table",
            () -> IForgeMenuType.create(ForgeMenu::fromNetwork));
}
