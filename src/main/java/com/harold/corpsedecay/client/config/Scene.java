package com.harold.corpsedecay.client.config;

import static com.harold.corpsedecay.client.config.DecayPreview.alpha;
import static com.harold.corpsedecay.client.config.DecayPreview.hash;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * The animated background: a moonlit graveyard scrolling slowly past, with hills, dead trees swaying, mist,
 * fireflies over the graves, drifting ash and leaves, the odd flash of distant lightning, and a cut-away of the
 * ground below with old bones and an ammonite in the rock.
 */
final class Scene {
    private static final int MOTES = 70, FLIES = 12, LEAVES = 12;
    private final RandomSource rand = RandomSource.create(0x5EED);
    private final float[][] motes = new float[MOTES][4]; // x, y, speed, phase (screen fractions)
    private final float[][] flies = new float[FLIES][4]; // home x, height, speed, phase
    private final float[][] leaves = new float[LEAVES][4]; // x, y, speed, phase
    private float time, flash, nextFlash = 9F;

    Scene() {
        for (float[] m : motes) {
            m[0] = rand.nextFloat();
            m[1] = rand.nextFloat();
            m[2] = 0.01F + rand.nextFloat() * 0.03F;
            m[3] = rand.nextFloat() * 6.28F;
        }
        for (float[] f : flies) {
            f[0] = rand.nextFloat();
            f[1] = rand.nextFloat();
            f[2] = 0.3F + rand.nextFloat() * 0.6F;
            f[3] = rand.nextFloat() * 6.28F;
        }
        for (float[] l : leaves) {
            l[0] = rand.nextFloat();
            l[1] = rand.nextFloat();
            l[2] = 0.03F + rand.nextFloat() * 0.04F;
            l[3] = rand.nextFloat() * 6.28F;
        }
    }

    float time() {
        return time;
    }

    void render(GuiGraphics g, int w, int h, float dt) {
        time += dt;
        float scroll = time * 7F;
        int horizon = (int) (h * 0.60F);
        int ground = (int) (h * 0.80F);

        g.fillGradient(0, 0, w, h, 0xFF06080E, 0xFF1C1424);
        g.fillGradient(0, horizon - h / 4, w, horizon + 4, 0x00281828, 0x70442A3A);
        stars(g, w, horizon);
        int mr = Math.max(8, h / 26);
        int mcx = (int) (w * 0.80F), mcy = (int) (h * 0.17F);
        moon(g, mcx, mcy, mr);
        clouds(g, w, h, mcy);

        hills(g, w, h, scroll * 0.15F, horizon, h * 0.07F, 0xFF1A1726, 3.1F);
        trees(g, w, scroll * 0.3F, horizon + (ground - horizon) / 3, 0xFF141220, 0.8F, 1);
        hills(g, w, h, scroll * 0.3F, horizon + (ground - horizon) / 3, h * 0.04F, 0xFF141220, 7.7F);
        // mist lying in the valley
        int mist = horizon + (ground - horizon) * 2 / 3;
        for (int i = 0; i < 3; i++) {
            float o = (float) Math.sin(time * 0.2F + i * 2.1F) * 30F;
            g.fillGradient(0, mist - 18 + i * 6 + (int) (o * 0.1F), w, mist + 4 + i * 6, 0x00A0A4B8, 0x18A0A4B8);
        }
        trees(g, w, scroll * 0.6F, ground + 1, 0xFF0C0A12, 1.15F, 2);
        graveyard(g, w, h, scroll, ground);
        fireflies(g, w, ground);
        motes(g, w, h, dt);
        leaves(g, w, h, dt);

        if (time > nextFlash) {
            flash = 1F;
            nextFlash = time + 14F + rand.nextFloat() * 22F;
        }
        if (flash > 0) {
            float f = flash > 0.7F ? 1F : flash > 0.55F ? 0.3F : flash > 0.4F ? 0.8F : flash * 1.4F;
            g.fill(0, 0, w, h, alpha(0xFFB8C0E0, f * 0.16F));
            flash -= dt * 2.2F;
        }
        g.fillGradient(0, 0, w, h / 5, 0x90000000, 0x00000000);
        g.fillGradient(0, h - h / 6, w, h, 0x00000000, 0x90000000);
    }

