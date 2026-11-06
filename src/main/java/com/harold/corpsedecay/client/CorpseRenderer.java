package com.harold.corpsedecay.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.harold.corpsedecay.CorpseKind;
import com.harold.corpsedecay.DeathCause;
import com.harold.corpsedecay.corpse.CorpseEntity;
import com.harold.corpsedecay.corpse.StuckArrows;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.DrownedOuterLayer;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.layers.HorseMarkingLayer;
import net.minecraft.client.renderer.entity.layers.MushroomCowMushroomLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.SheepFurLayer;
import net.minecraft.client.renderer.entity.layers.TropicalFishPatternLayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;

/** Draws a corpse: the dead mob's own model, in the ragdoll's pose, at its stage of decay. */
public class CorpseRenderer extends EntityRenderer<CorpseEntity> {
    private static final Field LAYERS;

    static {
        Field f = null;
        for (Field x : LivingEntityRenderer.class.getDeclaredFields()) {
            if (x.getType() == List.class) f = x;
        }
        LAYERS = f;
        if (LAYERS != null) LAYERS.setAccessible(true);
    }

    public CorpseRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity corpse) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void render(CorpseEntity corpse, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        CorpseClient cc = CorpseClient.of(corpse);
        if (!cc.ensurePuppet(corpse)) return;
        LivingEntity puppet = cc.puppet;
        LivingEntityRenderer renderer = cc.renderer;

        CorpseKind kind = corpse.kind();
        boolean remains = kind == CorpseKind.REMAINS;
        float decay = kind == CorpseKind.BONES || kind == CorpseKind.NO_DECAY ? 0F : remains ? 1F : corpse.decay(partial);
        ResourceLocation original = renderer.getTextureLocation(puppet);
        boolean burnt = corpse.burnt();
        DeathCause cause = corpse.cause();
        // old remains are drawn like a rotted-away body of their own kind, then weathered
        CorpseKind texKind = remains ? (CorpseKind.of(puppet.getType()) == CorpseKind.BONES ? CorpseKind.BONES : CorpseKind.FLESH) : kind;
        int variant = remains ? DecayTextures.REMAINS + corpse.material()
                : burnt ? DecayTextures.BURNT
                : corpse.mummy() == CorpseEntity.BOG ? DecayTextures.BOG
                : corpse.mummy() == CorpseEntity.ICE ? DecayTextures.FREEZE_DRIED
                : cause == DeathCause.DROWNED ? DecayTextures.DROWNED
                : cause == DeathCause.FROZEN ? DecayTextures.FROZEN
                : cause == DeathCause.WITHERED ? DecayTextures.WITHERED : DecayTextures.NORMAL;
        ResourceLocation tex = DecayTextures.get(original, cc.info, texKind, variant, decay);
        RenderType type = null;
        if (tex != null && tex != original) {
            boolean fading = kind == CorpseKind.NO_BONES && decay > 0.8F;
            type = fading ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex);
        }

        ps.pushPose();
        ps.mulPose(cc.body(partial));
        float squash = corpse.squash();
        if (squash < 1F) ps.scale(1F + (1F - squash) * 0.25F, squash, 1F + (1F - squash) * 0.25F); // crushed flat
        ps.translate(0, -corpse.bodyHeight() / 2F, 0);

        RenderContext ctx = new RenderContext(puppet, type);
        cc.applyPoses(ctx, partial);
        if (cause == DeathCause.EXPLODED) cc.hideMissingLimbs(corpse, ctx);
        renderGrowths(corpse, ps, buffers, light);
        List<RenderLayer> layers = layersOf(renderer);
        List<RenderLayer> saved = layers == null ? null : new ArrayList<>(layers);
        ctx.begin();
        try {
            final boolean blownUp = cause == DeathCause.EXPLODED;
            if (layers != null) {
                layers.removeIf(l -> hideLayer(l, texKind, burnt, decay)
                        || (blownUp && l instanceof net.minecraft.client.renderer.entity.layers.ItemInHandLayer));
            }
            renderer.render(puppet, 0F, 0F, ps, buffers, light);
        } catch (Throwable t) {
            // a renderer that can't cope with a stand-in mob just doesn't draw
            if (!cc.loggedError) {
                cc.loggedError = true;
                CorpseDecayClientLog.warn("Couldn't draw the corpse of " + corpse.mobType() + ": " + t);
                CorpseDecayClientLog.trace(t);
            }
        } finally {
            if (layers != null) {
                layers.clear();
                layers.addAll(saved);
            }
            ctx.end();
        }
        if (decay > 0.78F && !remains && corpse.leavesBeakAndPen()) {
            SquidBones.render(ps, buffers, light, corpse.mobType().contains("glow"), burnt);
        }
        renderArrows(corpse, cc, ps, buffers, light);
        ps.popPose();
        renderRope(corpse, cc, partial, ps, buffers);
        super.render(corpse, yaw, partial, ps, buffers, light);
    }

    /** The lead: from the body to the player leading it, or to the knot on the fence it's tied to. */
    private static void renderRope(CorpseEntity corpse, CorpseClient cc, float partial, PoseStack ps, MultiBufferSource buffers) {
        if (!corpse.roped()) return;
        net.minecraft.world.phys.Vec3 to;
        int state = corpse.state();
        if (state == CorpseEntity.TIED || state == CorpseEntity.HANGING) {
            net.minecraft.core.BlockPos a = corpse.anchor();
            to = new net.minecraft.world.phys.Vec3(a.getX() + 0.5, a.getY() + 0.4, a.getZ() + 0.5);
        } else {
            net.minecraft.world.entity.Entity h = corpse.level().getEntity(corpse.holder());
            if (h == null) return;
            to = h.getRopeHoldPosition(partial);
        }
        org.joml.Vector3f local = cc.body(partial).transform(corpse.ropePoint());
        double cx = net.minecraft.util.Mth.lerp(partial, corpse.xo, corpse.getX());
        double cy = net.minecraft.util.Mth.lerp(partial, corpse.yo, corpse.getY());
        double cz = net.minecraft.util.Mth.lerp(partial, corpse.zo, corpse.getZ());
        double fx = cx + local.x, fy = cy + local.y, fz = cz + local.z;
        float dx = (float) (to.x - fx), dy = (float) (to.y - fy), dz = (float) (to.z - fz);
        if (dx * dx + dz * dz < 1e-6F) dx = 0.001F;
        ps.pushPose();
        ps.translate(local.x, local.y, local.z);
        com.mojang.blaze3d.vertex.VertexConsumer vc = buffers.getBuffer(RenderType.leash());
        org.joml.Matrix4f m = ps.last().pose();
        float k = net.minecraft.util.Mth.invSqrt(dx * dx + dz * dz) * 0.025F / 2.0F;
        float ox = dz * k, oz = dx * k;
        net.minecraft.core.BlockPos p0 = net.minecraft.core.BlockPos.containing(fx, fy, fz);
        net.minecraft.core.BlockPos p1 = net.minecraft.core.BlockPos.containing(to);
        var level = corpse.level();
        int b0 = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p0), b1 = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p1);
        int s0 = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, p0), s1 = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, p1);
        for (int i = 0; i <= 24; i++) ropePair(vc, m, dx, dy, dz, b0, b1, s0, s1, 0.025F, 0.025F, ox, oz, i, false);
        for (int i = 24; i >= 0; i--) ropePair(vc, m, dx, dy, dz, b0, b1, s0, s1, 0.025F, 0.0F, ox, oz, i, true);
        ps.popPose();
    }

    /** One rung of the rope ribbon (the same shape as a vanilla lead, sagging between its ends). */
    private static void ropePair(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, float dx, float dy, float dz,
                                 int b0, int b1, int s0, int s1, float y0, float y1, float ox, float oz, int i, boolean flip) {
        float f = i / 24.0F;
        int light = net.minecraft.client.renderer.LightTexture.pack((int) net.minecraft.util.Mth.lerp(f, b0, b1),
                (int) net.minecraft.util.Mth.lerp(f, s0, s1));
        float shade = i % 2 == (flip ? 1 : 0) ? 0.7F : 1.0F;
        float r = 0.5F * shade, g = 0.4F * shade, b = 0.3F * shade;
        float x = dx * f;
        float y = dy > 0.0F ? dy * f * f : dy - dy * (1.0F - f) * (1.0F - f);
        float z = dz * f;
        vc.vertex(m, x - ox, y + y1, z + oz).color(r, g, b, 1.0F).uv2(light).endVertex();
        vc.vertex(m, x + ox, y + y0 - y1, z - oz).color(r, g, b, 1.0F).uv2(light).endVertex();
    }

    @SuppressWarnings("rawtypes")
    private static List<RenderLayer> layersOf(LivingEntityRenderer<?, ?> renderer) {
        try {
            return LAYERS == null ? null : (List<RenderLayer>) LAYERS.get(renderer);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /** Glowing eyes go out, wool falls off, skin markings rot away; armour and clothes stay. */
    @SuppressWarnings("rawtypes")
    private static boolean hideLayer(RenderLayer layer, CorpseKind kind, boolean burnt, float decay) {
        if (layer instanceof EyesLayer || layer instanceof EnergySwirlLayer) return true;
        String name = layer.getClass().getSimpleName();
        if (name.contains("Eyes") || name.contains("Emissive") || name.contains("Glow")) return true;
        // fire takes wool, fur, skin markings and cloth outfits (armour survives)
        if (burnt && (layer instanceof SheepFurLayer || layer instanceof HorseMarkingLayer || layer instanceof TropicalFishPatternLayer
                || layer instanceof DrownedOuterLayer || layer instanceof MushroomCowMushroomLayer || name.contains("Profession")
                || layer instanceof net.minecraft.client.renderer.entity.layers.LlamaDecorLayer)) {
            return true;
        }
        if (kind != CorpseKind.FLESH && kind != CorpseKind.NO_BONES) return false;
        if (layer instanceof SheepFurLayer) return decay > 0.38F;
        if (layer instanceof HorseMarkingLayer || layer instanceof TropicalFishPatternLayer) return decay > 0.3F;
        if (layer instanceof DrownedOuterLayer) return decay > 0.5F;
        if (layer instanceof MushroomCowMushroomLayer) return decay > 0.45F;
        return false;
    }

    /**
     * Whatever grows on old remains (mushrooms, moss, roots, snow, cobwebs), drawn upright on the
     * side of the body facing up. Called inside the body's frame, centred on its middle.
     */
    private void renderGrowths(CorpseEntity corpse, PoseStack ps, MultiBufferSource buffers, int light) {
        ListTag list = corpse.growths();
        if (list.isEmpty()) return;
        org.joml.Quaternionf inv = new org.joml.Quaternionf(corpse.orientation()).conjugate();
        var blocks = net.minecraft.client.Minecraft.getInstance().getBlockRenderer();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            net.minecraft.world.level.block.Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                    .getValue(new ResourceLocation(t.getString("Block")));
            if (block == null || block == net.minecraft.world.level.block.Blocks.AIR) continue;
            float sc = t.getFloat("Scale");
            ps.pushPose();
            ps.translate(t.getFloat("X"), t.getFloat("Y") + corpse.bodyHeight() / 2F, t.getFloat("Z"));
            ps.mulPose(inv); // stand up straight in the world, whichever way the body lies
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(t.getFloat("Yaw")));
            ps.scale(sc, sc, sc);
            ps.translate(-0.5, 0, -0.5);
            try {
                blocks.renderSingleBlock(block.defaultBlockState(), ps, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                        net.minecraftforge.client.model.data.ModelData.EMPTY, null);
            } catch (Throwable ignored) {
                // a block that can't be drawn on its own is left off
            }
            ps.popPose();
        }
    }

    /** Arrows stay stuck where they went in. */
    private void renderArrows(CorpseEntity corpse, CorpseClient cc, PoseStack ps, MultiBufferSource buffers, int light) {
        ListTag arrows = corpse.arrows();
        if (arrows.isEmpty()) return;
        for (int i = 0; i < arrows.size(); i++) {
            CompoundTag a = arrows.getCompound(i);
            AbstractArrow e = arrowEntity(corpse, cc, a);
            if (e == null) continue;
            float dx = a.getFloat("DX"), dy = a.getFloat("DY"), dz = a.getFloat("DZ");
            float yRot = (float) (Mth.atan2(dx, dz) * Mth.RAD_TO_DEG);
            float xRot = (float) (Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            e.setYRot(yRot);
            e.yRotO = yRot;
            e.setXRot(xRot);
            e.xRotO = xRot;
            ps.pushPose();
            // an arrow is drawn mostly behind its position (the tip is ~0.22 in front), so pushing the
            // position a little past the wound buries the tip in the body like a real hit
            ps.translate(a.getFloat("X") + dx * 0.14F, a.getFloat("Y") + dy * 0.14F, a.getFloat("Z") + dz * 0.14F);
            try {
                EntityRenderer<? super AbstractArrow> r = entityRenderDispatcher.getRenderer(e);
                r.render(e, yRot, 0F, ps, buffers, light);
            } catch (Throwable ignored) {
                // skip an arrow that can't be drawn
            }
            ps.popPose();
        }
    }

    private static AbstractArrow arrowEntity(CorpseEntity corpse, CorpseClient cc, CompoundTag a) {
        if (a.getInt("Kind") == StuckArrows.KIND_SPECTRAL) {
            if (cc.spectral == null) cc.spectral = new SpectralArrow(corpse.level(), 0, 0, 0);
            return cc.spectral;
        }
        if (cc.arrow == null) cc.arrow = new Arrow(corpse.level(), 0, 0, 0);
        CompoundTag t = new CompoundTag();
        t.putInt("Color", a.contains("Color") ? a.getInt("Color") : -1);
        try {
            cc.arrow.load(t);
        } catch (Throwable ignored) {
            // plain arrow then
        }
        return cc.arrow;
    }
}
