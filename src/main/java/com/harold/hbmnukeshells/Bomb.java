package com.harold.hbmnukeshells;

import java.util.UUID;

import com.hbm.config.BombConfig;
import com.hbm.entity.effect.EntityNukeTorex;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.explosion.ExplosionNukeSmall;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** The HBM bombs a shell can go off as. Each one does exactly what HBM's own bomb block does when it detonates. */
public enum Bomb {
    /** Leave the shell's own explosion alone. */
    NATIVE("the shell's own explosion"),
    /** HBM "Little Boy": gun-type uranium fission bomb. */
    LITTLE_BOY("Little Boy"),
    /** HBM "The Gadget": the first plutonium implosion test device. */
    GADGET("The Gadget"),
    /** HBM "Fat Man": plutonium implosion fission bomb. */
    FAT_MAN("Fat Man"),
    /** HBM "Ivy Mike": the hydrogen (thermonuclear) bomb. */
    IVY_MIKE("Ivy Mike"),
    /** HBM "Tsar Bomba": the biggest hydrogen bomb. */
    TSAR_BOMBA("Tsar Bomba"),
    /** HBM "N² Mine": huge blast, no radiation. */
    N2("N² Mine"),
    /** HBM mini nuke (Fat Man launcher round), low yield. */
    MINI_NUKE_LOW("mini nuke (low yield)"),
    /** HBM mini nuke, normal yield. */
    MINI_NUKE("mini nuke"),
    /** HBM mini nuke, high yield. */
    MINI_NUKE_HIGH("mini nuke (high yield)");

    public final String title;

    Bomb(String title) {
        this.title = title;
    }

    /** Sets the bomb off at (x, y, z). Server side only. */
    public void detonate(ServerLevel level, double x, double y, double z, UUID detonator) {
        switch (this) {
            case LITTLE_BOY -> big(level, x, y, z, BombConfig.boyRadius, false, detonator, true);
            case GADGET -> big(level, x, y, z, BombConfig.gadgetRadius, false, detonator, false);
            case FAT_MAN -> big(level, x, y, z, BombConfig.manRadius, false, detonator, false);
            case IVY_MIKE -> big(level, x, y, z, BombConfig.mikeRadius, false, detonator, false);
            case TSAR_BOMBA -> big(level, x, y, z, BombConfig.tsarRadius, false, detonator, false);
            case N2 -> big(level, x, y, z, BombConfig.n2Radius, true, detonator, false);
            case MINI_NUKE_LOW -> ExplosionNukeSmall.explode(level, x, y, z, ExplosionNukeSmall.PARAMS_LOW);
            case MINI_NUKE -> ExplosionNukeSmall.explode(level, x, y, z, ExplosionNukeSmall.PARAMS_MEDIUM);
            case MINI_NUKE_HIGH -> ExplosionNukeSmall.explode(level, x, y, z, ExplosionNukeSmall.PARAMS_HIGH);
            case NATIVE -> {
            }
        }
    }

    public int radius() {
        return switch (this) {
            case LITTLE_BOY -> BombConfig.boyRadius;
            case GADGET -> BombConfig.gadgetRadius;
            case FAT_MAN -> BombConfig.manRadius;
            case IVY_MIKE -> BombConfig.mikeRadius;
            case TSAR_BOMBA -> BombConfig.tsarRadius;
            case N2 -> BombConfig.n2Radius;
            default -> 0;
        };
    }

    /** As HBM's NukeBombBlock.ignite: the MK5 blast, then the mushroom cloud. */
    private static void big(ServerLevel level, double x, double y, double z, int radius, boolean noRadiation,
            UUID detonator, boolean littleBoyCloud) {
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.0F,
                level.random.nextFloat() * 0.1F + 0.9F);
        EntityNukeExplosionMK5 blast = noRadiation
                ? EntityNukeExplosionMK5.statFacNoRad(level, radius, x, y, z)
                : EntityNukeExplosionMK5.statFac(level, radius, x, y, z);
        blast.detonator = detonator;
        if (level.addFreshEntity(blast)) blast.startChunkLoading();
        if (BombConfig.enableNukeClouds) {
            EntityNukeTorex cloud = littleBoyCloud
                    ? EntityNukeTorex.statFacExact(level, x, y, z, 1.5F, radius)
                    : EntityNukeTorex.statFac(level, x, y, z, radius);
            if (level.addFreshEntity(cloud)) cloud.startChunkLoading();
        }
    }
}
