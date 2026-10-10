package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.entity.ThrownSpear;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Draws a thrown spear using the spear's own item model (the 3D one), pointed along its
 * flight path. The model's tip should point east (+X in Blockbench).
 */
public class SpearRenderer extends EntityRenderer<ThrownSpear, SpearRenderer.State> {
    private final ItemModelResolver itemModelResolver;

    public SpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    public static class State extends EntityRenderState {
        public float yRot;
        public float xRot;
        public float shake;
        public final ItemStackRenderState item = new ItemStackRenderState();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ThrownSpear spear, State state, float partialTicks) {
        super.extractRenderState(spear, state, partialTicks);
        state.yRot = spear.getYRot(partialTicks);
        state.xRot = spear.getXRot(partialTicks);
        state.shake = spear.shakeTime - partialTicks;
        itemModelResolver.updateForNonLiving(state.item, spear.getItem(), ItemDisplayContext.NONE, spear);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // same orientation maths as vanilla arrows: +X points along the flight path
        poseStack.rotate(Axis.YP.rotationDegrees(state.yRot - 90.0F));
        poseStack.rotate(Axis.ZP.rotationDegrees(state.xRot));
        if (state.shake > 0.0F) {
            float wobble = -net.minecraft.util.Mth.sin(state.shake * 3.0F) * state.shake;
            poseStack.rotate(Axis.ZP.rotationDegrees(wobble));
        }
        // the item renderer centres the 16x16x16 model box on the origin; the spear model runs
        // from x=-14 to x=24 at y=10.5, z=8.5, so shift its middle onto the entity
        poseStack.translate(0.1875F, -0.156F, -0.031F);
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
