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
 * Builds a whale fall as it really looks on the sea floor: the skeleton (boxes placed and turned in
 * the whale's own frame: x along the body with the head towards +x, y up from the floor, z across),
 * the bones stained yellow-brown and oily, with whatever is left of the whale on them and whatever
 * lives off it.
 * <p>
 * A whale goes through stages. While much of its flesh is left, pale scraps of it hang off the
 * head and fill the ribcage, furred white with bacteria, and hagfish swarm through it. Later only
 * shreds are left, and red plumes of bone worms grow out of the bones. In the end the bones are
 * bare, paler, coated in white and yellow mats. Octopuses come to sit on them and brood their eggs.
 * Loose bones lie scattered on the mud around.
 * <p>
 * A blue whale or humpback has a broad flat skull of two long plates with the blowhole at the back,
 * the lower jaw in two bowed halves, and fingered flippers (a humpback's a third as long as its
 * body); a sperm whale a deep basin of a skull with a high crest and one long narrow toothed jaw; a
 * beaked whale a long narrow beak and two crests rising round its blowhole.
 */
public final class WhaleSkeleton {
    /** One box, {@code size} big, centred and turned by {@code at}, coloured {@code rgb}. */
    public record Bone(Matrix4f at, Vector3f size, int rgb) {
    }

    /** A block drawn sitting on the bones at {@code pos}, {@code scale} the size of a block. */
    public record Growth(Vector3f pos, BlockState state, float scale, float yaw) {
    }

    /** Something alive on the whale fall, drawn moving: an octopus sitting on a bone, or a hagfish. */
    public record Creature(boolean octopus, Vector3f pos, float heading, float size, float phase, int rgb) {
    }

    public final List<Bone> bones = new ArrayList<>();
    public final List<Growth> growths = new ArrayList<>();
    public final List<Creature> creatures = new ArrayList<>();
    private final Random rand;
    private final float len;
    private final float whole;
    private final float flesh;
    /** bones that can carry something on top */
    private final List<Bone> tops = new ArrayList<>();
    private float skullBack, skullLen, ribWidth, spineHeight;

    private WhaleSkeleton(float len, int seed, float whole, float flesh) {
        this.len = len;
        this.rand = new Random(seed);
        this.whole = whole;
        this.flesh = flesh;
    }

    public static WhaleSkeleton build(int kind, float length, int seed, float whole, float flesh) {
        WhaleSkeleton s = new WhaleSkeleton(length, seed, whole, flesh);
        switch (kind) {
            case WhaleRemainsEntity.SPERM -> s.sperm();
            case WhaleRemainsEntity.BEAKED -> s.beaked();
            default -> s.baleen(kind == WhaleRemainsEntity.HUMPBACK);
        }
        s.scatter();
        s.tissue();
        s.mats();
        s.life();
        return s;
    }

    // ---- colours -----------------------------------------------------------------------------------

    /** Whale bone on the sea floor: yellow-brown and oily while fresh, paler and chalky when old. */
    private int boneColour() {
        int fresh = mix(0xDCCFAE, 0xC2B08A, rand.nextFloat() * 0.8F);
        int old = mix(0xE8E3D6, 0xD4CCB8, rand.nextFloat());
        int c = mix(old, fresh, Math.min(1F, 0.25F + flesh * 1.4F));
        if (rand.nextFloat() < 0.18F) c = mix(c, 0x8E7A5A, 0.3F); // oil stains
        return shade(c, 0.88F + rand.nextFloat() * 0.12F);
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    static int shade(int c, float f) {
        int r = Math.min(255, Math.round(((c >> 16) & 255) * f)), g = Math.min(255, Math.round(((c >> 8) & 255) * f));
        int b = Math.min(255, Math.round((c & 255) * f));
        return r << 16 | g << 8 | b;
    }

    // ---- placing bones -------------------------------------------------------------------------------

    private Bone box(float x, float y, float z, float sx, float sy, float sz, Quaternionf rot, int rgb) {
        Bone b = new Bone(new Matrix4f().translation(x, y, z).rotate(rot), new Vector3f(sx, sy, sz), rgb);
        bones.add(b);
        return b;
    }

    private Bone box(float x, float y, float z, float sx, float sy, float sz, Quaternionf rot) {
        Bone b = box(x, y, z, sx, sy, sz, rot, boneColour());
        if (sx > 0.3F && sz > 0.3F && sy > 0.15F) tops.add(b);
        return b;
    }

    private Bone box(float x, float y, float z, float sx, float sy, float sz) {
        return box(x, y, z, sx, sy, sz, new Quaternionf());
    }

    private static Quaternionf along(Vector3f dir) {
        Quaternionf q = new Quaternionf().rotationTo(1, 0, 0, dir.x, dir.y, dir.z);
        // keep the flat side up where it can
        Vector3f up = q.transform(new Vector3f(0, 1, 0));
        Vector3f want = new Vector3f(0, 1, 0).sub(new Vector3f(dir).mul(dir.y));
        if (want.lengthSquared() > 1e-4F) {
            want.normalize();
            float ang = (float) Math.atan2(new Vector3f(up).cross(want).dot(dir), up.dot(want));
            q = new Quaternionf().rotationAxis(ang, dir).mul(q);
        }
        return q;
    }

    /** A bone from {@code a} to {@code b}, {@code t} thick and {@code d} deep. */
    private void rod(Vector3f a, Vector3f b, float t, float d) {
        Vector3f dir = new Vector3f(b).sub(a);
        float l = dir.length();
        if (l < 1e-4F) return;
        dir.div(l);
        box((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2, l + t * 0.4F, t, d, along(dir));
    }

    private void rod(Vector3f a, Vector3f b, float t, float d, int rgb) {
        Vector3f dir = new Vector3f(b).sub(a);
        float l = dir.length();
        if (l < 1e-4F) return;
        dir.div(l);
        box((a.x + b.x) / 2, (a.y + b.y) / 2, (a.z + b.z) / 2, l + t * 0.4F, t, d, along(dir), rgb);
    }

    private void chain(List<Vector3f> pts, float t, float d) {
        for (int i = 1; i < pts.size(); i++) rod(pts.get(i - 1), pts.get(i), t, d);
    }

    private boolean scattered(float chance) {
        return rand.nextFloat() < (1F - whole) * chance;
    }

    // ---- baleen whales -------------------------------------------------------------------------------

    private void baleen(boolean humpback) {
        float L = len;
        skullLen = L * 0.25F;
        skullBack = L / 2 - skullLen;
        ribWidth = L * 0.13F;
        spineHeight = L * 0.09F;
        spine(-L / 2, skullBack - 0.2F, spineHeight, L * 0.032F);
        ribs(skullBack - 0.6F, L * 0.3F, 14, ribWidth, spineHeight);
        // the skull: a broad cranium sloping up to the back of the head, the blowhole in front of it,
        // then two long flat plates narrowing to the tip of the snout either side of a narrow middle bone
        float crW = L * 0.2F, crL = skullLen * 0.24F;
        box(skullBack + crL / 2, 0.75F, 0, crL, L * 0.06F, crW);
        box(skullBack + crL * 0.3F, 0.75F + L * 0.04F, 0, crL * 0.5F, L * 0.035F, crW * 0.6F,
                new Quaternionf().rotationZ(-0.25F)); // the shield at the back of the head
        box(skullBack + crL * 0.9F, 0.75F + L * 0.03F, 0, crL * 0.25F, 0.12F, crW * 0.18F, new Quaternionf(), 0x3A3024); // blowhole
        int steps = 8;
        float roL = skullLen - crL;
        for (int k = 0; k < steps; k++) {
            float tm = (k + 0.5F) / steps;
            float w = crW * 0.92F * (float) Math.pow(1 - tm, 0.85) + 0.3F;
            float x = skullBack + crL + roL * tm, segL = roL / steps + 0.05F;
            float plate = w / 2 - 0.12F;
            float arch = (float) Math.sin(tm * Math.PI) * 0.12F;
            box(x, 0.45F + arch, plate / 2 + 0.14F, segL, 0.32F, plate, new Quaternionf().rotationX(0.08F));
            box(x, 0.45F + arch, -plate / 2 - 0.14F, segL, 0.32F, plate, new Quaternionf().rotationX(-0.08F));
            box(x, 0.55F + arch, 0, segL, 0.24F, 0.18F);
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
                float rx = x - skullBack;
                pts.add(new Vector3f(skullBack + rx * (float) Math.cos(turn) - z * (float) Math.sin(turn) + dx, 0.32F,
                        rx * (float) Math.sin(turn) + z * (float) Math.cos(turn) + dz));
            }
            chain(pts, L * 0.022F, L * 0.016F);
        }
        flippers(skullBack - L * 0.03F, ribWidth + 0.4F, humpback ? L * 0.3F : L * 0.11F);
        for (int side : new int[] {-1, 1}) {
            rod(new Vector3f(-L * 0.17F, 0.15F, side * 1.0F), new Vector3f(-L * 0.17F - 0.8F, 0.12F, side * 1.4F), 0.18F, 0.18F);
        }
    }

    // ---- sperm whales -------------------------------------------------------------------------------

    private void sperm() {
        float L = len;
        skullLen = L * 0.32F;
        skullBack = L / 2 - skullLen;
        ribWidth = L * 0.12F;
        spineHeight = L * 0.085F;
        spine(-L / 2, skullBack - 0.2F, spineHeight, L * 0.034F);
        ribs(skullBack - 0.6F, L * 0.27F, 11, ribWidth, spineHeight);
        float sw = L * 0.21F, crest = L * 0.14F;
        box(skullBack + 0.35F, crest / 2, 0, 0.7F, crest, sw);
        for (int side : new int[] {-1, 1}) {
            for (int k = 0; k < 5; k++) {
                float t = (k + 0.5F) / 5F;
                float h = crest * (1 - t) * 0.85F + 0.4F;
                box(skullBack + 0.7F + skullLen * 0.55F * t, h / 2, side * (sw / 2 - 0.3F - t * 0.4F), skullLen * 0.55F / 5 + 0.05F, h, 0.5F);
            }
        }
        box(skullBack + skullLen * 0.3F, 0.22F, 0, skullLen * 0.55F, 0.44F, sw * 0.9F);
        for (int k = 0; k < 6; k++) {
            float t = (k + 0.5F) / 6F;
            float w = 1.4F * (1 - t) + 0.45F;
            box(skullBack + skullLen * 0.6F + skullLen * 0.4F * t, 0.3F, 0, skullLen * 0.4F / 6 + 0.05F, 0.5F, w);
        }
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
            Vector3f across = q.transform(new Vector3f(0, 0, (i % 2 == 0 ? 1 : -1) * 0.18F));
            box(p.x + across.x, p.y + 0.38F, p.z + across.z, 0.16F, 0.36F, 0.16F, new Quaternionf().rotationZ((rand.nextFloat() - 0.5F) * 0.4F),
                    mix(0xEEE6D2, 0xC8B48A, rand.nextFloat() * 0.5F));
        }
        flippers(skullBack - L * 0.02F, ribWidth + 0.3F, L * 0.09F);
    }

    // ---- beaked whales -------------------------------------------------------------------------------

    /**
     * A beaked whale: a small whale with a long narrow beak of two raised bones over a third in the
     * middle, the jaw bones joined at the front with a pair of tusks at the tip, and two crests
     * rising round the blowhole at the back of the skull.
     */
    private void beaked() {
        float L = len;
        skullLen = L * 0.2F;
        skullBack = L / 2 - skullLen;
        ribWidth = L * 0.1F;
        spineHeight = L * 0.085F;
        spine(-L / 2, skullBack - 0.1F, spineHeight, L * 0.05F);
        ribs(skullBack - 0.3F, L * 0.28F, 10, ribWidth, spineHeight);
        float cw = L * 0.11F;
        // braincase and the crests round the blowhole
        box(skullBack + skullLen * 0.15F, 0.35F, 0, skullLen * 0.3F, 0.6F, cw);
        for (int side : new int[] {-1, 1}) {
            box(skullBack + skullLen * 0.25F, 0.75F, side * cw * 0.22F, skullLen * 0.14F, 0.55F, cw * 0.18F,
                    new Quaternionf().rotationX(side * 0.35F)); // the crests
            box(skullBack + skullLen * 0.35F, 0.3F, side * cw * 0.42F, skullLen * 0.3F, 0.3F, cw * 0.25F,
                    new Quaternionf().rotationY(side * 0.3F)); // the wide base of the upper jaw
        }
        box(skullBack + skullLen * 0.22F, 0.68F, 0, skullLen * 0.08F, 0.1F, cw * 0.2F, new Quaternionf(), 0x3A3024); // blowhole
        // the beak
        float b0 = skullBack + skullLen * 0.42F, bl = skullLen * 0.62F;
        for (int k = 0; k < 6; k++) {
            float t = (k + 0.5F) / 6F;
            float w = cw * 0.5F * (1 - t) + 0.16F;
            float x = b0 + bl * t;
            box(x, 0.3F, 0, bl / 6 + 0.04F, 0.24F, w);
            for (int side : new int[] {-1, 1}) box(x, 0.46F, side * w * 0.25F, bl / 6 + 0.04F, 0.12F, w * 0.3F); // the raised bones
        }
        // the jaw, joined at the front, with a pair of tusks
        for (int side : new int[] {-1, 1}) {
            rod(new Vector3f(skullBack + skullLen * 0.1F, 0.1F, side * cw * 0.45F), new Vector3f(b0 + bl * 0.4F, 0.1F, side * 0.15F), 0.2F, 0.22F);
        }
        rod(new Vector3f(b0 + bl * 0.4F, 0.1F, 0), new Vector3f(b0 + bl * 1.02F, 0.1F, 0), 0.18F, 0.28F);
        for (int side : new int[] {-1, 1}) {
            box(b0 + bl * 0.98F, 0.28F, side * 0.08F, 0.12F, 0.32F, 0.1F, new Quaternionf().rotationZ(-0.3F), 0xEDE6D4);
        }
        flippers(skullBack - L * 0.04F, ribWidth + 0.2F, L * 0.1F);
    }

    // ---- shared parts --------------------------------------------------------------------------------

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
            float d = 0.12F + (thick - 0.12F) * (float) Math.pow(t, 0.7);
            if (scattered(0.5F)) {
                float zz = (rand.nextBoolean() ? 1 : -1) * (1 + rand.nextFloat() * 2.5F);
                box(x + rand.nextFloat() - 0.5F, d / 2, zz, step * 0.8F, d, d, new Quaternionf().rotationY(rand.nextFloat() * 6.28F));
                continue;
            }
            box(x, y, 0, step * 0.78F, d, d);
            if (t > 0.25F) {
                float sh = d * (0.6F + 0.5F * t);
                box(x - sh * 0.15F, y + d / 2 + sh / 2, 0, step * 0.4F, sh, d * 0.18F, new Quaternionf().rotationZ(0.3F));
                if (fromTail > tailLen * 0.5F) box(x, y, 0, step * 0.35F, d * 0.16F, d * 2.4F);
            } else if (i % 2 == 0) {
                box(x, y - d * 0.75F, 0, step * 0.35F, d * 0.5F, d * 0.18F);
            }
        }
    }

    private void ribs(float front, float span, int pairs, float width, float height) {
        for (int i = 0; i < pairs; i++) {
            float x = front - span * i / (pairs - 1);
            float f = 1F - 0.45F * i / (pairs - 1) * i / (pairs - 1);
            float w = width * f, sweep = 0.4F + 0.5F * i / pairs;
            float th = Math.max(0.1F, len * 0.0085F * (0.7F + 0.3F * f));
            for (int side : new int[] {-1, 1}) {
                List<Vector3f> pts = new ArrayList<>();
                boolean fallen = scattered(1.2F);
                float fz = fallen ? (rand.nextFloat() - 0.5F) * 1.5F : 0;
                for (int k = 0; k <= 7; k++) {
                    float t = k / 7F;
                    double a = t * Math.PI / 2;
                    float z = 0.45F + (w - 0.45F) * (float) Math.sin(a) + (float) Math.sin(t * Math.PI) * w * 0.12F;
                    float y = fallen ? 0.12F : 0.15F + (height - 0.15F) * (float) Math.cos(a);
                    pts.add(new Vector3f(x - sweep * t + (fallen ? fz * t : 0), y, side * (fallen ? z * 1.15F : z)));
                }
                chain(pts, th, th * 1.5F);
            }
        }
    }

    private void flippers(float x, float z0, float reach) {
        for (int side : new int[] {-1, 1}) {
            float s = side;
            box(x - 0.2F, 0.2F, s * (z0 - 0.3F), reach * 0.18F + 0.5F, 0.16F, reach * 0.16F + 0.5F, new Quaternionf().rotationY(s * 0.3F));
            float angle = s * (0.9F + rand.nextFloat() * 0.3F);
            Vector3f dir = new Vector3f((float) Math.cos(angle) * 0.35F + 0.25F, 0, (float) Math.sin(angle)).normalize();
            Vector3f p0 = new Vector3f(x, 0.25F, s * z0);
            Vector3f p1 = new Vector3f(dir).mul(reach * 0.2F).add(p0);
            rod(p0, p1, 0.32F, 0.32F);
            Vector3f p2 = new Vector3f(dir).mul(reach * 0.25F).add(p1);
            Vector3f across = new Vector3f(-dir.z, 0, dir.x).mul(0.18F);
            rod(new Vector3f(p1).add(across), new Vector3f(p2).add(across), 0.2F, 0.2F);
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

    // ---- what's round it, and on it ------------------------------------------------------------------

    /** Loose bones dragged off by scavengers, lying on the mud round about: vertebrae, rib ends, flat plates. */
    private void scatter() {
        int n = Math.round((1.2F - whole) * len * 0.6F) + 2;
        for (int i = 0; i < n; i++) {
            float x = (rand.nextFloat() - 0.5F) * len * 1.1F;
            float z = (rand.nextBoolean() ? 1 : -1) * (ribWidth + 0.8F + rand.nextFloat() * len * 0.15F);
            Quaternionf q = new Quaternionf().rotationY(rand.nextFloat() * 6.28F).rotateX((rand.nextFloat() - 0.5F) * 0.3F);
            float r = rand.nextFloat();
            if (r < 0.35F) {
                float d = 0.25F + rand.nextFloat() * len * 0.02F;
                box(x, d / 2, z, d * 0.9F, d, d, q); // a vertebra
            } else if (r < 0.7F) {
                box(x, 0.1F, z, 1.2F + rand.nextFloat() * len * 0.08F, 0.16F, 0.22F, q); // a piece of rib
            } else {
                box(x, 0.08F, z, 0.8F + rand.nextFloat() * 1.2F, 0.12F, 0.6F + rand.nextFloat(), q); // a flat plate of bone
            }
        }
    }

    /**
     * What's left of the whale itself: pale grey scraps of flesh and blubber hanging off the head and
     * jaw and filling the ribcage, furred white with bacteria, with dark stringy shreds between the
     * ribs. More of it the fresher the whale.
     */
    private void tissue() {
        if (flesh <= 0) return;
        int blobs = Math.round(flesh * len * 2.2F);
        for (int i = 0; i < blobs; i++) {
            // mostly round the head and inside the front of the ribcage
            float u = (float) Math.pow(rand.nextFloat(), 0.7);
            float x = skullBack + skullLen * 0.6F - u * (skullLen * 0.6F + len * 0.38F);
            boolean inChest = x < skullBack;
            float zr = inChest ? ribWidth * 0.75F : len * 0.09F;
            float z = (rand.nextFloat() * 2 - 1) * zr;
            float maxY = inChest ? spineHeight * 0.9F : len * 0.05F;
            float size = (0.5F + rand.nextFloat() * 1.1F) * (0.5F + flesh) * len / 24F;
            float y = Math.min(maxY, size * 0.4F + rand.nextFloat() * maxY * 0.6F);
            int base = rand.nextFloat() < 0.25F ? mix(0x9C8C80, 0x847468, rand.nextFloat()) : mix(0xCEC4BA, 0xB4AAA0, rand.nextFloat());
            for (int k = 0; k < 2 + rand.nextInt(3); k++) {
                float s = size * (0.6F + rand.nextFloat() * 0.5F);
                box(x + (rand.nextFloat() - 0.5F) * size, y + (rand.nextFloat() - 0.3F) * size * 0.4F, z + (rand.nextFloat() - 0.5F) * size,
                        s, s * (0.5F + rand.nextFloat() * 0.4F), s * (0.7F + rand.nextFloat() * 0.4F),
                        new Quaternionf().rotationXYZ(rand.nextFloat() - 0.5F, rand.nextFloat() * 6.28F, rand.nextFloat() - 0.5F),
                        shade(base, 0.85F + rand.nextFloat() * 0.2F));
            }
            // the white fur of bacteria over it
            for (int k = 0; k < 1 + rand.nextInt(3); k++) {
                float s = size * (0.5F + rand.nextFloat() * 0.5F);
                box(x + (rand.nextFloat() - 0.5F) * size * 0.8F, y + size * (0.25F + rand.nextFloat() * 0.2F), z + (rand.nextFloat() - 0.5F) * size * 0.8F,
                        s, s * 0.3F, s * 0.8F, new Quaternionf().rotationXYZ((rand.nextFloat() - 0.5F) * 0.4F, rand.nextFloat() * 6.28F, (rand.nextFloat() - 0.5F) * 0.4F),
                        mix(0xF6F4EE, 0xE4E0D4, rand.nextFloat()));
            }
        }
        // dark stringy shreds hanging between the ribs
        int shreds = Math.round(flesh * len * 0.8F);
        for (int i = 0; i < shreds; i++) {
            float x = skullBack - rand.nextFloat() * len * 0.3F;
            int side = rand.nextBoolean() ? 1 : -1;
            Vector3f a = new Vector3f(x, spineHeight * (0.4F + rand.nextFloat() * 0.5F), side * ribWidth * (0.3F + rand.nextFloat() * 0.5F));
            Vector3f b = new Vector3f(x + (rand.nextFloat() - 0.5F) * 1.2F, 0.1F + rand.nextFloat() * 0.3F, a.z + side * rand.nextFloat() * 0.8F);
            rod(a, b, 0.06F + rand.nextFloat() * 0.06F, 0.1F, mix(0x5A4434, 0x3E3026, rand.nextFloat()));
        }
    }

    /** White and yellow mats of bacteria coating the tops of bones, thicker on old bare ones. */
    private void mats() {
        float chance = 0.15F + (1 - Math.min(1F, flesh * 2)) * 0.3F;
        for (Bone b : new ArrayList<>(tops)) {
            if (rand.nextFloat() > chance) continue;
            Matrix4f at = new Matrix4f(b.at()).translate(0, b.size().y / 2 + 0.02F, 0);
            int c = rand.nextFloat() < 0.7F ? mix(0xF6F4EE, 0xE8E4D8, rand.nextFloat()) : mix(0xEADFA8, 0xDCCF8C, rand.nextFloat());
            bones.add(new Bone(at, new Vector3f(b.size().x * (0.5F + rand.nextFloat() * 0.45F), 0.05F, b.size().z * 0.9F), c));
        }
    }

    /**
     * What lives here: red plumes of bone worms on the bones (most once the flesh is nearly gone),
     * a few sea pickles, octopuses sitting on the bones, and hagfish weaving through, a whole swarm
     * of them while there's flesh to eat.
     */
    private void life() {
        List<Bone> spots = new ArrayList<>(tops);
        int worms = Math.round((flesh > 0.45F ? 3 : 10) + rand.nextInt(8) * (flesh > 0.45F ? 0.5F : 1F));
        int pickles = 1 + rand.nextInt(3);
        for (int i = 0; i < worms + pickles && !spots.isEmpty(); i++) {
            Bone b = spots.remove(rand.nextInt(spots.size()));
            Vector3f p = b.at().transformPosition(new Vector3f(0, b.size().y / 2, 0));
            BlockState state = i < worms ? Blocks.FIRE_CORAL_FAN.defaultBlockState()
                    : Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + rand.nextInt(3));
            growths.add(new Growth(p, state, i < worms ? 0.4F + rand.nextFloat() * 0.2F : 0.7F, rand.nextFloat() * 360F));
        }
        int octopuses = rand.nextInt(flesh > 0.45F ? 2 : 4) + (len > 15 ? 1 : 0);
        for (int i = 0; i < octopuses && !spots.isEmpty(); i++) {
            Bone b = spots.remove(rand.nextInt(spots.size()));
            Vector3f p = b.at().transformPosition(new Vector3f(0, b.size().y / 2, 0));
            creatures.add(new Creature(true, p, rand.nextFloat() * 6.28F, 0.5F + rand.nextFloat() * 0.35F, rand.nextFloat() * 6.28F,
                    mix(0xD4AAC4, 0xB48AA8, rand.nextFloat())));
        }
        int fish = Math.round(flesh * len * 0.7F) + rand.nextInt(3);
        for (int i = 0; i < fish; i++) {
            float x = (rand.nextFloat() - 0.4F) * len * 0.8F;
            float z = (rand.nextFloat() * 2 - 1) * (ribWidth + 1.5F);
            float y = 0.2F + rand.nextFloat() * Math.max(0.4F, spineHeight * 0.6F);
            boolean hag = rand.nextFloat() < 0.6F;
            creatures.add(new Creature(false, new Vector3f(x, y, z), rand.nextFloat() * 6.28F, 1.0F + rand.nextFloat() * 0.7F,
                    rand.nextFloat() * 6.28F, hag ? mix(0x6A5658, 0x584648, rand.nextFloat()) : mix(0xB4A8A0, 0x9C9088, rand.nextFloat())));
        }
    }

    // ---- creatures, which move -----------------------------------------------------------------------

    /** The boxes an octopus or a fish is made of at {@code time} seconds. */
    public static List<Bone> pose(Creature c, float time) {
        List<Bone> out = new ArrayList<>();
        if (c.octopus()) {
            float s = c.size();
            float breathe = 1F + 0.06F * (float) Math.sin(time * 1.3F + c.phase());
            Quaternionf face = new Quaternionf().rotationY(c.heading());
            Matrix4f base = new Matrix4f().translation(c.pos()).rotate(face);
            // the mantle, leaning back, and the head under it
            out.add(new Bone(new Matrix4f(base).translate(-s * 0.25F, s * 0.6F, 0).rotateZ(0.5F), new Vector3f(s * 0.9F, s * 0.7F * breathe, s * 0.75F * breathe), c.rgb()));
            out.add(new Bone(new Matrix4f(base).translate(s * 0.15F, s * 0.28F, 0), new Vector3f(s * 0.5F, s * 0.42F, s * 0.55F), shade(c.rgb(), 1.05F)));
            for (int side : new int[] {-1, 1}) {
                out.add(new Bone(new Matrix4f(base).translate(s * 0.3F, s * 0.4F, side * s * 0.2F), new Vector3f(s * 0.1F, s * 0.1F, s * 0.1F), 0x1E1A20));
            }
            // eight arms draped down over the bone, the tips curling
            for (int i = 0; i < 8; i++) {
                float a = (float) (i * Math.PI / 4 + 0.2F);
                Vector3f prev = new Vector3f((float) Math.cos(a) * s * 0.2F, s * 0.15F, (float) Math.sin(a) * s * 0.2F);
                for (int k = 1; k <= 5; k++) {
                    float t = k / 5F;
                    float curl = t * t * (0.6F + 0.5F * (float) Math.sin(time * 1.1F + c.phase() + i));
                    float r = s * (0.2F + 1.1F * t);
                    float aa = a + curl;
                    Vector3f p = new Vector3f((float) Math.cos(aa) * r, s * 0.15F - t * t * s * 0.5F, (float) Math.sin(aa) * r);
                    Vector3f d = new Vector3f(p).sub(prev);
                    float l = d.length();
                    Matrix4f m = new Matrix4f(base).translate((p.x + prev.x) / 2, (p.y + prev.y) / 2, (p.z + prev.z) / 2).rotate(along(d.normalize()));
                    float th = s * 0.13F * (1.1F - t);
                    out.add(new Bone(m, new Vector3f(l + th * 0.3F, th, th), shade(c.rgb(), 0.95F + t * 0.08F)));
                    prev = p;
                }
            }
        } else {
            // a hagfish or eelpout: a long body swimming slowly in place, the wave running down it
            float s = c.size();
            int n = 8;
            float seg = s / n;
            Matrix4f base = new Matrix4f().translation(c.pos()).rotate(new Quaternionf().rotationY(c.heading()));
            Vector3f prev = null;
            for (int k = 0; k <= n; k++) {
                float t = k / (float) n;
                float x = s * 0.5F - t * s;
                float z = (float) Math.sin(time * 4F + c.phase() - t * 5F) * 0.12F * (0.3F + t);
                Vector3f p = new Vector3f(x, 0, z);
                if (prev != null) {
                    Vector3f d = new Vector3f(prev).sub(p);
                    float l = d.length();
                    float th = Math.max(0.05F, s * 0.1F * (k == 1 ? 1.1F : 1.15F - t * 0.8F));
                    Matrix4f m = new Matrix4f(base).translate((p.x + prev.x) / 2, 0, (p.z + prev.z) / 2).rotate(along(d.normalize()));
                    out.add(new Bone(m, new Vector3f(l + 0.02F, th, th * 0.85F), shade(c.rgb(), k == 1 ? 1.08F : 1F)));
                }
                prev = p;
            }
        }
        return out;
    }
}
