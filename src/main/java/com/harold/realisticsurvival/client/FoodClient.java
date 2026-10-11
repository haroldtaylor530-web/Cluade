package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.survival.Spoilage;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Freshness on food: a tooltip line and a thin bar under the item. */
public final class FoodClient {
    private static long now() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0L : level.getGameTime();
    }

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!Spoilage.spoils(stack)) return;
        long now = now();
        var lines = event.getToolTip();
        int at = Math.min(1, lines.size());
        if (!stack.has(ModRegistry.FOOD_START.get())) {
            lines.add(at++, Component.translatable("tooltip.realisticsurvival.keeps", time(Spoilage.lifetime(stack))).withStyle(ChatFormatting.GRAY));
        } else {
            float p = Spoilage.progress(stack, now);
            Spoilage.Stage stage = Spoilage.stage(p);
            ChatFormatting colour = switch (stage) {
                case FRESH -> ChatFormatting.GREEN;
                case STALE -> ChatFormatting.YELLOW;
                case GOING_OFF -> ChatFormatting.GOLD;
                case ROTTEN -> ChatFormatting.RED;
            };
            Component line = Component.translatable("tooltip.realisticsurvival.stage." + stage.name().toLowerCase(java.util.Locale.ROOT)).withStyle(colour);
            if (stage != Spoilage.Stage.ROTTEN) {
                line = line.copy().append(Component.translatable("tooltip.realisticsurvival.left", time(Spoilage.ticksLeft(stack, now))).withStyle(ChatFormatting.GRAY));
            }
            lines.add(at++, line);
            if (stack.has(ModRegistry.FOOD_COLD.get())) {
                lines.add(at++, Component.translatable("tooltip.realisticsurvival.cold").withStyle(ChatFormatting.AQUA));
            }
        }
        if (Spoilage.smoked(stack)) lines.add(at++, Component.translatable("tooltip.realisticsurvival.smoked").withStyle(ChatFormatting.GRAY));
        if (Spoilage.salted(stack)) lines.add(at, Component.translatable("tooltip.realisticsurvival.salted").withStyle(ChatFormatting.GRAY));
    }

    /** "about 3 days" / "about 5 hours" in game time. */
    private static Component time(long ticks) {
        double days = ticks / (double) Spoilage.DAY;
        if (days >= 1.5) return Component.translatable("tooltip.realisticsurvival.days", Math.round(days));
        long hours = Math.max(1, Math.round(ticks / 1000.0));
        return Component.translatable("tooltip.realisticsurvival.hours", hours);
    }

    private static final IItemDecorator BAR = (g, font, stack, x, y) -> {
        if (!stack.has(ModRegistry.FOOD_START.get()) || !Spoilage.spoils(stack)) return false;
        float fresh = Mth.clamp(1.0F - Spoilage.progress(stack, now()), 0.0F, 1.0F);
        int left = x + 2, top = y + 13;
        int width = Math.round(13.0F * fresh);
        int colour = 0xFF000000 | Mth.hsvToRgb(fresh / 3.0F, 1.0F, 1.0F);
        g.fill(RenderPipelines.GUI, left, top, left + 13, top + 2, 0xFF000000);
        g.fill(RenderPipelines.GUI, left, top, left + width, top + 1, colour);
        return true;
    };

    public static void registerDecorations(RegisterItemDecorationsEvent event) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item.components().has(DataComponents.FOOD)) event.register(item, BAR);
        }
    }

    private FoodClient() {}
}
