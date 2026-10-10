package com.harold.realisticsurvival.item;

import com.harold.realisticsurvival.ModRegistry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Stops bleeding and helps the wound heal. */
public class BandageItem extends MedicalItem {
    public BandageItem(Properties properties) {
        super(properties, 40);
    }

    @Override
    protected boolean canTreat(LivingEntity entity) {
        return entity.hasEffect(ModRegistry.BLEEDING) || entity.getHealth() < entity.getMaxHealth();
    }

    @Override
    protected void treat(Level level, LivingEntity entity) {
        entity.removeEffect(ModRegistry.BLEEDING);
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }
}
