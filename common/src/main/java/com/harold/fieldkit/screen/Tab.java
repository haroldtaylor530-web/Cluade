package com.harold.fieldkit.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** One tab of the Fieldkit screen. */
public abstract class Tab {
    protected FieldkitScreen screen;
    protected Minecraft mc;
    protected Font font;
    protected int x, y, w, h;

    abstract String title();

    void init(FieldkitScreen screen, int x, int y, int w, int h) {
        this.screen = screen;
        this.mc = Minecraft.getInstance();
        this.font = mc.font;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        build();
    }

    /** Create this tab's widgets with screen.add(...). */
    void build() {
    }

    abstract void render(GuiGraphics g, int mx, int my, float pt);

    /** Drawn after the widgets (dropdowns, tooltips). */
    void renderOver(GuiGraphics g, int mx, int my, float pt) {
    }

    boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return false;
    }

    boolean mouseReleased(double mx, double my, int button) {
        return false;
    }

    boolean scrolled(double mx, double my, double delta) {
        return false;
    }

    boolean keyPressed(int key, int scan, int mods) {
        return false;
    }

    void tick() {
    }

    void removed() {
    }

    boolean inside(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    static void panel(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, 0xC0141418);
        g.fill(x1, y1, x2, y1 + 1, 0xFF3A3842);
        g.fill(x1, y2 - 1, x2, y2, 0xFF26242C);
        g.fill(x1, y1, x1 + 1, y2, 0xFF2E2C36);
        g.fill(x2 - 1, y1, x2, y2, 0xFF2E2C36);
    }
}
