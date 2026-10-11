package com.harold.realisticsurvival.block;

import com.harold.realisticsurvival.ModRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The brick oven's workbench: a 3x3 grid like a crafting table, but it makes the oven recipes
 * (furnaces, metal tools and armour, metal goods). It closes if the oven breaks or goes cold.
 */
public class OvenMenu extends AbstractContainerMenu {
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    public final BlockPos pos;
    private int validCheck;
    private boolean valid = true;

    public OvenMenu(int id, Inventory inv, ContainerLevelAccess access, BlockPos pos) {
        super(ModRegistry.OVEN_MENU.get(), id);
        this.access = access;
        this.player = inv.player;
        this.pos = pos;
        addSlot(new OvenResultSlot(player, craftSlots, resultSlots, 0, 124, 35));
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) addSlot(new Slot(craftSlots, x + y * 3, 30 + x * 18, 17 + y * 18));
        addStandardInventorySlots(inv, 8, 84);
    }

    public static OvenMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new OvenMenu(id, inv, ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public static Optional<RecipeHolder<CraftingRecipe>> recipeFor(ServerLevel level, CraftingInput input) {
        return level.getServer().getRecipeManager().getRecipeFor(ModRegistry.OVEN_RECIPE.get(), input, level);
    }

    @Override
    public void slotsChanged(Container container) {
        access.execute((level, p) -> {
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) return;
            CraftingInput input = craftSlots.asCraftInput();
            ItemStack result = ItemStack.EMPTY;
            Optional<RecipeHolder<CraftingRecipe>> recipe = recipeFor(serverLevel, input);
            if (recipe.isPresent() && resultSlots.setRecipeUsed(sp, recipe.get())) {
                ItemStack made = recipe.get().value().assemble(input);
                if (made.isItemEnabled(level.enabledFeatures())) result = made;
            }
            resultSlots.setItem(0, result);
            setRemoteSlot(0, result);
            sp.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), 0, result));
        });
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, p) -> clearContainer(player, craftSlots));
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return true;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64.0) return false;
        if (--validCheck <= 0) {
            validCheck = 20;
            BrickOven.Oven oven = BrickOven.find(player.level(), pos);
            valid = oven != null && oven.lit(player.level());
            if (!valid) {
                player.sendOverlayMessage(Component.translatable(oven == null
                        ? "message.realisticsurvival.oven.broken" : "message.realisticsurvival.oven.cold"));
            }
        }
        return valid;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack clicked = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return clicked;
        ItemStack stack = slot.getItem();
        clicked = stack.copy();
        if (index == 0) {
            stack.getItem().onCraftedBy(stack, player);
            if (!moveItemStackTo(stack, 10, 46, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(stack, clicked);
        } else if (index >= 10) {
            if (!moveItemStackTo(stack, 1, 10, false)) {
                if (index < 37) {
                    if (!moveItemStackTo(stack, 37, 46, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, 10, 37, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (!moveItemStackTo(stack, 10, 46, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == clicked.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        if (index == 0) player.drop(stack, false, Prediction.PREDICTED);
        return clicked;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != resultSlots && super.canTakeItemForPickAll(carried, target);
    }

    /** The output slot: takes one of each ingredient (leaving buckets etc. behind) when you take the result. */
    static class OvenResultSlot extends Slot {
        private final CraftingContainer craftSlots;
        private final Player player;
        private int removeCount;

        OvenResultSlot(Player player, CraftingContainer craftSlots, Container container, int id, int x, int y) {
            super(container, id, x, y);
            this.player = player;
            this.craftSlots = craftSlots;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public ItemStack remove(int amount) {
            if (hasItem()) removeCount += Math.min(amount, getItem().getCount());
            return super.remove(amount);
        }

        @Override
        protected void onQuickCraft(ItemStack picked, int count) {
            removeCount += count;
            checkTakeAchievements(picked);
        }

        @Override
        protected void onSwapCraft(int count) {
            removeCount += count;
        }

        @Override
        protected void checkTakeAchievements(ItemStack carried) {
            if (removeCount > 0) {
                carried.onCraftedBy(player, removeCount);
                EventHooks.firePlayerCraftingEvent(player, carried, craftSlots);
            }
            removeCount = 0;
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            checkTakeAchievements(carried);
            CraftingInput.Positioned positioned = craftSlots.asPositionedCraftInput();
            CraftingInput input = positioned.input();
            int left = positioned.left(), top = positioned.top();
            CommonHooks.setCraftingPlayer(player);
            NonNullList<ItemStack> remaining = player.level() instanceof ServerLevel sl
                    ? recipeFor(sl, input).map(r -> r.value().getRemainingItems(input)).orElseGet(() -> CraftingRecipe.defaultCraftingReminder(input))
                    : CraftingRecipe.defaultCraftingReminder(input);
            CommonHooks.setCraftingPlayer(null);
            for (int y = 0; y < input.height(); y++) {
                for (int x = 0; x < input.width(); x++) {
                    int slot = x + left + (y + top) * craftSlots.getWidth();
                    ItemStack have = craftSlots.getItem(slot);
                    ItemStack replacement = remaining.get(x + y * input.width());
                    if (!have.isEmpty()) {
                        craftSlots.removeItem(slot, 1);
                        have = craftSlots.getItem(slot);
                    }
                    if (!replacement.isEmpty()) {
                        if (have.isEmpty()) {
                            craftSlots.setItem(slot, replacement);
                        } else if (ItemStack.isSameItemSameComponents(have, replacement)) {
                            replacement.grow(have.getCount());
                            craftSlots.setItem(slot, replacement);
                        } else if (!this.player.getInventory().add(replacement)) {
                            this.player.drop(replacement, false, Prediction.PREDICTED);
                        }
                    }
                }
            }
        }

        @Override
        public boolean isFake() {
            return true;
        }
    }
}
