package com.harold.realisticsurvival.effect;

import com.harold.realisticsurvival.survival.SurvivalState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** From drinking dirty water: you lose water and food quickly. */
public class DysenteryEffect extends MobEffect {
    public DysenteryEffect() {
        super(MobEffectCategory.HARMFUL, 0x6b7a2a);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 40 == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            SurvivalState.get(player).addThirst(-0.7F * (amplifier + 1));
            player.causeFoodExhaustion(1.2F * (amplifier + 1));
        }
        return true;
    }
}
