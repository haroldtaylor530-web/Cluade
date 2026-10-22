package com.harold.corpsedecay;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** How a mob died, which decides what its remains look like. */
public enum DeathCause {
    /** killed normally: rots as usual */
    NORMAL,
    /** burnt: charred, starts just short of a skeleton */
    BURNT,
    /** drowned: pale and waterlogged, floats */
    DROWNED,
    /** froze to death: frosted over, rots very slowly */
    FROZEN,
    /** blown up: torn apart, some limbs missing */
    EXPLODED,
    /** withered: a shrivelled grey-black husk */
    WITHERED,
    /** fell to its death: crumpled, limbs bent at broken angles */
    FELL,
    /** crushed by an anvil or falling block: flattened */
    CRUSHED;

    public static DeathCause byId(int id) {
        DeathCause[] v = values();
        return id >= 0 && id < v.length ? v[id] : NORMAL;
    }

    public static DeathCause of(LivingEntity mob, DamageSource source) {
        if (source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_STALACTITE)) {
            return CRUSHED;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return EXPLODED;
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_LIGHTNING) || mob.isOnFire()) return BURNT;
        if (source.is(DamageTypeTags.IS_DROWNING)) return DROWNED;
        if (source.is(DamageTypeTags.IS_FREEZING) || mob.isFullyFrozen()) return FROZEN;
        if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL) || mob.hasEffect(MobEffects.WITHER)) return WITHERED;
        if (source.is(DamageTypeTags.IS_FALL)) return FELL;
        return NORMAL;
    }
}
