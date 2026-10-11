package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/** Tools go dull: their edge wears with use, they dig slower, and a whetstone brings it back. */
public final class ToolWear {
    public static final int MAX = 100;
    /** Digging speed of a tool with no edge left, as a share of a sharp one. */
    public static final float BLUNT_SPEED = 0.55F;

    public static boolean dulls(ItemStack stack) {
        return stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES);
    }

    public static int sharpness(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.SHARPNESS.get(), MAX);
    }

    public static void setSharpness(ItemStack stack, int value) {
        if (value >= MAX) stack.remove(ModRegistry.SHARPNESS.get());
        else stack.set(ModRegistry.SHARPNESS.get(), Math.max(0, value));
    }

    /** Digging speed multiplier from the edge. */
    public static float factor(ItemStack stack) {
        if (!Config.TOOL_DULLING.getAsBoolean() || !dulls(stack)) return 1.0F;
        return BLUNT_SPEED + (1.0F - BLUNT_SPEED) * sharpness(stack) / (float) MAX;
    }

    /** How likely a block wears the edge: flint and wood dull fastest, diamond slowest. */
    public static float wearChance(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.startsWith("diamond") || path.startsWith("netherite")) return 0.35F;
        if (path.startsWith("iron") || path.startsWith("copper")) return 0.6F;
        return 1.0F;
    }

    /** Wear the edge by up to {@code amount} points. Returns true if it got duller. */
    public static boolean wear(Player player, ItemStack tool, int amount) {
        if (!Config.TOOL_DULLING.getAsBoolean() || player.isCreative() || !dulls(tool)) return false;
        int lost = 0;
        for (int i = 0; i < amount; i++) if (player.getRandom().nextFloat() < wearChance(tool)) lost++;
        if (lost == 0) return false;
        int before = sharpness(tool);
        setSharpness(tool, before - lost);
        if (before > 25 && sharpness(tool) <= 25 && player instanceof ServerPlayer sp) {
            sp.sendOverlayMessage(Component.translatable("message.realisticsurvival.getting_dull", tool.getHoverName()));
        }
        return true;
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        float speed = event.getNewSpeed() * factor(player.getMainHandItem());
        if (Config.STAMINA.getAsBoolean() && !player.isCreative() && SurvivalState.get(player).winded) speed *= 0.7F;
        event.setNewSpeed(speed);
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isCreative()) return;
        if (event.getState().getDestroySpeed(event.getLevel(), event.getPos()) < 0.5F) return; // plants and the like
        wear(player, player.getMainHandItem(), 1);
        Stamina.spend(player, 0.25F);
    }

    private ToolWear() {}
}
