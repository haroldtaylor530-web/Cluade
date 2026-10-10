package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.ModRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Per-player survival values, saved with the player. Reset on death. */
public class SurvivalState {
    public static final float MAX_THIRST = 20.0F;
    public static final float NORMAL_TEMP = 37.0F;

    public static final Codec<SurvivalState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("thirst").forGetter(s -> s.thirst),
            Codec.FLOAT.fieldOf("bodyTemp").forGetter(s -> s.bodyTemp)
    ).apply(i, SurvivalState::new));

    public float thirst = MAX_THIRST;
    public float bodyTemp = NORMAL_TEMP;
    /** Last "feels like" air temperature, for messages and debugging. */
    public float ambient = 20.0F;
    public int warnCooldown;

    public SurvivalState() {}

    private SurvivalState(float thirst, float bodyTemp) {
        this.thirst = thirst;
        this.bodyTemp = bodyTemp;
    }

    public void addThirst(float amount) {
        thirst = Mth.clamp(thirst + amount, 0.0F, MAX_THIRST);
    }

    public static SurvivalState get(Player player) {
        return player.getData(ModRegistry.STATE);
    }
}
