package com.harold.corpsedecay.client.config;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** A little body lying on the grass that rots to bones and crumbles, in a loop, following the decay settings. */
final class DecayPreview {
    static final int W = 26, H = 11;
    static final boolean[][] BONE = new boolean[H][W];
    static final boolean[][] FLESH = new boolean[H][W];

    static {
        // seen from above, lying on its back, head to the left
        for (int r = 3; r <= 7; r++)
            for (int c = 0; c <= 3; c++)
                if (!((r == 3 || r == 7) && (c == 0 || c == 3))) BONE[r][c] = true;
        BONE[4][1] = BONE[6][1] = false; // eye sockets
        for (int c = 4; c <= 16; c++) BONE[5][c] = true; // spine
        for (int c = 7; c <= 13; c += 2)
            for (int r = 2; r <= 8; r++) BONE[r][c] = true; // ribs
        for (int r = 3; r <= 7; r++)
            for (int c = 14; c <= 16; c++) BONE[r][c] = true; // pelvis
        BONE[4][15] = BONE[6][15] = false;
        for (int c = 6; c <= 16; c++) BONE[1][c] = BONE[9][c] = true; // arms
        for (int c = 17; c <= 25; c++) BONE[4][c] = BONE[6][c] = true; // legs
        BONE[3][25] = BONE[7][25] = true; // feet

        for (int r = 2; r <= 8; r++)
            for (int c = 0; c <= 4; c++)
                if (!((r == 2 || r == 8) && (c == 0 || c == 4))) FLESH[r][c] = true;
        for (int r = 4; r <= 6; r++) FLESH[r][5] = true;
        for (int r = 1; r <= 9; r++)
            for (int c = 6; c <= 16; c++) FLESH[r][c] = true;
        for (int r = 3; r <= 7; r++)
            for (int c = 17; c <= 25; c++) FLESH[r][c] = (r != 5 || c < 19);
    }

    private DecayPreview() {
    }

