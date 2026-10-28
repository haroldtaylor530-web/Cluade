package com.harold.fieldkit.screen;

import com.harold.fieldkit.hud.CombatTracker;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** This session's fights: totals and a log of kills, deaths and big hits. */
class CombatTab extends Tab {
    @Override
    String title() {
        return "Combat";
    }

    @Override
    void build() {
        screen.add(Button.builder(Component.literal("Reset session"), b -> CombatTracker.resetSession())
                .bounds(x + 8, y + h - 26, 100, 20).build());
    }

    @Override
    void render(GuiGraphics g, int mx, int my, float pt) {
        int half = Math.min(260, w / 2);
        panel(g, x, y, x + half, y + h);
        panel(g, x + half + 6, y, x + w, y + h);
        int yy = y + 8;
        g.drawString(font, "This session", x + 8, yy, 0xFFFFFFFF, false);
        yy += 16;
        float kd = CombatTracker.deaths == 0 ? CombatTracker.kills : (float) CombatTracker.kills / CombatTracker.deaths;
        String[][] rows = {
                { "Kills", CombatTracker.kills + (CombatTracker.playerKills > 0 ? "  (" + CombatTracker.playerKills + " players)" : "") },
                { "Deaths", String.valueOf(CombatTracker.deaths) },
                { "K/D", String.format("%.2f", kd) },
                { "Hits landed", String.valueOf(CombatTracker.hits) },
                { "Damage dealt", String.format("%.1f  (%.1f hearts)", CombatTracker.dealt, CombatTracker.dealt / 2) },
                { "Damage taken", String.format("%.1f  (%.1f hearts)", CombatTracker.taken, CombatTracker.taken / 2) },
                { "Biggest hit", String.format("%.1f", CombatTracker.biggestHit) },
                { "Clicks/sec now", CombatTracker.cpsLeft() + " left, " + CombatTracker.cpsRight() + " right" } };
        for (String[] r : rows) {
            g.drawString(font, r[0], x + 8, yy, 0xFF8A867E, false);
            g.drawString(font, r[1], x + 110, yy, 0xFFE8E4DA, false);
            yy += 13;
        }
        int ex = x + half + 14;
        yy = y + 8;
        g.drawString(font, "Fight log", ex, yy, 0xFFFFFFFF, false);
        yy += 16;
        if (CombatTracker.LOG.isEmpty()) g.drawString(font, "Nothing yet. Kills, deaths and big hits show up here.", ex, yy, 0xFF8A867E, false);
        long now = Util.getMillis();
        for (CombatTracker.Event e : CombatTracker.LOG) {
            if (yy > y + h - 12) break;
            long s = (now - e.time()) / 1000;
            String ago = s < 60 ? s + "s ago" : s / 60 + "m ago";
            g.drawString(font, ago, ex, yy, 0xFF6A6660, false);
            g.drawString(font, e.text(), ex + 50, yy, e.text().startsWith("Killed") ? 0xFF7CDC5C
                    : e.text().startsWith("Died") ? 0xFFFF6A5A : 0xFFE8C040, false);
            yy += 11;
        }
    }
}
