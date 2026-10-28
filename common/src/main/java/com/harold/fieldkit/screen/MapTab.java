package com.harold.fieldkit.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.harold.fieldkit.FairPlay;
import com.harold.fieldkit.map.BiomeColors;
import com.harold.fieldkit.map.ExploredMap;
import com.harold.fieldkit.map.Gen;
import com.harold.fieldkit.map.MapTexture;
import com.mojang.math.Axis;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The big map. In singleplayer it's drawn from the world's real generator, so it shows places you haven't been, with
 * structures; on servers it shows the chunks you've loaded this session. Drag to move, scroll to zoom.
 */
class MapTab extends Tab {
    private static final int RES = 256;
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Fieldkit map");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    static double cx, cz, bpp = 4;
    static boolean showStructures = true;
    /** A seed to preview (from the seed finder), or null for this world. */
    static Long previewSeed;
    private static Gen previewGen;
    private static final Map<Long, Integer> CACHE = new HashMap<>();
    private static long cacheSeed;

    private MapTexture tex;
    private double texCx, texCz, texBpp = -1;
    private volatile boolean busy;
    private volatile int[] readyPixels;
    private volatile double readyCx, readyCz, readyBpp;
    private volatile List<Gen.Found> found = new ArrayList<>();
    private volatile String structNote = "";
    private boolean dragging;
    private long lastExploredRefresh;

    @Override
    String title() {
        return "Map";
    }

    private Gen gen() {
        Gen g = Gen.current();
        if (g == null) return null;
        if (previewSeed != null) {
            if (previewGen == null || previewGen.seed != previewSeed) previewGen = g.withSeed(previewSeed);
            return previewGen;
        }
        // the generator only describes the overworld
        return mc.level.dimension() == Level.OVERWORLD ? g : null;
    }

    private boolean centredHere;
    private static boolean skipCentre;

    /** Show a seed from the seed finder, centred on its origin. */
    static void preview(long seed) {
        previewSeed = seed;
        cx = 0;
        cz = 0;
        skipCentre = true;
    }

    @Override
    void build() {
        if (!centredHere) {
            if (!skipCentre && previewSeed == null && mc.player != null) {
                cx = mc.player.getX();
                cz = mc.player.getZ();
            }
            skipCentre = false;
            centredHere = true;
        }
        int bx = x + 4, by = y + h - 24;
        screen.add(Button.builder(Component.literal("Centre on me"), b -> {
            if (previewSeed == null && mc.player != null) {
                cx = mc.player.getX();
                cz = mc.player.getZ();
            } else {
                cx = 0;
                cz = 0;
            }
        }).bounds(bx, by, 90, 20).build());
        screen.add(Button.builder(Component.literal("Structures: " + (showStructures ? "on" : "off")), b -> {
            showStructures = !showStructures;
            b.setMessage(Component.literal("Structures: " + (showStructures ? "on" : "off")));
            texBpp = -1;
        }).bounds(bx + 94, by, 100, 20).build());
        if (previewSeed != null) {
            screen.add(Button.builder(Component.literal("Back to this world"), b -> {
                previewSeed = null;
                previewGen = null;
                if (mc.player != null) {
                    cx = mc.player.getX();
                    cz = mc.player.getZ();
                }
                texBpp = -1;
                screen.rebuild();
            }).bounds(bx + 198, by, 120, 20).build());
        }
    }

    private int mapX0() {
        return x;
    }

    private int mapY0() {
        return y;
    }

    private int mapW() {
        return w;
    }

    private int mapH() {
        return h - 28;
    }

    @Override
    void tick() {
        Gen g = gen();
        if (g == null && System.currentTimeMillis() - lastExploredRefresh > 1000) texBpp = -1; // refresh as chunks load
        if (readyPixels != null) {
            if (tex == null) tex = new MapTexture("bigmap", RES, RES);
            tex.upload(readyPixels);
            texCx = readyCx;
            texCz = readyCz;
            texBpp = readyBpp;
            readyPixels = null;
        }
        boolean stale = texBpp != bpp || Math.abs(cx - texCx) > RES * texBpp * 0.35 || Math.abs(cz - texCz) > RES * texBpp * 0.35;
        if (stale && !busy) refresh(g);
    }

