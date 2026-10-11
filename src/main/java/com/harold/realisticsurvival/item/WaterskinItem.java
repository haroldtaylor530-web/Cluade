package com.harold.realisticsurvival.item;

import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.survival.Drinking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Holds four drinks. Fill it by right-clicking water (or the rain, looking up). Water from
 * ponds, lakes and swamps stays dirty, and mixing dirty into clean makes it all dirty.
 */
public class WaterskinItem extends Item {
    public static final int CAPACITY = 4;

    public WaterskinItem(Properties properties) {
        super(properties);
    }

    public static int amount(ItemStack stack) {
        Integer a = stack.get(ModRegistry.WATER_AMOUNT.get());
        return a == null ? 0 : a;
    }

    public static boolean dirty(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModRegistry.WATER_DIRTY.get()));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int amount = amount(stack);
        Drinking.Source src = amount < CAPACITY ? Drinking.lookAtWater(level, player) : Drinking.Source.NONE;
        if (src == Drinking.Source.SALT) {
            if (!level.isClientSide()) player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.realisticsurvival.no_sea_water"));
            return InteractionResult.FAIL;
        }
        if (src != Drinking.Source.NONE) {
            if (!level.isClientSide()) {
                boolean nowDirty = src == Drinking.Source.DIRTY || (amount > 0 && dirty(stack));
                stack.set(ModRegistry.WATER_AMOUNT.get(), CAPACITY);
                stack.set(ModRegistry.WATER_DIRTY.get(), nowDirty);
                Drinking.playFillSound(player);
            }
            return InteractionResult.SUCCESS;
        }
        if (amount <= 0) return InteractionResult.PASS;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        int amount = amount(stack);
        if (amount <= 0 || !(entity instanceof Player player)) return stack;
        if (!level.isClientSide()) {
            Drinking.drink(player, 5.0F, dirty(stack));
            if (!player.getAbilities().instabuild) {
                stack.set(ModRegistry.WATER_AMOUNT.get(), amount - 1);
                if (amount - 1 == 0) stack.set(ModRegistry.WATER_DIRTY.get(), false);
            }
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * amount(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return dirty(stack) ? 0x7a8a3a : 0x3f76e4;
    }

    @Override
    public Component getName(ItemStack stack) {
        int amount = amount(stack);
        if (amount <= 0) return Component.translatable("item.realisticsurvival.waterskin.empty");
        return Component.translatable(dirty(stack) ? "item.realisticsurvival.waterskin.dirty" : "item.realisticsurvival.waterskin.clean", amount, CAPACITY);
    }
}
