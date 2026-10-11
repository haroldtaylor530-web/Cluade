package com.harold.realisticsurvival.recipe;

import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.survival.Spoilage;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * One salt and up to 8 pieces of the same meat or fish: they come out salted and keep six times
 * as long. Salting doesn't make old meat fresh again.
 */
public class SaltingRecipe extends CustomRecipe {
    public static final SaltingRecipe INSTANCE = new SaltingRecipe();
    public static final RecipeSerializer<SaltingRecipe> SERIALIZER = new RecipeSerializer<>(
            MapCodec.unit(INSTANCE), StreamCodec.<RegistryFriendlyByteBuf, SaltingRecipe>unit(INSTANCE));

    public static boolean canSalt(ItemStack stack) {
        return (stack.is(ItemTags.MEAT) || stack.is(ItemTags.FISHES)) && !stack.is(Items.ROTTEN_FLESH)
                && !Spoilage.salted(stack);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !assemble(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        int salt = 0;
        int pieces = 0;
        ItemStack food = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ModRegistry.SALT.get())) {
                if (++salt > 1) return ItemStack.EMPTY;
            } else if (canSalt(s)) {
                if (!food.isEmpty() && !ItemStack.isSameItemSameComponents(food, s)) return ItemStack.EMPTY;
                food = s;
                pieces++;
            } else {
                return ItemStack.EMPTY;
            }
        }
        if (salt != 1 || pieces == 0) return ItemStack.EMPTY;
        ItemStack out = food.copyWithCount(pieces);
        out.set(ModRegistry.PRESERVED.get(), Spoilage.preserved(out) | Spoilage.SALTED);
        return out;
    }

    @Override
    public RecipeSerializer<SaltingRecipe> getSerializer() {
        return SERIALIZER;
    }
}
