package com.harold.corpsedecay.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.harold.corpsedecay.corpse.WhaleRemainsEntity;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a whale skeleton as a set of bones (boxes placed and turned in the whale's own frame:
 * x along the body with the head towards +x, y up from the sea floor, z across), and what grows
 * on it. Lengths are in blocks. A blue whale or humpback has a broad flat skull made of two long
 * plates, the lower jaw in two bowed halves, and fingered flippers (a humpback's as long as a third
 * of its body); a sperm whale has a deep basin of a skull with a high crest, and one long narrow
 * toothed jaw.
 */
public final class WhaleSkeleton {
    /** One bone: a box {@code size} big, centred and turned by {@code at}, its colour scaled by {@code shade}. */
    public record Bone(Matrix4f at, Vector3f size, float shade) {
    }

    /** Something growing on the bones: a block drawn at {@code pos}, {@code scale} the size of a block. */
    public record Growth(Vector3f pos, BlockState state, float scale, float yaw) {
    }

    public final List<Bone> bones = new ArrayList<>();
    public final List<Growth> growths = new ArrayList<>();
    private final Random rand;
    private final float len;
    private final float whole;

    private WhaleSkeleton(float len, int seed, float whole) {
        this.len = len;
        this.rand = new Random(seed);
        this.whole = whole;
    }

    public static WhaleSkeleton build(int kind, float length, int seed, float whole) {
        WhaleSkeleton s = new WhaleSkeleton(length, seed, whole);
        if (kind == WhaleRemainsEntity.SPERM) s.sperm();
        else s.baleen(kind == WhaleRemainsEntity.HUMPBACK);
        s.life();
        return s;
    }

    // ---- placing bones ---------------------------------------------------------------------------

    private void box(float x, float y, float z, float sx, float sy, float sz, Quaternionf rot) {
        bones.add(new Bone(new Matrix4f().translation(x, y, z).rotate(rot), new Vector3f(sx, sy, sz), 0.86F + rand.nextFloat() * 0.14F));
    }

    private void box(float x, float y, float z, float sx, float sy, float sz) {
        box(x, y, z, sx, sy, sz, new Quaternionf());
    }

