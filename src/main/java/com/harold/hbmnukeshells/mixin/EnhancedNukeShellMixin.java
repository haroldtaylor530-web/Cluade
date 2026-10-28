package com.harold.hbmnukeshells.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.harold.hbmnukeshells.NukeShells;

import net.minecraft.core.Position;
import net.minecraft.world.entity.Entity;

/** CBC Enhanced Shells: Nuke Shell and Nuke Shell Mk2 (Mk2 inherits this detonate). Goes off as an HBM bomb instead of its own blast. */
@Pseudo
@Mixin(targets = "com.example.cbcenhancedshells.shells.NukeShellProjectile", remap = false)
public abstract class EnhancedNukeShellMixin {
    @Inject(method = "detonate(Lnet/minecraft/core/Position;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void hbmnukeshells$detonate(Position pos, CallbackInfo ci) {
        if (NukeShells.detonate((Entity) (Object) this, pos)) ci.cancel();
    }
}
