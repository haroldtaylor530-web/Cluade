package com.harold.corpsedecay.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.harold.corpsedecay.client.NamedPart;
import com.harold.corpsedecay.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.geom.ModelPart;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements NamedPart {
    @Unique
    private String corpsedecay$name;

    @Override
    public String corpsedecay$getName() {
        return corpsedecay$name;
    }

    @Override
    public void corpsedecay$setName(String name) {
        corpsedecay$name = name;
    }

    /** Remember the name every part is looked up by, so limbs can be told apart in any model. */
    @Inject(method = "getChild", at = @At("RETURN"))
    private void corpsedecay$rememberName(String name, CallbackInfoReturnable<ModelPart> cir) {
        ModelPart part = cir.getReturnValue();
        if (part != null && ((NamedPart) (Object) part).corpsedecay$getName() == null) {
            ((NamedPart) (Object) part).corpsedecay$setName(name);
        }
    }

    /** While a corpse is drawn, limbs take the pose of its ragdoll instead of their animation. */
    @Inject(method = "translateAndRotate", at = @At("HEAD"))
    private void corpsedecay$pose(PoseStack poseStack, CallbackInfo ci) {
        RenderContext ctx = RenderContext.current;
        if (ctx != null) ctx.apply((ModelPart) (Object) this, corpsedecay$name);
    }
}
