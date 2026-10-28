package com.harold.hbmnukeshells.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.harold.hbmnukeshells.NukeShells;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;

/**
 * CBC: Warium Projectiles: Fusion Bomb Shell. Warium only lets a fusion warhead go nuclear when it has been armed
 * (flown long enough) or the easyNukes game rule is on; otherwise it is a dud. This keeps that rule and swaps only the
 * nuclear blast for the HBM bomb, so the advancement still triggers.
 */
@Pseudo
@Mixin(targets = "net.ato.shupapium.blockentities.FusionBombShellProjectile", remap = false)
public abstract class WariumFusionShellMixin {
    @Redirect(method = "detonate(Lnet/minecraft/core/Position;)V", at = @At(value = "INVOKE",
            target = "Lnet/mcreator/crustychunks/procedures/FusionBombHitProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",
            remap = false), remap = false)
    private void hbmnukeshells$hit(LevelAccessor world, Entity shell) {
        if (NukeShells.wariumFusionArmed(world, shell) && NukeShells.detonate(shell, shell.position())) {
            shell.discard();
            return;
        }
        NukeShells.callHit("net.mcreator.crustychunks.procedures.FusionBombHitProcedure", world, shell);
    }
}
