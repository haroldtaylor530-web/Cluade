package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.RealisticSurvival;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/** Damage types defined in data/realisticsurvival/damage_type/*.json (they give the death messages). */
public final class ModDamage {
    public static final ResourceKey<DamageType> DEHYDRATION = key("dehydration");
    public static final ResourceKey<DamageType> HEATSTROKE = key("heatstroke");
    public static final ResourceKey<DamageType> HYPOTHERMIA = key("hypothermia");
    public static final ResourceKey<DamageType> BLEEDING = key("bleeding");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, RealisticSurvival.id(name));
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> key) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key));
    }

    private ModDamage() {}
}
