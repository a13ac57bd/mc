package com.rpgcore.client.screen;

import com.rpgcore.client.ClientState;
import com.rpgcore.net.RpgNetwork;
import com.rpgcore.net.RpgPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Quest journal (§13): active quests with their current step, finished quests, clues, navigation on/off. */
public class JournalScreen extends Screen {
    private static final int W = 300;
    private static final int H = 200;

    public JournalScreen() {
        super(Component.translatable("journal.rpgcore.title"));
    }

    @Override
    protected void init() {
        refresh();
    }

    public void refresh() {
        clearWidgets();
        int x = (width - W) / 2, y = (height - H) / 2;
        boolean nav = ClientState.journal != null && ClientState.journal.nav();
        addRenderableWidget(Button.builder(Component.translatable(nav ? "journal.rpgcore.nav_on" : "journal.rpgcore.nav_off"),
                b -> RpgNetwork.toServer(new RpgPackets.NavToggle())).bounds(x + W - 110, y + H - 24, 100, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(x, y, x + W, y + H, 0xE0201A14);
        g.drawCenteredString(font, title, width / 2, y + 8, 0xFFE0B860);
        RpgPackets.Journal j = ClientState.journal;
        int ly = y + 24;
        if (j != null) {
            for (RpgPackets.JournalEntry e : j.entries()) {
                if (ly > y + H - 40) break;
                g.drawString(font, Component.translatable(e.title()), x + 10, ly, e.done() ? 0xFF808080 : 0xFFFFFFFF, false);
                ly += 10;
                if (!e.done()) {
                    for (FormattedCharSequence line : font.split(Component.translatable(e.step()), W - 30)) {
                        g.drawString(font, line, x + 20, ly, 0xFFB0B0B0, false);
                        ly += 10;
                    }
                }
                ly += 2;
            }
            if (!j.clues().isEmpty() && ly < y + H - 40) {
                g.drawString(font, Component.translatable("journal.rpgcore.clues"), x + 10, ly + 4, 0xFFE0B860, false);
                ly += 16;
                for (String clue : j.clues()) {
                    if (ly > y + H - 34) break;
                    g.drawString(font, Component.literal("• ").append(Component.translatable(clue)), x + 14, ly, 0xFFD0D0D0, false);
                    ly += 10;
                }
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
