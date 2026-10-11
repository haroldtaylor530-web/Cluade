package com.harold.realisticsurvival.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * What a realistic campfire holds besides its cooking slots: kindling, a stack of fuel, how much
 * of the current piece of fuel is left, and how long the embers stay hot after it burns out.
 */
public class CampfireData {
    public static final Codec<CampfireData> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("kindling", ItemStack.EMPTY).forGetter(d -> d.kindling),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("fuel", ItemStack.EMPTY).forGetter(d -> d.fuel),
            Codec.FLOAT.optionalFieldOf("burn", 0.0F).forGetter(d -> d.burn),
            Codec.FLOAT.optionalFieldOf("burnTotal", 0.0F).forGetter(d -> d.burnTotal),
            Codec.INT.optionalFieldOf("embers", 0).forGetter(d -> d.embers)
    ).apply(i, CampfireData::new));

    public ItemStack kindling = ItemStack.EMPTY;
    public ItemStack fuel = ItemStack.EMPTY;
    /** Ticks of medium-fire burning left in the piece of fuel on the fire now. */
    public float burn;
    public float burnTotal;
    /** Ticks the embers stay hot after the fire runs out of fuel; adding fuel then relights it. */
    public int embers;

    public CampfireData() {}

    private CampfireData(ItemStack kindling, ItemStack fuel, float burn, float burnTotal, int embers) {
        this.kindling = kindling;
        this.fuel = fuel;
        this.burn = burn;
        this.burnTotal = burnTotal;
        this.embers = embers;
    }
}
