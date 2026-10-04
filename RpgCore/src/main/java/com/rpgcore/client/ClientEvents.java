package com.rpgcore.client;

import com.rpgcore.RpgCore;
import com.rpgcore.item.ItemData;
import com.rpgcore.logic.LootMath;
import com.rpgcore.logic.Rarity;
import com.rpgcore.net.RpgNetwork;
import com.rpgcore.net.RpgPackets;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

/** Client Forge-bus events: keys, hiding vanilla hearts, tooltips (read straight from item NBT), rarity borders, loot glow. */
@Mod.EventBusSubscriber(modid = RpgCore.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;
        while (Keys.DASH.consumeClick()) {
            RpgNetwork.toServer(new RpgPackets.Dash(player.input.forwardImpulse, player.input.leftImpulse));
        }
        while (Keys.JOURNAL.consumeClick()) RpgNetwork.toServer(new RpgPackets.JournalRequest());
        Hud.clientTick();
        if (player.tickCount % 4 == 0 && mc.level != null) lootGlow(mc.level, player);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientState.reset();
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) event.setCanceled(true);
    }

    // ---------- tooltips ----------

    public static String percent(double value) {
        return String.format(Locale.ROOT, "%.0f", value * 100.0);
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!ItemData.has(stack)) return;
        Rarity rarity = ItemData.rarity(stack);
        List<Component> tip = event.getToolTip();
        if (rarity == null || tip.isEmpty()) return;
        int color = rarity.color();
        if (ItemData.unique(stack) == null) tip.set(0, Component.empty().append(tip.get(0)).withStyle(s -> s.withColor(color)));
        int i = 1;
        int tier = ItemData.tier(stack);
        MutableComponent header = Component.translatable("rarity.rpgcore." + rarity.id()).withStyle(s -> s.withColor(color))
                .append(Component.literal("  ")).append(Component.translatable("tooltip.rpgcore.tier", tier).withStyle(ChatFormatting.GRAY));
        if (ItemData.upgrade(stack) > 0) header.append(Component.literal(" +" + ItemData.upgrade(stack)).withStyle(ChatFormatting.GOLD));
        tip.add(i++, header);
        double mult = LootMath.tierMultiplier(tier);
        for (ItemData.TraitRoll roll : ItemData.traits(stack)) {
            ResourceLocation id = roll.id();
            String key = "trait." + id.getNamespace() + "." + id.getPath();
            tip.add(i++, Component.literal(roll.locked() ? "◆ " : "• ")
                    .append(Component.translatable(key, percent(roll.roll() * mult)))
                    .withStyle(roll.locked() ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        }
        ResourceLocation ins = ItemData.inscription(stack);
        if (ins != null) {
            tip.add(i++, Component.literal("✦ ").append(Component.translatable("trait." + ins.getNamespace() + "." + ins.getPath(), ""))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        ResourceLocation unique = ItemData.unique(stack);
        if (unique != null) {
            tip.add(i, Component.translatable("unique." + unique.getNamespace() + "." + unique.getPath() + ".desc")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        Rarity rarity = ItemData.rarity(event.getItemStack());
        if (rarity == null || rarity == Rarity.COMMON) return;
        int c = 0xFF000000 | rarity.color();
        int dark = 0xFF000000 | ((rarity.color() >> 1) & 0x7F7F7F);
        event.setBorderStart(c);
        event.setBorderEnd(dark);
    }

    // ---------- loot glow ----------

    private static void lootGlow(ClientLevel level, LocalPlayer player) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(24))) {
            Rarity rarity = ItemData.rarity(item.getItem());
            if (rarity == null || rarity == Rarity.COMMON) continue;
            int c = rarity.color();
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(((c >> 16) & 0xFF) / 255F, ((c >> 8) & 0xFF) / 255F, (c & 0xFF) / 255F), 1.0F);
            int height = rarity.ordinal() >= Rarity.RARE.ordinal() ? 8 : 2;
            for (int k = 0; k < height; k++) {
                level.addParticle(dust, item.getX(), item.getY() + 0.3 + k * 0.3, item.getZ(), 0, 0.02, 0);
            }
        }
    }
}
