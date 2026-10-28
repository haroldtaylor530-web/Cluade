package com.harold.fieldkit.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.harold.fieldkit.Fieldkit;
import com.harold.fieldkit.console.LogWatch;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** The game log, filtered and searchable, with an analysis of what's going wrong and live performance numbers. */
class ConsoleTab extends Tab {
    private static int filter; // 0 all, 1 errors, 2 warnings, 3 chat, 4 fieldkit
    private static final String[] FILTERS = { "All", "Errors", "Warnings", "Chat", "Fieldkit" };
    private static String query = "";
    private int scroll; // lines up from the bottom
    private List<LogWatch.Line> shown = new ArrayList<>();
    private int analysisScroll;

    @Override
    String title() {
        return "Console";
    }

    private int leftW() {
        return Math.min(240, w * 2 / 5);
    }

    @Override
    void build() {
        int bx = x + leftW() + 8;
        for (int i = 0; i < FILTERS.length; i++) {
            final int f = i;
            Button b = screen.add(Button.builder(Component.literal(FILTERS[i]), btn -> {
                filter = f;
                scroll = 0;
                screen.rebuild();
            }).bounds(bx, y + 2, font.width(FILTERS[i]) + 12, 16).build());
            b.active = filter != i;
            bx += font.width(FILTERS[i]) + 14;
        }
        EditBox search = screen.add(new EditBox(font, bx + 4, y + 2, Math.max(60, x + w - bx - 100), 16, Component.literal("search")));
        search.setHint(Component.literal("search"));
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            scroll = 0;
        });
        screen.add(Button.builder(Component.literal("Open logs"), b -> Util.getPlatform().openFile(
                Fieldkit.platform.gameDir().resolve("logs").toFile())).bounds(x + w - 88, y + 2, 86, 16).build());
    }

    @Override
    void render(GuiGraphics g, int mx, int my, float pt) {
        int lw = leftW();
        panel(g, x, y, x + lw, y + h);
        analysis(g, x + 6, y + 6, lw - 12, h - 12);
        int lx = x + lw + 6, ly = y + 22, lwid = w - lw - 6, lh = h - 22;
        panel(g, lx, ly, lx + lwid, ly + lh);
        List<LogWatch.Line> all = LogWatch.lines();
        List<LogWatch.Line> list = new ArrayList<>();
        String q = query.toLowerCase(Locale.ROOT);
        for (LogWatch.Line l : all) {
            boolean ok = switch (filter) {
                case 1 -> l.level() == LogWatch.Level.ERROR;
                case 2 -> l.level() == LogWatch.Level.WARN;
                case 3 -> l.level() == LogWatch.Level.CHAT;
                case 4 -> l.level() == LogWatch.Level.NOTE;
                default -> true;
            };
            if (ok && (q.isEmpty() || l.text().toLowerCase(Locale.ROOT).contains(q) || l.source().toLowerCase(Locale.ROOT).contains(q))) list.add(l);
        }
        shown = list;
        int rows = (lh - 6) / 10;
        int end = Math.max(0, list.size() - scroll);
        int start = Math.max(0, end - rows);
        g.enableScissor(lx + 1, ly + 1, lx + lwid - 1, ly + lh - 1);
        int yy = ly + 3;
        for (int i = start; i < end; i++) {
            LogWatch.Line l = list.get(i);
            int col = switch (l.level()) {
                case ERROR -> 0xFFFF6A5A;
                case WARN -> 0xFFE8C040;
                case CHAT -> 0xFFB8D47A;
                case NOTE -> 0xFF7FB3E0;
                default -> 0xFFC8C4BA;
            };
            boolean hover = mx >= lx && mx < lx + lwid && my >= yy && my < yy + 10;
            if (hover) g.fill(lx + 1, yy - 1, lx + lwid - 1, yy + 9, 0x30FFFFFF);
            String head = l.time() + " ";
            g.drawString(font, head, lx + 4, yy, 0xFF6A6660, false);
            int hx = lx + 4 + font.width(head);
            String src = l.text().startsWith("    ") ? "" : "[" + l.source() + "] ";
            g.drawString(font, src, hx, yy, 0xFF8A867E, false);
            g.drawString(font, l.text(), hx + font.width(src), yy, col, false);
            yy += 10;
        }
        g.disableScissor();
        String foot = list.size() + " lines" + (scroll > 0 ? " · scrolled up (scroll down to follow)" : " · following") + " · click a line to copy it";
        g.drawString(font, foot, lx + 4, ly + lh + 0 - 10 > ly ? ly + lh - 11 : ly, 0xFF6A6660, false);
    }

    private void analysis(GuiGraphics g, int ax, int ay, int aw, int ah) {
        g.enableScissor(ax - 2, ay - 2, ax + aw + 2, ay + ah + 2);
        int yy = ay - analysisScroll;
        g.drawString(font, "Right now", ax, yy, 0xFFFFFFFF, false);
        yy += 12;
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) >> 20, max = rt.maxMemory() >> 20;
        yy = line(g, ax, yy, "FPS " + mc.getFps() + "   Memory " + used + " / " + max + " MB (" + (used * 100 / Math.max(1, max)) + "%)",
                used > max * 0.9 ? 0xFFFF6A5A : 0xFFC8C4BA);
        float mspt = Fieldkit.platform.averageTickMs();
        if (mspt >= 0) {
            float tps = Math.min(20F, 1000F / Math.max(mspt, 0.001F));
            yy = line(g, ax, yy, String.format("Server %.1f ms/tick · %.1f TPS", mspt, tps), mspt > 50 ? 0xFFFF6A5A : 0xFFC8C4BA);
        }
        if (mc.level != null) yy = line(g, ax, yy, "Entities " + mc.level.getEntityCount() + "   Errors " + LogWatch.errorCount()
                + "   Warnings " + LogWatch.warnCount(), 0xFFC8C4BA);
        yy += 6;
        g.drawString(font, "What the log says", ax, yy, 0xFFFFFFFF, false);
        yy += 12;
        List<LogWatch.Finding> fs = LogWatch.findings();
        if (fs.isEmpty()) yy = line(g, ax, yy, "Nothing worrying found.", 0xFF7CDC5C);
        for (LogWatch.Finding f : fs) {
            yy = line(g, ax, yy, "• " + f.title() + "  ×" + f.count(), f.title().startsWith("Other") ? 0xFFE8C040 : 0xFFFF9A7A);
            for (FormattedCharSequence s : font.split(Component.literal(f.hint()), aw - 8)) {
                g.drawString(font, s, ax + 8, yy, 0xFF8A867E, false);
                yy += 10;
            }
            yy += 3;
        }
        yy += 4;
        List<Object[]> noisy = LogWatch.noisiest(6);
        if (!noisy.isEmpty()) {
            g.drawString(font, "Noisiest sources", ax, yy, 0xFFFFFFFF, false);
            yy += 12;
            for (Object[] o : noisy) {
                yy = line(g, ax, yy, o[0] + "  " + o[1] + " err, " + o[2] + " warn", 0xFFC8C4BA);
            }
        }
        g.disableScissor();
        maxAnalysis = Math.max(0, yy + analysisScroll - ay - ah);
    }

    private int maxAnalysis;

    private int line(GuiGraphics g, int ax, int yy, String s, int col) {
        g.drawString(font, font.plainSubstrByWidth(s, leftW() - 12), ax, yy, col, false);
        return yy + 10;
    }

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        int lw = leftW();
        int lx = x + lw + 6, ly = y + 22, lwid = w - lw - 6, lh = h - 22;
        if (mx >= lx && mx < lx + lwid && my >= ly && my < ly + lh) {
            int rows = (lh - 6) / 10;
            int end = Math.max(0, shown.size() - scroll);
            int start = Math.max(0, end - rows);
            int i = start + (int) ((my - ly - 3) / 10);
            if (i >= start && i < end) {
                LogWatch.Line l = shown.get(i);
                mc.keyboardHandler.setClipboard("[" + l.time() + "] [" + l.source() + "] " + l.text());
                return true;
            }
        }
        return false;
    }

    @Override
    boolean scrolled(double mx, double my, double delta) {
        if (mx < x + leftW()) {
            analysisScroll = Math.max(0, Math.min(maxAnalysis, analysisScroll - (int) (delta * 20)));
            return true;
        }
        scroll = Math.max(0, Math.min(Math.max(0, shown.size() - 5), scroll + (int) (delta * 3)));
        return true;
    }
}