    private void refresh(Gen g) {
        final double ccx = cx, ccz = cz, b = bpp;
        final double step = b * 2;
        if (g == null) {
            lastExploredRefresh = System.currentTimeMillis();
            int[] px = new int[RES * RES];
            int[] hs = new int[RES * RES];
            for (int j = 0; j < RES; j++) {
                for (int i = 0; i < RES; i++) {
                    int bx = (int) Math.floor(ccx + (i - RES / 2 + 0.5) * step), bz = (int) Math.floor(ccz + (j - RES / 2 + 0.5) * step);
                    int[] seen = ExploredMap.at(bx, bz);
                    if (seen == null) {
                        px[j * RES + i] = ((i / 8 + j / 8) & 1) == 0 ? 0xFF15151A : 0xFF19191F;
                        hs[j * RES + i] = Integer.MIN_VALUE;
                    } else {
                        px[j * RES + i] = seen[0];
                        hs[j * RES + i] = seen[1];
                    }
                }
            }
            for (int j = 1; j < RES; j++)
                for (int i = 1; i < RES; i++) {
                    int a = hs[j * RES + i], n = hs[(j - 1) * RES + i - 1];
                    if (a == Integer.MIN_VALUE || n == Integer.MIN_VALUE) continue;
                    int d = Math.max(-4, Math.min(4, a - n));
                    if (d != 0) px[j * RES + i] = BiomeColors.shade(px[j * RES + i], 1F + d * 0.05F);
                }
            found = new ArrayList<>();
            structNote = "";
            readyCx = ccx;
            readyCz = ccz;
            readyBpp = b;
            readyPixels = px;
            return;
        }
        busy = true;
        final boolean structs = showStructures;
        EXEC.submit(() -> {
            try {
                synchronized (CACHE) {
                    if (cacheSeed != g.seed) {
                        CACHE.clear();
                        cacheSeed = g.seed;
                    }
                    if (CACHE.size() > 400000) CACHE.clear();
                }
                int[] px = new int[RES * RES];
                for (int j = 0; j < RES; j++) {
                    for (int i = 0; i < RES; i++) {
                        int bx = (int) Math.floor(ccx + (i - RES / 2 + 0.5) * step), bz = (int) Math.floor(ccz + (j - RES / 2 + 0.5) * step);
                        long key = ((long) (bx >> 2) << 32) ^ ((bz >> 2) & 0xFFFFFFFFL);
                        Integer c;
                        synchronized (CACHE) {
                            c = CACHE.get(key);
                        }
                        if (c == null) {
                            c = BiomeColors.of(g.biome(bx, bz));
                            synchronized (CACHE) {
                                CACHE.put(key, c);
                            }
                        }
                        px[j * RES + i] = c;
                    }
                }
                List<Gen.Found> f = new ArrayList<>();
                String note = "";
                if (structs) {
                    if (b <= 8) {
                        double half = RES * step / 2;
                        f = g.structures((int) Math.floor((ccx - half) / 16), (int) Math.floor((ccz - half) / 16),
                                (int) Math.floor((ccx + half) / 16), (int) Math.floor((ccz + half) / 16), null, 1500);
                    } else note = "Zoom in to see structures";
                }
                found = f;
                structNote = note;
                readyCx = ccx;
                readyCz = ccz;
                readyBpp = b;
                readyPixels = px;
            } catch (Throwable t) {
                structNote = "Map error: " + t;
            } finally {
                busy = false;
            }
        });
    }

    private int sx(double bx) {
        return (int) Math.round(mapX0() + mapW() / 2.0 + (bx - cx) / bpp);
    }

    private int sy(double bz) {
        return (int) Math.round(mapY0() + mapH() / 2.0 + (bz - cz) / bpp);
    }

