package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.block.OvenMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** The brick oven's workbench: the crafting table layout. */
public class OvenScreen extends AbstractContainerScreen<OvenMenu> {
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");

    public OvenScreen(OvenMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.titleLabelX = 29;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
    }
}
