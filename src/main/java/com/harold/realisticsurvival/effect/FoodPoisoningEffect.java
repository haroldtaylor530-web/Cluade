package com.harold.realisticsurvival.effect;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.survival.SurvivalState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** From raw or rotten food: you can't keep food down and lose some water too. */
public class FoodPoisoningEffect extends MobEffect {
    public FoodPoisoningEffect() {
        super(MobEffectCategory.HARMFUL, 0x8a9a3c);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 40 == 0;
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof Player player) {
            player.causeFoodExhaustion(2.5F * (amplifier + 1));
            if (Config.THIRST.getAsBoolean()) SurvivalState.get(player).addThirst(-0.4F * (amplifier + 1));
        }
        return true;
    }
}
