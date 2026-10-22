package com.harold.corpsedecay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.harold.corpsedecay.client.RenderContext;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    /** A corpse's body is drawn with the texture for its stage of decay. */
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void corpsedecay$decayTexture(LivingEntity entity, boolean bodyVisible, boolean translucent, boolean glowing,
                                          CallbackInfoReturnable<RenderType> cir) {
        RenderContext ctx = RenderContext.current;
        if (ctx != null && entity == ctx.puppet && ctx.renderType() != null) {
            cir.setReturnValue(ctx.renderType());
        }
    }
}
