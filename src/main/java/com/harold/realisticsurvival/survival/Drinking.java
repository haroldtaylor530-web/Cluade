package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared drinking rules. */
public final class Drinking {
    /** Chance that a drink of dirty water makes you ill. */
    public static final float SICK_CHANCE = 0.5F;

    public enum Source { NONE, DIRTY, CLEAN }

    /** Drink: add thirst and maybe get sick. Server side only. */
    public static void drink(Player player, float amount, boolean dirty) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (!Config.THIRST.getAsBoolean()) return;
        SurvivalState.get(sp).addThirst(amount);
        if (dirty && sp.getRandom().nextFloat() < SICK_CHANCE) {
            sp.addEffect(new MobEffectInstance(ModRegistry.DYSENTERY, 20 * 90, 0));
            sp.sendOverlayMessage(Component.translatable("message.realisticsurvival.sick"));
        }
    }

    /** What water the player is looking at, if any. */
    public static Source lookAtWater(Level level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.SOURCE_ONLY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            // nothing hit: catching rain from the open sky is clean
            return isCatchingRain(level, player) ? Source.CLEAN : Source.NONE;
        }
        BlockPos pos = hit.getBlockPos();
        if (!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER)) return Source.NONE;
        return isCleanWaterAt(level, pos) ? Source.CLEAN : Source.DIRTY;
    }

    /** Fast running river water and high mountain springs are safe; ponds, lakes, swamps and the sea are not. */
    public static boolean isCleanWaterAt(Level level, BlockPos pos) {
        if (level.getBiome(pos).is(BiomeTags.IS_RIVER)) return true;
        return pos.getY() > 110 && !level.getBiome(pos).is(BiomeTags.IS_OCEAN);
    }

    public static boolean isCatchingRain(Level level, Player player) {
        return player.getXRot() < -50.0F && level.isRainingAt(player.blockPosition().above());
    }

    public static void playDrinkSound(Player player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.6F, 1.0F);
    }

    public static void playFillSound(Player player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private Drinking() {}
}
