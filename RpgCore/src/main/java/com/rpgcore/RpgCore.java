package com.rpgcore;

import com.mojang.logging.LogUtils;
import com.rpgcore.compat.Compat;
import com.rpgcore.net.RpgNetwork;
import com.rpgcore.registry.RpgRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/** Entry point (TECH_SPEC §3). Feature packages register their own Forge-bus listeners via @EventBusSubscriber. */
@Mod(RpgCore.MODID)
public final class RpgCore {
    public static final String MODID = "rpgcore";
    public static final Logger LOG = LogUtils.getLogger();

    public RpgCore() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        RpgRegistries.register(modBus);
        modBus.addListener(this::commonSetup);
        Compat.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            RpgNetwork.register();
            Compat.setup();
        });
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
