package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.entity.FallingTree;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;

/** Draws every block of a falling tree, tipped over around the stump. */
public class FallingTreeRenderer extends EntityRenderer<FallingTree, FallingTreeRenderer.State> {

    public FallingTreeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    public static class State extends EntityRenderState {
        public final List<MovingBlockRenderState> blocks = new ArrayList<>();
        public final List<BlockPos> offsets = new ArrayList<>();
        public float angle;
        public float slump;
        public float dirX, dirZ;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(FallingTree tree, float partialTicks) {
        float e = tree.extent();
        return new AABB(tree.getX() - e, tree.getY() - e, tree.getZ() - e, tree.getX() + e, tree.getY() + e, tree.getZ() + e);
    }

    @Override
    public void extractRenderState(FallingTree tree, State state, float partialTicks) {
        super.extractRenderState(tree, state, partialTicks);
        state.angle = tree.angle(partialTicks);
        state.slump = tree.slump(partialTicks);
        state.dirX = tree.dirX();
        state.dirZ = tree.dirZ();
        List<FallingTree.Part> parts = tree.parts();
        while (state.blocks.size() < parts.size()) state.blocks.add(new MovingBlockRenderState());
        state.offsets.clear();
        BlockPos base = tree.blockPosition();
        for (int i = 0; i < parts.size(); i++) {
            FallingTree.Part part = parts.get(i);
            MovingBlockRenderState b = state.blocks.get(i);
            BlockPos at = base.offset(part.offset());
            b.blockState = part.state();
            b.blockPos = at;
            b.randomSeedPos = at;
            if (tree.level() instanceof ClientLevel level) {
                b.biome = level.getBiome(base);
                b.cardinalLighting = level.cardinalLighting();
                b.lightEngine = level.getLightEngine();
            }
            state.offsets.add(part.offset());
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // tip over around the horizontal axis at right angles to the fall direction
        poseStack.rotate(new Quaternionf().rotationAxis((float) Math.toRadians(state.angle), state.dirZ, 0.0F, -state.dirX));
        for (int i = 0; i < state.offsets.size(); i++) {
            MovingBlockRenderState b = state.blocks.get(i);
            if (b.blockState.getRenderShape() != RenderShape.MODEL) continue;
            BlockPos o = state.offsets.get(i);
            poseStack.pushPose();
            poseStack.translate(o.getX() - 0.5F, o.getY() - state.slump, o.getZ() - 0.5F);
            collector.submitMovingBlock(poseStack, b, state.outlineColor);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