    static float hash(int i) {
        i = (i ^ 61) ^ (i >>> 16);
        i *= 9;
        i ^= i >>> 4;
        i *= 0x27d4eb2d;
        i ^= i >>> 15;
        return (i & 0xFFFFFF) / (float) 0x1000000;
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    static int alpha(int argb, float m) {
        int a = (int) (((argb >>> 24) & 255) * Math.max(0, Math.min(1, m)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    static int flesh(float decay) {
        if (decay < 0.35F) return lerp(0xFFC79A7E, 0xFF7F8C5A, decay / 0.35F);
        return lerp(0xFF7F8C5A, 0xFF5C4A36, (decay - 0.35F) / 0.45F);
    }

    /** Draws the body: decay 0 = fresh, 1 = bare bones; crumble 0..1 = bones falling away. */
    static void body(GuiGraphics g, int x, int y, int px, float decay, float crumble, float a, int boneCol) {
        for (int r = 0; r < H; r++) {
            for (int c = 0; c < W; c++) {
                int i = r * 31 + c * 7;
                int x0 = x + c * px, y0 = y + r * px;
                boolean fl = FLESH[r][c] && hash(i) * 0.9F + 0.06F > decay;
                if (fl) {
                    int col = flesh(decay);
                    boolean edge = r == 0 || c == 0 || r == H - 1 || !FLESH[r - 1][c] || !FLESH[r + 1 < H ? r + 1 : r][c];
                    if (edge) col = lerp(col, 0xFF000000, 0.25F);
                    if (hash(i + 999) < 0.15F) col = lerp(col, 0xFF000000, 0.12F);
                    g.fill(x0, y0, x0 + px, y0 + px, alpha(col, a));
                } else if (BONE[r][c] && hash(i + 77) >= crumble) {
                    int col = hash(i + 5) < 0.2F ? lerp(boneCol, 0xFF000000, 0.12F) : boneCol;
                    g.fill(x0, y0, x0 + px, y0 + px, alpha(col, a * (1 - crumble * 0.5F)));
                }
            }
        }
    }

    static String days(double minutes) {
        double d = minutes / 20.0;
        if (d < 0.05) return String.format("%.0f s", minutes * 60);
        if (Math.abs(d - Math.rint(d)) < 0.05) return (long) Math.rint(d) + (Math.rint(d) == 1 ? " day" : " days");
        return String.format("%.1f days", d);
    }

    /** The whole preview box. */
    static void render(GuiGraphics g, Font font, int x, int y, int w, int h, double toSkel, double skelMin,
            boolean flies, float a, float time) {
        // the box: night grass and soil
        g.fillGradient(x, y, x + w, y + h - 30, alpha(0xFF0E1018, a), alpha(0xFF18161E, a));
        int ground = y + h - 44;
        g.fill(x, ground, x + w, ground + 2, alpha(0xFF2C3A22, a));
        g.fill(x, ground + 2, x + w, y + h - 30, alpha(0xFF221810, a));
        for (int i = 0; i < w; i += 3) {
            if (hash(i * 13) < 0.5F) {
                int gh = 1 + (int) (hash(i * 17) * 3);
                g.fill(x + i, ground - gh, x + i + 1, ground, alpha(0xFF2C3A22, a));
            }
        }

        boolean forever = skelMin <= 0;
        double total = toSkel + (forever ? toSkel * 0.5 : skelMin);
        float split = (float) Math.max(0.25, Math.min(0.75, toSkel / total));

        // loop: rest fresh, sweep, rest at the end
        float period = 9F;
        float p = (time % period) / period;
        float m = p < 0.08F ? 0 : p > 0.9F ? 1 : (p - 0.08F) / 0.82F;
        float decay, crumble;
        double minutes;
        if (m < split) {
            decay = m / split;
            crumble = 0;
            minutes = toSkel * decay;
        } else {
            decay = 1;
            float k = (m - split) / (1 - split);
            crumble = forever ? 0 : Math.max(0, (k - 0.55F) / 0.45F);
            minutes = toSkel + (forever ? toSkel * 0.5 * k : skelMin * k);
        }
        if (forever && m >= split) m = Math.min(m, split + (1 - split) * 0.6F);

        int px = Math.max(2, Math.min(3, (w - 40) / (W * 2)));
        int bx = x + (w - W * px) / 2, by = ground - H * px + px;
        // shadow
        g.fill(bx - 2, ground - 1, bx + W * px + 2, ground + 1, alpha(0x50000000, a));
        int boneCol = lerp(0xFFEDE6D3, 0xFFB8AD94, (m - split) / (1 - split));
        body(g, bx, by, px, decay, crumble, a, boneCol);

        // flies while there is still flesh
        if (flies && decay < 0.9F) {
            for (int i = 0; i < 5; i++) {
                float ph = time * (2.3F + i * 0.4F) + i * 1.7F;
                int fx = bx + W * px / 2 + (int) (Math.sin(ph) * (W * px * 0.45F)) + (int) (Math.sin(ph * 2.7F) * 3);
                int fy = by - 4 + (int) (Math.cos(ph * 1.3F) * 5) + (int) (Math.sin(ph * 3.1F) * 2);
                g.fill(fx, fy, fx + 1, fy + 1, alpha(0xFF101010, a));
                if (((int) (time * 30 + i)) % 2 == 0) g.fill(fx - 1, fy - 1, fx, fy, alpha(0x60C0C0C0, a));
            }
        }

        // the timeline
        int tx0 = x + 8, tx1 = x + w - 8, ty = y + h - 22;
        int sx = tx0 + (int) ((tx1 - tx0) * split);
        for (int i = tx0; i < sx; i++) {
            float t = (i - tx0) / (float) (sx - tx0);
            g.fill(i, ty, i + 1, ty + 4, alpha(lerp(flesh(t), 0xFFEDE6D3, t * t), a));
        }
        if (forever) {
            for (int i = sx; i < tx1; i++)
                g.fill(i, ty, i + 1, ty + 4, alpha(((i / 3) % 2 == 0) ? 0xFFB8AD94 : 0xFF8A826E, a));
        } else {
            g.fillGradient(sx, ty, tx1, ty + 4, alpha(0xFFD8D0BC, a), alpha(0xFF6E665A, a));
        }
        int mx = tx0 + (int) ((tx1 - tx0) * m);
        g.fill(mx, ty - 4, mx + 1, ty + 8, alpha(0xFFFFFFFF, a));
        g.fill(mx - 2, ty - 6, mx + 3, ty - 5, alpha(0xFFFFFFFF, a));
        g.fill(mx - 1, ty - 5, mx + 2, ty - 4, alpha(0xFFFFFFFF, a));

        int dim = alpha(0xFF9C948A, a);
        if (a > 0.1F) {
            g.drawString(font, "fresh", tx0, ty + 8, dim, false);
            String sk = "skeleton";
            g.drawString(font, sk, Math.max(tx0 + font.width("fresh") + 6, sx - font.width(sk) / 2), ty + 8, dim, false);
            String end = forever ? "stays forever" : "gone";
            g.drawString(font, end, tx1 - font.width(end), ty + 8, dim, false);
            String when = forever && decay >= 1 ? "bones stay" : days(minutes);
            g.drawString(font, when, x + w - 6 - font.width(when), y + 5, alpha(0xFFB8D47A, a), false);
            g.drawString(font, decay < 0.3F ? "fresh" : decay < 1 ? "rotting" : crumble > 0 ? "crumbling" : "bones",
                    x + 6, y + 5, alpha(0xFFEDE6D3, a), false);
        }
    }
}
