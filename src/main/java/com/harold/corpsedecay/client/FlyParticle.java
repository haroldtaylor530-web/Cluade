package com.harold.corpsedecay.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** A fly zig-zagging over a rotting body. */
public class FlyParticle extends TextureSheetParticle {
    private final double homeX, homeY, homeZ;

    protected FlyParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.lifetime = 50 + random.nextInt(60);
        this.quadSize = 0.035F + random.nextFloat() * 0.015F;
        this.gravity = 0F;
        this.hasPhysics = false;
        this.xd = (random.nextDouble() - 0.5) * 0.1;
        this.yd = (random.nextDouble() - 0.5) * 0.04;
        this.zd = (random.nextDouble() - 0.5) * 0.1;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        // darting about, always pulled back towards the body
        xd = xd * 0.8 + (random.nextDouble() - 0.5) * 0.06 + (homeX - x) * 0.04;
        yd = yd * 0.8 + (random.nextDouble() - 0.5) * 0.03 + (homeY - y) * 0.05;
        zd = zd * 0.8 + (random.nextDouble() - 0.5) * 0.06 + (homeZ - z) * 0.04;
        move(xd, yd, zd);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new FlyParticle(level, x, y, z, sprites);
        }
    }
}
