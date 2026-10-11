package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.block.CampfireNet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The hand drill game: rub the mouse left and right, fast. Every full sweep is a stroke that
 * adds heat; the heat drains away all the time, so you have to keep your speed up. Fill the heat
 * bar before the time runs out and the tinder catches. Same panel, packets and result as the
 * spark-striking game ({@link LightingScreen}).
 */
public class FrictionScreen extends Screen {
    private static final int W = 250, H = 118, BAR_X = 35, BAR_W = 180;
    private static final long TIME_LIMIT = 22_000;
    /** A sweep has to cover at least this many GUI pixels to count as a stroke. */
    private static final int MIN_TRAVEL = 12;

    private final BlockPos pos;
    private final int strokesNeeded;
    private final float decay;
    private final boolean wet;
    private final Random random = new Random();

    private float heat;
    private int strokes;
    private int dir, travel, lastMouseX = Integer.MIN_VALUE;
    private long start = -1, last;
    private long endAt = -1;
    private boolean success, resolved;
    private final Deque<Long> recent = new ArrayDeque<>();
    private final List<float[]> smoke = new ArrayList<>(); // x, y, vx, vy, life
    private float stickX = Float.NaN;

    public FrictionScreen(BlockPos pos, int strokesNeeded, float decayPerSecond, boolean wet) {
        super(Component.translatable("gui.realisticsurvival.lighting.title"));
        this.pos = pos;
        this.strokesNeeded = Math.max(4, strokesNeeded);
        this.decay = Mth.clamp(decayPerSecond, 0.02F, 0.6F);
        this.wet = wet;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Strokes in the last second. */
    private float rate(long now) {
        while (!recent.isEmpty() && now - recent.peekFirst() > 1000) recent.pollFirst();
        return recent.size();
    }

    private float breakEven() {
        return decay * strokesNeeded;
    }

    private void stroke(long now) {
        if (endAt >= 0) return;
        if (start < 0) {
            start = now;
            last = now;
        }
        strokes++;
        recent.addLast(now);
        heat = Math.min(1.0F, heat + 1.0F / strokesNeeded);
        if (strokes % 3 == 0) sound(SoundEvents.WOOD_HIT, 0.8F + 0.6F * heat);
        if (heat > 0.2F) {
            int cx = (width - W) / 2 + W / 2;
            smoke.add(new float[] { cx + random.nextFloat() * 6 - 3, (height - H) / 2 + 52, (random.nextFloat() - 0.5F) * 14, -18 - random.nextFloat() * 14, 0.8F + random.nextFloat() * 0.5F });
        }
        if (heat >= 1.0F) {
            success = true;
            endAt = now + 800;
            sound(SoundEvents.FIRECHARGE_USE, 1.0F);
        }
    }

    /** For the client test: pretend to rub the stick. */
    public void simulate(int count) {
        long now = Util.getMillis();
        for (int i = 0; i < count; i++) stroke(now);
    }

    public float heat() {
        return heat;
    }

    public boolean succeeded() {
        return success;
    }

    private void sound(net.minecraft.sounds.SoundEvent event, float pitch) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch));
    }

    private void update(long now, int mouseX) {
        if (lastMouseX != Integer.MIN_VALUE && endAt < 0) {
            int dx = mouseX - lastMouseX;
            if (dx != 0) {
                int s = dx > 0 ? 1 : -1;
                if (s == dir) {
                    travel += Math.abs(dx);
                } else {
                    if (dir != 0 && travel >= MIN_TRAVEL) stroke(now);
                    dir = s;
                    travel = Math.abs(dx);
                }
            }
        }
        lastMouseX = mouseX;
        if (start >= 0) {
            float dt = Math.min(0.1F, (now - last) / 1000.0F);
            last = now;
            if (endAt < 0) {
                heat = Math.max(0.0F, heat - decay * dt);
                if (now - start > TIME_LIMIT) {
                    endAt = now + 900;
                    sound(SoundEvents.FIRE_EXTINGUISH, 1.2F);
                }
            }
            for (int i = smoke.size() - 1; i >= 0; i--) {
                float[] s = smoke.get(i);
                s[0] += s[2] * dt;
                s[1] += s[3] * dt;
                s[4] -= dt;
                if (s[4] <= 0) smoke.remove(i);
            }
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
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        long now = Util.getMillis();
        update(now, mouseX);
        super.extractRenderState(g, mouseX, mouseY, a);

        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + H + 1, 0xFF000000);
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0281A12);
        g.fill(x0, y0, x0 + W, y0 + 1, 0xFF6B4A33);
        int cx = x0 + W / 2;
        g.centeredText(font, title.getString(), cx, y0 + 7, 0xFFFFD9A0);
        g.centeredText(font, Component.translatable("item.realisticsurvival.hand_drill").getString(), cx, y0 + 18, 0xFFB8A48C);

        // the hearth board with its notch, and the drill stick that follows the mouse
        int bx = x0 + BAR_X, bw = BAR_W;
        int boardY = y0 + 44;
        g.fill(bx - 1, boardY - 1, bx + bw + 1, boardY + 14, 0xFF000000);
        g.fill(bx, boardY, bx + bw, boardY + 13, 0xFF6B4A2A);
        g.fill(bx, boardY, bx + bw, boardY + 2, 0xFF8A6238);
        g.fill(bx, boardY + 11, bx + bw, boardY + 13, 0xFF4A3220);
        float target = Mth.clamp(lastMouseX == Integer.MIN_VALUE ? cx : lastMouseX, bx + 34, bx + bw - 34);
        stickX = Float.isNaN(stickX) ? target : stickX + (target - stickX) * 0.6F;
        int sx = Math.round(stickX);
        g.fill(sx - 34, boardY - 5, sx + 34, boardY - 1, 0xFF000000);
        g.fill(sx - 33, boardY - 4, sx + 33, boardY - 2, 0xFFA1711F);
        g.fill(sx - 33, boardY - 4, sx + 33, boardY - 3, 0xFFC99A3E);
        for (int k = -28; k <= 28; k += 14) g.fill(sx + k, boardY - 4, sx + k + 2, boardY - 2, 0xFFF0F0F0); // string wraps
        // the notch glows as it heats up
        int glow = Math.round(255 * Mth.clamp(heat * 1.4F - 0.1F, 0, 1));
        int pulse = (int) (glow * (0.8F + 0.2F * Mth.sin(now / 90.0F)));
        g.fill(cx - 6, boardY + 3, cx + 6, boardY + 10, 0xFF1E140C);
        if (pulse > 0) g.fill(cx - 4, boardY + 5, cx + 4, boardY + 9, 0xFF000000 | (Mth.clamp(pulse, 0, 255) << 16) | ((pulse / 3) << 8));

        // heat bar
        int hy = y0 + 66;
        g.text(font, Component.translatable("gui.realisticsurvival.lighting.heat"), bx, hy - 1, 0xFFB8A48C, false);
        int mw = bw - 44, mxl = bx + 44;
        g.fill(mxl - 1, hy - 1, mxl + mw + 1, hy + 8, 0xFF000000);
        g.fill(mxl, hy, mxl + mw, hy + 7, 0xFF241812);
        int filled = Math.round(mw * heat);
        g.fill(mxl, hy, mxl + filled, hy + 7, 0xFFE8601A);
        g.fill(mxl, hy, mxl + filled, hy + 2, 0xFFFFB347);

        // rubbing speed against the speed you need to keep up
        int ry = y0 + 80;
        g.text(font, Component.translatable("gui.realisticsurvival.lighting.speed"), bx, ry - 1, 0xFFB8A48C, false);
        float rate = rate(now), need = breakEven();
        float scaleMax = Math.max(8.0F, need * 2.2F);
        g.fill(mxl - 1, ry - 1, mxl + mw + 1, ry + 8, 0xFF000000);
        g.fill(mxl, ry, mxl + mw, ry + 7, 0xFF241812);
        int rw = Math.round(mw * Mth.clamp(rate / scaleMax, 0, 1));
        g.fill(mxl, ry, mxl + rw, ry + 7, rate >= need ? 0xFF7FCB4A : 0xFFD9A441);
        int tick = mxl + Math.round(mw * Mth.clamp(need / scaleMax, 0, 1));
        g.fill(tick, ry - 2, tick + 1, ry + 9, 0xFFFFFFFF);

        // time
        float timeLeft = start < 0 ? 1.0F : 1.0F - Mth.clamp((now - start) / (float) TIME_LIMIT, 0.0F, 1.0F);
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
            hint = Component.translatable("gui.realisticsurvival.lighting.friction.hint").getString();
        }
        g.centeredText(font, hint, cx, y0 + 104, hintColour);

        for (float[] s : smoke) {
            float life = Mth.clamp(s[4] / 0.8F, 0.0F, 1.0F);
            int grey = 150 + (int) (60 * (1 - life));
            int col = ((int) (life * 170) << 24) | (grey << 16) | (grey << 8) | grey;
            int px = Math.round(s[0]), py = Math.round(s[1]);
            g.fill(px, py, px + 3, py + 3, col);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        g.fill(0, 0, width, height, 0x60000000);
    }
}
