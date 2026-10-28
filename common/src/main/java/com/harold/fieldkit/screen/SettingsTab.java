package com.harold.fieldkit.screen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.harold.fieldkit.Config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Switches for everything, saved to config/fieldkit.json. */
class SettingsTab extends Tab {
    private record Toggle(String label, Supplier<Boolean> get, Consumer<Boolean> set) {
    }

    private record Number(String label, IntSupplier get, IntConsumer set, int min, int max, int step) {
    }

    private final List<String> headers = new ArrayList<>();
    private final List<int[]> headerPos = new ArrayList<>();

    @Override
    String title() {
        return "Settings";
    }

    @Override
    void build() {
        Config c = Config.INSTANCE;
        headers.clear();
        headerPos.clear();
        int colW = Math.min(200, (w - 20) / 3);
        int[] colX = { x + 8, x + 8 + colW + 6, x + 8 + (colW + 6) * 2 };
        int[] colY = { y + 18, y + 18, y + 18 };

        section(0, colX, colY, "HUD");
        toggle(0, colX, colY, colW, new Toggle("Show the HUD", () -> c.hud, v -> c.hud = v));
        toggle(0, colX, colY, colW, new Toggle("Minimap", () -> c.minimap, v -> c.minimap = v));
        number(0, colX, colY, colW, new Number("Minimap size", () -> c.minimapSize, v -> c.minimapSize = v, 48, 200, 8));
        number(0, colX, colY, colW, new Number("Blocks per pixel", () -> c.minimapZoom, v -> c.minimapZoom = v, 1, 8, 1));
        toggle(0, colX, colY, colW, new Toggle("Terrain shading", () -> c.minimapShading, v -> c.minimapShading = v));
        toggle(0, colX, colY, colW, new Toggle("Mobs on minimap", () -> c.minimapEntities, v -> c.minimapEntities = v));
        toggle(0, colX, colY, colW, new Toggle("Coords & biome", () -> c.coordsLine, v -> c.coordsLine = v));

        section(1, colX, colY, "Warnings");
        toggle(1, colX, colY, colW, new Toggle("Mobs closing in", () -> c.warnMobs, v -> c.warnMobs = v));
        number(1, colX, colY, colW, new Number("Mob range", () -> c.mobRadius, v -> c.mobRadius = v, 4, 64, 4));
        toggle(1, colX, colY, colW, new Toggle("Players nearby", () -> c.warnPlayers, v -> c.warnPlayers = v));
        number(1, colX, colY, colW, new Number("Player range", () -> c.playerRadius, v -> c.playerRadius = v, 8, 128, 8));
        toggle(1, colX, colY, colW, new Toggle("Warning sounds", () -> c.warnSound, v -> c.warnSound = v));
        section(1, colX, colY, "Combat");
        toggle(1, colX, colY, colW, new Toggle("Combat panel", () -> c.combatHud, v -> c.combatHud = v));
        toggle(1, colX, colY, colW, new Toggle("Target info", () -> c.targetInfo, v -> c.targetInfo = v));
        toggle(1, colX, colY, colW, new Toggle("Fight log line", () -> c.fightLog, v -> c.fightLog = v));
        toggle(1, colX, colY, colW, new Toggle("Effect timers", () -> c.effectTimers, v -> c.effectTimers = v));

        section(2, colX, colY, "Console & alerts");
        toggle(2, colX, colY, colW, new Toggle("Errors in the log", () -> c.notifyErrors, v -> c.notifyErrors = v));
        toggle(2, colX, colY, colW, new Toggle("Server lag", () -> c.notifyLag, v -> c.notifyLag = v));
        toggle(2, colX, colY, colW, new Toggle("Low memory", () -> c.notifyMemory, v -> c.notifyMemory = v));
        toggle(2, colX, colY, colW, new Toggle("FPS drops", () -> c.notifyFps, v -> c.notifyFps = v));
        toggle(2, colX, colY, colW, new Toggle("Load times", () -> c.loadInfo, v -> c.loadInfo = v));
        toggle(2, colX, colY, colW, new Toggle("Server messages", () -> c.serverMessages, v -> c.serverMessages = v));
        toggle(2, colX, colY, colW, new Toggle("Chat mentions", () -> c.chatMentions, v -> c.chatMentions = v));
        headers.add("Chat keywords (comma separated)");
        headerPos.add(new int[] { colX[2], colY[2] + 4 });
        colY[2] += 16;
        EditBox kw = screen.add(new EditBox(font, colX[2], colY[2], colW, 18, Component.literal("keywords")));
        kw.setMaxLength(300);
        kw.setValue(String.join(", ", c.chatKeywords));
        kw.setHint(Component.literal("e.g. diamonds, help, base"));
        kw.setResponder(s -> {
            List<String> list = new ArrayList<>();
            for (String p : Arrays.asList(s.split(","))) if (!p.isBlank()) list.add(p.trim());
            c.chatKeywords = list;
            Config.save();
        });
    }

    private void section(int col, int[] colX, int[] colY, String title) {
        headers.add(title);
        headerPos.add(new int[] { colX[col], colY[col] - 12 + (colY[col] > y + 18 ? 8 : 0) });
        if (colY[col] > y + 18) colY[col] += 8;
    }

    private void toggle(int col, int[] colX, int[] colY, int colW, Toggle t) {
        screen.add(Button.builder(label(t), b -> {
            t.set.accept(!t.get.get());
            Config.save();
            b.setMessage(label(t));
        }).bounds(colX[col], colY[col], colW, 18).build());
        colY[col] += 20;
    }

    private static Component label(Toggle t) {
        return Component.literal(t.label + ": " + (t.get.get() ? "§aon" : "§7off"));
    }

    private void number(int col, int[] colX, int[] colY, int colW, Number n) {
        Button value = Button.builder(Component.literal(n.label + ": " + n.get.getAsInt()), b -> {
        }).bounds(colX[col] + 20, colY[col], colW - 40, 18).build();
        value.active = false;
        screen.add(Button.builder(Component.literal("-"), b -> {
            n.set.accept(Math.max(n.min, n.get.getAsInt() - n.step));
            Config.save();
            value.setMessage(Component.literal(n.label + ": " + n.get.getAsInt()));
        }).bounds(colX[col], colY[col], 18, 18).build());
        screen.add(value);
        screen.add(Button.builder(Component.literal("+"), b -> {
            n.set.accept(Math.min(n.max, n.get.getAsInt() + n.step));
            Config.save();
            value.setMessage(Component.literal(n.label + ": " + n.get.getAsInt()));
        }).bounds(colX[col] + colW - 18, colY[col], 18, 18).build());
        colY[col] += 20;
    }

    @Override
    void render(GuiGraphics g, int mx, int my, float pt) {
        panel(g, x, y, x + w, y + h);
        for (int i = 0; i < headers.size(); i++) {
            int[] p = headerPos.get(i);
            g.drawString(font, headers.get(i), p[0], p[1], 0xFFB8D47A, false);
        }
        String foot = "M opens this screen; a key to hide/show the HUD can be set in Controls › Fieldkit.";
        g.drawString(font, foot, x + 8, y + h - 12, 0xFF6A6660, false);
    }
}
