package com.harold.realisticsurvival.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** Hold right-click to apply; only usable when it would help. */
public abstract class MedicalItem extends Item {
    private final int useTicks;

    protected MedicalItem(Properties properties, int useTicks) {
        super(properties);
        this.useTicks = useTicks;
    }

    protected abstract boolean canTreat(LivingEntity entity);

    protected abstract void treat(Level level, LivingEntity entity);

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!canTreat(player)) return InteractionResult.PASS;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (canTreat(entity)) {
            if (!level.isClientSide()) treat(level, entity);
            if (!(entity instanceof Player p && p.getAbilities().instabuild)) stack.shrink(1);
        }
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return useTicks;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BRUSH;
    }
}
