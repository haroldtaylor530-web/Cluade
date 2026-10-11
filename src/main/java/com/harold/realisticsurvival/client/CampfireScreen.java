package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.RealisticSurvival;
import com.harold.realisticsurvival.block.CampfireMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/** Kindling and fuel slots, a burn gauge, fire size buttons and a light button. */
public class CampfireScreen extends AbstractContainerScreen<CampfireMenu> {
    private static final Identifier TEXTURE = RealisticSurvival.id("textures/gui/campfire.png");
    private static final Identifier FLAME = Identifier.withDefaultNamespace("container/furnace/lit_progress");
    private static final String[] SIZES = { "small", "medium", "large" };

    private final List<Button> sizeButtons = new ArrayList<>();
    private Button lightButton;

    public CampfireScreen(CampfireMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 176);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        sizeButtons.clear();
        for (int i = 0; i < 3; i++) {
            int id = i;
            Button b = Button.builder(Component.translatable("gui.realisticsurvival.campfire." + SIZES[i]), btn -> press(id))
                    .bounds(leftPos + 116, topPos + 16 + i * 16, 52, 14)
                    .tooltip(Tooltip.create(Component.translatable("gui.realisticsurvival.campfire." + SIZES[i] + ".tip")))
                    .build();
            sizeButtons.add(addRenderableWidget(b));
        }
        lightButton = addRenderableWidget(Button.builder(Component.translatable("gui.realisticsurvival.campfire.light"), btn -> press(CampfireMenu.BUTTON_LIGHT))
                .bounds(leftPos + 116, topPos + 64, 52, 14)
                .tooltip(Tooltip.create(Component.translatable("gui.realisticsurvival.campfire.light.tip")))
                .build());
        updateButtons();
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private void updateButtons() {
        int size = menu.get(CampfireMenu.DATA_SIZE);
        for (int i = 0; i < sizeButtons.size(); i++) sizeButtons.get(i).active = size != i + 1;
        if (lightButton != null) lightButton.active = menu.get(CampfireMenu.DATA_LIT) == 0 && menu.get(CampfireMenu.DATA_STARTER) == 1;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButtons();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        int fx = leftPos + 10, fy = topPos + 36;
        if (menu.get(CampfireMenu.DATA_LIT) == 1) {
            int h = Mth.ceil(menu.get(CampfireMenu.DATA_BURN_PCT) / 100.0F * 13.0F) + 1;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, FLAME, 14, 14, 0, 14 - h, fx, fy + 14 - h, 14, h);
        } else if (menu.get(CampfireMenu.DATA_EMBERS) > 0) {
            // glowing coals
            g.fill(fx + 2, fy + 11, fx + 12, fy + 14, 0xFF8A2A0A);
            g.fill(fx + 4, fy + 11, fx + 6, fy + 13, 0xFFFF8A1E);
            g.fill(fx + 8, fy + 12, fx + 10, fy + 14, 0xFFE8601A);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        int grey = 0xFF404040;
        g.text(font, Component.translatable("gui.realisticsurvival.campfire.kindling"), 30, 21, grey, false);
        g.text(font, Component.translatable("gui.realisticsurvival.campfire.fuel"), 30, 57, grey, false);

        int seconds = menu.get(CampfireMenu.DATA_SECONDS);
        Component status;
        Component detail = null;
        int colour;
        if (menu.get(CampfireMenu.DATA_LIT) == 1) {
            status = Component.translatable("gui.realisticsurvival.campfire.burning");
            detail = Component.translatable("gui.realisticsurvival.campfire.left", time(seconds));
            colour = 0xFFC2410C;
        } else if (menu.get(CampfireMenu.DATA_EMBERS) > 0) {
            status = Component.translatable("gui.realisticsurvival.campfire.embers");
            detail = Component.translatable("gui.realisticsurvival.campfire.embers_left", time(menu.get(CampfireMenu.DATA_EMBERS)));
            colour = 0xFF9A3412;
        } else {
            status = Component.translatable("gui.realisticsurvival.campfire.out");
            if (seconds > 0) detail = Component.translatable("gui.realisticsurvival.campfire.fuel_for", time(seconds));
            colour = 0xFF555555;
        }
        g.text(font, status, 30, 34, colour, false);
        if (detail != null) g.text(font, detail, 30, 44, grey, false);
        if (menu.get(CampfireMenu.DATA_RAIN) == 1) {
            g.text(font, Component.translatable("gui.realisticsurvival.campfire.rain"), 30, 70, 0xFF1D4ED8, false);
        }
    }

    private static String time(int seconds) {
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < 2 && menu.getCarried().isEmpty()) {
            String key = hoveredSlot.index == 0 ? "kindling" : "fuel";
            g.setTooltipForNextFrame(font.split(Component.translatable("gui.realisticsurvival.campfire." + key + ".tip"), 180), mouseX, mouseY);
        }
    }
}
