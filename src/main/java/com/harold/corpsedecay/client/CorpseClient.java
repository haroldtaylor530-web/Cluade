package com.harold.corpsedecay.client;

import java.util.List;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.CorpseKind;
import com.harold.corpsedecay.ModRegistry;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Client side of one corpse: a stand-in copy of the dead mob (never added to the world) that the
 * mob's own renderer draws, plus the floppy-limb physics that runs on top of the body's ragdoll.
 */
public final class CorpseClient {
    private int lookVersion = -1;
    private int epoch = -1;
    public LivingEntity puppet;
    public boolean loggedError;
    public LivingEntityRenderer<?, ?> renderer;
    public ModelInfo info;

    private final Quaternionf prevBody = new Quaternionf();
    private final Quaternionf curBody = new Quaternionf();
    private boolean bodyInit;

    private Quaternionf[] limbPrev = new Quaternionf[0];
    private Quaternionf[] limbCur = new Quaternionf[0];
    private Vector3f[] limbVel = new Vector3f[0];
    private int quiet;
    private boolean warmed;

    public net.minecraft.world.entity.projectile.Arrow arrow;
    public net.minecraft.world.entity.projectile.SpectralArrow spectral;

    public static CorpseClient of(CorpseEntity corpse) {
        if (corpse.clientState instanceof CorpseClient c) return c;
        CorpseClient c = new CorpseClient();
        corpse.clientState = c;
        return c;
    }

    /** Runs every client tick for every corpse. */
    public static void tick(CorpseEntity corpse) {
        CorpseClient c = of(corpse);
        c.prevBody.set(c.curBody);
        c.curBody.set(corpse.orientation());
        if (!c.bodyInit) {
            c.prevBody.set(c.curBody);
            c.bodyInit = true;
        }
        if (!c.ensurePuppet(corpse)) return;
        c.tickLimbs(corpse);
        c.flies(corpse);
        c.smoke(corpse);
    }

    public Quaternionf body(float partial) {
        return new Quaternionf(prevBody).slerp(curBody, partial);
    }

