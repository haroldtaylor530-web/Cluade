package com.harold.realisticsurvival.item;

import com.harold.realisticsurvival.survival.ToolWear;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Hold it in one hand and the tool in the other, then right-click to sharpen the tool. */
public class WhetstoneItem extends Item {
    public static final int RESTORES = 35;

    public WhetstoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack tool = player.getItemInHand(other);
        ItemStack stone = player.getItemInHand(hand);
        if (!ToolWear.dulls(tool)) return InteractionResult.PASS;
        if (ToolWear.sharpness(tool) >= ToolWear.MAX) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.realisticsurvival.already_sharp", tool.getHoverName()));
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            ToolWear.setSharpness(tool, ToolWear.sharpness(tool) + RESTORES);
            level.playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.7F, 1.4F);
            stone.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            player.getCooldowns().addCooldown(stone, 12);
        }
        return InteractionResult.SUCCESS;
    }
}
