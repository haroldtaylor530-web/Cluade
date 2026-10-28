package com.harold.fieldkit.hud;

import java.util.HashMap;
import java.util.Map;

import com.harold.fieldkit.Config;
import com.harold.fieldkit.FairPlay;
import com.harold.fieldkit.map.BiomeColors;
import com.harold.fieldkit.map.ExploredMap;
import com.harold.fieldkit.map.Gen;
import com.harold.fieldkit.map.MapTexture;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/** Biome minimap in the top-left corner: north up, you in the middle, mobs and players as dots. */
public final class Minimap {
    static final int RES = 128;
    private static MapTexture tex;
    private static final int[] pixels = new int[RES * RES];
    private static int lastCx = Integer.MIN_VALUE, lastCz, lastZoom, tickCount;
    private static final Map<Long, Integer> GEN_CACHE = new HashMap<>();
    static String biomeName = "";

    private Minimap() {
    }

    static void tick(Minecraft mc) {
        if (!Config.INSTANCE.minimap || !FairPlay.minimap()) return;
        LocalPlayer p = mc.player;
        int zoom = Math.max(1, Math.min(8, Config.INSTANCE.minimapZoom));
        int cx = p.getBlockX(), cz = p.getBlockZ();
        Holder<Biome> here = mc.level.getBiome(p.blockPosition());
        biomeName = here.unwrapKey().map(BiomeColors::pretty).orElse("");
        boolean moved = Math.abs(cx - lastCx) >= zoom || Math.abs(cz - lastCz) >= zoom || zoom != lastZoom;
        if (!moved && ++tickCount % 20 != 0) return;
        lastCx = cx;
        lastCz = cz;
        lastZoom = zoom;
        if (tex == null) tex = new MapTexture("minimap", RES, RES);
        Gen gen = Gen.current();
        if (GEN_CACHE.size() > 50000) GEN_CACHE.clear();
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        int[] heights = new int[RES * RES];
        for (int py = 0; py < RES; py++) {
            for (int px = 0; px < RES; px++) {
                int x = cx + (px - RES / 2) * zoom, z = cz + (py - RES / 2) * zoom;
                int col;
                int h;
                if (mc.level.hasChunk(x >> 4, z >> 4)) {
                    h = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                    long q = ChunkPos.asLong(x >> 2, z >> 2);
                    Integer c = GEN_CACHE.get(q ^ 0x5DEECE66DL);
                    if (c == null) {
                        c = BiomeColors.of(mc.level.getBiome(bp.set(x, h - 1, z)));
                        GEN_CACHE.put(q ^ 0x5DEECE66DL, c);
                    }
                    col = c;
                } else {
                    int[] seen = ExploredMap.at(x, z);
                    if (seen != null) {
                        col = seen[0];
                        h = seen[1];
                    } else if (gen != null) {
                        long q = ChunkPos.asLong(x >> 2, z >> 2);
                        Integer c = GEN_CACHE.get(q);
                        if (c == null) {
                            c = BiomeColors.of(gen.biome(x, z));
                            GEN_CACHE.put(q, c);
                        }
                        col = BiomeColors.shade(c, 0.8F);
                        h = Integer.MIN_VALUE;
                    } else {
                        col = 0xFF141414;
                        h = Integer.MIN_VALUE;
                    }
                }
                heights[py * RES + px] = h;
                pixels[py * RES + px] = col;
            }
        }
        if (Config.INSTANCE.minimapShading) {
            for (int py = 1; py < RES; py++) {
                for (int px = 1; px < RES; px++) {
                    int h = heights[py * RES + px], n = heights[(py - 1) * RES + px - 1];
                    if (h == Integer.MIN_VALUE || n == Integer.MIN_VALUE) continue;
                    int d = Math.max(-4, Math.min(4, h - n));
                    if (d != 0) pixels[py * RES + px] = BiomeColors.shade(pixels[py * RES + px], 1F + d * 0.06F);
                }
            }
        }
        tex.upload(pixels);
    }

    static int render(GuiGraphics g, Minecraft mc, float partial, int x, int y) {
        if (!Config.INSTANCE.minimap || !FairPlay.minimap() || tex == null) return y;
        int size = Math.max(48, Math.min(200, Config.INSTANCE.minimapSize));
        int zoom = Math.max(1, lastZoom);
        g.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xC0101014);
        g.enableScissor(x, y, x + size, y + size);
        // keep the map steady while the texture catches up with the player
        double ox = (mc.player.getX() - lastCx) / zoom * size / RES, oz = (mc.player.getZ() - lastCz) / zoom * size / RES;
        g.pose().pushPose();
        g.pose().translate(-ox, -oz, 0);
        tex.draw(g, x, y, size, size);
        g.pose().popPose();
        int cx = x + size / 2, cy = y + size / 2;
        if (Config.INSTANCE.minimapEntities) {
            double range = RES / 2.0 * zoom;
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player || !(e instanceof LivingEntity)) continue;
                double dx = e.getX() - mc.player.getX(), dz = e.getZ() - mc.player.getZ();
                if (Math.abs(dx) > range || Math.abs(dz) > range) continue;
                int col;
                if (e instanceof Player) {
                    if (!FairPlay.playerRadar()) continue;
                    col = 0xFFFFFFFF;
                } else if (e instanceof Enemy) col = 0xFFFF4040;
                else col = 0xFF60E060;
                int ex = cx + (int) (dx / zoom * size / RES), ez = cy + (int) (dz / zoom * size / RES);
                int s = e instanceof Player ? 2 : 1;
                g.fill(ex - s - 1, ez - s - 1, ex + s + 1, ez + s + 1, 0xFF000000);
                g.fill(ex - s, ez - s, ex + s, ez + s, col);
            }
        }
        // you: an arrow pointing where you look
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(mc.player.getViewYRot(partial) + 180F));
        for (int r = 0; r < 6; r++) {
            g.fill(-r / 2 - 1, -4 + r, r / 2 + 1, -3 + r, 0xFF000000);
        }
        for (int r = 0; r < 5; r++) {
            g.fill(-r / 2, -3 + r, r / 2 + 1, -2 + r, 0xFFFFFFFF);
        }
        g.pose().popPose();
        g.disableScissor();
        g.drawString(mc.font, "N", cx - 2, y + 2, 0xFFFFFFFF, true);
        return y + size + 4;
    }
}
