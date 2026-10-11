package com.harold.realisticsurvival.block;

import com.harold.realisticsurvival.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The campfire's menu: a kindling slot, a fuel slot, fire size buttons and a light button.
 * Buttons: 0/1/2 = small/medium/large, 3 = light it.
 */
public class CampfireMenu extends AbstractContainerMenu {
    public static final int DATA_LIT = 0, DATA_EMBERS = 1, DATA_BURN_PCT = 2, DATA_SECONDS = 3, DATA_SIZE = 4, DATA_RAIN = 5, DATA_STARTER = 6;
    public static final int DATA_COUNT = 7;
    public static final int BUTTON_LIGHT = 3;

    private final Container fire;
    private final ContainerData data;
    private final ContainerLevelAccess access;
    public final BlockPos pos;

    public CampfireMenu(int id, Inventory inv, Container fire, ContainerData data, ContainerLevelAccess access, BlockPos pos) {
        super(ModRegistry.CAMPFIRE_MENU.get(), id);
        this.fire = fire;
        this.data = data;
        this.access = access;
        this.pos = pos;
        addSlot(new Slot(fire, 0, 8, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RealisticCampfireBlock.isKindling(stack);
            }
        });
        addSlot(new Slot(fire, 1, 8, 53) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return RealisticCampfireBlock.fuelTicks(stack) > 0;
            }
        });
        addStandardInventorySlots(inv, 8, 94);
        addDataSlots(data);
    }

    public static CampfireMenu fromNetwork(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        return new CampfireMenu(id, inv, new SimpleContainer(2), new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL, buf.readBlockPos());
    }

    public static CampfireMenu forCampfire(int id, Inventory inv, CampfireBlockEntity be, BlockPos pos) {
        Player player = inv.player;
        CampfireData d = RealisticCampfireBlock.data(be);
        ContainerData live = new ContainerData() {
            @Override
            public int get(int index) {
                BlockState state = be.getBlockState();
                if (!(state.getBlock() instanceof RealisticCampfireBlock)) return 0;
                return switch (index) {
                    case DATA_LIT -> state.getValue(RealisticCampfireBlock.LIT) ? 1 : 0;
                    case DATA_EMBERS -> (d.embers + 19) / 20;
                    case DATA_BURN_PCT -> d.burnTotal <= 0 ? 0 : Math.round(100.0F * Math.max(0.0F, d.burn) / d.burnTotal);
                    case DATA_SECONDS -> Math.min(32000, RealisticCampfireBlock.secondsLeft(d, state.getValue(RealisticCampfireBlock.SIZE)));
                    case DATA_SIZE -> state.getValue(RealisticCampfireBlock.SIZE);
                    case DATA_RAIN -> be.getLevel() != null && be.getLevel().isRainingAt(pos.above()) ? 1 : 0;
                    case DATA_STARTER -> RealisticCampfireBlock.hasStarter(player) ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
        return new CampfireMenu(id, inv, new Slots(be, d), live, ContainerLevelAccess.create(be.getLevel(), pos), pos);
    }

    public int get(int index) {
        return data.get(index);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp)) return id >= 0 && id <= BUTTON_LIGHT;
        if (id >= 0 && id < 3) {
            access.execute((level, p) -> {
                BlockState state = level.getBlockState(p);
                if (state.getBlock() instanceof RealisticCampfireBlock) {
                    level.setBlock(p, state.setValue(RealisticCampfireBlock.SIZE, id + 1), Block.UPDATE_ALL);
                }
            });
            return true;
        }
        if (id == BUTTON_LIGHT) {
            RealisticCampfireBlock.startLighting(sp, pos);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 2) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) return ItemStack.EMPTY;
        } else if (RealisticCampfireBlock.isKindling(stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (RealisticCampfireBlock.fuelTicks(stack) > 0) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModRegistry.CAMPFIRE_BLOCK.get());
    }

    /** The two slots, backed by the campfire's saved data. */
    private static final class Slots implements Container {
        private final CampfireBlockEntity be;
        private final CampfireData d;

        Slots(CampfireBlockEntity be, CampfireData d) {
            this.be = be;
            this.d = d;
        }

        @Override
        public int getContainerSize() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return d.kindling.isEmpty() && d.fuel.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot == 0 ? d.kindling : d.fuel;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack out = getItem(slot).split(count);
            setChanged();
            return out;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack out = getItem(slot);
            setItem(slot, ItemStack.EMPTY);
            return out;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot == 0) d.kindling = stack; else d.fuel = stack;
            setChanged();
        }

        @Override
        public void setChanged() {
            be.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(be, player);
        }

        @Override
        public void clearContent() {
            d.kindling = ItemStack.EMPTY;
            d.fuel = ItemStack.EMPTY;
        }
    }
}
