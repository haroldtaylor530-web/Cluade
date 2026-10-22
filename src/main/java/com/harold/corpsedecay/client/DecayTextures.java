package com.harold.corpsedecay.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.joml.Vector3f;

import com.harold.corpsedecay.CorpseDecay;
import com.harold.corpsedecay.CorpseKind;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;

/**
 * Makes the textures a corpse is drawn with. From the mob's own texture and its model it works out
 * which pixels are skin, which are clothes (on mobs with arms) and where the bones lie under each
 * pixel, then builds a series of stages: fresh and pale, bloated and discoloured, rotting black
 * with flesh and bone showing through, dried leathery scraps, and finally bare bone. Clothes stay
 * on (getting dirty and torn), horns and hooves stay, and the skeleton matches the mob's own shape.
 */
public final class DecayTextures {
    public static final int STAGES = 16;
    /** decay a burnt body starts at (matches CorpseEntity.BURNT_DECAY) */
    static final float BURNT_START = 0.75F;

    private static final Map<String, Source> SOURCES = new HashMap<>();
    private static int counter;

    private DecayTextures() {
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Source s : SOURCES.values()) {
            for (ResourceLocation rl : s.stages) {
                if (rl != null) mc.getTextureManager().release(rl);
            }
        }
        SOURCES.clear();
    }

    public static int stageFor(float decay) {
        return Mth.clamp(Math.round(decay * (STAGES - 1)), 0, STAGES - 1);
    }

    /** The texture to draw a corpse with, or the original if it can't be made. */
    /** Looks a body can have besides plain rotting. */
    public static final int NORMAL = 0, BURNT = 1, DROWNED = 2, FROZEN = 3, WITHERED = 5, FOSSIL = 10;

    public static ResourceLocation get(ResourceLocation original, ModelInfo info, CorpseKind kind, int variant, float decay) {
        if (kind == CorpseKind.NO_DECAY || original == null) return original;
        String key = original + "|" + System.identityHashCode(info) + "|" + kind.ordinal() + "|" + variant;
        Source src = SOURCES.computeIfAbsent(key, k -> Source.build(original, info, kind, variant));
        if (src.failed) return original;
        int stage = stageFor(decay);
        if (src.stages[stage] == null) src.stages[stage] = src.makeStage(stage);
        return src.stages[stage] != null ? src.stages[stage] : original;
    }

    /** Pre-analysed texture: per pixel what it is and what's under it. */
    private static final class Source {
        boolean failed;
        int w, h;
        int[] argb;
        byte[] what;
        int[] bone;
        boolean[] clothes;
        boolean[] painted;
        float[] hole, blot, tear;
        CorpseKind kind;
        boolean burnt;
        int variant;
        String id;
        final ResourceLocation[] stages = new ResourceLocation[STAGES];

        static Source build(ResourceLocation original, ModelInfo info, CorpseKind kind, int variant) {
            Source s = new Source();
            s.kind = kind;
            s.variant = variant;
            s.burnt = variant == BURNT;
            try {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(original);
                if (res.isEmpty()) {
                    s.failed = true;
                    return s;
                }
                try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                    s.w = img.getWidth();
                    s.h = img.getHeight();
                    s.argb = new int[s.w * s.h];
                    for (int y = 0; y < s.h; y++) {
                        for (int x = 0; x < s.w; x++) {
                            int abgr = img.getPixelRGBA(x, y);
                            s.argb[y * s.w + x] = abgrToArgb(abgr);
                        }
                    }
                }
                s.id = Integer.toHexString(original.toString().hashCode()) + "_v" + variant + "_" + (counter++);
                s.analyse(info, original.toString().hashCode());
            } catch (Throwable t) {
                CorpseDecayClientLog.warn("Couldn't make decay textures for " + original + ": " + t);
                s.failed = true;
            }
            return s;
        }

        void analyse(ModelInfo info, int seed) {
            int n = w * h;
            what = new byte[n];
            bone = new int[n];
            clothes = new boolean[n];
            painted = new boolean[n];
            byte[] partOf = new byte[n];
            boolean[] onHead = new boolean[n];
            hole = new float[n];
            blot = new float[n];
            tear = new float[n];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    hole[i] = 0.65F * Noise.value(x / 2.6F, y / 2.6F, seed) + 0.35F * Noise.hash(x, y, seed + 7);
                    blot[i] = Noise.value(x / 4.0F, y / 4.0F, seed + 13);
                    tear[i] = 0.5F * Noise.value(x / 1.7F, y / 1.7F, seed + 29) + 0.5F * Noise.hash(x, y, seed + 31);
                }
            }

            BonePainter painter = new BonePainter(info);
            BonePainter.Box skull = null;
            if (info.head != null && !info.head.cubes.isEmpty()) {
                skull = painter.box(info.head, biggest(info.head.cubes), true);
            }
            for (ModelInfo.Part part : info.parts) {
                if (part.cubes.isEmpty()) continue;
                ModelPart.Cube main = biggest(part.cubes);
                for (ModelPart.Cube cube : part.cubes) {
                    BonePainter.Box box = (part == info.head && cube == main && skull != null)
                            ? skull : painter.box(part, cube, cube == main);
                    if (part.cls == PartClass.HEAD) painter.classifyHeadBit(box, skull);
                    for (ModelPart.Polygon poly : ModelInfo.polygonsOf(cube)) {
                        paintFace(painter, box, poly, part, info, partOf, onHead);
                    }
                }
            }
            if (info.humanoid && kind == CorpseKind.FLESH) findClothes(onHead, partOf);
        }

        private void paintFace(BonePainter painter, BonePainter.Box box, ModelPart.Polygon poly, ModelInfo.Part part,
                               ModelInfo info, byte[] partOf, boolean[] onHead) {
            ModelPart.Vertex[] v = poly.vertices;
            if (v.length < 4) return;
            float umin = Float.MAX_VALUE, umax = -Float.MAX_VALUE, vmin = Float.MAX_VALUE, vmax = -Float.MAX_VALUE;
            for (ModelPart.Vertex vx : v) {
                umin = Math.min(umin, vx.u);
                umax = Math.max(umax, vx.u);
                vmin = Math.min(vmin, vx.v);
                vmax = Math.max(vmax, vx.v);
            }
            if (umax - umin < 1e-6F || vmax - vmin < 1e-6F) return;
            ModelPart.Vertex a = corner(v, umin, vmin), b = corner(v, umax, vmin), c = corner(v, umin, vmax);
            if (a == null || b == null || c == null) return;
            Vector3f ab = new Vector3f(b.pos).sub(a.pos);
            Vector3f ac = new Vector3f(c.pos).sub(a.pos);
            Vector3f normal = new Vector3f(poly.normal);
            int x0 = Math.max(0, (int) Math.floor(umin * w + 1e-4F)), x1 = Math.min(w, (int) Math.ceil(umax * w - 1e-4F));
            int y0 = Math.max(0, (int) Math.floor(vmin * h + 1e-4F)), y1 = Math.min(h, (int) Math.ceil(vmax * h - 1e-4F));
            Vector3f p = new Vector3f();
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int i = y * w + x;
                    if (painted[i]) continue;
                    float s = (((x + 0.5F) / w) - a.u) / (b.u - a.u);
                    float t = (((y + 0.5F) / h) - a.v) / (c.v - a.v);
                    p.set(a.pos).add(ab.x * s, ab.y * s, ab.z * s).add(ac.x * t, ac.y * t, ac.z * t);
                    float ss = (x + 0.5F - umin * w) / ((umax - umin) * w);
                    float tt = (y + 0.5F - vmin * h) / ((vmax - vmin) * h);
                    byte kindOfPixel = painter.paint(box, p, normal, ss, tt);
                    painted[i] = true;
                    what[i] = kindOfPixel;
                    partOf[i] = (byte) part.cls.ordinal();
                    onHead[i] = part == info.head || (part.cls == PartClass.HEAD && part.parent == info.head);
                    if (kindOfPixel == BonePainter.BONE) {
                        Vector3f nm = box.rot.transform(new Vector3f(normal));
                        float shade = 1.0F - 0.1F * nm.y - 0.04F * Math.abs(nm.x);
                        shade += (Noise.hash(x, y, 101) - 0.5F) * 0.12F;
                        bone[i] = boneColor(shade);
                    } else if (kindOfPixel == BonePainter.DARK) {
                        bone[i] = 0xFF2A2018;
                    }
                }
            }
        }

        private static ModelPart.Vertex corner(ModelPart.Vertex[] v, float u, float vv) {
            for (ModelPart.Vertex x : v) {
                if (Math.abs(x.u - u) < 1e-5F && Math.abs(x.v - vv) < 1e-5F) return x;
            }
            return null;
        }

        /**
         * Mobs with arms can wear clothes painted into their skin texture (a zombie's shirt and
         * trousers, an illager's robe). Skin is whatever colours the face is mostly made of;
         * anything on the body clearly different from those is treated as clothing.
         */
        private void findClothes(boolean[] onHead, byte[] partOf) {
            Map<Integer, int[]> bins = new HashMap<>();
            int total = 0;
            for (int i = 0; i < argb.length; i++) {
                if (!onHead[i] || (argb[i] >>> 24) < 128) continue;
                int c = argb[i];
                int key = ((c >> 20) & 0xF) << 8 | ((c >> 12) & 0xF) << 4 | ((c >> 4) & 0xF);
                int[] acc = bins.computeIfAbsent(key, k -> new int[4]);
                acc[0]++;
                acc[1] += (c >> 16) & 255;
                acc[2] += (c >> 8) & 255;
                acc[3] += c & 255;
                total++;
            }
            if (total == 0) return;
            List<int[]> sorted = new ArrayList<>(bins.values());
            sorted.sort((x, y) -> y[0] - x[0]);
            List<int[]> skin = new ArrayList<>();
            int covered = 0;
            for (int[] b : sorted) {
                skin.add(new int[] {b[1] / b[0], b[2] / b[0], b[3] / b[0]});
                covered += b[0];
                if (covered >= total * 0.8 || skin.size() >= 8) break;
            }
            for (int i = 0; i < argb.length; i++) {
                if (!painted[i] || onHead[i] || (argb[i] >>> 24) < 128) continue;
                int c = argb[i];
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                int best = Integer.MAX_VALUE;
                for (int[] k : skin) {
                    int dr = r - k[0], dg = g - k[1], db = b - k[2];
                    best = Math.min(best, dr * dr + dg * dg + db * db);
                }
                clothes[i] = best > 42 * 42;
            }
            // outer layers on the head (hats, hoods) are clothing unless they're skin coloured too
            for (int i = 0; i < argb.length; i++) {
                if (!painted[i] || !onHead[i] || partOf[i] != (byte) PartClass.OUTER.ordinal()) continue;
                int c = argb[i];
                if ((c >>> 24) < 128) continue;
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                int best = Integer.MAX_VALUE;
                for (int[] k : skin) {
                    int dr = r - k[0], dg = g - k[1], db = b - k[2];
                    best = Math.min(best, dr * dr + dg * dg + db * db);
                }
                clothes[i] = best > 42 * 42;
            }
        }

        ResourceLocation makeStage(int stage) {
            float p = stage / (float) (STAGES - 1);
            boolean last = stage == STAGES - 1;
            try {
                NativeImage img = new NativeImage(w, h, true);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int i = y * w + x;
                        img.setPixelRGBA(x, y, argbToAbgr(pixel(i, p, last)));
                    }
                }
                DynamicTexture tex = new DynamicTexture(img);
                ResourceLocation rl = new ResourceLocation(CorpseDecay.MODID, "decay/" + id + "_" + stage);
                Minecraft.getInstance().getTextureManager().register(rl, tex);
                return rl;
            } catch (Throwable t) {
                CorpseDecayClientLog.warn("Couldn't make decay stage " + stage + ": " + t);
                return null;
            }
        }

        private int pixel(int i, float p, boolean last) {
            int c = argb[i];
            int a = c >>> 24;
            if (a == 0) return 0;
            if (kind == CorpseKind.FOSSIL) return fossil(i, a);
            if (burnt) return charred(i, c, a, p, last);
            if (variant == WITHERED) return withered(i, c, a, p, last);
            if (variant == DROWNED || variant == FROZEN) return tinted(i, normal(i, c, a, p, last), p);
            return normal(i, c, a, p, last);
        }

        private int normal(int i, int c, int a, float p, boolean last) {
            if (kind == CorpseKind.BONES) {
                return withAlpha(mix(c, 0xFFCDC3A0, 0.18F * p), a);
            }
            if (what[i] == BonePainter.HORN) {
                return withAlpha(scale(c, 0.85F - 0.1F * p), a);
            }
            if (clothes[i]) {
                int dirty = mix(c, 0xFF584834, 0.42F * p);
                boolean torn = p > 0.55F && tear[i] < (p - 0.55F) * 0.85F;
                if (!torn) return withAlpha(dirty, a);
                return exposed(i, p, a);
            }
            float exposure = smooth(0.28F, 0.97F, p) * 1.04F;
            if (last || (painted[i] && hole[i] < exposure)) return exposed(i, p, a);
            int skin = skin(c, p, blot[i]);
            if (kind == CorpseKind.NO_BONES && p > 0.82F) {
                int fade = Math.round(a * (1F - smooth(0.82F, 1F, p)));
                return withAlpha(skin, fade);
            }
            return withAlpha(skin, a);
        }

        /**
         * A burnt body: blackened, cracked skin with the odd glowing ember, scorched bones showing
         * through, clothes mostly burnt away. As it carries on rotting the char flakes off down to
         * dark, scorched bone.
         */
        private int charred(int i, int c, int a, float p, boolean last) {
            if (kind == CorpseKind.BONES) return withAlpha(mix(c, 0xFF3A3028, 0.5F), a);
            if (what[i] == BonePainter.HORN) return withAlpha(scale(c, 0.35F), a);
            float gone = smooth(BURNT_START, 1F, p);
            if (clothes[i] && tear[i] > 0.62F - 0.3F * gone) {
                return withAlpha(mix(scale(c, 0.45F), 0xFF1A1512, 0.55F), a);
            }
            boolean exposed = last || (painted[i] && hole[i] < 0.38F + 0.7F * gone) || clothes[i];
            if (exposed) {
                if (kind == CorpseKind.FLESH && bone[i] != 0) {
                    int b = bone[i];
                    if (what[i] == BonePainter.BONE) b = scorched(b, i);
                    return withAlpha(b, a);
                }
                return 0;
            }
            int coal = mix(0xFF2C2622, 0xFF141110, blot[i]);
            coal = mix(coal, c, 0.08F);
            if (tear[i] < 0.07F && p < 0.9F) coal = mix(coal, 0xFF9A3412, 0.75F); // embers in the cracks
            if (kind == CorpseKind.NO_BONES && p > 0.82F) {
                return withAlpha(coal, Math.round(a * (1F - smooth(0.82F, 1F, p))));
            }
            return withAlpha(coal, a);
        }

        /** Pale and waterlogged, or frosted over. */
        private int tinted(int i, int c, float p) {
            if ((c >>> 24) == 0) return c;
            if (variant == DROWNED) {
                float f = 0.55F * (1F - smooth(0.4F, 1F, p));
                int waxy = mix(gray(c), 0xFFAAB8BC, 0.5F);
                return mix(c, waxy, f);
            }
            float f = 0.55F * (1F - 0.7F * smooth(0.2F, 1F, p));
            int out = mix(c, 0xFFD6ECFA, f);
            if (Noise.hash(i % w, i / w, 77) > 0.93F) out = mix(out, 0xFFF6FCFF, 0.8F * (1F - p)); // ice crystals
            return out;
        }

        /** A withered body: shrivelled to an ashen grey-black husk with dark veins. */
        private int withered(int i, int c, int a, float p, boolean last) {
            if (kind == CorpseKind.BONES) return withAlpha(mix(c, 0xFF4A4644, 0.45F), a);
            if (what[i] == BonePainter.HORN) return withAlpha(scale(c, 0.5F), a);
            boolean exposed = last || (painted[i] && hole[i] < smooth(0.5F, 1F, p) * 1.05F);
            if (clothes[i] && !exposed) return withAlpha(mix(scale(c, 0.6F), 0xFF2E2A28, 0.4F), a);
            if (exposed) {
                if (kind == CorpseKind.FLESH && bone[i] != 0) {
                    int b = bone[i];
                    if (what[i] == BonePainter.BONE) b = mix(b, 0xFF5E5A56, 0.45F);
                    return withAlpha(b, a);
                }
                return 0;
            }
            int husk = mix(mix(gray(c), 0xFF3A3634, 0.75F), 0xFF1E1C1B, blot[i] * 0.6F);
            if (tear[i] < 0.1F) husk = mix(husk, 0xFF0C0B0B, 0.8F); // dark veins
            if (kind == CorpseKind.NO_BONES && p > 0.82F) return withAlpha(husk, Math.round(a * (1F - smooth(0.82F, 1F, p))));
            return withAlpha(husk, a);
        }

        /** Bones turned to stone: only the skeleton, in the colour of the rock it lay in. */
        private int fossil(int i, int a) {
            if (bone[i] == 0) return 0;
            int x = i % w, y = i / w;
            if (what[i] == BonePainter.DARK) return withAlpha(0xFF201E1C, a);
            int rock = switch (variant - FOSSIL) {
                case 1 -> 0xFFCDB98C; // sandstone
                case 2 -> 0xFF55555C; // deepslate
                case 3 -> 0xFFE9E3D2; // bleached
                default -> 0xFF8E8E8A; // stone
            };
            float shade = brightness(bone[i]) / 220F;
            int out = scale(rock, Math.min(1.15F, shade));
            float n = Noise.hash(x, y, 313);
            if (n > 0.86F) out = scale(out, 0.78F);       // mineral flecks
            else if (n < 0.08F) out = scale(out, 1.12F);
            if (variant - FOSSIL == 3 && tear[i] < 0.08F) out = scale(out, 0.72F); // weathering cracks
            return withAlpha(out, a);
        }

        /** Burnt bone stays burnt: charcoal grey, blackened in patches, chalky where it burnt hottest. */
        private int scorched(int bone, int i) {
            int b = mix(bone, 0xFF3C342E, 0.66F);
            if (blot[i] > 0.58F) b = mix(b, 0xFF15120F, Math.min(1F, (blot[i] - 0.58F) * 2.4F));
            if (tear[i] > 0.9F) b = mix(b, 0xFFB4AC9E, 0.55F);
            return b;
        }

        /** What shows where the skin has rotted away. */
        private int exposed(int i, float p, int a) {
            if (kind == CorpseKind.FLESH && bone[i] != 0) {
                int b = bone[i];
                if (what[i] == BonePainter.BONE) {
                    if (p < 0.62F) b = mix(b, 0xFF7E2E24, Mth.clamp((0.62F - p) / 0.3F, 0F, 1F) * 0.7F);
                    if (p > 0.85F) b = mix(b, 0xFFD8CCA4, (p - 0.85F) / 0.15F * 0.35F);
                }
                return withAlpha(b, a);
            }
            if (p < 0.52F && kind == CorpseKind.FLESH) {
                float f = Mth.clamp((p - 0.28F) / 0.24F, 0F, 1F);
                return withAlpha(mix(0xFF7A1E1A, 0xFF3C1712, f), a);
            }
            return 0;
        }

        /** Skin going pale, then green and purple, then black and wet, then dry and leathery. */
        private static int skin(int c, float p, float blot) {
            int gray = gray(c);
            float f0 = Math.min(1F, p / 0.12F);
            int out = scale(mix(c, gray, f0 * 0.3F), 1F - 0.12F * f0);
            if (p > 0.12F) {
                float f = Mth.clamp((p - 0.12F) / 0.23F, 0F, 1F);
                int target = mix(0xFF707A4E, 0xFF5C4E62, blot);
                out = mix(out, target, f * (0.35F + 0.45F * blot));
            }
            if (p > 0.35F) {
                float f = Mth.clamp((p - 0.35F) / 0.3F, 0F, 1F);
                int target = mix(0xFF483628, 0xFF261E18, blot);
                out = mix(out, target, f * 0.85F);
            }
            if (p > 0.65F) {
                float f = Mth.clamp((p - 0.65F) / 0.3F, 0F, 1F);
                out = mix(out, 0xFF5C4A34, f * 0.6F);
            }
            return out;
        }
    }

    // ---- colour helpers ------------------------------------------------------------------------------

    private static ModelPart.Cube biggest(List<ModelPart.Cube> cubes) {
        ModelPart.Cube best = cubes.get(0);
        float bv = -1;
        for (ModelPart.Cube c : cubes) {
            float v = (c.maxX - c.minX) * (c.maxY - c.minY) * (c.maxZ - c.minZ);
            if (v > bv) {
                bv = v;
                best = c;
            }
        }
        return best;
    }

    static int boneColor(float shade) {
        int r = Mth.clamp(Math.round(226 * shade), 0, 255);
        int g = Mth.clamp(Math.round(219 * shade), 0, 255);
        int b = Mth.clamp(Math.round(196 * shade), 0, 255);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0F, 1F);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int r = Math.round(ar + (br - ar) * t), g = Math.round(ag + (bg - ag) * t), bl = Math.round(ab + (bb - ab) * t);
        return (a & 0xFF000000) | r << 16 | g << 8 | bl;
    }

    static int scale(int c, float f) {
        int r = Mth.clamp(Math.round(((c >> 16) & 255) * f), 0, 255);
        int g = Mth.clamp(Math.round(((c >> 8) & 255) * f), 0, 255);
        int b = Mth.clamp(Math.round((c & 255) * f), 0, 255);
        return (c & 0xFF000000) | r << 16 | g << 8 | b;
    }

    static float brightness(int c) {
        return ((c >> 16) & 255) * 0.3F + ((c >> 8) & 255) * 0.59F + (c & 255) * 0.11F;
    }

    static int gray(int c) {
        int l = Math.round(((c >> 16) & 255) * 0.3F + ((c >> 8) & 255) * 0.59F + (c & 255) * 0.11F);
        return (c & 0xFF000000) | l << 16 | l << 8 | l;
    }

    static int withAlpha(int c, int a) {
        return (a & 255) << 24 | (c & 0xFFFFFF);
    }

    static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0F, 1F);
        return t * t * (3 - 2 * t);
    }

    static int abgrToArgb(int c) {
        return (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
    }

    static int argbToAbgr(int c) {
        return abgrToArgb(c);
    }
}
