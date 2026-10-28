package com.harold.fieldkit.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.harold.fieldkit.map.BiomeColors;
import com.harold.fieldkit.map.Gen;
import com.harold.fieldkit.map.MapTexture;
import com.harold.fieldkit.seed.Criteria;
import com.harold.fieldkit.seed.SeedSearch;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * The seed finder: say what you want near the world origin and it searches seeds, using the game's own world
 * generation with this world's settings (so it understands your modded biomes and structures).
 */
class SeedTab extends Tab {
    private static final Criteria CRITERIA = new Criteria();
    static SeedSearch search;
    private static final Map<Long, MapTexture> PREVIEWS = new HashMap<>();
    private static final int MAX_RULES = 5;

    private int picker; // 0 none, 1 biome, 2 structure
    private int pickerIndex;
    private EditBox pickerFilter;
    private int pickerScroll;
    private List<Object[]> pickerItems = new ArrayList<>(); // [name, key]
    private int resultsScroll;
    private Button searchButton;

    @Override
    String title() {
        return "Seed finder";
    }

    private int leftW() {
        return Math.min(330, Math.max(230, w / 2));
    }

    /** Width of the biome/structure picker buttons. */
    private int pickW() {
        return leftW() - 6 - 3 - 42 - 3 - 60 - 3 - 18 - 4;
    }

    @Override
    void build() {
        Gen gen = Gen.current();
        if (gen == null) return;
        int lx = x + 6, ly = y + 18;
        screen.add(Button.builder(Component.literal("+ Biome"), b -> {
            if (CRITERIA.biomes.size() < MAX_RULES) CRITERIA.biomes.add(new Criteria.BiomeRule());
            screen.rebuild();
        }).bounds(lx + leftW() - 70, y + 2, 60, 14).build());
        for (int i = 0; i < CRITERIA.biomes.size(); i++) {
            Criteria.BiomeRule r = CRITERIA.biomes.get(i);
            final int idx = i;
            int ry = ly + i * 22;
            screen.add(Button.builder(Component.literal(r.biome == null ? "Pick a biome…" : BiomeColors.pretty(r.biome)),
                    b -> openPicker(1, idx)).bounds(lx, ry, pickW(), 20).build());
            int ex = lx + pickW() + 3;
            EditBox rad = screen.add(new EditBox(font, ex, ry + 1, 42, 18, Component.literal("radius")));
            rad.setValue(String.valueOf(r.radius));
            rad.setResponder(s -> r.radius = clampInt(s, 16, 8000, r.radius));
            screen.add(Button.builder(Component.literal(r.must ? "Must" : "Avoid"), b -> {
                r.must = !r.must;
                b.setMessage(Component.literal(r.must ? "Must" : "Avoid"));
            }).bounds(ex + 45, ry, 60, 20).build());
            screen.add(Button.builder(Component.literal("×"), b -> {
                CRITERIA.biomes.remove(idx);
                screen.rebuild();
            }).bounds(ex + 108, ry, 18, 20).build());
        }
        int sy = ly + CRITERIA.biomes.size() * 22 + 20;
        screen.add(Button.builder(Component.literal("+ Structure"), b -> {
            if (CRITERIA.structs.size() < MAX_RULES) CRITERIA.structs.add(new Criteria.StructRule());
            screen.rebuild();
        }).bounds(lx + leftW() - 86, sy - 16, 76, 14).build());
        for (int i = 0; i < CRITERIA.structs.size(); i++) {
            Criteria.StructRule r = CRITERIA.structs.get(i);
            final int idx = i;
            int ry = sy + i * 22;
            screen.add(Button.builder(Component.literal(r.structure == null ? "Pick a structure…" : BiomeColors.pretty(r.structure)),
                    b -> openPicker(2, idx)).bounds(lx, ry, pickW(), 20).build());
            int ex = lx + pickW() + 3;
            EditBox rad = screen.add(new EditBox(font, ex, ry + 1, 42, 18, Component.literal("radius")));
            rad.setValue(String.valueOf(r.radius));
            rad.setResponder(s -> r.radius = clampInt(s, 16, 8000, r.radius));
            EditBox cnt = screen.add(new EditBox(font, ex + 45, ry + 1, 26, 18, Component.literal("count")));
            cnt.setValue(String.valueOf(r.count));
            cnt.setResponder(s -> r.count = clampInt(s, 1, 20, r.count));
            screen.add(Button.builder(Component.literal("×"), b -> {
                CRITERIA.structs.remove(idx);
                screen.rebuild();
            }).bounds(ex + 108, ry, 18, 20).build());
        }
        int by = sy + CRITERIA.structs.size() * 22 + 18;
        EditBox start = screen.add(new EditBox(font, lx + 58, by, leftW() - 70, 18, Component.literal("start seed")));
        start.setValue(CRITERIA.start);
        start.setHint(Component.literal("random"));
        start.setMaxLength(40);
        start.setResponder(s -> CRITERIA.start = s);
        EditBox limit = screen.add(new EditBox(font, lx + 152, by + 25, 26, 18, Component.literal("stop after")));
        limit.setValue(String.valueOf(CRITERIA.limit));
        limit.setResponder(s -> CRITERIA.limit = clampInt(s, 1, 50, CRITERIA.limit));
        searchButton = screen.add(Button.builder(Component.literal(searching() ? "Stop" : "Search"), b -> {
            if (searching()) search.stop();
            else startSearch(gen);
            b.setMessage(Component.literal(searching() ? "Stop" : "Search"));
        }).bounds(lx, by + 24, 80, 20).build());
        if (picker != 0) {
            pickerFilter = screen.add(new EditBox(font, x + w / 2 - 110, y + 24, 220, 18, Component.literal("filter")));
            pickerFilter.setHint(Component.literal("type to filter"));
            pickerFilter.setResponder(s -> {
                pickerScroll = 0;
                fillPicker();
            });
            screen.setFocused(pickerFilter);
            pickerFilter.setFocused(true);
            fillPicker();
        }
    }

