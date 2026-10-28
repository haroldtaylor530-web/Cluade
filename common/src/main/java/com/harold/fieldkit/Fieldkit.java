package com.harold.fieldkit;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.harold.fieldkit.console.ChatWatch;
import com.harold.fieldkit.console.LogWatch;
import com.harold.fieldkit.hud.CombatTracker;
import com.harold.fieldkit.hud.Hud;
import com.harold.fieldkit.hud.Notifications;
import com.harold.fieldkit.hud.Warnings;
import com.harold.fieldkit.map.ExploredMap;
import com.harold.fieldkit.screen.FieldkitScreen;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** Client-side toolkit: map + seed finder (M), minimap, approach warnings, combat HUD, console analyser. */
public final class Fieldkit {
    public static final String MOD_ID = "fieldkit";
    public static final Logger LOG = LoggerFactory.getLogger("Fieldkit");
    public static Platform platform;

    public static final KeyMapping OPEN = new KeyMapping("key.fieldkit.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M,
            "key.categories.fieldkit");
    public static final KeyMapping TOGGLE_HUD = new KeyMapping("key.fieldkit.hud", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, "key.categories.fieldkit");

    /** Wall-clock time the mod was set up, and when the title screen first showed (for load times). */
    public static final long STARTED = System.currentTimeMillis();
    public static long titleShown;
    private static long joinStarted;

    private Fieldkit() {
    }

    public static void init(Platform p) {
        platform = p;
        Config.load(p.configDir());
        LogWatch.start(p.gameDir().resolve("logs").resolve("latest.log"));
        LOG.info("Fieldkit ready on {} {}", p.loader(), p.loaderVersion());
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (titleShown == 0 && mc.screen instanceof TitleScreen) {
            titleShown = System.currentTimeMillis();
            if (Config.INSTANCE.loadInfo) {
                Notifications.info("Game loaded in " + secs(titleShown - LogWatch.firstLineMillis(STARTED)) + " · "
                        + platform.modCount() + " mods · " + LogWatch.errorCount() + " errors, " + LogWatch.warnCount()
                        + " warnings in the log");
            }
        }
        while (OPEN.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new FieldkitScreen());
        }
        while (TOGGLE_HUD.consumeClick()) {
            Config.INSTANCE.hud = !Config.INSTANCE.hud;
            Config.save();
        }
        LogWatch.tick();
        Notifications.tick();
        if (joinStarted == 0 && (mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.ConnectScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)) {
            joinStarted = System.currentTimeMillis();
        }
        if (mc.level == null || mc.player == null) return;
        fpsWatch(mc);
        if (joinStarted > 0 && mc.screen == null) {
            if (Config.INSTANCE.loadInfo) Notifications.info("World joined in " + secs(System.currentTimeMillis() - joinStarted));
            joinStarted = 0;
        }
        Warnings.tick(mc);
        CombatTracker.tick(mc);
        ExploredMap.tick(mc);
        Hud.tick(mc);
    }

    public static void renderHud(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Notifications.render(g, mc);
        if (!Config.INSTANCE.hud || mc.options.hideGui || mc.player == null) return;
        if (platform.debugScreenOpen()) return;
        Hud.render(g, mc, partial);
    }

    public static void chat(Component message, boolean fromPlayer) {
        FairPlay.check(message.getString());
        ChatWatch.onMessage(message, fromPlayer);
    }

    private static int lowFpsSeconds;
    private static long lastFpsCheck, lastFpsNote;

    private static void fpsWatch(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastFpsCheck < 1000) return;
        lastFpsCheck = now;
        int fps = mc.getFps();
        lowFpsSeconds = fps < 20 && mc.screen == null ? lowFpsSeconds + 1 : 0;
        if (lowFpsSeconds == 5 && Config.INSTANCE.notifyFps && now - lastFpsNote > 120000) {
            lastFpsNote = now;
            Notifications.warn("Low FPS: " + fps + " for 5 seconds. Lower render distance or check the Console tab for lag.");
        }
    }

    public static void joining() {
        if (joinStarted == 0) joinStarted = System.currentTimeMillis();
        FairPlay.reset();
        CombatTracker.resetSession();
        ExploredMap.reset();
    }

    public static void left() {
        FairPlay.reset();
        ExploredMap.reset();
    }

    public static void attacked(Entity target) {
        CombatTracker.onAttack(target);
    }

    public static String secs(long ms) {
        return ms < 60000 ? String.format("%.1f s", ms / 1000.0) : (ms / 60000) + " min " + (ms / 1000 % 60) + " s";
    }
}