    @Override
    void render(GuiGraphics g, int mx, int my, float pt) {
        int x0 = mapX0(), y0 = mapY0(), mw = mapW(), mh = mapH();
        panel(g, x0 - 1, y0 - 1, x0 + mw + 1, y0 + mh + 1);
        g.enableScissor(x0, y0, x0 + mw, y0 + mh);
        if (tex != null && texBpp > 0) {
            double size = RES * 2 * texBpp / bpp;
            double left = x0 + mw / 2.0 + (texCx - cx) / bpp - size / 2, top = y0 + mh / 2.0 + (texCz - cz) / bpp - size / 2;
            g.pose().pushPose();
            g.pose().translate(left, top, 0);
            g.pose().scale((float) (size / RES), (float) (size / RES), 1);
            tex.draw(g, 0, 0, RES, RES);
            g.pose().popPose();
        }
        Gen gen = gen();
        // structures
        Gen.Found hoverF = null;
        for (Gen.Found f : found) {
            int px = sx(f.x()), py = sy(f.z());
            if (px < x0 - 4 || py < y0 - 4 || px > x0 + mw + 4 || py > y0 + mh + 4) continue;
            int col = structColor(f.structure());
            int r = bpp >= 8 ? 2 : 3;
            g.fill(px - r, py - r, px + r + 1, py + r + 1, 0xFF000000);
            g.fill(px - r + 1, py - r + 1, px + r, py + r, col);
            if (Math.abs(mx - px) <= 4 && Math.abs(my - py) <= 4) hoverF = f;
        }
        // spawn and you
        if (previewSeed == null && mc.getSingleplayerServer() != null && mc.level.dimension() == Level.OVERWORLD) {
            BlockPos sp = mc.level.getSharedSpawnPos();
            int px = sx(sp.getX()), py = sy(sp.getZ());
            g.fill(px - 3, py - 3, px + 4, py + 4, 0xFF000000);
            g.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
            g.drawString(font, "Spawn", px + 5, py - 4, 0xFFFFFFFF, true);
        }
        if (previewSeed != null) {
            int px = sx(0), py = sy(0);
            g.fill(px - 1, py - 6, px + 2, py + 7, 0xFFFFFFFF);
            g.fill(px - 6, py - 1, px + 7, py + 2, 0xFFFFFFFF);
        }
        if (previewSeed == null && mc.player != null) {
            for (Entity e : mc.level.players()) {
                if (e == mc.player || !FairPlay.playerRadar()) continue;
                int px = sx(e.getX()), py = sy(e.getZ());
                g.fill(px - 2, py - 2, px + 3, py + 3, 0xFFFFFFFF);
                g.drawString(font, ((Player) e).getName().getString(), px + 4, py - 4, 0xFFFFFFFF, true);
            }
            int px = sx(mc.player.getX()), py = sy(mc.player.getZ());
            g.pose().pushPose();
            g.pose().translate(px, py, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(mc.player.getYRot() + 180F));
            for (int r = 0; r < 8; r++) g.fill(-r / 2 - 1, -5 + r, r / 2 + 2, -4 + r, 0xFF000000);
            for (int r = 0; r < 7; r++) g.fill(-r / 2, -4 + r, r / 2 + 1, -3 + r, 0xFFFF4040);
            g.pose().popPose();
        }
        g.disableScissor();

        // info line
        String mode;
        if (previewSeed != null) mode = "Previewing seed " + previewSeed + " (from the seed finder)";
        else if (gen != null) mode = "This world, from its seed" + (FairPlay.singleplayer() ? "" : "");
        else if (mc.getSingleplayerServer() == null) mode = "Server: showing what you've loaded this session ("
                + ExploredMap.size() + " chunks); seeds aren't available on servers";
        else mode = "This dimension: showing what you've loaded";
        g.drawString(font, mode, x0 + 4, y0 + 4, 0xFFE8E4DA, true);
        if (!structNote.isEmpty()) g.drawString(font, structNote, x0 + 4, y0 + 14, 0xFFE0B040, true);
        if (busy) g.drawString(font, "Generating…", x0 + mw - font.width("Generating…") - 4, y0 + 4, 0xFFB8D47A, true);
        String scale = String.format("1 px = %s blocks", bpp >= 1 ? String.valueOf((int) bpp) : String.format("%.2f", bpp));
        g.drawString(font, scale, x0 + mw - font.width(scale) - 4, y0 + mh - 12, 0xFFB0ACA4, true);

        if (mx >= x0 && mx < x0 + mw && my >= y0 && my < y0 + mh) {
            int bx = (int) Math.floor(cx + (mx - x0 - mw / 2.0) * bpp), bz = (int) Math.floor(cz + (my - y0 - mh / 2.0) * bpp);
            String where = bx + ", " + bz;
            if (gen != null) {
                Holder<Biome> b = gen.biome(bx, bz);
                where += "  " + b.unwrapKey().map(BiomeColors::pretty).orElse("?");
            }
            if (hoverF != null) {
                where = hoverF.structure().unwrapKey().map(BiomeColors::pretty).orElse("Structure") + " at " + hoverF.x() + ", "
                        + hoverF.z();
            }
            int tw = font.width(where) + 8;
            int tx = Math.min(mx + 10, x0 + mw - tw), ty = Math.max(my - 14, y0);
            g.fill(tx, ty, tx + tw, ty + 12, 0xE0101014);
            g.drawString(font, where, tx + 4, ty + 2, 0xFFFFFFFF, false);
        }
    }

