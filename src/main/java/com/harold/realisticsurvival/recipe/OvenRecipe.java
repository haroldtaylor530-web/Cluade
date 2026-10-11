package com.harold.realisticsurvival.recipe;

import com.harold.realisticsurvival.ModRegistry;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * A crafting recipe that only works in the brick oven. It reads exactly like a normal shaped or
 * shapeless crafting recipe (type realisticsurvival:oven_shaped / oven_shapeless), so vanilla
 * recipes are moved to the oven by overriding them with the same pattern and a new type.
 */
public final class OvenRecipe implements CraftingRecipe {
    public static final RecipeSerializer<OvenRecipe> SHAPED = new RecipeSerializer<>(
            ShapedRecipe.MAP_CODEC.xmap(r -> new OvenRecipe(r, true), r -> (ShapedRecipe) r.inner),
            ShapedRecipe.STREAM_CODEC.map(r -> new OvenRecipe(r, true), r -> (ShapedRecipe) r.inner));
    public static final RecipeSerializer<OvenRecipe> SHAPELESS = new RecipeSerializer<>(
            ShapelessRecipe.MAP_CODEC.xmap(r -> new OvenRecipe(r, false), r -> (ShapelessRecipe) r.inner),
            ShapelessRecipe.STREAM_CODEC.map(r -> new OvenRecipe(r, false), r -> (ShapelessRecipe) r.inner));

    private final CraftingRecipe inner;
    private final boolean shaped;

    private OvenRecipe(CraftingRecipe inner, boolean shaped) {
        this.inner = inner;
        this.shaped = shaped;
    }

    public CraftingRecipe inner() {
        return inner;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return inner.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return inner.assemble(input);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return inner.getRemainingItems(input);
    }

    /** Kept out of the crafting table's recipe book. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return inner.group();
    }

    @Override
    public RecipeSerializer<OvenRecipe> getSerializer() {
        return shaped ? SHAPED : SHAPELESS;
    }

    @Override
    public RecipeType<CraftingRecipe> getType() {
        return ModRegistry.OVEN_RECIPE.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return inner.placementInfo();
    }

    @Override
    public CraftingBookCategory category() {
        return inner.category();
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return inner.recipeBookCategory();
    }

    @Override
    public List<RecipeDisplay> display() {
        return inner.display();
    }
}
