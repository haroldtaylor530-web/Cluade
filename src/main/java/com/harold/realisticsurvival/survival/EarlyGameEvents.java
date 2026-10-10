package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * A harder, more grounded start:
 * - you can't get logs by punching; you need an axe (a flint hatchet to start),
 * - punching wood or stone bare-handed hurts,
 * - knap flint on stone into sharp shards,
 * - pull plant fibre from grass (much better with a knife) and sticks from leaves,
 * - mining and chopping is hard work: hungrier and thirstier.
 */
public final class EarlyGameEvents {

    private static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    private static boolean hasAxe(Player player) {
        return player.getMainHandItem().is(ItemTags.AXES);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (!Config.EARLY_GAME.getAsBoolean()) return;
        Player player = event.getEntity();
        BlockState state = event.getState();
        if (isLog(state) && !hasAxe(player)) {
            event.setNewSpeed(event.getNewSpeed() * 0.25F);
        }
        // bare fists on wood or stone: it hurts
        if (player.getMainHandItem().isEmpty() && !player.isCreative()
                && (isLog(state) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.PLANKS))
                && player instanceof ServerPlayer sp && sp.tickCount % 40 == 0 && sp.getRandom().nextFloat() < 0.35F) {
            sp.hurtServer((ServerLevel) sp.level(), sp.damageSources().generic(), 1.0F);
            sp.sendOverlayMessage(Component.translatable("message.realisticsurvival.sore_hands"));
        }
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (Config.EARLY_GAME.getAsBoolean() && isLog(event.getTargetBlock()) && !hasAxe(event.getEntity())) {
            event.setCanHarvest(false);
        }
    }

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isCreative()) return;
        BlockState state = event.getState();
        if (Config.EARLY_GAME.getAsBoolean() && isLog(state) && !hasAxe(player)) {
            player.sendOverlayMessage(Component.translatable("message.realisticsurvival.need_axe"));
        }
        if (Config.WORK_TIRES.getAsBoolean() && state.getDestroySpeed(event.getLevel(), event.getPos()) >= 1.0F) {
            player.causeFoodExhaustion(0.03F);
            if (Config.THIRST.getAsBoolean()) SurvivalState.get(player).addThirst(-0.04F);
        }
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!Config.EARLY_GAME.getAsBoolean()) return;
        if (!(event.getBreaker() instanceof Player player)) return;
        ServerLevel level = event.getLevel();
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        ItemStack tool = event.getTool();
        float r = level.getRandom().nextFloat();

        if (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)) {
            float chance = tool.is(ModRegistry.FLINT_KNIFE.get()) ? 0.65F : 0.2F;
            if (r < chance) event.getDrops().add(drop(level, pos, new ItemStack(ModRegistry.PLANT_FIBRE.get())));
        } else if (state.is(BlockTags.LEAVES) && !tool.is(Items.SHEARS)) {
            if (r < 0.15F) event.getDrops().add(drop(level, pos, new ItemStack(Items.STICK)));
        }
    }

    private static ItemEntity drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        return new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
    }

    /** Knapping: right-click stone holding flint to split it into sharp shards. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!Config.EARLY_GAME.getAsBoolean()) return;
        ItemStack held = event.getItemStack();
        if (!held.is(Items.FLINT)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(Blocks.COBBLESTONE)) return;

        Player player = event.getEntity();
        if (!event.getLevel().isClientSide()) {
            if (!player.isCreative()) held.shrink(1);
            int shards = player.getRandom().nextFloat() < 0.6F ? 2 : 1;
            ItemStack out = new ItemStack(ModRegistry.FLINT_SHARD.get(), shards);
            player.getInventory().placeItemBackInInventory(out, net.minecraft.util.Prediction.SERVER_ONLY);
            event.getLevel().playSound(null, event.getPos(), SoundEvents.STONE_HIT, SoundSource.PLAYERS, 1.0F, 1.4F);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Furry animals drop pelts for warm clothes. */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        Entity e = event.getEntity();
        EntityType<?> type = e.getType();
        int count = 0;
        if (type == EntityTypes.WOLF || type == EntityTypes.FOX) count = 1;
        else if (type == EntityTypes.POLAR_BEAR) count = 2 + e.getRandom().nextInt(2);
        else if (type == EntityTypes.LLAMA || type == EntityTypes.TRADER_LLAMA || type == EntityTypes.CAMEL) count = 1 + e.getRandom().nextInt(2);
        else if (type == EntityTypes.RABBIT && e.getRandom().nextFloat() < 0.3F) count = 1;
        if (count > 0 && e.level() instanceof ServerLevel level) {
            event.getDrops().add(new ItemEntity(level, e.getX(), e.getY() + 0.3, e.getZ(), new ItemStack(ModRegistry.FUR_PELT.get(), count)));
        }
    }

    private EarlyGameEvents() {}
}
