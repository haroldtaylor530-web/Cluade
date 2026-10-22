package com.harold.corpsedecay.client;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import net.minecraft.client.model.geom.ModelPart;

/**
 * Decides, for one point on one face of one box of a model, whether a bone shows there and how
 * bright it is. Patterns are worked out from the box's real shape and where it sits on the mob:
 * a skull with eye sockets on the face, a ribcage and spine along the body (whichever way the body
 * lies, upright or on all fours), thin bones down the legs with knobbly joints, vertebrae along the
 * tail, a leading-edge bone on wings, rays in fins.
 */
final class BonePainter {
    /** what a pixel of the decay texture is */
    static final byte NONE = 0, BONE = 1, DARK = 2, HORN = 3;

    private final ModelInfo info;
    private final Vector3f headModel;

    BonePainter(ModelInfo info) {
        this.info = info;
        if (info.head != null) {
            headModel = info.head.pivotModel;
        } else {
            headModel = null;
        }
    }

    /** Per-box facts needed by the patterns. */
    static final class Box {
        ModelInfo.Part part;
        ModelPart.Cube cube;
        boolean main;
        float[] min = new float[3], max = new float[3], dim = new float[3], mid = new float[3];
        int longAxis;
        Matrix3f rot;
        // body only
        int dorsalAxis = -1, sideAxis = -1;
        float dorsalSign = 1, headSign = 1;
        // head extras
        HeadBit headBit = HeadBit.SKULL;
    }

    enum HeadBit { SKULL, SNOUT, HORN, FLAP }

    Box box(ModelInfo.Part part, ModelPart.Cube cube, boolean main) {
        Box b = new Box();
        b.part = part;
        b.cube = cube;
        b.main = main;
        b.min[0] = cube.minX;
        b.min[1] = cube.minY;
        b.min[2] = cube.minZ;
        b.max[0] = cube.maxX;
        b.max[1] = cube.maxY;
        b.max[2] = cube.maxZ;
        int l = 0;
        for (int k = 0; k < 3; k++) {
            b.dim[k] = Math.max(0.001F, b.max[k] - b.min[k]);
            b.mid[k] = (b.min[k] + b.max[k]) / 2F;
            if (b.dim[k] > b.dim[l]) l = k;
        }
        b.longAxis = l;
        b.rot = new Matrix3f();
        part.restToModel.get3x3(b.rot);

        if (part.cls == PartClass.BODY && main) {
            Vector3f spine = b.rot.transform(axis(l));
            Vector3f center = new Vector3f(b.mid[0], b.mid[1], b.mid[2]);
            part.restToModel.transformPosition(center);
            Vector3f toHead = headModel != null ? new Vector3f(headModel).sub(center) : new Vector3f(0, 0, -1);
            if (toHead.lengthSquared() < 1e-4F) toHead.set(0, 0, -1);
            float d = spine.dot(toHead);
            if (Math.abs(d) < 1e-3F) d = -spine.z;
            b.headSign = d >= 0 ? 1 : -1;
            // back of the animal: behind an upright body, on top of a horizontal one
            Vector3f dorsalModel = Math.abs(spine.y) > 0.7F ? new Vector3f(0, 0, 1) : new Vector3f(0, -1, 0);
            Vector3f dorsalLocal = new Matrix3f(b.rot).transpose().transform(dorsalModel);
            float best = -1;
            for (int k = 0; k < 3; k++) {
                if (k == l) continue;
                float c = Math.abs(component(dorsalLocal, k));
                if (c > best) {
                    best = c;
                    b.dorsalAxis = k;
                    b.dorsalSign = component(dorsalLocal, k) >= 0 ? 1 : -1;
                }
            }
            b.sideAxis = 3 - l - b.dorsalAxis;
        }
        return b;
    }

    /** Which head bit a non-skull box on the head is: a snout, a horn, an ear flap, or more skull. */
    void classifyHeadBit(Box b, Box skull) {
        if (skull == null || b == skull) {
            b.headBit = HeadBit.SKULL;
            return;
        }
        Vector3f c = new Vector3f(b.mid[0], b.mid[1], b.mid[2]);
        b.part.restToModel.transformPosition(c);
        Vector3f smin = new Vector3f(skull.min[0], skull.min[1], skull.min[2]);
        Vector3f smax = new Vector3f(skull.max[0], skull.max[1], skull.max[2]);
        skull.part.restToModel.transformPosition(smin);
        skull.part.restToModel.transformPosition(smax);
        float frontZ = Math.min(smin.z, smax.z);
        float minDim = Math.min(b.dim[0], Math.min(b.dim[1], b.dim[2]));
        if (minDim <= 1.01F && b.dim[b.longAxis] >= 2.5F) {
            b.headBit = HeadBit.FLAP;
        } else if (c.z < frontZ + 0.5F) {
            b.headBit = HeadBit.SNOUT;
        } else if (volume(b) < volume(skull) * 0.15F) {
            b.headBit = HeadBit.HORN;
        } else {
            b.headBit = HeadBit.SKULL;
        }
    }

