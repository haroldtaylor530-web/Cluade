package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.RealisticSurvival;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Drinks from food and vanilla items, and drinking straight from water with your hands. */
public final class ThirstEvents {

    @SubscribeEvent
    public static void onFinishUsing(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItem();
        if (stack.is(Items.POTION)) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            boolean water = contents != null && contents.is(Potions.WATER);
            // a water bottle is unboiled water: boil it in a clay cup instead
            Drinking.drink(player, water ? 6.0F : 3.0F, water);
            return;
        }
        float amount = 0.0F;
        if (stack.is(Items.MILK_BUCKET)) amount = 5.0F;
        else if (stack.is(Items.MUSHROOM_STEW) || stack.is(Items.BEETROOT_SOUP) || stack.is(Items.RABBIT_STEW) || stack.is(Items.SUSPICIOUS_STEW)) amount = 4.0F;
        else if (stack.is(Items.MELON_SLICE)) amount = 2.0F;
        else if (stack.is(Items.HONEY_BOTTLE)) amount = 2.0F;
        else if (stack.is(Items.APPLE) || stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.CARROT) || stack.is(Items.BEETROOT)) amount = 1.0F;
        else if (stack.is(Items.DRIED_KELP) || stack.is(Items.COOKIE) || stack.is(Items.BREAD)) amount = -0.5F;
        if (amount != 0.0F) Drinking.drink(player, amount, false);
    }

    // ---- drinking with your hands: sneak + right-click water with an empty hand -------------

    public record DrinkFromHand() implements CustomPacketPayload {
        public static final Type<DrinkFromHand> TYPE = new Type<>(RealisticSurvival.id("drink_from_hand"));
        public static final StreamCodec<ByteBuf, DrinkFromHand> CODEC = StreamCodec.unit(new DrinkFromHand());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(DrinkFromHand.TYPE, DrinkFromHand.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) drinkFromHand(player);
        });
    }

    private static void drinkFromHand(ServerPlayer player) {
        if (!Config.THIRST.getAsBoolean() || !player.getMainHandItem().isEmpty()) return;
        SurvivalState s = SurvivalState.get(player);
        if (s.thirst >= SurvivalState.MAX_THIRST) return;
        Drinking.Source src = Drinking.lookAtWater(player.level(), player);
        if (src == Drinking.Source.NONE) return;
        // a couple of handfuls: small amount per drink
        Drinking.drink(player, 1.5F, src == Drinking.Source.DIRTY);
        Drinking.playDrinkSound(player);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    /** Client asks to drink; checked again on the server. */
    public static boolean canTryHandDrink(Player player) {
        return player.isShiftKeyDown() && player.getMainHandItem().isEmpty();
    }

    private ThirstEvents() {}
}
