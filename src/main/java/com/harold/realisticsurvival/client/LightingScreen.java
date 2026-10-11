package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.block.CampfireNet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import com.mojang.blaze3d.platform.InputConstants;

/**
 * The fire-lighting game. A spark swings back and forth along a bar; press space or click while
 * it is inside the glowing patch of tinder to build up an ember. Enough good strikes and the
 * tinder catches; three misses or running out of time and it doesn't. A bow drill needs one
 * more strike and has a smaller sweet spot than flint and steel; rain makes it smaller still.
 */
public class LightingScreen extends Screen {
    private static final int W = 220, H = 118;
    private static final long TIME_LIMIT = 20_000;
    private static final int MAX_MISSES = 3;

    private final BlockPos pos;
    private final int hitsNeeded;
    private final float zoneWidth;
    private final boolean bowDrill;
    private final boolean wet;
    private final Random random = new Random();

    private float zoneCentre;
    private int hits, misses;
    private double phase;
    private float speed;
    private long start = -1, last;
    private long flashUntil;
    private int flashColour;
    private long endAt = -1;
    private boolean success, resolved;
    private final List<float[]> sparks = new ArrayList<>(); // x, y, vx, vy, life

    public LightingScreen(BlockPos pos, int hitsNeeded, float zoneWidth, boolean bowDrill, boolean wet) {
        super(Component.translatable("gui.realisticsurvival.lighting.title"));
        this.pos = pos;
        this.hitsNeeded = Math.max(1, hitsNeeded);
        this.zoneWidth = Mth.clamp(zoneWidth, 0.05F, 0.6F);
        this.bowDrill = bowDrill;
        this.wet = wet;
        this.speed = bowDrill ? 3.6F : 3.0F;
        newZone();
    }

    private void newZone() {
        zoneCentre = zoneWidth / 2 + random.nextFloat() * (1.0F - zoneWidth);
    }

    /** Where the spark is along the bar, 0..1. */
    private float marker() {
        return (float) (0.5 - 0.5 * Math.cos(phase));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void strike() {
        if (endAt >= 0 || start < 0) return;
        float m = marker();
        boolean inside = Math.abs(m - zoneCentre) <= zoneWidth / 2;
        int left = (width - W) / 2 + 20;
        float sx = left + m * 180, sy = (height - H) / 2 + 47;
        if (inside) {
            hits++;
            speed += 0.5F;
            flash(0xFFFFB347);
            for (int i = 0; i < 14; i++) {
                sparks.add(new float[] { sx, sy, (random.nextFloat() - 0.5F) * 120, -40 - random.nextFloat() * 90, 0.5F + random.nextFloat() * 0.4F });
            }
            sound(bowDrill ? SoundEvents.AXE_STRIP.value() : SoundEvents.FLINTANDSTEEL_USE, 0.9F + hits * 0.1F);
            if (hits >= hitsNeeded) {
                success = true;
                endAt = Util.getMillis() + 700;
                sound(SoundEvents.FIRECHARGE_USE, 1.0F);
            } else {
                newZone();
            }
        } else {
            misses++;
            flash(0xFF7F1D1D);
            sound(SoundEvents.DISPENSER_FAIL, 0.7F);
            if (misses >= MAX_MISSES) {
                endAt = Util.getMillis() + 900;
                sound(SoundEvents.FIRE_EXTINGUISH, 1.2F);
            }
        }
    }

    private void flash(int colour) {
        flashColour = colour;
        flashUntil = Util.getMillis() + 180;
    }

    private void sound(net.minecraft.sounds.SoundEvent event, float pitch) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
    }

    private void update(long now) {
        if (start < 0) {
            start = now;
            last = now;
        }
        float dt = Math.min(0.1F, (now - last) / 1000.0F);
        last = now;
        if (endAt < 0) {
            phase += speed * dt;
            if (now - start > TIME_LIMIT) {
                endAt = now + 900;
                sound(SoundEvents.FIRE_EXTINGUISH, 1.2F);
            }
        }
        for (int i = sparks.size() - 1; i >= 0; i--) {
            float[] s = sparks.get(i);
            s[0] += s[2] * dt;
            s[1] += s[3] * dt;
            s[3] += 160 * dt;
            s[4] -= dt;
            if (s[4] <= 0) sparks.remove(i);
        }
        if (endAt >= 0 && now >= endAt) finish();
    }

    private void finish() {
        if (resolved) return;
        resolved = true;
        ClientPacketDistributor.sendToServer(new CampfireNet.LightingResult(pos, success));
        if (minecraft != null) minecraft.gui.setScreen(null);
    }

    @Override
    public void onClose() {
        if (!resolved) {
            resolved = true;
            ClientPacketDistributor.sendToServer(new CampfireNet.LightingResult(pos, false));
        }
        super.onClose();
    }