    private void stars(GuiGraphics g, int w, int horizon) {
        for (int i = 0; i < 110; i++) {
            int x = (int) (hash(i * 3 + 1) * w);
            int y = (int) (hash(i * 3 + 2) * horizon * 0.9F);
            float tw = 0.45F + 0.55F * (float) Math.sin(time * (0.8F + hash(i * 3 + 3) * 2.5F) + i);
            float fade = 1F - (float) y / horizon;
            int c = alpha(0xFFE8E8FF, tw * fade * (hash(i * 7) < 0.15F ? 1F : 0.6F));
            g.fill(x, y, x + 1, y + 1, c);
            if (hash(i * 11) < 0.06F && tw > 0.85F) {
                g.fill(x - 1, y, x + 2, y + 1, alpha(c, 0.5F));
                g.fill(x, y - 1, x + 1, y + 2, alpha(c, 0.5F));
            }
        }
    }

    private static void disc(GuiGraphics g, int cx, int cy, float r, int col) {
        int ri = (int) Math.ceil(r);
        for (int dy = -ri; dy <= ri; dy++) {
            float s = r * r - dy * dy;
            if (s < 0) continue;
            int hw = (int) Math.sqrt(s);
            g.fill(cx - hw, cy + dy, cx + hw + 1, cy + dy + 1, col);
        }
    }

    private void moon(GuiGraphics g, int cx, int cy, int r) {
        float pulse = 1F + 0.05F * (float) Math.sin(time * 0.7F);
        disc(g, cx, cy, r * 3.2F * pulse, 0x08D8DCF0);
        disc(g, cx, cy, r * 2.2F * pulse, 0x0CD8DCF0);
        disc(g, cx, cy, r * 1.5F, 0x14E0E4F4);
        disc(g, cx, cy, r, 0xFFE6E2CE);
        disc(g, cx - r / 3, cy - r / 4, r * 0.28F, 0xFFCFCAB4);
        disc(g, cx + r / 3, cy + r / 3, r * 0.2F, 0xFFD6D1BB);
        disc(g, cx + r / 4, cy - r / 2, r * 0.12F, 0xFFD2CDB7);
    }

    private void clouds(GuiGraphics g, int w, int h, int moonY) {
        for (int i = 0; i < 5; i++) {
            float speed = 4F + hash(i * 5) * 5F;
            float len = w * (0.25F + hash(i * 5 + 1) * 0.3F);
            float x = ((time * speed + hash(i * 5 + 2) * (w + len)) % (w + len)) - len;
            int y = moonY - h / 10 + (int) (hash(i * 5 + 3) * h / 4);
            int hh = 3 + (int) (hash(i * 5 + 4) * 4);
            for (int dy = -hh; dy <= hh; dy++) {
                float k = 1F - (float) (dy * dy) / (hh * hh);
                int half = (int) (len / 2 * Math.sqrt(Math.max(0, k)));
                g.fill((int) (x + len / 2 - half), y + dy, (int) (x + len / 2 + half), y + dy + 1, 0x1A8A90A8);
            }
        }
    }

