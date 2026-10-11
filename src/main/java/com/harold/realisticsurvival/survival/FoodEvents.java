package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Keeps food freshness up to date and makes bad food make you ill. */
public final class FoodEvents {
    public static final int POISON_TICKS = 20 * 60;

    /** Your own inventory: every two seconds. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 40 != 0) return;
        if (!Config.SPOILAGE.getAsBoolean()) return;
        updateInventory(player);
    }

    public static void updateInventory(Player player) {
        long now = player.level().getGameTime();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            ItemStack next = Spoilage.update(stack, now);
            if (next != stack) inv.setItem(i, next);
        }
        ItemStack carried = player.containerMenu.getCarried();
        ItemStack next = Spoilage.update(carried, now);
        if (next != carried) player.containerMenu.setCarried(next);
    }

    /** Opening a chest: settle the time the food spent in there. */
    @SubscribeEvent
    public static void onOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.SPOILAGE.getAsBoolean()) return;
        long now = player.level().getGameTime();
        for (Container c : containers(event.getContainer())) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                ItemStack next = Spoilage.update(stack, now);
                if (next != stack) c.setItem(i, next);
            }
        }
        event.getContainer().broadcastChanges();
    }

    /** Closing a chest in a cold place: the food in it ages slowly until it's opened again. */
    @SubscribeEvent
    public static void onClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.SPOILAGE.getAsBoolean()) return;
        long now = player.level().getGameTime();
        for (Container c : containers(event.getContainer())) {
            BlockPos pos = containerPos(player, c);
            if (pos == null) continue;
            float rate = Spoilage.coldness(player.level(), pos);
            if (rate >= 1.0F) continue;
            boolean changed = false;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                if (!Spoilage.spoils(stack) || stack.has(ModRegistry.FOOD_COLD.get())) continue;
                ItemStack cold = stack.copy();
                Spoilage.markCold(cold, now, rate);
                c.setItem(i, cold);
                changed = true;
            }
            if (changed) c.setChanged();
        }
    }

    /** The containers in a menu other than the player's own inventory. */
    private static Set<Container> containers(AbstractContainerMenu menu) {
        Set<Container> out = new LinkedHashSet<>();
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory)) out.add(slot.container);
        }
        return out;
    }

    /** Where a container is: its block entity, or for a double chest the block you're looking at. */
    private static BlockPos containerPos(Player player, Container c) {
        if (c instanceof BlockEntity be) return be.getBlockPos();
        if (!(c instanceof net.minecraft.world.CompoundContainer)) return null;
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.blockInteractionRange() + 1.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : player.blockPosition();
    }

    /** Dropped food (mob drops, broken blocks) starts its clock when it appears. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ItemEntity item)) return;
        ItemStack stack = item.getItem();
        ItemStack next = Spoilage.update(stack, event.getLevel().getGameTime());
        if (next != stack) item.setItem(next);
    }

    // ---- eating ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack eaten = event.getItem();
        FoodProperties food = eaten.get(DataComponents.FOOD);
        if (food == null) return;
        afterEating(player, eaten, food, player.getRandom().nextFloat());
    }

    /** Applies the penalties for raw, stale or rotten food. {@code roll} is a random 0..1. */
    public static void afterEating(ServerPlayer player, ItemStack eaten, FoodProperties food, float roll) {
        long now = player.level().getGameTime();
        float chance = Config.RAW_FOOD_RISK.getAsBoolean() ? Spoilage.rawRisk(eaten) : 0.0F;
        if (Spoilage.smoked(eaten)) chance = 0.0F;
        else if (Spoilage.salted(eaten)) chance *= 0.4F;

        float keep = 1.0F;
        if (Spoilage.spoils(eaten)) {
            switch (Spoilage.stage(Spoilage.progress(eaten, now))) {
                case STALE -> keep = 0.75F;
                case GOING_OFF -> {
                    keep = 0.5F;
                    if (Config.RAW_FOOD_RISK.getAsBoolean()) chance = Math.max(chance, 0.35F);
                }
                case ROTTEN -> {
                    keep = 0.25F;
                    chance = 1.0F;
                }
                default -> {}
            }
        }
        if (keep < 1.0F) {
            FoodData data = player.getFoodData();
            int lose = Math.round(food.nutrition() * (1.0F - keep));
            data.setFoodLevel(Math.max(0, data.getFoodLevel() - lose));
            data.setSaturation(Math.max(0.0F, Math.min(data.getSaturationLevel() - food.saturation() * (1.0F - keep), data.getFoodLevel())));
        }
        if (chance > 0.0F && roll < chance) poison(player);
    }

    public static void poison(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(ModRegistry.FOOD_POISONING, POISON_TICKS, 0));
        player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 20 * 8, 0));
        player.sendOverlayMessage(Component.translatable("message.realisticsurvival.food_poisoning"));
    }

    private FoodEvents() {}
}
