package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.survival.ToolWear;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** A tooltip line for how keen a tool's edge is. */
public final class ToolClient {
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!ToolWear.dulls(stack)) return;
        int sharp = ToolWear.sharpness(stack);
        if (sharp >= ToolWear.MAX) return;
        String word = sharp > 70 ? "keen" : sharp > 40 ? "worn" : sharp > 15 ? "dull" : "blunt";
        ChatFormatting colour = sharp > 70 ? ChatFormatting.GREEN : sharp > 40 ? ChatFormatting.YELLOW : sharp > 15 ? ChatFormatting.GOLD : ChatFormatting.RED;
        var lines = event.getToolTip();
        lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.realisticsurvival.edge." + word, sharp).withStyle(colour));
    }

    private ToolClient() {}
}
