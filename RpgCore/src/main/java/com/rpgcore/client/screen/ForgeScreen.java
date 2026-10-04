package com.rpgcore.client.screen;

import com.rpgcore.client.ClientEvents;
import com.rpgcore.forge.ForgeMenu;
import com.rpgcore.forge.ForgeOps;
import com.rpgcore.forge.Inscriptions;
import com.rpgcore.item.ItemData;
import com.rpgcore.logic.ForgeCosts;
import com.rpgcore.logic.LootMath;
import com.rpgcore.logic.Rarity;
import com.rpgcore.net.RpgNetwork;
import com.rpgcore.net.RpgPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Forge table screen (§6): four tabs (upgrade, reroll, inscribe, salvage). Plain colors until the 176x166 texture
 * arrives (ART_SPEC 1.8).
 */
public class ForgeScreen extends AbstractContainerScreen<ForgeMenu> {
    private enum Tab { UPGRADE, REROLL, INSCRIBE, SALVAGE }

    private Tab tab = Tab.UPGRADE;
    private ItemStack lastStack = ItemStack.EMPTY;

    public ForgeScreen(ForgeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int x = leftPos, y = topPos;
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab t = tabs[i];
            addRenderableWidget(Button.builder(Component.translatable("forge.rpgcore.tab." + t.name().toLowerCase(java.util.Locale.ROOT)), b -> {
                tab = t;
                rebuild();
            }).bounds(x + i * 44, y - 20, 44, 18).build()).active = t != tab;
        }
        int bx = x + 70, by = y + 18;
        ItemStack stack = menu.equipment();
        switch (tab) {
            case UPGRADE -> addRenderableWidget(Button.builder(Component.translatable("forge.rpgcore.upgrade"), b -> send(ForgeOps.UPGRADE, 0))
                    .bounds(bx, by + 36, 100, 18).build());
            case REROLL -> {
                List<ItemData.TraitRoll> traits = ItemData.traits(stack);
                for (int i = 0; i < Math.min(3, traits.size()); i++) {
                    int index = i;
                    Button b = addRenderableWidget(Button.builder(Component.translatable("forge.rpgcore.reroll_n", i + 1), btn -> send(ForgeOps.REROLL, index))
                            .bounds(bx + i * 34, by + 36, 32, 18).build());
                    b.active = !traits.get(i).locked();
                }
            }
            case INSCRIBE -> addRenderableWidget(Button.builder(Component.translatable("forge.rpgcore.inscribe"), b -> send(ForgeOps.INSCRIBE, 0))
                    .bounds(bx, by + 36, 100, 18).build());
            case SALVAGE -> {
                addRenderableWidget(Button.builder(Component.translatable("forge.rpgcore.salvage"), b -> send(ForgeOps.SALVAGE, 0))
                        .bounds(bx, by + 20, 100, 16).build());
                for (int i = 0; i < 4; i++) {
                    int level = i;
                    Button b = addRenderableWidget(Button.builder(Component.translatable("forge.rpgcore.threshold." + i), btn -> send(ForgeOps.SET_THRESHOLD, level))
                            .bounds(bx + i * 25, by + 40, 25, 16).build());
                    b.active = menu.salvageThreshold() != i;
                }
            }
        }
    }

    private void send(int action, int arg) {
        RpgNetwork.toServer(new RpgPackets.ForgeAction(action, arg));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack now = menu.equipment();
        if (!ItemStack.matches(now, lastStack)) {
            lastStack = now.copy();
            rebuild();
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        g.fill(x, y, x + imageWidth, y + 1, 0xFFFFFFFF);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF555555);
        g.fill(x + 66, y + 14, x + imageWidth - 4, y + 76, 0xFF8B8B8B);
        for (Slot slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF373737);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF8B8B8B);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        int tx = 70, ty = 18;
        ItemStack stack = menu.equipment();
        Rarity rarity = ItemData.rarity(stack);
        if (tab == Tab.SALVAGE) {
            g.drawString(font, Component.translatable("forge.rpgcore.auto_salvage"), tx, ty + 62 - 4, 0xFFFFFF, false);
        }
        if (rarity == null) {
            g.drawString(font, Component.translatable("forge.rpgcore.put_item"), tx, ty, 0xFFFFFF, false);
            return;
        }
        int tier = ItemData.tier(stack);
        switch (tab) {
            case UPGRADE -> {
                int cost = ForgeCosts.upgradeCost(tier);
                g.drawString(font, Component.translatable("tooltip.rpgcore.tier", tier), tx, ty, 0xFFFFFF, false);
                g.drawString(font, cost < 0 ? Component.translatable("forge.rpgcore.max_tier")
                        : Component.translatable("forge.rpgcore.cost", cost, tier), tx, ty + 12, 0xFFFF55, false);
            }
            case REROLL -> {
                if (!ForgeCosts.canReroll(rarity)) {
                    g.drawString(font, Component.translatable("forge.rpgcore.reroll_rare_only"), tx, ty, 0xFF5555, false);
                } else {
                    List<ItemData.TraitRoll> traits = ItemData.traits(stack);
                    double mult = LootMath.tierMultiplier(tier);
                    for (int i = 0; i < Math.min(3, traits.size()); i++) {
                        ResourceLocation id = traits.get(i).id();
                        Component line = Component.literal((i + 1) + ". ").append(Component.translatable("trait." + id.getNamespace() + "." + id.getPath(),
                                ClientEvents.percent(traits.get(i).roll() * mult)));
                        g.drawString(font, font.plainSubstrByWidth(line.getString(), 100), tx, ty + i * 10, traits.get(i).locked() ? 0xFFAA00 : 0x55FFFF, false);
                    }
                    g.drawString(font, Component.translatable("forge.rpgcore.cost", ForgeCosts.rerollCost(ItemData.rerolls(stack)), tier), tx, ty + 30 - 4, 0xFFFF55, false);
                }
            }
            case INSCRIBE -> {
                Inscriptions.InscriptionDef def = Inscriptions.forMaterial(menu.material());
                ResourceLocation current = ItemData.inscription(stack);
                g.drawString(font, def == null ? Component.translatable("forge.rpgcore.put_core") : Component.translatable("forge.rpgcore.inscribe_ready"), tx, ty, 0xFFFFFF, false);
                if (current != null) {
                    g.drawString(font, Component.translatable("trait." + current.getNamespace() + "." + current.getPath(), ""), tx, ty + 12, 0xFF55FF, false);
                }
            }
            case SALVAGE -> g.drawString(font, Component.translatable("forge.rpgcore.yield",
                    ForgeCosts.salvageYield(rarity, ItemData.upgrade(stack)) * stack.getCount(), tier), tx, ty, 0xFFFFFF, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