    /**
     * @param p point on the face, part space (pixels)
     * @param n face normal, part space
     * @param s,t position across the face's texture rectangle (0..1, t downwards)
     * @return NONE, BONE, DARK (socket / gap) or HORN (keep the original colour)
     */
    byte paint(Box b, Vector3f p, Vector3f n, float s, float t) {
        PartClass cls = b.part.cls;
        int na = dominant(n);
        float[] q = new float[3];
        for (int k = 0; k < 3; k++) q[k] = (component(p, k) - b.mid[k]) / (b.dim[k] / 2F);
        if (cls == PartClass.HEAD) return head(b, na, n, s, t);
        if (cls == PartClass.BODY) return b.main ? body(b, p, na, n, q) : limb(b, p, na, q, false, false);
        switch (cls) {
            case LEG:
            case ARM:
                return limb(b, p, na, q, true, false);
            case NECK:
                return limb(b, p, na, q, false, false);
            case TAIL:
                return limb(b, p, na, q, false, true);
            case WING:
                return wing(b, p, na, n, q);
            case FIN:
                return fin(b, p, na);
            case HORN:
                return HORN;
            case EAR:
            case OUTER:
                return NONE;
            default:
                float second = secondLargest(b.dim);
                if (b.dim[b.longAxis] >= second * 2F) return limb(b, p, na, q, false, false);
                if (volume(b) < 6F) return NONE;
                return BONE;
        }
    }

    private byte head(Box b, int na, Vector3f n, float s, float t) {
        switch (b.headBit) {
            case FLAP:
                return NONE;
            case HORN:
                return HORN;
            case SNOUT: {
                if (!facesFront(b, n)) return BONE;
                boolean nostril = (inRange(s, 0.18F, 0.42F) || inRange(s, 0.58F, 0.82F)) && inRange(t, 0.3F, 0.7F);
                return nostril ? DARK : BONE;
            }
            default:
                break;
        }
        if (!b.main || !facesFront(b, n)) return BONE;
        // the face: eye sockets, a nose hole and a row of teeth
        if ((inRange(s, 0.13F, 0.42F) || inRange(s, 0.58F, 0.87F)) && inRange(t, 0.28F, 0.6F)) return DARK;
        if (inRange(s, 0.4F, 0.6F) && inRange(t, 0.6F, 0.8F)) return DARK;
        if (t > 0.82F) {
            int col = (int) Math.floor(s * b.dim[faceAcross(b, n)]);
            return (col & 1) == 0 ? BONE : DARK;
        }
        return BONE;
    }

    private byte body(Box b, Vector3f p, int na, Vector3f n, float[] q) {
        int l = b.longAxis;
        float along = (component(p, l) - b.min[l]) / b.dim[l];
        float fromHead = b.headSign > 0 ? 1F - along : along;
        int row = (int) Math.floor(component(p, l) - b.min[l]);
        boolean ribRow = (row & 1) == 1;
        boolean cage = fromHead >= 0.08F && fromHead <= 0.62F;
        boolean pelvis = fromHead >= 0.74F && fromHead <= 0.96F;
        float spineW = Math.max(0.26F, 1.01F / b.dim[b.sideAxis]);
        float sternumW = spineW;
        if (na == l) {
            // end of the body: one vertebra where the spine passes through
            boolean dorsalHalf = q[b.dorsalAxis] * b.dorsalSign > 0.25F;
            return dorsalHalf && Math.abs(q[b.sideAxis]) <= spineW + 0.1F ? BONE : NONE;
        }
        if (na == b.dorsalAxis) {
            boolean dorsal = component(n, na) * b.dorsalSign > 0;
            if (dorsal) {
                if (Math.abs(q[b.sideAxis]) <= spineW) return BONE;
                if (cage && ribRow) return BONE;
                if (pelvis && Math.abs(q[b.sideAxis]) < 0.75F) return BONE;
                return NONE;
            }
            if (cage && (ribRow || Math.abs(q[b.sideAxis]) <= sternumW)) return BONE;
            if (pelvis && ribRow && Math.abs(q[b.sideAxis]) > 0.3F) return BONE;
            return NONE;
        }
        // the sides
        if (cage && ribRow) return BONE;
        if (pelvis && q[b.dorsalAxis] * b.dorsalSign > -0.4F) return BONE;
        return NONE;
    }

