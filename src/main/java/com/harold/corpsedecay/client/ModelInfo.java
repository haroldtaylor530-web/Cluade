package com.harold.corpsedecay.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

/**
 * Everything the mod needs to know about one entity model: all of its parts, what each part is,
 * where it sits when standing, and which parts swing loose on a corpse. Built once per model by
 * reflection, so it works for any mob (modded ones too) without knowing the model class.
 */
public final class ModelInfo {
    private static final Map<Object, ModelInfo> CACHE = Collections.synchronizedMap(new IdentityHashMap<>());

    private static final Field CUBES;
    private static final Field CHILDREN;
    private static final Field POLYGONS;

    static {
        Field cubes = null, children = null, polygons = null;
        for (Field f : ModelPart.class.getDeclaredFields()) {
            if (f.getType() == List.class) cubes = f;
            if (f.getType() == Map.class) children = f;
        }
        for (Field f : ModelPart.Cube.class.getDeclaredFields()) {
            if (f.getType().isArray()) polygons = f;
        }
        CUBES = cubes;
        CHILDREN = children;
        POLYGONS = polygons;
        if (CUBES != null) CUBES.setAccessible(true);
        if (CHILDREN != null) CHILDREN.setAccessible(true);
        if (POLYGONS != null) POLYGONS.setAccessible(true);
    }

    public static final class Part {
        public final ModelPart part;
        public final String name;
        public final PartClass cls;
        public Part parent;
        public final PartPose rest;
        public final List<ModelPart.Cube> cubes;
        /** part space (pixels) -> model space (pixels), standing pose */
        public final Matrix4f restToModel = new Matrix4f();
        /** rotation of the parent chain in model space, standing pose */
        public final Quaternionf parentRot = new Quaternionf();
        /** pivot in model space (pixels) */
        public final Vector3f pivotModel = new Vector3f();
        /** farthest point of the limb from its pivot, in part space (pixels) */
        public final Vector3f tip = new Vector3f();
        public boolean flops;
        /** another flopping part this one follows (a hat follows the head) */
        public Part follows;
        public int index;

        Part(ModelPart part, String name) {
            this.part = part;
            this.name = name;
            this.cls = PartClass.of(name);
            this.rest = part.getInitialPose();
            this.cubes = cubesOf(part);
        }

        public Quaternionf restRotation() {
            return new Quaternionf().rotationZYX(rest.zRot, rest.yRot, rest.xRot);
        }
    }

    public final List<Part> parts = new ArrayList<>();
    public final List<Part> limbs = new ArrayList<>();
    /** parts whose name is known, by name (for applying limb poses to every model drawn on the corpse) */
    public final Map<String, Part> byName = new java.util.HashMap<>();
    public final boolean humanoid;
    public final Part head;
    public final Part body;

    private ModelInfo(EntityModel<?> model) {
        Set<ModelPart> roots = new LinkedHashSet<>();
        if (model instanceof HierarchicalModel<?> h) roots.add(h.root());
        collectFields(model, roots);

        Map<ModelPart, Part> known = new IdentityHashMap<>();
        Map<ModelPart, String> childNames = new IdentityHashMap<>();
        Map<ModelPart, ModelPart> parents = new IdentityHashMap<>();
        for (ModelPart r : roots) walkNames(r, childNames, parents);
        for (ModelPart r : roots) addTree(r, known, childNames);
        for (Part p : parts) {
            ModelPart parent = parents.get(p.part);
            if (parent != null) p.parent = known.get(parent);
        }
        for (Part p : parts) computeRest(p);

        Part bestHead = null, bestBody = null;
        boolean arms = false;
        for (Part p : parts) {
            if (p.name != null) byName.putIfAbsent(p.name, p);
            if (p.cls == PartClass.ARM) arms = true;
            if (p.cls == PartClass.HEAD && !p.cubes.isEmpty() && (bestHead == null || volume(p) > volume(bestHead))) bestHead = p;
            if (p.cls == PartClass.BODY && !p.cubes.isEmpty() && (bestBody == null || volume(p) > volume(bestBody))) bestBody = p;
        }
        if (bestHead != null) {
            // the skull is the outermost head part (a villager's nose is a child of its head)
            Part h = bestHead;
            while (h.parent != null && h.parent.cls == PartClass.HEAD && !h.parent.cubes.isEmpty()) h = h.parent;
            bestHead = h;
        }
        head = bestHead;
        body = bestBody;
        humanoid = arms;

        for (Part p : parts) {
            if (!p.cls.flops() || (p.cubes.isEmpty() && childCount(p) == 0)) continue;
            boolean nested = false;
            for (Part a = p.parent; a != null; a = a.parent) {
                if (a.cls.flops()) {
                    nested = true;
                    break;
                }
            }
            if (nested) continue;
            // head-class bits that aren't the head itself (beaks, noses) move with the head
            if (p.cls == PartClass.HEAD && head != null && p != head) continue;
            p.flops = true;
            p.index = limbs.size();
            limbs.add(p);
            computeTip(p);
        }
        // overlay parts sharing a limb's pivot (hat, sleeves, trouser legs) follow that limb
        for (Part p : parts) {
            if (p.flops || p.parent != null) continue;
            for (Part l : limbs) {
                if (l.parent == null && l.pivotModel.distanceSquared(p.pivotModel) < 0.01F && sameShape(p, l)) {
                    p.follows = l;
                    break;
                }
            }
        }
    }