    @Override
    public void removed() {
        if (!resolved) {
            resolved = true;
            ClientPacketDistributor.sendToServer(new CampfireNet.LightingResult(pos, false));
        }
        super.removed();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE || event.key() == InputConstants.KEY_RETURN) {
            strike();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        strike();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        long now = Util.getMillis();
        update(now);
        super.extractRenderState(g, mouseX, mouseY, a);

        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        // panel
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 0xFF000000);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0281A12);
        g.fill(x0, y0, x0 + W, y0 + 1, 0xFF6B4A33);
        int cx = x0 + W / 2;
        g.centeredText(font, title.getString(), cx, y0 + 7, 0xFFFFD9A0);
        String tool = Component.translatable(bowDrill ? "item.realisticsurvival.bow_drill" : "item.minecraft.flint_and_steel").getString();
        g.centeredText(font, tool, cx, y0 + 18, 0xFFB8A48C);

        // the bar with its glowing sweet spot
        int bx = x0 + 20, by = y0 + 40, bw = 180, bh = 14;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF000000);
        g.fill(bx, by, bx + bw, by + bh, 0xFF3B2A20);
        int zl = bx + Math.round((zoneCentre - zoneWidth / 2) * bw), zr = bx + Math.round((zoneCentre + zoneWidth / 2) * bw);
        float pulse = 0.75F + 0.25F * Mth.sin(now / 120.0F);
        g.fill(zl, by, zr, by + bh, argb(1.0F, 0.55F * pulse + 0.2F, 0.25F * pulse, 0.05F));
        g.fill(zl + 2, by + 3, zr - 2, by + bh - 3, argb(1.0F, 1.0F * pulse, 0.62F * pulse, 0.15F));
        if (now < flashUntil) g.fill(bx, by, bx + bw, by + bh, (flashColour & 0x00FFFFFF) | 0x66000000);
        int mx = bx + Math.round(marker() * bw);
        g.fill(mx - 2, by - 4, mx + 2, by + bh + 4, 0xFF000000);
        g.fill(mx - 1, by - 3, mx + 1, by + bh + 3, 0xFFFFF4D6);

        // ember building up
        int ey = y0 + 66;
        g.text(font, Component.translatable("gui.realisticsurvival.lighting.ember"), bx, ey - 1, 0xFFB8A48C, false);
        int mw = bw - 44, mxl = bx + 44;
        g.fill(mxl - 1, ey - 1, mxl + mw + 1, ey + 8, 0xFF000000);
        g.fill(mxl, ey, mxl + mw, ey + 7, 0xFF241812);
        int filled = Math.round(mw * (float) hits / hitsNeeded);
        g.fill(mxl, ey, mxl + filled, ey + 7, 0xFFE8601A);
        g.fill(mxl, ey, mxl + filled, ey + 2, 0xFFFFB347);

        // misses and time
        int my = y0 + 82;
        g.text(font, Component.translatable("gui.realisticsurvival.lighting.misses"), bx, my, 0xFFB8A48C, false);
        for (int i = 0; i < MAX_MISSES; i++) {
            int px = bx + 44 + i * 12;
            g.fill(px, my, px + 8, my + 8, i < misses ? 0xFFB91C1C : 0xFF4A3A30);
        }
        float timeLeft = 1.0F - Mth.clamp((now - start) / (float) TIME_LIMIT, 0.0F, 1.0F);
        g.fill(bx, y0 + 96, bx + bw, y0 + 98, 0xFF3B2A20);
        g.fill(bx, y0 + 96, bx + Math.round(bw * timeLeft), y0 + 98, 0xFFB8A48C);

        String hint;
        int hintColour = 0xFFE7D5BE;
        if (endAt >= 0) {
            hint = Component.translatable(success ? "gui.realisticsurvival.lighting.caught" : "gui.realisticsurvival.lighting.failed").getString();
            hintColour = success ? 0xFFFFB347 : 0xFFF87171;
        } else if (wet) {
            hint = Component.translatable("gui.realisticsurvival.lighting.wet").getString();
            hintColour = 0xFF93C5FD;
        } else {
            hint = Component.translatable("gui.realisticsurvival.lighting.hint").getString();
        }
        g.centeredText(font, hint, cx, y0 + 104, hintColour);

        for (float[] s : sparks) {
            float life = Mth.clamp(s[4] / 0.8F, 0.0F, 1.0F);
            int col = argb(life, 1.0F, 0.75F + 0.25F * life, 0.3F * life);
            int sx = Math.round(s[0]), sy = Math.round(s[1]);
            g.fill(sx, sy, sx + 2, sy + 2, col);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0x60000000);
    }

    private static int argb(float a, float r, float g, float b) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | ((int) (Mth.clamp(r, 0, 1) * 255) << 16)
                | ((int) (Mth.clamp(g, 0, 1) * 255) << 8) | (int) (Mth.clamp(b, 0, 1) * 255);
    }
}
