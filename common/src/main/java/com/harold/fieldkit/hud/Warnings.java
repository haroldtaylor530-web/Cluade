package com.harold.fieldkit.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.harold.fieldkit.Config;
import com.harold.fieldkit.FairPlay;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/**
 * Warns when a hostile mob is closing in or a player comes near: a line at the top of the screen with the name,
 * distance and an arrow pointing at it, and a soft ping the first time. Only uses what your game already sees.
 */
public final class Warnings {
    record Threat(Entity e, double dist, boolean player, boolean closing) {
    }

    private static final Map<Integer, Double> lastDist = new HashMap<>();
    private static final Map<Integer, Long> pinged = new HashMap<>();
    private static final Set<Integer> playersNear = new HashSet<>();
    static final List<Threat> threats = new ArrayList<>();

    private Warnings() {
    }

    public static void tick(Minecraft mc) {
        threats.clear();
        Config c = Config.INSTANCE;
        LocalPlayer me = mc.player;
        Map<Integer, Double> now = new HashMap<>();
        Set<Integer> near = new HashSet<>();
        long t = Util.getMillis();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == me || !(e instanceof LivingEntity le) || !le.isAlive()) continue;
            double d = e.distanceTo(me);
            if (e instanceof Player p) {
                if (!c.warnPlayers || !FairPlay.playerRadar() || p.isSpectator() || d > c.playerRadius) continue;
                near.add(e.getId());
                threats.add(new Threat(e, d, true, lastDist.containsKey(e.getId()) && d < lastDist.get(e.getId()) - 0.05));
                if (!playersNear.contains(e.getId())) {
                    Notifications.add(Notifications.Kind.DANGER, p.getName().getString() + " is nearby (" + Math.round(d) + " m)",
                            4000, c.warnSound);
                }
                now.put(e.getId(), d);
            } else if (e instanceof Enemy && c.warnMobs && d <= c.mobRadius) {
                Double before = lastDist.get(e.getId());
                boolean closing = before != null && d < before - 0.02;
                // a mob counts when it is coming at you, or is already very close
                if (closing || d < 4) {
                    threats.add(new Threat(e, d, false, closing));
                    Long last = pinged.get(e.getId());
                    if (c.warnSound && (last == null || t - last > 15000)) {
                        pinged.put(e.getId(), t);
                        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.5F));
                    }
                }
                now.put(e.getId(), d);
            }
        }
        lastDist.clear();
        lastDist.putAll(now);
        playersNear.clear();
        playersNear.addAll(near);
        pinged.entrySet().removeIf(en -> t - en.getValue() > 60000);
        threats.sort((a, b) -> a.player != b.player ? (a.player ? -1 : 1) : Double.compare(a.dist, b.dist));
    }

    static void render(GuiGraphics g, Minecraft mc, float partial) {
        if (threats.isEmpty()) return;
        int sw = g.guiWidth();
        int y = 22;
        int shown = 0;
        long t = Util.getMillis();
        for (Threat th : threats) {
            if (shown++ >= 4) break;
            Entity e = th.e;
            double dx = e.getX() - mc.player.getX(), dz = e.getZ() - mc.player.getZ();
            float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float rel = bearing - mc.player.getViewYRot(partial);
            String arrow = arrow(rel);
            String name = e.getName().getString();
            String text = (th.player ? "Player " : "") + name + "  " + Math.round(th.dist) + " m " + arrow;
            int w = mc.font.width(text) + 22;
            int x = (sw - w) / 2;
            boolean urgent = th.dist < 6;
            float pulse = urgent ? 0.5F + 0.5F * (float) Math.sin(t / 120.0) : 1F;
            int edge = th.player ? 0xFFE0B040 : 0xFFFF5040;
            g.fill(x, y, x + w, y + 13, Notifications.alpha(0xC8140C0C, 1F));
            g.fill(x, y, x + 2, y + 13, Notifications.alpha(edge, pulse));
            g.drawString(mc.font, "⚠", x + 6, y + 3, Notifications.alpha(edge, pulse), false);
            g.drawString(mc.font, text, x + 16, y + 3, th.player ? 0xFFFFE8B0 : 0xFFFFD0C8, false);
            y += 15;
        }
    }

    /** An arrow for a direction relative to where you're looking. */
    static String arrow(float rel) {
        rel = ((rel % 360) + 540) % 360 - 180; // -180..180, 0 = ahead
        String[] a = { "↑", "↗", "→", "↘", "↓", "↙", "←", "↖" };
        int i = Math.round(rel / 45F);
        return a[((i % 8) + 8) % 8];
    }
}
