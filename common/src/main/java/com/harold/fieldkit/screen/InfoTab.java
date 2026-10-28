package com.harold.fieldkit.screen;

import java.util.ArrayList;
import java.util.List;

import com.harold.fieldkit.Fieldkit;
import com.harold.fieldkit.console.LogWatch;
import com.mojang.blaze3d.platform.GlUtil;

import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;

/** Your setup and this session at a glance. */
class InfoTab extends Tab {
    @Override
    String title() {
        return "Info";
    }

    @Override
    void render(GuiGraphics g, int mx, int my, float pt) {
        panel(g, x, y, x + w, y + h);
        List<String[]> rows = new ArrayList<>();
        Runtime rt = Runtime.getRuntime();
        rows.add(new String[] { "Game", "Minecraft " + SharedConstants.getCurrentVersion().getName() + " · "
                + Fieldkit.platform.loader() + " " + Fieldkit.platform.loaderVersion() + " · " + Fieldkit.platform.modCount() + " mods" });
        rows.add(new String[] { "Java", System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")" });
        rows.add(new String[] { "Memory", ((rt.totalMemory() - rt.freeMemory()) >> 20) + " MB used · " + (rt.totalMemory() >> 20)
                + " MB allocated · " + (rt.maxMemory() >> 20) + " MB max" });
        rows.add(new String[] { "CPU", rt.availableProcessors() + " threads · " + System.getProperty("os.name") + " "
                + System.getProperty("os.arch") });
        rows.add(new String[] { "Graphics", GlUtil.getRenderer() });
        rows.add(new String[] { "", GlUtil.getVendor() + " · OpenGL " + GlUtil.getOpenGLVersion() });
        rows.add(new String[] { "Display", mc.getWindow().getWidth() + "×" + mc.getWindow().getHeight() + " · GUI scale "
                + (int) mc.getWindow().getGuiScale() + " · " + mc.getFps() + " FPS · render distance "
                + mc.options.renderDistance().get() });
        if (Fieldkit.titleShown > 0) rows.add(new String[] { "Startup", "Game took " + Fieldkit.secs(Fieldkit.titleShown
                - LogWatch.firstLineMillis(Fieldkit.STARTED)) + " to reach the title screen" });
        rows.add(new String[] { "Log", LogWatch.errorCount() + " errors · " + LogWatch.warnCount() + " warnings since launch" });
        if (mc.getSingleplayerServer() != null) {
            var s = mc.getSingleplayerServer();
            rows.add(new String[] { "World", s.getWorldData().getLevelName() + " · seed " + s.overworld().getSeed() });
        } else if (mc.getCurrentServer() != null) {
            rows.add(new String[] { "Server", mc.getCurrentServer().name + " (" + mc.getCurrentServer().ip + ")" });
        }
        if (mc.player != null) {
            rows.add(new String[] { "Position", mc.player.getBlockX() + ", " + mc.player.getBlockY() + ", " + mc.player.getBlockZ()
                    + " in " + mc.level.dimension().location() });
            rows.add(new String[] { "Day", "Day " + (mc.level.getDayTime() / 24000 + 1) });
        }
        int yy = y + 10;
        for (String[] r : rows) {
            g.drawString(font, r[0], x + 10, yy, 0xFF8A867E, false);
            g.drawString(font, font.plainSubstrByWidth(r[1], w - 100), x + 80, yy, 0xFFE8E4DA, false);
            yy += 13;
        }
        if (mc.getSingleplayerServer() != null) {
            yy += 6;
            g.drawString(font, "Click the seed line to copy the seed.", x + 10, yy, 0xFF6A6660, false);
        }
    }

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        if (mc.getSingleplayerServer() != null && mx > x + 70 && my >= y && my < y + h) {
            // the World row
            int row = (int) ((my - y - 10) / 13);
            int worldRow = Fieldkit.titleShown > 0 ? 9 : 8;
            if (row == worldRow) {
                mc.keyboardHandler.setClipboard(String.valueOf(mc.getSingleplayerServer().overworld().getSeed()));
                com.harold.fieldkit.hud.Notifications.info("Copied the world seed");
                return true;
            }
        }
        return false;
    }
}
