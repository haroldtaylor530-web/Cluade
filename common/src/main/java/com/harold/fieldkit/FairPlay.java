package com.harold.fieldkit;

import net.minecraft.client.Minecraft;

/**
 * Servers can switch off radar-type features with the codes minimap mods already use (Xaero's "fair play" and
 * "no minimap" codes, JourneyMap's radar code). When a server sends one, Fieldkit stops showing other players on the
 * minimap and stops warning about approaching players, just as those mods do.
 */
public final class FairPlay {
    private static boolean noPlayerRadar, noMinimap;

    private FairPlay() {
    }

    public static void check(String text) {
        if (text.contains("§f§a§i§r§x§a§e§r§o") || text.contains("§3 §6 §3 §6 §3 §6 §e")) noPlayerRadar = true;
        if (text.contains("§n§o§m§i§n§i§m§a§p")) {
            noMinimap = true;
            noPlayerRadar = true;
        }
    }

    public static void reset() {
        noPlayerRadar = false;
        noMinimap = false;
    }

    /** Other players may be shown and warned about. */
    public static boolean playerRadar() {
        return !noPlayerRadar;
    }

    public static boolean minimap() {
        return !noMinimap;
    }

    public static boolean singleplayer() {
        return Minecraft.getInstance().hasSingleplayerServer();
    }
}
