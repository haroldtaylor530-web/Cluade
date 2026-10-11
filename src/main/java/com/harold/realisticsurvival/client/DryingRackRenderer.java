package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.block.DryingRackBlock;
import com.harold.realisticsurvival.block.DryingRackBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The food hanging from the rack's pole. */
public class DryingRackRenderer implements BlockEntityRenderer<DryingRackBlockEntity, DryingRackRenderer.State> {
    public static class State extends BlockEntityRenderState {
        public List<ItemStackRenderState> items = Collections.emptyList();
        public List<Integer> counts = Collections.emptyList();
        public Direction facing = Direction.NORTH;
    }

    private final ItemModelResolver itemModelResolver;

    public DryingRackRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DryingRackBlockEntity rack, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(rack, state, partialTicks, cameraPosition, breakProgress);
        state.facing = rack.getBlockState().getValue(DryingRackBlock.FACING);
        int seed = (int) rack.getBlockPos().asLong();
        state.items = new ArrayList<>();
        state.counts = new ArrayList<>();
        for (int slot = 0; slot < rack.getItems().size(); slot++) {
            ItemStackRenderState item = new ItemStackRenderState();
            itemModelResolver.updateForTopItem(item, rack.getItems().get(slot), ItemDisplayContext.FIXED, rack.getLevel(), null, seed + slot);
            state.items.add(item);
            state.counts.add(rack.getItems().get(slot).getCount());
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int slot = 0; slot < state.items.size(); slot++) {
            ItemStackRenderState item = state.items.get(slot);
            if (item.isEmpty()) continue;
            int count = state.counts.get(slot);
            for (int n = 0; n < Math.min(2, count); n++) {
                pose.pushPose();
                pose.translate(0.5F, 0.0F, 0.5F);
                pose.rotateDegrees(Axis.YP, -state.facing.toYRot());
                // along the pole, hanging just below it; a second piece hangs behind the first
                float x = -0.3F + slot * 0.2F;
                float z = n == 0 ? 0.03F : -0.03F;
                pose.translate(x, 0.6F, z);
                pose.rotateDegrees(Axis.ZP, (slot % 2 == 0 ? 6.0F : -6.0F) + n * 10.0F);
                pose.scale(0.36F, 0.36F, 0.36F);
                item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
                pose.popPose();
            }
        }
    }
}
