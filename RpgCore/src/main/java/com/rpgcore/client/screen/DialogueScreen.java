package com.rpgcore.client.screen;

import com.rpgcore.net.RpgNetwork;
import com.rpgcore.net.RpgPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** The small dialogue box (§13): NPC name, 2-4 lines, up to 3 options. */
public class DialogueScreen extends Screen {
    private static final int W = 320;
    private static final int H = 110;

    private final RpgPackets.Dialogue dialogue;
    private boolean answered;

    public DialogueScreen(RpgPackets.Dialogue dialogue) {
        super(Component.translatable(dialogue.speaker()));
        this.dialogue = dialogue;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return height - H - 20;
    }

    @Override
    protected void init() {
        List<String> options = dialogue.options();
        int bw = (W - 20 - (options.size() - 1) * 6) / Math.max(1, options.size());
        for (int i = 0; i < options.size(); i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.translatable(options.get(i)), b -> choose(index))
                    .bounds(left() + 10 + i * (bw + 6), top() + H - 26, bw, 18).build());
        }
    }

    private void choose(int index) {
        answered = true;
        RpgNetwork.toServer(new RpgPackets.DialogueChoice(dialogue.entityId(), index));
    }

    /** Closed by the server: no "closed" packet back. */
    public void closeSilently() {
        answered = true;
        onClose();
    }

    @Override
    public void removed() {
        if (!answered) RpgNetwork.toServer(new RpgPackets.DialogueChoice(dialogue.entityId(), -1));
        super.removed();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = left(), y = top();
        g.fill(x, y, x + W, y + H, 0xD0101018);
        g.fill(x, y, x + W, y + 1, 0xFFB08D57);
        g.fill(x, y + H - 1, x + W, y + H, 0xFFB08D57);
        g.drawString(font, title, x + 10, y + 8, 0xFFE0B860, true);
        int ly = y + 22;
        for (String key : dialogue.lines()) {
            List<FormattedCharSequence> wrapped = new ArrayList<>(font.split(Component.translatable(key), W - 20));
            for (FormattedCharSequence line : wrapped) {
                if (ly > y + H - 36) break;
                g.drawString(font, line, x + 10, ly, 0xFFFFFFFF, false);
                ly += 10;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