    static int structColor(Holder<Structure> s) {
        String p = s.unwrapKey().map(k -> k.location().getPath()).orElse("");
        if (p.contains("village")) return 0xFFF0C84A;
        if (p.contains("outpost")) return 0xFFC0392B;
        if (p.contains("monument")) return 0xFF2BB3C0;
        if (p.contains("mansion")) return 0xFF8E44AD;
        if (p.contains("ancient_city")) return 0xFF34495E;
        if (p.contains("desert_pyramid")) return 0xFFE1A95F;
        if (p.contains("jungle")) return 0xFF27AE60;
        if (p.contains("swamp") || p.contains("witch")) return 0xFF6C8F3D;
        if (p.contains("igloo")) return 0xFFECF0F1;
        if (p.contains("shipwreck")) return 0xFFA0522D;
        if (p.contains("ocean_ruin")) return 0xFF3498DB;
        if (p.contains("ruined_portal")) return 0xFFE056A0;
        if (p.contains("stronghold")) return 0xFF7F8C8D;
        if (p.contains("trail")) return 0xFFD35400;
        if (p.contains("trial")) return 0xFF95A5A6;
        if (p.contains("mineshaft")) return 0xFF8D6E4A;
        int h = s.unwrapKey().map(k -> k.location().hashCode()).orElse(0);
        return 0xFF000000 | ((h & 0x7F7F7F) + 0x404040);
    }

    @Override
    boolean mouseClicked(double mx, double my, int button) {
        if (mx >= mapX0() && mx < mapX0() + mapW() && my >= mapY0() && my < mapY0() + mapH()) {
            dragging = true;
            return true;
        }
        return false;
    }

    @Override
    boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!dragging) return false;
        cx -= dx * bpp;
        cz -= dy * bpp;
        return true;
    }

    @Override
    boolean mouseReleased(double mx, double my, int button) {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    @Override
    boolean scrolled(double mx, double my, double delta) {
        if (!(mx >= mapX0() && mx < mapX0() + mapW() && my >= mapY0() && my < mapY0() + mapH())) return false;
        double before = bpp;
        double after = Math.max(0.25, Math.min(256, delta > 0 ? bpp / 2 : bpp * 2));
        // keep the point under the cursor still
        double ox = mx - mapX0() - mapW() / 2.0, oy = my - mapY0() - mapH() / 2.0;
        cx += ox * (before - after);
        cz += oy * (before - after);
        bpp = after;
        return true;
    }

    @Override
    void removed() {
        if (tex != null) tex.close();
        tex = null;
    }
}
