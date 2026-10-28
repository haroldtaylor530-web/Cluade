package com.harold.fieldkit.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.harold.fieldkit.console.LogWatch;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

/** Little cards that slide in at the bottom right: errors, lag, mentions, load times. */
public final class Notifications {
    public enum Kind {
        INFO(0xFF7FB3E0, "i"), WARN(0xFFE0B040, "!"), ERROR(0xFFE05A4A, "×"), CHAT(0xFFB8D47A, "@"), DANGER(0xFFFF5040, "⚠");

        final int color;
        final String icon;

        Kind(int color, String icon) {
            this.color = color;
            this.icon = icon;
        }
    }

    private record Note(Kind kind, String text, long born, long life) {
    }

    private static final Deque<Note> NOTES = new ArrayDeque<>();
    private static final List<Note> PENDING = new ArrayList<>();

    private Notifications() {
    }

    public static void info(String text) {
        add(Kind.INFO, text, 6000, false);
    }

    public static void warn(String text) {
        add(Kind.WARN, text, 7000, false);
    }

    public static void error(String text) {
        add(Kind.ERROR, text, 8000, false);
    }

    public static void chat(String text) {
        add(Kind.CHAT, text, 7000, true);
    }

    public static void danger(String text) {
        add(Kind.DANGER, text, 4000, true);
    }

    /** Thread safe: the log watcher calls this from its own thread. */
    public static void add(Kind kind, String text, long life, boolean ping) {
        synchronized (PENDING) {
            PENDING.add(new Note(kind, text, 0, life));
        }
        LogWatch.recordNotification(kind.name(), text);
        if (ping) {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6F, 0.6F)));
        }
    }

    public static void tick() {
        synchronized (PENDING) {
            long now = Util.getMillis();
            for (Note n : PENDING) NOTES.addFirst(new Note(n.kind, n.text, now, n.life));
            PENDING.clear();
        }
        while (NOTES.size() > 5) NOTES.removeLast();
        NOTES.removeIf(n -> Util.getMillis() - n.born > n.life);
    }

    public static void render(GuiGraphics g, Minecraft mc) {
        if (NOTES.isEmpty()) return;
        Font font = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        int w = Math.min(200, sw / 3);
        int y = Math.max(40, sh * 2 / 5);
        long now = Util.getMillis();
        for (Note n : NOTES) {
            List<FormattedCharSequence> lines = font.split(Component.literal(n.text), w - 22);
            if (lines.size() > 3) lines = lines.subList(0, 3);
            int h = 8 + lines.size() * 10;
            long age = now - n.born;
            float in = Math.min(1F, age / 250F), out = Math.min(1F, (n.life - age) / 400F);
            float a = Math.max(0, Math.min(in, out));
            int slide = (int) ((1 - in) * (w + 8));
            int x = sw - w - 6 + slide;
            if (y + h > sh - 30) break;
            g.fill(x, y, x + w, y + h, alpha(0xE0101016, a));
            g.fill(x, y, x + 2, y + h, alpha(n.kind.color, a));
            if (a > 0.1F) {
                g.drawString(font, n.kind.icon, x + 7, y + 4, alpha(n.kind.color, a), false);
                int ly = y + 4;
                for (FormattedCharSequence l : lines) {
                    g.drawString(font, l, x + 17, ly, alpha(0xFFE8E4DA, a), false);
                    ly += 10;
                }
            }
            y += h + 4;
        }
    }

    public static int alpha(int argb, float a) {
        int al = (int) (((argb >>> 24) & 255) * Math.max(0, Math.min(1, a)));
        return (al << 24) | (argb & 0xFFFFFF);
    }
}
