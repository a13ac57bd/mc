package com.rpgcore.client;

import com.rpgcore.RpgCore;
import com.rpgcore.client.render.BossRenderer;
import com.rpgcore.client.render.NpcRenderer;
import com.rpgcore.client.screen.ForgeScreen;
import com.rpgcore.registry.RpgEntities;
import com.rpgcore.registry.RpgMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client registration on the mod bus. */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(RpgMenus.FORGE.get(), ForgeScreen::new));
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(Keys.DASH);
        event.register(Keys.JOURNAL);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.PLAYER_HEALTH.id(), "health_bar", Hud::renderHealth);
        event.registerAboveAll("dash_charges", Hud::renderDash);
        event.registerAboveAll("quest_nav", Hud::renderNav);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(RpgEntities.BOSS.get(), BossRenderer::new);
        event.registerEntityRenderer(RpgEntities.NPC.get(), NpcRenderer::new);
        event.registerEntityRenderer(RpgEntities.RIFT_PORTAL.get(), NoopRenderer::new);
    }
}
