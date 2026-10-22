package com.harold.corpsedecay;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/** How a mob's body breaks down. */
public enum CorpseKind {
    /** Flesh rots away down to a skeleton. */
    FLESH,
    /** Already bones (skeletons, strays): nothing left to rot, the bones just lie there. */
    BONES,
    /** No skeleton (spiders, squid, blazes...): rots to a dry husk and fades away. */
    NO_BONES,
    /** Doesn't rot at all (golems, shulkers): stays as it fell until it crumbles. */
    NO_DECAY,
    /** Bones turned to stone long ago, found in the world. */
    FOSSIL;

    public static final TagKey<EntityType<?>> NO_CORPSE = tag("no_corpse");
    public static final TagKey<EntityType<?>> ALREADY_BONES = tag("already_bones");
    public static final TagKey<EntityType<?>> NO_SKELETON = tag("no_skeleton");
    public static final TagKey<EntityType<?>> DOES_NOT_ROT = tag("does_not_rot");
    /** mobs whose fossils can be found in the world */
    public static final TagKey<EntityType<?>> FOSSIL_MOBS = tag("fossil_mobs");

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(CorpseDecay.MODID, name));
    }

    public static CorpseKind of(EntityType<?> type) {
        if (type.is(ALREADY_BONES)) return BONES;
        if (type.is(DOES_NOT_ROT)) return NO_DECAY;
        if (type.is(NO_SKELETON)) return NO_BONES;
        return FLESH;
    }

    public static CorpseKind byId(int id) {
        CorpseKind[] v = values();
        return id >= 0 && id < v.length ? v[id] : FLESH;
    }
}