    private static void hills(GuiGraphics g, int w, int h, float off, int base, float amp, int col, float seed) {
        for (int x = 0; x < w; x += 2) {
            float wx = x + off;
            float n = (float) (0.6 * Math.sin(wx * 0.008 + seed) + 0.3 * Math.sin(wx * 0.021 + seed * 2)
                    + 0.1 * Math.sin(wx * 0.053 + seed * 3));
            g.fill(x, base - (int) (amp * (n + 0.4F)), x + 2, h, col);
        }
    }

    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, int t, int col) {
        int steps = (int) Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        for (int s = 0; s <= steps; s++) {
            float k = steps == 0 ? 0 : (float) s / steps;
            int x = Math.round(x0 + (x1 - x0) * k), y = Math.round(y0 + (y1 - y0) * k);
            g.fill(x, y, x + t, y + t, col);
        }
    }

    private void branch(GuiGraphics g, float x, float y, float ang, float len, int depth, int seed, int col, float sway) {
        float a = ang + sway * (4 - depth) * (float) Math.sin(time * 0.9F + seed);
        float x1 = x + (float) Math.sin(a) * len, y1 = y - (float) Math.cos(a) * len;
        line(g, x, y, x1, y1, depth > 2 ? 2 : 1, col);
        if (depth <= 0) return;
        int n = 2 + (hash(seed) < 0.3F ? 1 : 0);
        for (int i = 0; i < n; i++) {
            float spread = 0.35F + hash(seed * 7 + i) * 0.5F;
            float na = a + (i == 0 ? -spread : i == 1 ? spread : (hash(seed + i) - 0.5F) * 0.4F);
            branch(g, x1, y1, na, len * (0.55F + hash(seed * 3 + i) * 0.2F), depth - 1, seed * 5 + i + 1, col, sway);
        }
    }

    private void trees(GuiGraphics g, int w, float off, int base, int col, float scale, int layer) {
        float spacing = 230F * scale;
        int first = (int) Math.floor(off / spacing) - 1;
        for (int k = first; k < first + w / spacing + 3; k++) {
            if (hash(k * 13 + layer * 1000) < 0.4F) continue;
            float x = k * spacing + hash(k * 17 + layer) * spacing * 0.6F - off;
            float tall = (18F + hash(k * 19 + layer) * 14F) * scale;
            line(g, x, base, x, base - tall, layer == 2 ? 3 : 2, col);
            branch(g, x + 1, base - tall, -0.2F + hash(k * 23) * 0.4F, tall * 0.55F, 3, k * 31 + layer, col, 0.012F);
        }
    }

    private void graveyard(GuiGraphics g, int w, int h, float off, int ground) {
        int depth = h - ground;
        for (int x = 0; x < w; x += 2) {
            float wx = x + off;
            int gy = ground + (int) (2 * Math.sin(wx * 0.012));
            int b2 = gy + 3 + (int) (depth * 0.22F + 2 * Math.sin(wx * 0.02 + 1));
            int b3 = b2 + (int) (depth * 0.22F + 2 * Math.sin(wx * 0.017 + 2));
            int b4 = b3 + (int) (depth * 0.2F + 3 * Math.sin(wx * 0.011 + 3));
            g.fill(x, gy, x + 2, gy + 3, 0xFF141C12);
            g.fill(x, gy + 3, x + 2, b2, 0xFF261A13);
            g.fill(x, b2, x + 2, b3, 0xFF2F2218);
            g.fill(x, b3, x + 2, b4, 0xFF372A1F);
            g.fill(x, b4, x + 2, h, 0xFF26252B);
            int col = (int) Math.floor(wx / 2);
            if (hash(col * 3) < 0.18F) {
                int sy = gy + 4 + (int) (hash(col * 3 + 1) * (h - gy - 6));
                g.fill(x, sy, x + 1, sy + 1, sy > b4 ? 0xFF3A3940 : 0xFF46352A);
            }
            if (hash(col * 5 + 9) < 0.35F) {
                g.fill(x, gy - 1 - (int) (hash(col * 5) * 3), x + 1, gy, 0xFF141C12);
            }
        }
        // old bones in the soil and an ammonite in the rock
        if (depth > 40) {
            float sp = 760F;
            int first = (int) Math.floor(off / sp) - 1;
            for (int k = first; k < first + w / sp + 3; k++) {
                int bx = (int) (k * sp + 120 - off);
                int by = ground + (int) (depth * 0.32F);
                DecayPreview.body(g, bx, by, 2, 1F, 0.25F, 0.55F, 0xFFB9AE94);
                int ax = (int) (k * sp + 470 - off), ay = ground + (int) (depth * 0.78F);
                for (float t = 0; t < 12.5F; t += 0.15F) {
                    float r = 0.6F + t * 0.42F;
                    g.fill(ax + (int) (Math.cos(t) * r), ay + (int) (Math.sin(t) * r),
                            ax + (int) (Math.cos(t) * r) + 1, ay + (int) (Math.sin(t) * r) + 1, 0xC07A7062);
                }
            }
        }
        // gravestones with a moonlit edge
        float sp = 64F;
        int first = (int) Math.floor(off / sp) - 1;
        for (int k = first; k < first + w / sp + 3; k++) {
            float r = hash(k * 41 + 7);
            if (r < 0.3F) continue;
            int x = (int) (k * sp + hash(k * 43) * 30 - off);
            int gy = ground + (int) (2 * Math.sin((x + off) * 0.012));
            int hgt = 10 + (int) (hash(k * 47) * 9);
            int dark = 0xFF0B0910, lit = 0xFF2A2634;
            if (r < 0.62F) { // rounded headstone
                int sw = 7 + (int) (hash(k * 53) * 4);
                g.fill(x, gy - hgt + 2, x + sw, gy, dark);
                g.fill(x + 1, gy - hgt, x + sw - 1, gy - hgt + 2, dark);
                g.fill(x + sw - 1, gy - hgt + 2, x + sw, gy, lit);
                g.fill(x + 1, gy - hgt, x + sw - 1, gy - hgt + 1, lit);
            } else if (r < 0.85F) { // cross
                int cxx = x + 3;
                g.fill(cxx, gy - hgt - 3, cxx + 2, gy, dark);
                g.fill(cxx - 3, gy - hgt + 1, cxx + 5, gy - hgt + 3, dark);
                g.fill(cxx + 1, gy - hgt - 3, cxx + 2, gy, lit);
                g.fill(cxx - 3, gy - hgt + 1, cxx + 5, gy - hgt + 2, lit);
            } else { // obelisk
                g.fill(x + 1, gy - hgt - 8, x + 4, gy, dark);
                g.fill(x + 2, gy - hgt - 10, x + 3, gy - hgt - 8, dark);
                g.fill(x, gy - 2, x + 5, gy, dark);
                g.fill(x + 3, gy - hgt - 8, x + 4, gy - 2, lit);
            }
        }
    }

    private void fireflies(GuiGraphics g, int w, int ground) {
        for (int i = 0; i < FLIES; i++) {
            float[] f = flies[i];
            float t = time * f[2] + f[3];
            int x = (int) (f[0] * w + Math.sin(t) * 26 + Math.sin(t * 2.3F) * 8);
            int y = ground - 6 - (int) (f[1] * 40) + (int) (Math.cos(t * 1.4F) * 7);
            float glow = Mth.clamp(0.5F + 0.5F * (float) Math.sin(time * (1.5F + f[2]) + f[3] * 3), 0, 1);
            glow = glow * glow;
            g.fill(x - 2, y - 2, x + 3, y + 3, alpha(0xFFD8F07A, glow * 0.10F));
            g.fill(x - 1, y - 1, x + 2, y + 2, alpha(0xFFD8F07A, glow * 0.25F));
            g.fill(x, y, x + 1, y + 1, alpha(0xFFF0FFB0, 0.25F + glow * 0.75F));
        }
    }

    private void motes(GuiGraphics g, int w, int h, float dt) {
        for (float[] m : motes) {
            m[1] -= m[2] * dt;
            if (m[1] < -0.02F) {
                m[1] = 1.02F;
                m[0] = rand.nextFloat();
            }
            int x = (int) (m[0] * w + Math.sin(time * 0.6F + m[3]) * 10);
            int y = (int) (m[1] * h);
            float life = Mth.clamp(Math.min(m[1], 1 - m[1]) * 5F, 0, 1);
            g.fill(x, y, x + 1, y + 1, alpha(0xFFD8CCBC, life * 0.35F));
        }
    }

    private void leaves(GuiGraphics g, int w, int h, float dt) {
        for (float[] l : leaves) {
            l[1] += l[2] * dt;
            l[0] += 0.015F * dt;
            if (l[1] > 1.02F) {
                l[1] = -0.02F;
                l[0] = rand.nextFloat();
            }
            if (l[0] > 1.02F) l[0] -= 1.04F;
            int x = (int) (l[0] * w + Math.sin(time * 1.3F + l[3]) * 14);
            int y = (int) (l[1] * h);
            int col = l[3] > 3.14F ? 0xFF6A4026 : 0xFF8A5A2A;
            if (Math.sin(time * 4F + l[3]) > 0) g.fill(x, y, x + 2, y + 1, col);
            else g.fill(x, y, x + 1, y + 2, col);
        }
    }
}
