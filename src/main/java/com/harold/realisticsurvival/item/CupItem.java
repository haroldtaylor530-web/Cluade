package com.harold.realisticsurvival.item;

import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.survival.Drinking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fired clay cup. Fill it from water (river water and rain are clean, still water is dirty)
 * or from a cauldron of rainwater. Boil a dirty cup in a furnace or on a campfire.
 */
public class CupItem extends Item {
    public enum Kind { EMPTY, DIRTY, CLEAN }

    private final Kind kind;

    public CupItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (kind != Kind.EMPTY || ctx.getPlayer() == null) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.WATER_CAULDRON)) {
            if (!level.isClientSide()) {
                LayeredCauldronBlock.lowerFillLevel(state, level, pos);
                fill(ctx.getPlayer(), ctx.getHand(), true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (kind == Kind.EMPTY) {
            Drinking.Source src = Drinking.lookAtWater(level, player);
            if (src == Drinking.Source.NONE) return InteractionResult.PASS;
            if (!level.isClientSide()) fill(player, hand, src == Drinking.Source.CLEAN);
            return InteractionResult.SUCCESS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    private static void fill(Player player, InteractionHand hand, boolean clean) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack filled = new ItemStack(clean ? ModRegistry.CLEAN_WATER_CUP.get() : ModRegistry.DIRTY_WATER_CUP.get());
        player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, filled));
        Drinking.playFillSound(player);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (kind == Kind.EMPTY || !(entity instanceof Player player)) return stack;
        if (!level.isClientSide()) Drinking.drink(player, 6.0F, kind == Kind.DIRTY);
        if (player.getAbilities().instabuild) return stack;
        return new ItemStack(ModRegistry.CLAY_CUP.get());
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return kind == Kind.EMPTY ? 0 : 32;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return kind == Kind.EMPTY ? ItemUseAnimation.NONE : ItemUseAnimation.DRINK;
    }
}
