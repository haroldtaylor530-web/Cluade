package com.harold.corpsedecay.corpse;

import java.util.List;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The body as a rigid box: eight corner points kept the same distance apart by constraints
 * (Verlet integration), falling under gravity and colliding with the real collision shape of
 * every block they touch. Corner i has bit 0 = +x, bit 1 = +y (up), bit 2 = +z (forward).
 */
public final class Ragdoll {
    private static final int SUBSTEPS = 2;
    private static final int ITERATIONS = 4;
    private static final double GRAVITY = 0.08 / (SUBSTEPS * SUBSTEPS);
    private static final double AIR_DRAG = 0.995;
    private static final double WATER_DRAG = 0.90;
    private static final double FRICTION = 0.55;
    private static final double BOUNCE = 0.12;
    private static final double MAX_STEP = 0.9;

    private final double[] half = new double[3];
    private final double[][] p = new double[8][3];
    private final double[][] prev = new double[8][3];
    private final double[] rest = new double[64];

    /** extra push on the top corners for the first ticks so the body actually collapses */
    private double collapseX, collapseZ;
    private int collapseTicks;
    /** keep pushing until the body has actually gone over (up to this many ticks) */
    private int collapseUntilDown;
    private double collapseStop = 0.55;
    /** this body is meant to end up lying down (not a flat spider) */
    private boolean toppling;
    private int blockedTicks;
    private int retries;

    private int stillTicks;
    private int age;
    private boolean landed;
    private double impactSpeed;
    private boolean inLava;

    public Ragdoll(float width, float height, float depth) {
        half[0] = Math.max(0.1, width / 2.0);
        half[1] = Math.max(0.1, height / 2.0);
        half[2] = Math.max(0.1, depth / 2.0);
    }

    /** Places the box at {@code center} with orientation {@code q}, moving with the given velocity (blocks/tick). */
    public void place(Vec3 center, Quaternionf q, Vec3 velocity, Vector3f spin) {
        Vector3f v = new Vector3f();
        for (int i = 0; i < 8; i++) {
            corner(i, v);
            q.transform(v);
            p[i][0] = center.x + v.x;
            p[i][1] = center.y + v.y;
            p[i][2] = center.z + v.z;
            // velocity of this corner = linear + spin x r
            double vx = velocity.x + (spin.y * v.z - spin.z * v.y);
            double vy = velocity.y + (spin.z * v.x - spin.x * v.z);
            double vz = velocity.z + (spin.x * v.y - spin.y * v.x);
            prev[i][0] = p[i][0] - vx / SUBSTEPS;
            prev[i][1] = p[i][1] - vy / SUBSTEPS;
            prev[i][2] = p[i][2] - vz / SUBSTEPS;
        }
        for (int i = 0; i < 8; i++) {
            for (int j = i + 1; j < 8; j++) {
                rest[i * 8 + j] = dist(p[i], p[j]);
            }
        }
        stillTicks = 0;
        age = 0;
    }

