package com.harold.realisticsurvival.item;

import com.harold.realisticsurvival.ModRegistry;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** Sets a broken leg: you limp a little for three minutes instead of a lot for ten. */
public class SplintItem extends MedicalItem {
    public SplintItem(Properties properties) {
        super(properties, 60);
    }

    @Override
    protected boolean canTreat(LivingEntity entity) {
        return entity.hasEffect(ModRegistry.BROKEN_LEG);
    }

    @Override
    protected void treat(Level level, LivingEntity entity) {
        entity.removeEffect(ModRegistry.BROKEN_LEG);
        entity.addEffect(new MobEffectInstance(ModRegistry.SPLINTED, 20 * 180, 0));
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
    }
}