    /** An overlay is the same box as the part under it, puffed out by up to a pixel or so. */
    private static boolean sameShape(Part a, Part b) {
        if (a.cubes.isEmpty() || b.cubes.isEmpty()) return false;
        ModelPart.Cube x = a.cubes.get(0), y = b.cubes.get(0);
        float tol = 1.3F;
        return Math.abs(x.minX - y.minX) < tol && Math.abs(x.maxX - y.maxX) < tol
                && Math.abs(x.minY - y.minY) < tol && Math.abs(x.maxY - y.maxY) < tol
                && Math.abs(x.minZ - y.minZ) < tol && Math.abs(x.maxZ - y.maxZ) < tol;
    }

    public static ModelInfo of(EntityModel<?> model) {
        return CACHE.computeIfAbsent(model, m -> new ModelInfo((EntityModel<?>) m));
    }

    public static void clearCache() {
        CACHE.clear();
    }

    // ---- discovery ----------------------------------------------------------------------------

    private static void collectFields(Object model, Set<ModelPart> out) {
        for (Class<?> c = model.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    if (ModelPart.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object v = f.get(model);
                        if (v instanceof ModelPart mp) out.add(mp);
                    } else if (f.getType().isArray() && ModelPart.class.isAssignableFrom(f.getType().getComponentType())) {
                        f.setAccessible(true);
                        Object v = f.get(model);
                        if (v instanceof ModelPart[] arr) {
                            for (ModelPart mp : arr) if (mp != null) out.add(mp);
                        }
                    } else if (Iterable.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object v = f.get(model);
                        if (v instanceof Iterable<?> it) {
                            for (Object o : it) if (o instanceof ModelPart mp) out.add(mp);
                        }
                    }
                } catch (Throwable ignored) {
                    // inaccessible field: skip it
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, ModelPart> childrenOf(ModelPart part) {
        try {
            return CHILDREN == null ? Map.of() : (Map<String, ModelPart>) CHILDREN.get(part);
        } catch (IllegalAccessException e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    static List<ModelPart.Cube> cubesOf(ModelPart part) {
        try {
            return CUBES == null ? List.of() : (List<ModelPart.Cube>) CUBES.get(part);
        } catch (IllegalAccessException e) {
            return List.of();
        }
    }

    public static ModelPart.Polygon[] polygonsOf(ModelPart.Cube cube) {
        try {
            return POLYGONS == null ? new ModelPart.Polygon[0] : (ModelPart.Polygon[]) POLYGONS.get(cube);
        } catch (IllegalAccessException e) {
            return new ModelPart.Polygon[0];
        }
    }

    private static void walkNames(ModelPart part, Map<ModelPart, String> names, Map<ModelPart, ModelPart> parents) {
        for (Map.Entry<String, ModelPart> e : childrenOf(part).entrySet()) {
            names.putIfAbsent(e.getValue(), e.getKey());
            parents.putIfAbsent(e.getValue(), part);
            walkNames(e.getValue(), names, parents);
        }
    }

    private void addTree(ModelPart part, Map<ModelPart, Part> known, Map<ModelPart, String> childNames) {
        if (known.containsKey(part)) return;
        String name = childNames.get(part);
        if (name == null && ((Object) part) instanceof NamedPart np) name = np.corpsedecay$getName();
        Part p = new Part(part, name);
        known.put(part, p);
        parts.add(p);
        for (ModelPart c : childrenOf(part).values()) addTree(c, known, childNames);
    }

    private static void computeRest(Part p) {
        Matrix4f m = new Matrix4f();
        Quaternionf parentRot = new Quaternionf();
        List<Part> chain = new ArrayList<>();
        for (Part a = p; a != null; a = a.parent) chain.add(0, a);
        for (int i = 0; i < chain.size(); i++) {
            Part a = chain.get(i);
            m.translate(a.rest.x, a.rest.y, a.rest.z);
            if (i == chain.size() - 1) p.pivotModel.set(m.m30(), m.m31(), m.m32());
            Quaternionf r = a.restRotation();
            m.rotate(r);
            if (i < chain.size() - 1) parentRot.mul(r);
        }
        p.restToModel.set(m);
        p.parentRot.set(parentRot);
    }

    private static void computeTip(Part p) {
        float best = -1;
        for (ModelPart.Cube c : p.cubes) {
            for (int i = 0; i < 8; i++) {
                float x = (i & 1) != 0 ? c.maxX : c.minX;
                float y = (i & 2) != 0 ? c.maxY : c.minY;
                float z = (i & 4) != 0 ? c.maxZ : c.minZ;
                float d = x * x + y * y + z * z;
                if (d > best) {
                    best = d;
                    p.tip.set(x, y, z);
                }
            }
        }
        if (best <= 0) p.tip.set(0, 4, 0);
    }

    private static int childCount(Part p) {
        return childrenOf(p.part).size();
    }

    public static float volume(Part p) {
        float v = 0;
        for (ModelPart.Cube c : p.cubes) v += (c.maxX - c.minX) * (c.maxY - c.minY) * (c.maxZ - c.minZ);
        return v;
    }
}