    /** Makes the body tip over towards (dx, dz) for the first ticks. */
    public void collapse(double dx, double dz, double strength, int ticks, int untilDown, double stopUpright) {
        collapseStop = stopUpright;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-6) return;
        collapseX = dx / len * strength;
        collapseZ = dz / len * strength;
        collapseTicks = ticks;
        collapseUntilDown = untilDown;
        toppling = untilDown > 0;
        retries = 0;
        blockedTicks = 0;
    }

    /** How upright the body is: 1 = standing, 0 = lying on its side or back. */
    public double uprightness() {
        Vector3f up = axis(1);
        double len = up.length();
        return len < 1e-9 ? 0 : up.y / len;
    }

    /** Shoves the whole body (a punch, an explosion). */
    public void push(Vec3 impulse, boolean topHeavy) {
        for (int i = 0; i < 8; i++) {
            double k = topHeavy && (i & 2) != 0 ? 1.4 : (topHeavy ? 0.6 : 1.0);
            prev[i][0] -= impulse.x * k / SUBSTEPS;
            prev[i][1] -= impulse.y * k / SUBSTEPS;
            prev[i][2] -= impulse.z * k / SUBSTEPS;
        }
        stillTicks = 0;
    }

    /** One game tick. Returns true once the body has come to rest. */
    public boolean tick(Level level) {
        age++;
        inLava = false;
        double before = 0;
        double[] start = new double[24];
        for (int i = 0; i < 8; i++) {
            start[i * 3] = p[i][0];
            start[i * 3 + 1] = p[i][1];
            start[i * 3 + 2] = p[i][2];
        }
        for (int s = 0; s < SUBSTEPS; s++) {
            for (int i = 0; i < 8; i++) {
                double[] a = p[i];
                double[] o = prev[i];
                boolean water = inWater(level, a);
                double drag = water ? WATER_DRAG : AIR_DRAG;
                double g = water ? GRAVITY * 0.08 : GRAVITY;
                double vx = (a[0] - o[0]) * drag;
                double vy = (a[1] - o[1]) * drag - g;
                double vz = (a[2] - o[2]) * drag;
                if (collapseTicks > 0 && (i & 2) != 0) {
                    vx += collapseX / (SUBSTEPS * SUBSTEPS);
                    vz += collapseZ / (SUBSTEPS * SUBSTEPS);
                }
                double sp = Math.sqrt(vx * vx + vy * vy + vz * vz);
                if (sp > MAX_STEP) {
                    vx *= MAX_STEP / sp;
                    vy *= MAX_STEP / sp;
                    vz *= MAX_STEP / sp;
                }
                o[0] = a[0];
                o[1] = a[1];
                o[2] = a[2];
                a[0] += vx;
                a[1] += vy;
                a[2] += vz;
            }
            for (int it = 0; it < ITERATIONS; it++) {
                for (int i = 0; i < 8; i++) {
                    for (int j = i + 1; j < 8; j++) {
                        double[] a = p[i];
                        double[] b = p[j];
                        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
                        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (len < 1e-9) continue;
                        double diff = (len - rest[i * 8 + j]) / len * 0.5;
                        a[0] += dx * diff;
                        a[1] += dy * diff;
                        a[2] += dz * diff;
                        b[0] -= dx * diff;
                        b[1] -= dy * diff;
                        b[2] -= dz * diff;
                    }
                }
                for (int i = 0; i < 8; i++) collide(level, i);
            }
        }
        if (collapseTicks > 0) collapseTicks--;
        if (collapseUntilDown > 0) {
            collapseUntilDown--;
            if (collapseTicks == 0 && uprightness() > collapseStop) collapseTicks = 1;
        }

        double maxMove = 0;
        for (int i = 0; i < 8; i++) {
            double dx = p[i][0] - start[i * 3], dy = p[i][1] - start[i * 3 + 1], dz = p[i][2] - start[i * 3 + 2];
            maxMove = Math.max(maxMove, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        // propped up against a wall or wedged in a gap: try falling another way
        if (toppling && retries < 4 && uprightness() > 0.75) {
            if (maxMove < 0.012) blockedTicks++;
            else blockedTicks = 0;
            if (blockedTicks > 6) {
                retries++;
                double ox = collapseX, oz = collapseZ;
                switch (retries) {
                    case 1 -> { collapseX = -ox; collapseZ = -oz; }
                    case 2 -> { collapseX = -oz; collapseZ = ox; }
                    case 3 -> { collapseX = -collapseX; collapseZ = -collapseZ; }
                    default -> { collapseX = ox * 1.6; collapseZ = oz * 1.6; }
                }
                collapseTicks = 6;
                collapseUntilDown = 40;
                blockedTicks = 0;
                stillTicks = 0;
                return false;
            }
        }
        if (maxMove < 0.006 && collapseTicks == 0) stillTicks++;
        else stillTicks = 0;
        return stillTicks > 12 || age > 400;
    }

    private void collide(Level level, int i) {
        double[] a = p[i];
        double[] o = prev[i];
        BlockPos pos = BlockPos.containing(a[0], a[1], a[2]);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        if (state.getFluidState().is(FluidTags.LAVA)) inLava = true;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return;
        List<AABB> boxes = shape.toAabbs();
        for (AABB local : boxes) {
            AABB b = local.move(pos);
            if (a[0] <= b.minX || a[0] >= b.maxX || a[1] <= b.minY || a[1] >= b.maxY || a[2] <= b.minZ || a[2] >= b.maxZ) continue;
            // push out through the face the point came in through (falls back to the shallowest face)
            double[] mins = {b.minX, b.minY, b.minZ};
            double[] maxs = {b.maxX, b.maxY, b.maxZ};
            int[] base = {pos.getX(), pos.getY(), pos.getZ()};
            int axis = -1;
            double target = 0;
            double best = Double.MAX_VALUE;
            // push out through a face that opens onto free space (never down into the block
            // below), preferring the face the point actually came in through, then the shallowest
            for (int k = 0; k < 3; k++) {
                for (int dir = -1; dir <= 1; dir += 2) {
                    double face = dir > 0 ? maxs[k] : mins[k];
                    boolean onEdge = dir > 0 ? face >= base[k] + 1 - 1e-6 : face <= base[k] + 1e-6;
                    if (onEdge) {
                        BlockPos n = pos.relative(net.minecraft.core.Direction.fromAxisAndDirection(
                                net.minecraft.core.Direction.Axis.values()[k],
                                dir > 0 ? net.minecraft.core.Direction.AxisDirection.POSITIVE : net.minecraft.core.Direction.AxisDirection.NEGATIVE));
                        if (level.getBlockState(n).isCollisionShapeFullBlock(level, n)) continue;
                    }
                    double depth = dir > 0 ? maxs[k] - a[k] : a[k] - mins[k];
                    boolean came = dir > 0 ? o[k] >= maxs[k] - 1e-6 : o[k] <= mins[k] + 1e-6;
                    double score = depth - (came ? 2.0 : 0.0) - (k == 1 && dir > 0 ? 0.05 : 0.0);
                    if (score < best) {
                        best = score;
                        axis = k;
                        target = face + dir * 1e-4;
                    }
                }
            }
            if (axis < 0) {
                // buried on every side: lift it out of the top
                axis = 1;
                target = maxs[1] + 1e-4;
            }
            double v = a[axis] - o[axis];
            if (axis == 1 && v < 0) {
                if (!landed) {
                    landed = true;
                    impactSpeed = Math.max(impactSpeed, -v * SUBSTEPS);
                }
            }
            a[axis] = target;
            o[axis] = target + v * BOUNCE;
            for (int k = 0; k < 3; k++) {
                if (k == axis) continue;
                double t = a[k] - o[k];
                o[k] = a[k] - t * (1.0 - FRICTION);
            }
            return;
        }
    }

    private static boolean inWater(Level level, double[] a) {
        BlockPos pos = BlockPos.containing(a[0], a[1], a[2]);
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    public Vec3 center() {
        double x = 0, y = 0, z = 0;
        for (double[] a : p) {
            x += a[0];
            y += a[1];
            z += a[2];
        }
        return new Vec3(x / 8, y / 8, z / 8);
    }

    /** Orientation of the box, rebuilt from its corner points. */
    public Quaternionf orientation() {
        Vector3f x = axis(0), y = axis(1);
        x.normalize();
        y.sub(new Vector3f(x).mul(x.dot(y))).normalize();
        Vector3f z = new Vector3f(x).cross(y).normalize();
        if (!Float.isFinite(x.x + y.y + z.z)) return new Quaternionf();
        Matrix3f m = new Matrix3f(x, y, z);
        return new Quaternionf().setFromNormalized(m).normalize();
    }

    private Vector3f axis(int bit) {
        double ax = 0, ay = 0, az = 0;
        for (int i = 0; i < 8; i++) {
            double s = (i & (1 << bit)) != 0 ? 1 : -1;
            ax += s * p[i][0];
            ay += s * p[i][1];
            az += s * p[i][2];
        }
        return new Vector3f((float) ax, (float) ay, (float) az);
    }

    private void corner(int i, Vector3f out) {
        out.set((i & 1) != 0 ? half[0] : -half[0], (i & 2) != 0 ? half[1] : -half[1], (i & 4) != 0 ? half[2] : -half[2]);
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Speed of the first hit on the ground, then cleared (for the thud sound). */
    public double takeImpact() {
        double v = impactSpeed;
        impactSpeed = 0;
        return v;
    }

    public boolean inLava() {
        return inLava;
    }

    public static float lerpAngle(float a, float b, float t) {
        return a + Mth.wrapDegrees(b - a) * t;
    }
}
