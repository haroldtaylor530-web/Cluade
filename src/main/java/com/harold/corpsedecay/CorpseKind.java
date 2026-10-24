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
    /** Old bones of something that died long ago, found lying about the world. */
    REMAINS;

    public static final TagKey<EntityType<?>> NO_CORPSE = tag("no_corpse");
    public static final TagKey<EntityType<?>> ALREADY_BONES = tag("already_bones");
    public static final TagKey<EntityType<?>> NO_SKELETON = tag("no_skeleton");
    public static final TagKey<EntityType<?>> DOES_NOT_ROT = tag("does_not_rot");
    /** soft-bodied things that rot away to a hard beak and a pen (squid) rather than nothing */
    public static final TagKey<EntityType<?>> BEAK_AND_PEN = tag("beak_and_pen");
    /** mobs whose old remains can be found lying about the overworld, the nether and the end */
    public static final TagKey<EntityType<?>> REMAINS_MOBS = tag("remains_mobs");
    public static final TagKey<EntityType<?>> NETHER_REMAINS_MOBS = tag("nether_remains_mobs");
    public static final TagKey<EntityType<?>> END_REMAINS_MOBS = tag("end_remains_mobs");

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(CorpseDecay.MODID, name));
    }

    public static CorpseKind of(EntityType<?> type) {
        if (type.is(ALREADY_BONES)) return BONES;
        if (type.is(DOES_NOT_ROT)) return NO_DECAY;
        if (type.is(NO_SKELETON)) return NO_BONES;
        return FLESH;
    }

    /** Whether the mob with this registry name leaves a beak and a pen behind. */
    public static boolean leavesBeakAndPen(String mobType) {
        EntityType<?> type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(mobType));
        return type != null && type.is(BEAK_AND_PEN);
    }

    public static CorpseKind byId(int id) {
        CorpseKind[] v = values();
        return id >= 0 && id < v.length ? v[id] : FLESH;
    }
}
