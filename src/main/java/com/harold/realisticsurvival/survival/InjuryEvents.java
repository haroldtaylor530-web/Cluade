package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Broken legs from hard falls; bleeding from cuts, bites and arrows. */
public final class InjuryEvents {
    public static final int BROKEN_LEG_TICKS = 20 * 60 * 10;

    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!Config.INJURIES.getAsBoolean()) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative()) return;
        float dmg = event.getHealthDamage();
        if (dmg <= 0.0F || !player.isAlive()) return;
        DamageSource src = event.getSource();

        if (src.is(DamageTypeTags.IS_FALL)) {
            // about 7+ blocks: from 2 hearts of fall damage the chance climbs 12% per half heart
            float chance = (dmg - 3.0F) * 0.12F;
            if (chance > 0 && player.getRandom().nextFloat() < chance && !player.hasEffect(ModRegistry.BROKEN_LEG)) {
                player.addEffect(new MobEffectInstance(ModRegistry.BROKEN_LEG, BROKEN_LEG_TICKS, 0));
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.0F, 0.6F);
                player.displayClientMessage(Component.translatable("message.realisticsurvival.broken_leg"), true);
            }
            return;
        }

        if (isWound(src)) {
            float chance = 0.12F + dmg * 0.04F;
            if (player.getRandom().nextFloat() < chance) {
                int amp = dmg >= 8.0F ? 1 : 0;
                MobEffectInstance current = player.getEffect(ModRegistry.BLEEDING);
                if (current != null && current.getAmplifier() > amp) amp = current.getAmplifier();
                player.addEffect(new MobEffectInstance(ModRegistry.BLEEDING, 20 * 60, amp));
                player.displayClientMessage(Component.translatable("message.realisticsurvival.bleeding"), true);
            }
        }
    }

    /** Cuts, bites, stabs and arrows; not fire, falls, explosions, magic, drowning... */
    public static boolean isWound(DamageSource src) {
        if (src.is(DamageTypeTags.IS_FIRE) || src.is(DamageTypeTags.IS_EXPLOSION) || src.is(DamageTypeTags.IS_FALL)
                || src.is(DamageTypeTags.IS_DROWNING) || src.is(DamageTypeTags.IS_FREEZING) || src.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            return false;
        }
        return src.getEntity() instanceof LivingEntity || src.getDirectEntity() != null && src.is(DamageTypeTags.IS_PROJECTILE);
    }

    private InjuryEvents() {}
}