    private byte limb(Box b, Vector3f p, int na, float[] q, boolean joints, boolean vertebrae) {
        int l = b.longAxis;
        float along = (component(p, l) - b.min[l]) / b.dim[l];
        int row = (int) Math.floor(component(p, l) - b.min[l]);
        if (na == l) {
            if (vertebrae) return NONE;
            int a = (l + 1) % 3, c = (l + 2) % 3;
            return Math.abs(q[a]) <= 0.45F && Math.abs(q[c]) <= 0.45F ? BONE : NONE;
        }
        int across = 3 - l - na;
        float half = Math.max(0.34F, 1.01F / b.dim[across]);
        if (joints && (along < 0.12F || along > 0.88F)) half = Math.max(half, 0.62F);
        if (Math.abs(q[across]) > half) return NONE;
        if (vertebrae && (row & 1) == 1) return NONE;
        return BONE;
    }

    private byte wing(Box b, Vector3f p, int na, Vector3f n, float[] q) {
        int l = b.longAxis;
        int thin = 0;
        for (int k = 0; k < 3; k++) if (b.dim[k] < b.dim[thin]) thin = k;
        if (thin == l) thin = (l + 1) % 3;
        int w = 3 - l - thin;
        Vector3f wm = b.rot.transform(axis(w));
        float lead = Math.abs(wm.y) > 0.3F ? (wm.y < 0 ? 1 : -1) : (wm.z < 0 ? 1 : -1);
        float edge = q[w] * lead;
        float edgeW = Math.max(0.45F, 1F - 2.02F / b.dim[w]);
        if (na == w) return component(n, w) * lead > 0 ? BONE : NONE;
        if (na == l) return edge > edgeW ? BONE : NONE;
        return edge > edgeW ? BONE : NONE;
    }

    private byte fin(Box b, Vector3f p, int na) {
        int l = b.longAxis;
        int thin = 0;
        for (int k = 0; k < 3; k++) if (b.dim[k] < b.dim[thin]) thin = k;
        int w = 3 - l - thin;
        if (w < 0 || w > 2 || w == l) return NONE;
        if (na != thin) return NONE;
        int col = (int) Math.floor(component(p, w) - b.min[w]);
        return (col & 1) == 0 ? BONE : NONE;
    }

    // ---- helpers ------------------------------------------------------------------------------------

    /** Does this face look the way the mob looks (model -z)? */
    private static boolean facesFront(Box b, Vector3f n) {
        Vector3f nm = b.rot.transform(new Vector3f(n));
        return nm.z < -0.7F;
    }

    private static int faceAcross(Box b, Vector3f n) {
        int na = dominant(n);
        int best = -1;
        for (int k = 0; k < 3; k++) {
            if (k == na) continue;
            Vector3f am = b.rot.transform(axis(k));
            if (Math.abs(am.x) > 0.5F) best = k;
        }
        return best < 0 ? (na + 1) % 3 : best;
    }

    static int dominant(Vector3f v) {
        float ax = Math.abs(v.x), ay = Math.abs(v.y), az = Math.abs(v.z);
        if (ax >= ay && ax >= az) return 0;
        return ay >= az ? 1 : 2;
    }

    static float component(Vector3f v, int k) {
        return k == 0 ? v.x : k == 1 ? v.y : v.z;
    }

    static Vector3f axis(int k) {
        return new Vector3f(k == 0 ? 1 : 0, k == 1 ? 1 : 0, k == 2 ? 1 : 0);
    }

    private static boolean inRange(float v, float lo, float hi) {
        return v >= lo && v <= hi;
    }

    private static float secondLargest(float[] d) {
        float a = Math.max(d[0], Math.max(d[1], d[2]));
        float c = Math.min(d[0], Math.min(d[1], d[2]));
        return d[0] + d[1] + d[2] - a - c;
    }

    static float volume(Box b) {
        return b.dim[0] * b.dim[1] * b.dim[2];
    }
}
