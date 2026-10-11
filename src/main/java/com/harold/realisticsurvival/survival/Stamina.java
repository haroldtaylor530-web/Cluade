package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;

/**
 * Stamina: sprinting and swimming wear you out, resting gets it back. Out of breath (0) you can't
 * sprint and dig slower until it's back above 6. Hunger and thirst slow the recovery.
 */
public final class Stamina {
    public static final float RECOVER_AT = 6.0F;

    /** Called every tick for survival players. */
    public static void tick(ServerPlayer player, SurvivalState s) {
        double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
        boolean moving = dx * dx + dz * dz > 1.0E-4;
        float change;
        if (player.isSprinting()) change = -0.045F;
        else if (player.isSwimming()) change = -0.03F;
        else {
            change = moving ? 0.02F : 0.05F;
            if (player.getFoodData().getFoodLevel() <= 6) change *= 0.5F;
            if (Config.THIRST.getAsBoolean() && s.thirst < 6.0F) change *= 0.5F;
        }
        apply(player, s, change);
        if (s.winded && player.isSprinting()) player.setSprinting(false);
    }

    /** Hard work: spend some stamina (never wakes anyone up, never kills anything). */
    public static void spend(Player player, float amount) {
        if (!Config.STAMINA.getAsBoolean() || player.isCreative() || player.isSpectator()) return;
        if (player instanceof ServerPlayer sp) apply(sp, SurvivalState.get(sp), -amount);
    }

    private static void apply(ServerPlayer player, SurvivalState s, float change) {
        boolean was = s.winded;
        s.stamina = Mth.clamp(s.stamina + change, 0.0F, SurvivalState.MAX_STAMINA);
        if (s.stamina <= 0.0F) s.winded = true;
        else if (s.stamina >= RECOVER_AT) s.winded = false;
        if (s.winded && !was) player.sendOverlayMessage(Component.translatable("message.realisticsurvival.winded"));
    }

    private Stamina() {}
}
