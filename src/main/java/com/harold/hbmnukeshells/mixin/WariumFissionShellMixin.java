package com.harold.hbmnukeshells.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.harold.hbmnukeshells.NukeShells;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * CBC: Warium Projectiles: Fission Bomb Shell. Only Warium's explosion call is swapped for the HBM bomb, so the shell's
 * advancement and Alex's Caves sirens still work.
 */
@Pseudo
@Mixin(targets = "net.ato.shupapium.blockentities.FissionBombShellProjectile", remap = false)
public abstract class WariumFissionShellMixin {
    @Redirect(method = "detonate(Lnet/minecraft/core/Position;)V", at = @At(value = "INVOKE",
            target = "Lnet/mcreator/crustychunks/procedures/FissionExplosionProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;DDD)V",
            remap = false), remap = false)
    private void hbmnukeshells$explode(LevelAccessor world, double x, double y, double z) {
        if (!NukeShells.detonate((Entity) (Object) this, new Vec3(x, y, z))) {
            NukeShells.callProcedure("net.mcreator.crustychunks.procedures.FissionExplosionProcedure", world, x, y, z);
        }
    }
}
