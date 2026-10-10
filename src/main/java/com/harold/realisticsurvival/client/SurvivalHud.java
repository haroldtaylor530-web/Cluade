package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.RealisticSurvival;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Water drops above the food bar, and a body-temperature gauge above the armour bar. */
public final class SurvivalHud {
    private static final Identifier DROP_EMPTY = RealisticSurvival.id("hud/thirst_empty");
    private static final Identifier DROP_HALF = RealisticSurvival.id("hud/thirst_half");
    private static final Identifier DROP_FULL = RealisticSurvival.id("hud/thirst_full");
    private static final Identifier ICON_COLD = RealisticSurvival.id("hud/temp_cold");
    private static final Identifier ICON_OK = RealisticSurvival.id("hud/temp_normal");
    private static final Identifier ICON_HOT = RealisticSurvival.id("hud/temp_hot");

    private static boolean hidden(Minecraft mc) {
        LocalPlayer p = mc.player;
        return p == null || p.isCreative() || p.isSpectator() || mc.gui.hud.isHidden();
    }

    public static void renderThirst(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden(mc) || !Config.THIRST.getAsBoolean()) return;
        Hud hud = mc.gui.hud;
        int thirst = mc.player.getData(ModRegistry.THIRST_HUD);
        int right = g.guiWidth() / 2 + 91;
        int y = g.guiHeight() - hud.rightHeight;
        boolean shake = thirst <= 4 && mc.player.tickCount % (thirst * 3 + 1) == 0;
        for (int i = 0; i < 10; i++) {
            int x = right - i * 8 - 9;
            int yy = y + (shake ? mc.player.getRandom().nextInt(3) - 1 : 0);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, DROP_EMPTY, x, yy, 9, 9);
            int v = i * 2 + 1;
            if (v < thirst) g.blitSprite(RenderPipelines.GUI_TEXTURED, DROP_FULL, x, yy, 9, 9);
            else if (v == thirst) g.blitSprite(RenderPipelines.GUI_TEXTURED, DROP_HALF, x, yy, 9, 9);
        }
        hud.rightHeight += 10;
    }

    public static void renderTemperature(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden(mc) || !Config.TEMPERATURE.getAsBoolean()) return;
        Hud hud = mc.gui.hud;
        float temp = mc.player.getData(ModRegistry.TEMP_HUD) / 10.0F;
        int left = g.guiWidth() / 2 - 91;
        int y = g.guiHeight() - hud.leftHeight;

        Identifier icon = temp < 35.5F ? ICON_COLD : temp > 38.5F ? ICON_HOT : ICON_OK;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, left, y, 9, 9);

        // gauge from 31 C (left) to 43 C (right); normal 37 in the middle
        int bx = left + 11, by = y + 2, bw = 69, bh = 5;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF1A1A1A);
        for (int i = 0; i < bw; i++) {
            float t = 31.0F + 12.0F * i / (bw - 1);
            g.fill(bx + i, by, bx + i + 1, by + bh, colour(t));
        }
        int marker = bx + Mth.clamp(Math.round((temp - 31.0F) / 12.0F * (bw - 1)), 0, bw - 1);
        g.fill(marker - 1, by - 2, marker + 2, by + bh + 2, 0xFF000000);
        g.fill(marker, by - 1, marker + 1, by + bh + 1, 0xFFFFFFFF);
        hud.leftHeight += 10;
    }

    /** Blue when cold, green when normal, red when hot. */
    private static int colour(float t) {
        float r, gr, b;
        if (t < 35.5F) { float f = (t - 31.0F) / 4.5F; r = 0.25F * f; gr = 0.45F + 0.3F * f; b = 1.0F - 0.6F * f; }
        else if (t <= 38.5F) { r = 0.3F; gr = 0.8F; b = 0.35F; }
        else { float f = Math.min(1.0F, (t - 38.5F) / 4.5F); r = 0.95F; gr = 0.65F - 0.55F * f; b = 0.15F; }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (gr * 255) << 8) | (int) (b * 255);
    }

    private SurvivalHud() {}
}
