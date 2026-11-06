package com.harold.corpsedecay.corpse;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What a player sees looking through a body: the gear that can be taken off it, any arrows that
 * can be pulled out, and the things it dropped, which stayed inside it. Taking an item out of a
 * slot takes it off the body itself (the armour disappears from the model, the arrow comes out).
 * Nothing can be put in.
 */
public final class CorpseLoot implements Container {
    public static final int SIZE = 27;

    private record Entry(int kind, int index, String key, CompoundTag arrow) {
    }

    private static final int GEAR = 0, EXTRA = 1, ARROW = 2, STORED = 3;
    private final CorpseEntity corpse;
    private final List<Entry> entries = new ArrayList<>();

    public CorpseLoot(CorpseEntity corpse) {
        this.corpse = corpse;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (corpse.lootable(slot) && !corpse.equipment(slot).isEmpty()) entries.add(new Entry(GEAR, slot.ordinal(), null, null));
        }
        for (String key : corpse.extraLoot().getAllKeys()) entries.add(new Entry(EXTRA, 0, key, null));
        for (Tag t : corpse.arrows()) {
            CompoundTag a = (CompoundTag) t;
            if (a.getBoolean("Loot")) entries.add(new Entry(ARROW, 0, null, a));
        }
        for (int i = 0; i < corpse.stored().size(); i++) {
            if (!corpse.stored().get(i).isEmpty()) entries.add(new Entry(STORED, i, null, null));
        }
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) if (!getItem(i).isEmpty()) return false;
        return true;
    }

    @Override
    public ItemStack getItem(int i) {
        if (i < 0 || i >= entries.size()) return ItemStack.EMPTY;
        Entry e = entries.get(i);
        return switch (e.kind) {
            case GEAR -> corpse.lootable(EquipmentSlot.values()[e.index]) ? corpse.equipment(EquipmentSlot.values()[e.index]) : ItemStack.EMPTY;
            case EXTRA -> corpse.extraLoot().contains(e.key) ? ItemStack.of(corpse.extraLoot().getCompound(e.key)) : ItemStack.EMPTY;
            case ARROW -> corpse.hasArrow(e.arrow) ? StuckArrows.item(e.arrow) : ItemStack.EMPTY;
            default -> e.index < corpse.stored().size() ? corpse.stored().get(e.index) : ItemStack.EMPTY;
        };
    }

    @Override
    public ItemStack removeItem(int i, int count) {
        ItemStack cur = getItem(i);
        if (cur.isEmpty() || count <= 0) return ItemStack.EMPTY;
        ItemStack rest = cur.copy();
        ItemStack out = rest.split(count);
        setItem(i, rest);
        return out;
    }

    @Override
    public ItemStack removeItemNoUpdate(int i) {
        ItemStack out = getItem(i).copy();
        setItem(i, ItemStack.EMPTY);
        return out;
    }

    @Override
    public void setItem(int i, ItemStack stack) {
        if (i < 0 || i >= entries.size()) return;
        Entry e = entries.get(i);
        switch (e.kind) {
            case GEAR -> corpse.giveGear(EquipmentSlot.values()[e.index], stack.copy());
            case EXTRA -> corpse.setExtraLoot(e.key, stack.copy());
            case ARROW -> {
                if (stack.isEmpty()) corpse.pullArrow(e.arrow);
            }
            default -> corpse.setStored(e.index, stack.copy());
        }
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return !corpse.isRemoved() && player.distanceToSqr(corpse) < 8 * 8;
    }

    @Override
    public void clearContent() {
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }
}
