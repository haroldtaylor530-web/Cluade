package com.harold.fieldkit.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** The M screen: Map, Seed finder, Console, Combat, Info, Settings. */
public class FieldkitScreen extends Screen {
    private static int lastTab;
    private final List<Tab> tabs = new ArrayList<>();
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private Tab tab;

    public FieldkitScreen() {
        super(Component.literal("Fieldkit"));
        tabs.add(new MapTab());
        tabs.add(new SeedTab());
        tabs.add(new ConsoleTab());
        tabs.add(new CombatTab());
        tabs.add(new InfoTab());
        tabs.add(new SettingsTab());
    }

    public static void openTab(int i) {
        lastTab = i;
    }

    public void switchTo(int i) {
        if (tab != null) tab.removed();
        lastTab = i;
        rebuild();
    }

    @Override
    protected void init() {
        rebuild();
    }

    public void rebuild() {
        clearWidgets();
        widgets.clear();
        tab = tabs.get(Math.max(0, Math.min(tabs.size() - 1, lastTab)));
        tab.init(this, 8, 26, width - 16, height - 34);
    }

    public <T extends AbstractWidget> T add(T w) {
        addWidget(w);
        widgets.add(w);
        return w;
    }

    public void remove(AbstractWidget w) {
        removeWidget(w);
        widgets.remove(w);
    }

    @Override
    public void tick() {
        if (tab != null) tab.tick();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xF00C0C10, 0xF0141218);
        // tabs
        int tx = 8;
        for (int i = 0; i < tabs.size(); i++) {
            String t = tabs.get(i).title();
            int tw = font.width(t) + 16;
            boolean sel = tabs.get(i) == tab;
            boolean hover = mx >= tx && mx < tx + tw && my >= 4 && my < 22;
            g.fill(tx, 4, tx + tw, 22, sel ? 0xFF2A3320 : hover ? 0xFF24222A : 0xFF18171C);
            if (sel) g.fill(tx, 20, tx + tw, 22, 0xFFB8D47A);
            g.drawString(font, t, tx + 8, 9, sel ? 0xFFFFFFFF : 0xFFB0ACA4, false);
            tx += tw + 3;
        }
        String hint = "M or Esc to close";
        g.drawString(font, hint, width - font.width(hint) - 8, 9, 0xFF6A6660, false);
        if (tab != null) tab.render(g, mx, my, pt);
        for (AbstractWidget w : widgets) w.render(g, mx, my, pt);
        if (tab != null) tab.renderOver(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int tx = 8;
        for (int i = 0; i < tabs.size(); i++) {
            int tw = font.width(tabs.get(i).title()) + 16;
            if (mx >= tx && mx < tx + tw && my >= 4 && my < 22) {
                if (tabs.get(i) != tab) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1F));
                    switchTo(i);
                }
                return true;
            }
            tx += tw + 3;
        }
        if (tab != null && tab.mouseClicked(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (tab != null && tab.mouseDragged(mx, my, button, dx, dy)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (tab != null && tab.mouseReleased(mx, my, button)) return true;
        return super.mouseReleased(mx, my, button);
    }

    /** 1.20.1 */
    public boolean mouseScrolled(double mx, double my, double delta) {
        return tab != null && tab.scrolled(mx, my, delta);
    }

    /** 1.20.2 and later */
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        return tab != null && tab.scrolled(mx, my, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (getFocused() instanceof net.minecraft.client.gui.components.EditBox eb && eb.isFocused()) {
            if (key == 256) {
                eb.setFocused(false);
                setFocused(null);
                return true;
            }
            return super.keyPressed(key, scan, mods);
        }
        if (tab != null && tab.keyPressed(key, scan, mods)) return true;
        if (com.harold.fieldkit.Fieldkit.OPEN.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void removed() {
        if (tab != null) tab.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
