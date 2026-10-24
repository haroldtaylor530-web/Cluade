package com.harold.corpsedecay.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/**
 * All that's left of a squid: the long thin leaf of its pen, and the little dark hooked beak where
 * its arms met. Drawn in the squid's own frame (feet at the origin, mantle upright between about
 * -0.2 and 0.8), so it lies however the body fell.
 */
final class SquidBones {
    private SquidBones() {
    }

    /** @param glow a glow squid's pen has a faint blue-green cast; @param burnt scorched dark */
    static void render(PoseStack ps, MultiBufferSource buffers, int light, boolean glow, boolean burnt) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WhaleRenderer.TEXTURE));
        Matrix4f pose = ps.last().pose();
        Matrix3f normal = ps.last().normal();
        int rib = glow ? 0xD3E6D0 : 0xE6DBB8;
        int vane = glow ? 0xE1F0DA : 0xF1E9CE;
        int dark = 0x2A1D14, mid = 0x4A3524;
        if (burnt) {
            rib = WhaleSkeleton.shade(rib, 0.45F);
            vane = WhaleSkeleton.shade(vane, 0.4F);
        }
        // the pen: a stiff central rib, with a thin leaf-shaped vane either side that widens and narrows again
        add(vc, pose, normal, light, 0F, 0.3F, 0F, 0.04F, 0.86F, 0.035F, rib);
        int steps = 9;
        for (int i = 0; i < steps; i++) {
            float t = (i + 0.5F) / steps;
            float width = 0.05F + 0.2F * (float) Math.sin(Math.PI * (0.1 + 0.85 * t));
            float y = 0.68F - t * 0.78F;
            add(vc, pose, normal, light, 0F, y, -0.003F, width, 0.09F, 0.012F, vane);
        }
        // a small curved plate at the head end of the pen
        add(vc, pose, normal, light, 0F, 0.74F, 0F, 0.09F, 0.04F, 0.02F, rib);
        // the beak: a hooked upper half over a smaller lower one, dark as old horn
        add(vc, pose, normal, light, 0F, -0.11F, 0F, 0.11F, 0.07F, 0.13F, dark);
        add(vc, pose, normal, light, 0F, -0.15F, 0.07F, 0.05F, 0.07F, 0.05F, dark);
        add(vc, pose, normal, light, 0F, -0.2F, -0.01F, 0.085F, 0.05F, 0.1F, mid);
        add(vc, pose, normal, light, 0F, -0.07F, -0.075F, 0.07F, 0.05F, 0.05F, mid);
    }

    private static void add(VertexConsumer vc, Matrix4f pose, Matrix3f normal, int light, float x, float y, float z,
                            float sx, float sy, float sz, int rgb) {
        WhaleRenderer.cube(vc, pose, normal, new WhaleSkeleton.Bone(new Matrix4f().translation(x, y, z), new Vector3f(sx, sy, sz), rgb), light);
    }
}
