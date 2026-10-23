package com.harold.corpsedecay.client;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.harold.corpsedecay.CorpseDecay;
import com.harold.corpsedecay.corpse.WhaleRemainsEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Draws a whale's skeleton lying on the sea floor, and what's growing on it. */
public class WhaleRenderer extends EntityRenderer<WhaleRemainsEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(CorpseDecay.MODID, "textures/entity/whale_bone.png");
    private static final Map<String, WhaleSkeleton> CACHE = new HashMap<>();

    public WhaleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0F;
    }

    @Override
    public ResourceLocation getTextureLocation(WhaleRemainsEntity e) {
        return TEXTURE;
    }

    static WhaleSkeleton skeleton(WhaleRemainsEntity e) {
        String key = e.kind() + "|" + e.length() + "|" + e.seed() + "|" + e.whole() + "|" + e.flesh();
        if (CACHE.size() > 64) CACHE.clear();
        return CACHE.computeIfAbsent(key, k -> WhaleSkeleton.build(e.kind(), e.length(), e.seed(), e.whole(), e.flesh()));
    }

    @Override
    public void render(WhaleRemainsEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        WhaleSkeleton s = skeleton(e);
        ps.pushPose();
        ps.mulPose(Axis.YP.rotation(-e.facing()));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f pose = ps.last().pose();
        Matrix3f normal = ps.last().normal();
        for (WhaleSkeleton.Bone b : s.bones) cube(vc, pose, normal, b, light);
        float time = (e.tickCount + partial) / 20F;
        for (WhaleSkeleton.Creature c : s.creatures) {
            for (WhaleSkeleton.Bone b : WhaleSkeleton.pose(c, time)) cube(vc, pose, normal, b, light);
        }
        var blocks = Minecraft.getInstance().getBlockRenderer();
        for (WhaleSkeleton.Growth g : s.growths) {
            ps.pushPose();
            ps.translate(g.pos().x, g.pos().y, g.pos().z);
            ps.mulPose(Axis.YP.rotationDegrees(g.yaw()));
            ps.scale(g.scale(), g.scale(), g.scale());
            ps.translate(-0.5, 0, -0.5);
            try {
                blocks.renderSingleBlock(g.state(), ps, buffers, light, OverlayTexture.NO_OVERLAY,
                        net.minecraftforge.client.model.data.ModelData.EMPTY, null);
            } catch (Throwable ignored) {
                // leave it off
            }
            ps.popPose();
        }
        ps.popPose();
        super.render(e, yaw, partial, ps, buffers, light);
    }

    private static final int[][] FACES = {
            // normal, then four corners as signs of x, y, z
            {0, 1, 0, -1, 1, -1, -1, 1, 1, 1, 1, 1, 1, 1, -1},
            {0, -1, 0, -1, -1, 1, -1, -1, -1, 1, -1, -1, 1, -1, 1},
            {1, 0, 0, 1, -1, -1, 1, 1, -1, 1, 1, 1, 1, -1, 1},
            {-1, 0, 0, -1, -1, 1, -1, 1, 1, -1, 1, -1, -1, -1, -1},
            {0, 0, 1, 1, -1, 1, 1, 1, 1, -1, 1, 1, -1, -1, 1},
            {0, 0, -1, -1, -1, -1, -1, 1, -1, 1, 1, -1, 1, -1, -1},
    };
    private static final float[][] UV = {{0, 1}, {0, 0}, {1, 0}, {1, 1}};

    private static void cube(VertexConsumer vc, Matrix4f pose, Matrix3f normal, WhaleSkeleton.Bone b, int light) {
        Matrix4f m = new Matrix4f(pose).mul(b.at());
        Matrix3f n = new Matrix3f(normal).mul(new Matrix3f(b.at()));
        Vector3f h = new Vector3f(b.size()).mul(0.5F);
        int rgb = b.rgb();
        for (int[] f : FACES) {
            Vector3f nn = n.transform(new Vector3f(f[0], f[1], f[2])).normalize();
            float shadeFace = f[1] == -1 ? 0.7F : 1F;
            int cr = Math.round(((rgb >> 16) & 255) * shadeFace), cg = Math.round(((rgb >> 8) & 255) * shadeFace), cb = Math.round((rgb & 255) * shadeFace);
            for (int k = 0; k < 4; k++) {
                float x = f[3 + k * 3] * h.x, y = f[4 + k * 3] * h.y, z = f[5 + k * 3] * h.z;
                Vector3f p = m.transformPosition(new Vector3f(x, y, z));
                vc.vertex(p.x, p.y, p.z).color(cr, cg, cb, 255).uv(UV[k][0], UV[k][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(nn.x, nn.y, nn.z).endVertex();
            }
        }
    }
}
