package com.harold.corpsedecay;

import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.harold.corpsedecay.world.FossilBedFeature;
import com.harold.corpsedecay.world.RemainsFeature;

public final class ModRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, CorpseDecay.MODID);
    private static final DeferredRegister<net.minecraft.world.level.levelgen.feature.Feature<?>> FEATURES =
            DeferredRegister.create(ForgeRegistries.FEATURES, CorpseDecay.MODID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, CorpseDecay.MODID);

    public static final RegistryObject<EntityType<CorpseEntity>> CORPSE = ENTITIES.register("corpse",
            () -> EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("corpse"));

    static {
        for (RemainsFeature.Where w : RemainsFeature.Where.values()) {
            FEATURES.register(w.name().toLowerCase(java.util.Locale.ROOT) + "_remains", () -> new RemainsFeature(w));
        }
    }
    public static final RegistryObject<FossilBedFeature> FOSSIL_BED = FEATURES.register("fossil_bed", FossilBedFeature::new);

    public static final RegistryObject<SimpleParticleType> FLY = PARTICLES.register("fly", () -> new SimpleParticleType(false));

    private ModRegistry() {
    }

    static void register(IEventBus bus) {
        ENTITIES.register(bus);
        PARTICLES.register(bus);
        FEATURES.register(bus);
        com.harold.corpsedecay.block.FossilBlocks.register(bus);
    }
}