    /** A bone running from {@code a} to {@code b}, {@code t} thick and {@code d} deep (across its run). */
    private void rod(Vector3f a, Vector3f b, float t, float d) {
        Vector3f dir = new Vector3f(b).sub(a);
        float l = dir.length();
        if (l < 1e-4F) return;
        dir.div(l);
        Quaternionf q = new Quaternionf().rotationTo(1, 0, 0, dir.x, dir.y, dir.z);
        // keep the bone's flat side facing up where it can
        Vector3f up = q.transform(new Vector3f(0, 1, 0));
        Vector3f want = new Vector3f(0, 1, 0).sub(new Vector3f(dir).mul(dir.y));
        if (want.lengthSquared() > 1e-4F) {
            want.normalize();
            float ang = (float) Math.atan2(new Vector3f(up).cross(want).dot(dir), up.dot(want));
            q = new Quaternionf().rotationAxis(ang, dir).mul(q);
        }
        box((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2, l + t * 0.4F, t, d, q);
    }

    /** A chain of rods through the given points. */
    private void chain(List<Vector3f> pts, float t, float d) {
        for (int i = 1; i < pts.size(); i++) rod(pts.get(i - 1), pts.get(i), t, d);
    }

    private boolean scattered(float chance) {
        return rand.nextFloat() < (1F - whole) * chance;
    }

    // ---- baleen whales ---------------------------------------------------------------------------

    private void baleen(boolean humpback) {
        float L = len;
        float skullLen = L * 0.25F, skullBack = L / 2 - skullLen;
        float width = L * 0.13F, height = L * 0.09F;
        spine(-L / 2, skullBack - 0.2F, height, L * 0.032F);
        ribs(skullBack - 0.6F, L * 0.3F, 14, width, height);
        // the skull: a broad cranium, then two long flat plates narrowing to the tip of the snout
        float crW = L * 0.2F, crL = skullLen * 0.24F;
        box(skullBack + crL / 2, 0.75F, 0, crL, L * 0.06F, crW);
        box(skullBack + crL * 0.35F, 0.75F + L * 0.04F, 0, crL * 0.55F, L * 0.035F, crW * 0.55F); // the back of the head
        int steps = 8;
        float roL = skullLen - crL;
        for (int k = 0; k < steps; k++) {
            float t0 = k / (float) steps, t1 = (k + 1) / (float) steps, tm = (t0 + t1) / 2;
            float w = crW * 0.92F * (float) Math.pow(1 - tm, 0.85) + 0.3F;
            float x = skullBack + crL + roL * tm, segL = roL / steps + 0.05F;
            float plate = w / 2 - 0.12F;
            box(x, 0.45F, plate / 2 + 0.14F, segL, 0.32F, plate);
            box(x, 0.45F, -plate / 2 - 0.14F, segL, 0.32F, plate);
            box(x, 0.52F, 0, segL, 0.22F, 0.18F); // the narrow bone down the middle
        }
        // the lower jaw: two great bowed bones lying either side, sometimes knocked askew
        for (int side : new int[] {-1, 1}) {
            float dx = 0, dz = 0, turn = 0;
            if (scattered(1.4F)) {
                dx = -1.5F - rand.nextFloat() * 2;
                dz = side * (0.8F + rand.nextFloat() * 1.5F);
                turn = side * (0.15F + rand.nextFloat() * 0.25F);
            }
            List<Vector3f> pts = new ArrayList<>();
            for (int k = 0; k <= 10; k++) {
                float t = k / 10F;
                float x = skullBack + skullLen * 0.05F + (skullLen + 0.6F) * t;
                float hw = crW / 2 * (float) Math.pow(1 - t, 0.75) + 0.35F;
                float bow = (float) Math.sin(t * Math.PI) * L * 0.035F;
                float z = side * (hw + bow + 0.35F);
                float cx = skullBack, rx = x - cx, rz = z;
                pts.add(new Vector3f(cx + rx * (float) Math.cos(turn) - rz * (float) Math.sin(turn) + dx, 0.32F,
                        rx * (float) Math.sin(turn) + rz * (float) Math.cos(turn) + dz));
            }
            chain(pts, L * 0.022F, L * 0.016F);
        }
        flippers(skullBack - L * 0.03F, width + 0.4F, humpback ? L * 0.3F : L * 0.11F);
        // little leftover hip bones, all a whale has of legs
        for (int side : new int[] {-1, 1}) {
            rod(new Vector3f(-L * 0.17F, 0.15F, side * 1.0F), new Vector3f(-L * 0.17F - 0.8F, 0.12F, side * 1.4F), 0.18F, 0.18F);
        }
    }

    // ---- sperm whales ----------------------------------------------------------------------------

    private void sperm() {
        float L = len;
        float skullLen = L * 0.32F, skullBack = L / 2 - skullLen;
        float width = L * 0.12F, height = L * 0.085F;
        spine(-L / 2, skullBack - 0.2F, height, L * 0.034F);
        ribs(skullBack - 0.6F, L * 0.27F, 11, width, height);
        float sw = L * 0.21F, crest = L * 0.14F;
        // the high crest at the back, curving round into the side walls of the basin
        box(skullBack + 0.35F, crest / 2, 0, 0.7F, crest, sw);
        for (int side : new int[] {-1, 1}) {
            for (int k = 0; k < 5; k++) {
                float t = (k + 0.5F) / 5F;
                float h = crest * (1 - t) * 0.85F + 0.4F;
                box(skullBack + 0.7F + skullLen * 0.55F * t, h / 2, side * (sw / 2 - 0.3F - t * 0.4F), skullLen * 0.55F / 5 + 0.05F, h, 0.5F);
            }
        }
        box(skullBack + skullLen * 0.3F, 0.22F, 0, skullLen * 0.55F, 0.44F, sw * 0.9F); // the floor of the basin
        // the long narrow snout
        for (int k = 0; k < 6; k++) {
            float t = (k + 0.5F) / 6F;
            float w = 1.4F * (1 - t) + 0.45F;
            box(skullBack + skullLen * 0.6F + skullLen * 0.4F * t, 0.3F, 0, skullLen * 0.4F / 6 + 0.05F, 0.5F, w);
        }
        // the jaw, fallen away to one side: two arms joining into one long toothed bone
        int side = rand.nextBoolean() ? 1 : -1;
        float off = side * (sw / 2 + 1.6F + rand.nextFloat());
        float turn = side * (rand.nextFloat() * 0.25F);
        float jawBack = skullBack + skullLen * 0.1F, jawLen = skullLen * 0.95F, join = jawBack + jawLen * 0.35F;
        Quaternionf q = new Quaternionf().rotationY(-turn);
        for (int arm : new int[] {-1, 1}) {
            Vector3f a = q.transform(new Vector3f(jawBack - skullBack, 0.25F, arm * 0.9F)).add(skullBack, 0, off);
            Vector3f b = q.transform(new Vector3f(join - skullBack, 0.25F, 0)).add(skullBack, 0, off);
            rod(a, b, 0.5F, 0.35F);
        }
        Vector3f j0 = q.transform(new Vector3f(join - skullBack, 0.25F, 0)).add(skullBack, 0, off);
        Vector3f j1 = q.transform(new Vector3f(jawBack + jawLen - skullBack, 0.22F, 0)).add(skullBack, 0, off);
        rod(j0, j1, 0.45F, 0.55F);
        int teeth = 18 + rand.nextInt(6);
        for (int i = 0; i < teeth; i++) {
            if (scattered(0.6F)) continue;
            float t = (i / 2 + 0.5F) / (teeth / 2F);
            Vector3f p = new Vector3f(j0).lerp(j1, t);
            float zz = (i % 2 == 0 ? 1 : -1) * 0.18F;
            Vector3f across = q.transform(new Vector3f(0, 0, zz));
            box(p.x + across.x, p.y + 0.38F, p.z + across.z, 0.16F, 0.36F, 0.16F, new Quaternionf().rotationZ((rand.nextFloat() - 0.5F) * 0.4F));
        }
        flippers(skullBack - L * 0.02F, width + 0.3F, L * 0.09F);
    }

    // ---- shared parts ----------------------------------------------------------------------------

    /**
     * The backbone from the tail tip to the back of the skull: raised over the chest, sloping down to
     * the floor along the tail, each vertebra with a spine up off its top and wings out to the sides.
     */
    private void spine(float tail, float neck, float height, float thick) {
        float span = neck - tail;
        int n = Math.round(span * 1.7F);
        float step = span / n;
        float tailLen = len * 0.38F;
        for (int i = 0; i < n; i++) {
            float x = tail + step * (i + 0.5F);
            float fromTail = x - tail;
            float t = Math.min(1F, fromTail / tailLen);
            float y = 0.2F + (height - 0.2F) * (float) Math.sin(t * Math.PI / 2);
            float d = 0.15F + (thick - 0.15F) * (float) Math.pow(t, 0.7);
            if (scattered(0.5F)) {
                // knocked off, lying on the floor nearby
                float zz = (rand.nextBoolean() ? 1 : -1) * (1 + rand.nextFloat() * 2.5F);
                box(x + rand.nextFloat() - 0.5F, d / 2, zz, step * 0.8F, d, d, new Quaternionf().rotationY(rand.nextFloat() * 6.28F));
                continue;
            }
            box(x, y, 0, step * 0.78F, d, d);
            if (t > 0.25F) {
                // the spine sticking up, leaning back
                float sh = d * (0.6F + 0.5F * t);
                box(x - sh * 0.15F, y + d / 2 + sh / 2, 0, step * 0.4F, sh, d * 0.18F, new Quaternionf().rotationZ(0.3F));
                // wings out to either side along the back
                if (fromTail > tailLen * 0.5F) box(x, y, 0, step * 0.35F, d * 0.16F, d * 2.4F);
            } else if (i % 2 == 0) {
                box(x, y - d * 0.75F, 0, step * 0.35F, d * 0.5F, d * 0.18F); // little chevron bones under the tail
            }
        }
    }

    /** Pairs of ribs from the spine at {@code front} back over {@code span}, arching out and down to the floor. */
    private void ribs(float front, float span, int pairs, float width, float height) {
        for (int i = 0; i < pairs; i++) {
            float x = front - span * i / (pairs - 1);
            float f = 1F - 0.45F * i / (pairs - 1) * i / (pairs - 1); // shorter towards the back
            float w = width * f, sweep = 0.4F + 0.5F * i / pairs;
            float th = len * 0.0085F * (0.7F + 0.3F * f);
            for (int side : new int[] {-1, 1}) {
                List<Vector3f> pts = new ArrayList<>();
                boolean fallen = scattered(1.2F);
                float fz = fallen ? (rand.nextFloat() - 0.5F) * 1.5F : 0;
                for (int k = 0; k <= 7; k++) {
                    float t = k / 7F;
                    double a = t * Math.PI / 2;
                    float z = 0.45F + (w - 0.45F) * (float) Math.sin(a) + (float) Math.sin(t * Math.PI) * w * 0.12F;
                    float y = fallen ? 0.12F : 0.15F + (height - 0.15F) * (float) Math.cos(a);
                    float xx = x - sweep * t + (fallen ? fz * t : 0);
                    pts.add(new Vector3f(xx, y, side * (fallen ? z * 1.15F : z)));
                }
                chain(pts, th, th * 1.5F);
            }
        }
    }

    /** Each flipper: a fan of a shoulder blade, then arm bones and fingers splayed out on the floor. */
    private void flippers(float x, float z0, float reach) {
        for (int side : new int[] {-1, 1}) {
            float s = side;
            box(x - 0.2F, 0.2F, s * (z0 - 0.3F), reach * 0.18F + 0.5F, 0.16F, reach * 0.16F + 0.5F,
                    new Quaternionf().rotationY(s * 0.3F)); // shoulder blade
            float angle = s * (0.9F + rand.nextFloat() * 0.3F); // out to the side, a little forward
            Vector3f dir = new Vector3f((float) Math.cos(angle) * 0.35F + 0.25F, 0, (float) Math.sin(angle)).normalize();
            Vector3f p0 = new Vector3f(x, 0.25F, s * z0);
            Vector3f p1 = new Vector3f(dir).mul(reach * 0.2F).add(p0);
            rod(p0, p1, 0.32F, 0.32F); // upper arm
            Vector3f p2 = new Vector3f(dir).mul(reach * 0.25F).add(p1);
            Vector3f across = new Vector3f(-dir.z, 0, dir.x).mul(0.18F);
            rod(new Vector3f(p1).add(across), new Vector3f(p2).add(across), 0.2F, 0.2F); // the two forearm bones
            rod(new Vector3f(p1).sub(across), new Vector3f(p2).sub(across), 0.2F, 0.2F);
            for (int f = -2; f <= 1; f++) {
                Vector3f fd = new Quaternionf().rotationY(-f * 0.12F).transform(new Vector3f(dir));
                Vector3f a = new Vector3f(p2).add(new Vector3f(across).mul(f + 0.5F));
                int joints = 3 + (f == -1 || f == 0 ? 1 : 0);
                float seg = reach * 0.55F / 4;
                for (int j = 0; j < joints; j++) {
                    Vector3f b = new Vector3f(fd).mul(seg * 0.85F).add(a);
                    rod(a, b, 0.14F, 0.14F);
                    a = new Vector3f(fd).mul(seg).add(a);
                }
            }
        }
    }

    /**
     * What lives on a whale fall: tufts of red bone worms and a few glowing sea pickles on top of
     * the bigger bones.
     */
    private void life() {
        List<Bone> tops = new ArrayList<>();
        for (Bone b : bones) if (b.size.x > 0.3F && b.size.z > 0.3F && b.size.y > 0.15F) tops.add(b);
        int worms = Math.min(tops.size(), 6 + rand.nextInt(10)), pickles = 2 + rand.nextInt(4);
        for (int i = 0; i < worms + pickles && !tops.isEmpty(); i++) {
            Bone b = tops.remove(rand.nextInt(tops.size()));
            Vector3f p = b.at.transformPosition(new Vector3f(0, b.size.y / 2, 0));
            BlockState state = i < worms ? Blocks.FIRE_CORAL_FAN.defaultBlockState()
                    : Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + rand.nextInt(3));
            growths.add(new Growth(p, state, i < worms ? 0.45F + rand.nextFloat() * 0.2F : 0.7F, rand.nextFloat() * 360F));
        }
    }
}
