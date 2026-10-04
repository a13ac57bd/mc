package com.rpgcore.client;

import com.rpgcore.logic.CombatMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Locale;

/**
 * HUD (§8): vanilla hearts become a health bar with numbers; absorption is a thin yellow bar with "+N" (shown x5);
 * dash charges appear under the crosshair while not full; quest navigation hint at the top when enabled.
 */
public final class Hud {
    private Hud() {}

    private static final int WIDTH = 81;

    public static void renderHealth(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer() || !(mc.getCameraEntity() instanceof Player player)) return;
        int x = screenWidth / 2 - 91;
        int y = screenHeight - gui.leftHeight;
        float hp = player.getHealth();
        float max = Math.max(1F, player.getMaxHealth());
        float abs = player.getAbsorptionAmount();
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + 9, 0xC0000000);
        int filled = (int) (WIDTH * Mth.clamp(hp / max, 0F, 1F));
        int color = hp / max > 0.5F ? 0xFFC62828 : hp / max > 0.25F ? 0xFFE65100 : 0xFFFF1744;
        g.fill(x, y, x + filled, y + 8, color);
        g.fill(x, y, x + filled, y + 2, 0x40FFFFFF);
        Font font = mc.font;
        String text = Mth.ceil(hp) + " / " + Mth.ceil(max);
        g.drawString(font, text, x + (WIDTH - font.width(text)) / 2, y, 0xFFFFFFFF, true);
        if (abs > 0) {
            int absWidth = (int) (WIDTH * Mth.clamp(abs * CombatMath.SCALE / max, 0F, 1F));
            g.fill(x, y - 3, x + absWidth, y - 1, 0xFFFFD54F);
            g.drawString(font, "+" + Mth.ceil(abs * CombatMath.SCALE), x + WIDTH + 3, y, 0xFFFFD54F, true);
        }
        gui.leftHeight += 11;
    }

    public static void renderDash(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || ClientState.dashCharges < 0 || ClientState.dashCharges >= ClientState.dashMax) return;
        int max = ClientState.dashMax;
        int cx = screenWidth / 2, cy = screenHeight / 2 + 10;
        int size = 5, gap = 2;
        int total = max * size + (max - 1) * gap;
        int x0 = cx - total / 2;
        float progress = Mth.clamp((ClientState.dashElapsed + partialTick) / ClientState.dashTicksPerCharge, 0F, 1F);
        for (int i = 0; i < max; i++) {
            int x = x0 + i * (size + gap);
            g.fill(x, cy, x + size, cy + 3, 0x80000000);
            if (i < ClientState.dashCharges) g.fill(x, cy, x + size, cy + 3, 0xFF4FC3F7);
            else if (i == ClientState.dashCharges) g.fill(x, cy, x + (int) (size * progress), cy + 3, 0xFF81D4FA);
        }
    }

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public static void renderNav(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || ClientState.nav == null || mc.screen != null) return;
        double dx = ClientState.nav.x() - mc.player.getX(), dz = ClientState.nav.z() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = Mth.wrapDegrees(target - mc.player.getYRot());
        int idx = Math.floorMod((int) Math.round(rel / 45.0), 8);
        Component line = Component.literal(ARROWS[idx] + " ").append(Component.translatable(ClientState.nav.label()))
                .append(String.format(Locale.ROOT, "  %dm", (int) dist));
        int w = mc.font.width(line);
        g.fill(screenWidth / 2 - w / 2 - 4, 4, screenWidth / 2 + w / 2 + 4, 17, 0x80000000);
        g.drawString(mc.font, line, screenWidth / 2 - w / 2, 7, 0xFFFFFFFF, true);
    }

    /** Keeps the dash progress bar moving between server syncs. */
    public static void clientTick() {
        if (ClientState.dashCharges >= 0 && ClientState.dashCharges < ClientState.dashMax) ClientState.dashElapsed++;
    }
}