    private static boolean searching() {
        return search != null && search.running();
    }

    private void startSearch(Gen gen) {
        Criteria c = CRITERIA.copy();
        if (c.biomes.isEmpty() && c.structs.isEmpty()) return;
        for (MapTexture t : PREVIEWS.values()) t.close();
        PREVIEWS.clear();
        resultsScroll = 0;
        search = new SeedSearch(gen, CRITERIA);
        search.start();
    }

    private static int clampInt(String s, int lo, int hi, int fallback) {
        try {
            return Math.max(lo, Math.min(hi, Integer.parseInt(s.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void openPicker(int kind, int idx) {
        picker = kind;
        pickerIndex = idx;
        pickerScroll = 0;
        screen.rebuild();
    }

    private void fillPicker() {
        Gen gen = Gen.current();
        pickerItems = new ArrayList<>();
        if (gen == null) return;
        String q = pickerFilter == null ? "" : pickerFilter.getValue().toLowerCase(Locale.ROOT).trim();
        TreeMap<String, Object> sorted = new TreeMap<>();
        if (picker == 1) {
            for (Holder<Biome> b : gen.source.possibleBiomes()) {
                b.unwrapKey().ifPresent(k -> sorted.put(BiomeColors.pretty(k) + "  §8" + k.location().getNamespace(), k));
            }
        } else {
            for (Holder<StructureSet> set : gen.sets) {
                for (StructureSet.StructureSelectionEntry e : set.value().structures()) {
                    e.structure().unwrapKey().ifPresent(k -> sorted.put(BiomeColors.pretty(k) + "  §8" + k.location().getNamespace(), k));
                }
            }
        }
        sorted.forEach((name, key) -> {
            if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q)) pickerItems.add(new Object[] { name, key });
        });
    }

    @Override
    void render(GuiGraphics g, int mx, int my, float pt) {
        Gen gen = Gen.current();
        int lw = leftW();
        panel(g, x, y, x + lw + 12, y + h);
        panel(g, x + lw + 16, y, x + w, y + h);
        if (gen == null) {
            String[] msg = { "The seed finder uses this world's own world generation, so it understands",
                    "modded biomes and structures. It works in singleplayer worlds: open one and press M.",
                    "Servers don't share their world generation (or their seed), so it can't run here." };
            for (int i = 0; i < msg.length; i++) g.drawString(font, msg[i], x + 10, y + 12 + i * 12, 0xFFE8E4DA, false);
            return;
        }
        int lx = x + 6;
        g.drawString(font, "Biomes near spawn (0, 0)", lx, y + 5, 0xFFFFFFFF, false);
        int sy = y + 18 + CRITERIA.biomes.size() * 22 + 20;
        g.drawString(font, "Structures", lx, sy - 13, 0xFFFFFFFF, false);
        if (!CRITERIA.structs.isEmpty()) g.drawString(font, "within · count", lx + pickW() + 3, sy - 13, 0xFF8A867E, false);
        int by = sy + CRITERIA.structs.size() * 22 + 18;
        g.drawString(font, "Start seed", lx, by + 5, 0xFFB0ACA4, false);
        g.drawString(font, "Stop after", lx + 98, by + 30, 0xFFB0ACA4, false);
        String status;
        if (search == null) status = CRITERIA.biomes.isEmpty() && CRITERIA.structs.isEmpty() ? "Add a biome or structure, then Search" : "Ready";
        else {
            status = String.format("%s · %,d seeds · %,.0f/s · %d found", searching() ? "Searching" : "Done", search.checked.get(),
                    search.rate(), search.results.size());
        }
        g.drawString(font, font.plainSubstrByWidth(status, lw), lx, by + 50, searching() ? 0xFFB8D47A : 0xFFB0ACA4, false);
        if (search != null && search.error != null) g.drawString(font, "Error: " + search.error, lx, by + 62, 0xFFFF6050, false);
        int ny = by + 76;
        String note = "Uses this world's generator settings (modded biomes too) with each seed. Structure spots are exact; "
                + "biome checks sample the surface. Distances are blocks from 0, 0.";
        for (net.minecraft.util.FormattedCharSequence n : font.split(Component.literal(note), lw)) {
            if (ny > y + h - 10) break;
            g.drawString(font, n, lx, ny, 0xFF6A6660, false);
            ny += 10;
        }
        if (searchButton != null) searchButton.setMessage(Component.literal(searching() ? "Stop" : "Search"));
        renderResults(g, mx, my, x + lw + 20, y + 4, w - lw - 24, h - 8);
    }

    private void renderResults(GuiGraphics g, int mx, int my, int rx, int ry, int rw, int rh) {
        if (search == null || search.results.isEmpty()) {
            g.drawString(font, search == null ? "Results appear here" : "No matches yet…", rx + 4, ry + 4, 0xFF8A867E, false);
            return;
        }
        List<SeedSearch.Result> res;
        synchronized (search.results) {
            res = new ArrayList<>(search.results);
        }
        g.enableScissor(rx, ry, rx + rw, ry + rh);
        int cy = ry - resultsScroll;
        for (SeedSearch.Result r : res) {
            int ch = 74;
            if (cy + ch >= ry && cy <= ry + rh) {
                g.fill(rx, cy, rx + rw, cy + ch - 4, 0xFF1C1B22);
                MapTexture t = PREVIEWS.get(r.seed());
                if (t == null) {
                    t = new MapTexture("seed_" + Long.toHexString(r.seed()), r.previewSize(), r.previewSize());
                    t.upload(r.preview());
                    PREVIEWS.put(r.seed(), t);
                }
                t.draw(g, rx + 3, cy + 3, 64, 64);
                // structures on the preview
                for (Gen.Found f : r.found()) {
                    int px = rx + 3 + 32 + (int) (f.x() * 64.0 / r.span()), py = cy + 3 + 32 + (int) (f.z() * 64.0 / r.span());
                    g.fill(px - 1, py - 1, px + 2, py + 2, MapTab.structColor(f.structure()));
                }
                g.fill(rx + 34, cy + 34, rx + 36, cy + 36, 0xFFFFFFFF);
                g.drawString(font, String.valueOf(r.seed()), rx + 72, cy + 4, 0xFFFFFFFF, false);
                int ty = cy + 16;
                Map<String, int[]> groups = new LinkedHashMap<>();
                for (Gen.Found f : r.found()) {
                    String n = f.structure().unwrapKey().map(BiomeColors::pretty).orElse("?");
                    int d = (int) Math.sqrt((double) f.x() * f.x() + (double) f.z() * f.z());
                    int[] gv = groups.computeIfAbsent(n, k -> new int[] { 0, Integer.MAX_VALUE });
                    gv[0]++;
                    gv[1] = Math.min(gv[1], d);
                }
                for (Map.Entry<String, int[]> e : groups.entrySet()) {
                    if (ty > cy + 44) break;
                    g.drawString(font, font.plainSubstrByWidth(e.getKey() + " ×" + e.getValue()[0] + " · " + e.getValue()[1] + " m", rw - 76),
                            rx + 72, ty, 0xFFB0ACA4, false);
                    ty += 10;
                }
                button(g, mx, my, rx + 72, cy + 54, "Copy");
                button(g, mx, my, rx + 72 + font.width("Copy") + 12, cy + 54, "Map");
            }
            cy += ch;
        }
        g.disableScissor();
    }

    private void button(GuiGraphics g, int mx, int my, int bx, int by, String label) {
        int bw = font.width(label) + 8;
        boolean hover = mx >= bx && mx < bx + bw && my >= by && my < by + 13;
        g.fill(bx, by, bx + bw, by + 13, hover ? 0xFF3A4A2A : 0xFF2A2830);
        g.drawString(font, label, bx + 4, by + 3, hover ? 0xFFB8D47A : 0xFFE8E4DA, false);
    }

    @Override
    void renderOver(GuiGraphics g, int mx, int my, float pt) {
        if (picker == 0) return;
        int pw = 240, ph = Math.min(h - 10, 260);
        int px = x + w / 2 - pw / 2, py = y + 4;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(px - 4, py - 4, px + pw + 4, py + ph + 4, 0xF0101014);
        g.fill(px - 4, py - 4, px + pw + 4, py - 3, 0xFFB8D47A);
        g.drawString(font, picker == 1 ? "Pick a biome" : "Pick a structure", px, py + 4, 0xFFFFFFFF, false);
        int ly = py + 42;
        int rows = (ph - 46) / 12;
        for (int i = pickerScroll; i < Math.min(pickerItems.size(), pickerScroll + rows); i++) {
            boolean hover = mx >= px && mx < px + pw && my >= ly && my < ly + 12;
            if (hover) g.fill(px, ly, px + pw, ly + 12, 0x40B8D47A);
            g.drawString(font, (String) pickerItems.get(i)[0], px + 4, ly + 2, 0xFFE8E4DA, false);
            ly += 12;
        }
        if (pickerItems.isEmpty()) g.drawString(font, "Nothing matches", px + 4, py + 44, 0xFF8A867E, false);
        g.pose().popPose();
        if (pickerFilter != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 410);
            pickerFilter.render(g, mx, my, pt);
            g.pose().popPose();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    boolean mouseClicked(double mx, double my, int button) {
        if (picker != 0) {
            int pw = 240, ph = Math.min(h - 10, 260);
            int px = x + w / 2 - pw / 2, py = y + 4;
            if (pickerFilter != null && pickerFilter.mouseClicked(mx, my, button)) return true;
            int ly = py + 42;
            int rows = (ph - 46) / 12;
            for (int i = pickerScroll; i < Math.min(pickerItems.size(), pickerScroll + rows); i++) {
                if (mx >= px && mx < px + pw && my >= ly && my < ly + 12) {
                    Object key = pickerItems.get(i)[1];
                    if (picker == 1 && pickerIndex < CRITERIA.biomes.size()) CRITERIA.biomes.get(pickerIndex).biome = (ResourceKey<Biome>) key;
                    if (picker == 2 && pickerIndex < CRITERIA.structs.size()) CRITERIA.structs.get(pickerIndex).structure = (ResourceKey<Structure>) key;
                    picker = 0;
                    pickerFilter = null;
                    screen.rebuild();
                    return true;
                }
                ly += 12;
            }
            if (!(mx >= px - 4 && mx < px + pw + 4 && my >= py - 4 && my < py + ph + 4)) {
                picker = 0;
                pickerFilter = null;
                screen.rebuild();
            }
            return true;
        }
        // result buttons
        if (search != null) {
            int lw = leftW();
            int rx = x + lw + 20, ry = y + 4;
            List<SeedSearch.Result> res;
            synchronized (search.results) {
                res = new ArrayList<>(search.results);
            }
            int cy = ry - resultsScroll;
            for (SeedSearch.Result r : res) {
                int by = cy + 54;
                if (my >= by && my < by + 13 && my >= ry && my < y + h) {
                    int b1 = rx + 72, b1w = font.width("Copy") + 8;
                    int b2 = rx + 72 + font.width("Copy") + 12, b2w = font.width("Map") + 8;
                    if (mx >= b1 && mx < b1 + b1w) {
                        mc.keyboardHandler.setClipboard(String.valueOf(r.seed()));
                        com.harold.fieldkit.hud.Notifications.info("Copied seed " + r.seed());
                        return true;
                    }
                    if (mx >= b2 && mx < b2 + b2w) {
                        MapTab.preview(r.seed());
                        screen.switchTo(0);
                        return true;
                    }
                }
                cy += 74;
            }
        }
        return false;
    }

    @Override
    boolean scrolled(double mx, double my, double delta) {
        if (picker != 0) {
            pickerScroll = Math.max(0, Math.min(Math.max(0, pickerItems.size() - 5), pickerScroll - (int) Math.signum(delta) * 3));
            return true;
        }
        int lw = leftW();
        if (mx > x + lw + 16 && search != null) {
            int max = Math.max(0, search.results.size() * 74 - (h - 8));
            resultsScroll = Math.max(0, Math.min(max, resultsScroll - (int) (delta * 30)));
            return true;
        }
        return false;
    }

    @Override
    boolean keyPressed(int key, int scan, int mods) {
        if (picker != 0 && key == 256) {
            picker = 0;
            pickerFilter = null;
            screen.rebuild();
            return true;
        }
        return false;
    }

    /** Test hook: set up a search and start it. */
    static void testSearch(Gen gen, ResourceKey<Biome> biome, int biomeRadius, ResourceKey<Structure> structure, int structRadius) {
        CRITERIA.biomes.clear();
        CRITERIA.structs.clear();
        Criteria.BiomeRule b = new Criteria.BiomeRule();
        b.biome = biome;
        b.radius = biomeRadius;
        CRITERIA.biomes.add(b);
        Criteria.StructRule s = new Criteria.StructRule();
        s.structure = structure;
        s.radius = structRadius;
        CRITERIA.structs.add(s);
        CRITERIA.start = "1";
        CRITERIA.limit = 4;
        search = new SeedSearch(gen, CRITERIA);
        search.start();
    }
}
