package com.harold.corpsedecay.block;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/** A flute carved from a bone. Play it: look up for high notes, down for low ones. */
public class BoneFluteItem extends Item {
    public BoneFluteItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // the note follows where you look: straight down is the lowest of two octaves, straight up the highest
        int note = Math.round((90F - player.getXRot()) / 180F * 24F);
        float pitch = (float) Math.pow(2.0, (note - 12) / 12.0);
        level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1F, pitch);
        level.gameEvent(player, GameEvent.INSTRUMENT_PLAY, player.position());
        player.getCooldowns().addCooldown(this, 4);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