    /** (Re)builds the stand-in mob when the corpse's look changes. Returns false if it can't be drawn. */
    public boolean ensurePuppet(CorpseEntity corpse) {
        if (lookVersion == corpse.lookVersion && epoch == ClientSetup.epoch && puppet != null) {
            syncEquipment(corpse);
            return renderer != null;
        }
        lookVersion = corpse.lookVersion;
        epoch = ClientSetup.epoch;
        puppet = null;
        renderer = null;
        info = null;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(corpse.mobType()));
        if (type == null) return false;
        Entity e;
        try {
            e = type.create(corpse.level());
        } catch (Throwable t) {
            return false;
        }
        if (!(e instanceof LivingEntity le)) return false;
        try {
            CompoundTag tag = corpse.renderTag().copy();
            le.load(tag);
        } catch (Throwable ignored) {
            // the mob just looks like its default variety
        }
        le.setId(corpse.getId());
        le.setPos(corpse.getX(), corpse.getY(), corpse.getZ());
        le.setYRot(0);
        le.yRotO = 0;
        le.yBodyRot = 0;
        le.yBodyRotO = 0;
        le.yHeadRot = 0;
        le.yHeadRotO = 0;
        le.setXRot(0);
        le.xRotO = 0;
        le.hurtTime = 0;
        le.deathTime = 0;
        le.setInvisible(false);
        le.setSharedFlagOnFire(false);
        le.walkAnimation.setSpeed(0F);
        if (le instanceof Mob m) m.setNoAi(true);
        puppet = le;
        syncEquipment(corpse);
        EntityRenderer<?> r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(le);
        if (r instanceof LivingEntityRenderer<?, ?> lr) {
            renderer = lr;
            info = ModelInfo.of(lr.getModel());
            int n = info.limbs.size();
            limbPrev = new Quaternionf[n];
            limbCur = new Quaternionf[n];
            limbVel = new Vector3f[n];
            for (int i = 0; i < n; i++) {
                limbCur[i] = info.limbs.get(i).restRotation();
                limbPrev[i] = new Quaternionf(limbCur[i]);
                limbVel[i] = new Vector3f();
            }
            warmed = false;
            quiet = 0;
        }
        return renderer != null;
    }

    private void syncEquipment(CorpseEntity corpse) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack want = corpse.equipment(slot);
            if (!ItemStack.matches(puppet.getItemBySlot(slot), want)) puppet.setItemSlot(slot, want.copy());
        }
    }

    // ---- floppy limbs ------------------------------------------------------------------------------

    private void tickLimbs(CorpseEntity corpse) {
        if (info == null || info.limbs.isEmpty()) return;
        if (corpse.cause() == com.harold.corpsedecay.DeathCause.CRUSHED) return; // squashed flat as it stood
        boolean bodyMoving = !corpse.settled() || prevBody.dot(curBody) < 0.99999F;
        if (!warmed && corpse.settled()) {
            // a body that was already lying here when it came into view: settle its limbs at once
            for (int i = 0; i < 160; i++) step(corpse);
            for (int i = 0; i < limbCur.length; i++) limbPrev[i].set(limbCur[i]);
            warmed = true;
            return;
        }
        warmed = true;
        if (!bodyMoving && quiet > 60) {
            for (int i = 0; i < limbCur.length; i++) limbPrev[i].set(limbCur[i]);
            return;
        }
        for (int i = 0; i < limbCur.length; i++) limbPrev[i].set(limbCur[i]);
        float moved = step(corpse);
        quiet = moved < 0.002F ? quiet + 1 : 0;
    }

    /** One step of every limb swinging towards hanging straight down, stopped by the ground. */
    private float step(CorpseEntity corpse) {
        Level level = corpse.level();
        Quaternionf body = curBody;
        // model space -> world rotation: the body's tilt, then the renderer's flip (model y points down)
        Matrix3f modelToWorld = new Matrix3f().rotation(body).mul(new Matrix3f().scaling(1, -1, -1));
        Matrix3f worldToModel = new Matrix3f(modelToWorld).transpose();
        Vector3f gModel = worldToModel.transform(new Vector3f(0, -1, 0));
        float h = corpse.bodyHeight();
        float moved = 0;
        List<ModelInfo.Part> limbs = info.limbs;
        for (int i = 0; i < limbs.size(); i++) {
            ModelInfo.Part l = limbs.get(i);
            Quaternionf parentInv = new Quaternionf(l.parentRot).conjugate();
            Vector3f g = parentInv.transform(new Vector3f(gModel));
            Quaternionf rest = l.restRotation();
            Vector3f u = new Vector3f(l.tip);
            if (u.lengthSquared() < 1e-6F) continue;
            u.normalize();
            Vector3f r = rest.transform(new Vector3f(u));
            float swing = l.cls.swing() * (corpse.cause() == com.harold.corpsedecay.DeathCause.FELL ? 1.9F : 1F);
            Vector3f t = limitTowards(r, g, swing);
            Quaternionf target = new Quaternionf().rotationTo(r, t).mul(rest);

            Quaternionf cur = limbCur[i];
            Quaternionf diff = new Quaternionf(target).mul(new Quaternionf(cur).conjugate());
            if (diff.w < 0) diff.set(-diff.x, -diff.y, -diff.z, -diff.w);
            float angle = 2F * (float) Math.acos(Math.min(1F, diff.w));
            Vector3f axis = new Vector3f(diff.x, diff.y, diff.z);
            Vector3f vel = limbVel[i];
            if (axis.lengthSquared() > 1e-10F && angle > 1e-5F) {
                axis.normalize().mul(angle * 0.2F);
                vel.mul(0.8F).add(axis);
            } else {
                vel.mul(0.8F);
            }
            float speed = vel.length();
            if (speed > 0.35F) vel.mul(0.35F / speed);
            Quaternionf next = new Quaternionf(cur);
            if (speed > 1e-6F) {
                next = new Quaternionf().rotateAxis(vel.length(), new Vector3f(vel).normalize()).mul(cur).normalize();
            }
            // don't swing a limb into the ground
            Vector3f tipModel = new Vector3f(l.tip);
            next.transform(tipModel);
            l.parentRot.transform(tipModel);
            tipModel.add(l.pivotModel);
            Vector3f midModel = new Vector3f(l.tip).mul(0.5F);
            next.transform(midModel);
            l.parentRot.transform(midModel);
            midModel.add(l.pivotModel);
            if (solid(level, toWorld(corpse, body, tipModel, h)) || solid(level, toWorld(corpse, body, midModel, h))) {
                vel.mul(-0.2F);
            } else {
                moved = Math.max(moved, 1F - Math.abs(next.dot(cur)));
                cur.set(next);
            }
        }
        return moved;
    }

    /** Turns {@code from} towards {@code to} by at most {@code max} radians. */
    private static Vector3f limitTowards(Vector3f from, Vector3f to, float max) {
        float dot = Math.max(-1F, Math.min(1F, from.dot(to)));
        float ang = (float) Math.acos(dot);
        if (ang <= max) return new Vector3f(to);
        Vector3f axis = new Vector3f(from).cross(to);
        if (axis.lengthSquared() < 1e-8F) {
            axis = new Vector3f(from).cross(Math.abs(from.x) < 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 0, 1));
        }
        axis.normalize();
        return new Quaternionf().rotationAxis(max, axis).transform(new Vector3f(from));
    }

    /** Model space (pixels, y down, feet at y=24) to a world position. */
    private static Vec3 toWorld(CorpseEntity corpse, Quaternionf body, Vector3f m, float h) {
        Vector3f local = new Vector3f(m.x / 16F, 1.501F - m.y / 16F - h / 2F, -m.z / 16F);
        body.transform(local);
        return new Vec3(corpse.getX() + local.x, corpse.getY() + local.y, corpse.getZ() + local.z);
    }

    private static boolean solid(Level level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return false;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        for (AABB box : shape.toAabbs()) {
            if (box.move(pos).contains(p)) return true;
        }
        return false;
    }

    /** A body blown apart is missing some of its limbs (chosen the same way every time). */
    public void hideMissingLimbs(CorpseEntity corpse, RenderContext ctx) {
        if (info == null) return;
        int missing = 0;
        for (int i = 0; i < info.limbs.size(); i++) {
            ModelInfo.Part l = info.limbs.get(i);
            if (l.name == null || l == info.head) continue;
            int h = corpse.getUUID().hashCode() * 31 + i * 7919;
            h ^= h >>> 15;
            h *= 0x2c1b3c6d;
            h ^= h >>> 12;
            if ((h & 0xFF) < 115 && missing < info.limbs.size() - 2) {
                ctx.hide(l.name);
                for (ModelInfo.Part p : info.parts) if (p.follows == l && p.name != null) ctx.hide(p.name);
                missing++;
            }
        }
    }

    /** For the CI log: where each limb ended up. */
    public String debug(CorpseEntity corpse) {
        StringBuilder b = new StringBuilder();
        Vector3f up = new Quaternionf(curBody).transform(new Vector3f(0, 1, 0));
        b.append(String.format("%s up=(%.2f,%.2f,%.2f) parts=%d limbs=%d", corpse.mobType(), up.x, up.y, up.z,
                info == null ? -1 : info.parts.size(), info == null ? -1 : info.limbs.size()));
        if (info == null) return b.toString();
        for (int i = 0; i < info.limbs.size(); i++) {
            ModelInfo.Part l = info.limbs.get(i);
            Vector3f tipModel = new Vector3f(l.tip);
            limbCur[i].transform(tipModel);
            l.parentRot.transform(tipModel);
            tipModel.add(l.pivotModel);
            Vec3 w = toWorld(corpse, curBody, tipModel, corpse.bodyHeight());
            float[] e = eulerZYX(limbCur[i]);
            b.append(String.format(" | %s[%s] rot=(%.2f,%.2f,%.2f) tipY=%.2f", l.name, l.cls, e[0], e[1], e[2], w.y));
        }
        b.append(" | names:");
        for (ModelInfo.Part p : info.parts) b.append(' ').append(p.name).append('/').append(p.cls).append(p.follows != null ? "->" + p.follows.name : "");
        return b.toString();
    }

    /** Pushes the current limb poses into the render context. */
    public void applyPoses(RenderContext ctx, float partial) {
        if (info == null) return;
        for (int i = 0; i < info.limbs.size(); i++) {
            ModelInfo.Part l = info.limbs.get(i);
            Quaternionf q = new Quaternionf(limbPrev[i]).slerp(limbCur[i], partial);
            float[] e = eulerZYX(q);
            if (l.name != null) ctx.pose(l.name, e[0], e[1], e[2]);
            for (ModelInfo.Part p : info.parts) {
                if (p.follows == l && p.name != null) ctx.pose(p.name, e[0], e[1], e[2]);
            }
        }
    }

    /** x, y, z angles such that rotationZYX(z, y, x) == q (what ModelPart uses). */
    static float[] eulerZYX(Quaternionf q) {
        Matrix3f m = new Matrix3f().rotation(q);
        float sy = -m.m02;
        sy = Math.max(-1F, Math.min(1F, sy));
        float y = (float) Math.asin(sy);
        float x = (float) Math.atan2(m.m12, m.m22);
        float z = (float) Math.atan2(m.m01, m.m00);
        return new float[] {x, y, z};
    }

    // ---- smoke from a burnt body -------------------------------------------------------------------

    private void smoke(CorpseEntity corpse) {
        if (!corpse.burnt()) return;
        long since = corpse.level().getGameTime() - corpse.burnTime();
        if (since < 0 || since > 900) return;
        RandomSource rand = corpse.level().random;
        float chance = since < 200 ? 0.45F : 0.45F * (1F - (since - 200) / 700F);
        if (rand.nextFloat() > chance) return;
        AABB box = corpse.getBoundingBox();
        double x = box.minX + rand.nextDouble() * box.getXsize();
        double z = box.minZ + rand.nextDouble() * box.getZsize();
        corpse.level().addParticle(rand.nextInt(4) == 0 ? net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE
                : net.minecraft.core.particles.ParticleTypes.SMOKE, x, box.maxY, z, 0, 0.03, 0);
    }

    // ---- flies -------------------------------------------------------------------------------------

    private void flies(CorpseEntity corpse) {
        if (!Config.FLIES.get()) return;
        CorpseKind kind = corpse.kind();
        if (kind != CorpseKind.FLESH && kind != CorpseKind.NO_BONES) return;
        float d = corpse.decay(0);
        com.harold.corpsedecay.DeathCause cause = corpse.cause();
        if (cause == com.harold.corpsedecay.DeathCause.FROZEN && d < 0.4F && corpse.level().random.nextFloat() < 0.04F) {
            AABB b = corpse.getBoundingBox();
            corpse.level().addParticle(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    b.minX + corpse.level().random.nextDouble() * b.getXsize(), b.maxY, b.minZ + corpse.level().random.nextDouble() * b.getZsize(), 0, 0, 0);
        }
        if (d < 0.06F || d > 0.8F || corpse.burnt() || cause == com.harold.corpsedecay.DeathCause.WITHERED
                || (cause == com.harold.corpsedecay.DeathCause.FROZEN && d < 0.5F)) return;
        RandomSource rand = corpse.level().random;
        float size = corpse.bodyWidth() * corpse.bodyHeight();
        float chance = 0.04F + 0.05F * Math.min(2F, size);
        if (d > 0.55F) chance *= 0.5F;
        if (rand.nextFloat() > chance) return;
        AABB box = corpse.getBoundingBox();
        double x = box.minX + rand.nextDouble() * box.getXsize();
        double z = box.minZ + rand.nextDouble() * box.getZsize();
        double y = box.maxY + 0.1 + rand.nextDouble() * 0.4;
        corpse.level().addParticle(ModRegistry.FLY.get(), x, y, z, 0, 0, 0);
    }
}
