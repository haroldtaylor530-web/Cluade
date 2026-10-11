package com.harold.realisticsurvival;

import com.harold.realisticsurvival.block.CampfireData;
import com.harold.realisticsurvival.block.ChoppedLogBlock;
import com.harold.realisticsurvival.block.ClayBrickPileBlock;
import com.harold.realisticsurvival.block.OvenWallBlock;
import net.minecraft.resources.Identifier;
import com.harold.realisticsurvival.block.CampfireMenu;
import com.harold.realisticsurvival.block.CookingPotBlock;
import com.harold.realisticsurvival.block.RealisticCampfireBlock;
import com.harold.realisticsurvival.entity.FallingTree;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import com.harold.realisticsurvival.block.GroundItemBlock;
import com.harold.realisticsurvival.effect.BleedingEffect;
import com.harold.realisticsurvival.effect.DysenteryEffect;
import com.harold.realisticsurvival.effect.SimpleEffect;
import com.harold.realisticsurvival.entity.ThrownSpear;
import com.harold.realisticsurvival.item.BandageItem;
import com.harold.realisticsurvival.item.CupItem;
import com.harold.realisticsurvival.item.SpearItem;
import com.harold.realisticsurvival.item.SplintItem;
import com.harold.realisticsurvival.item.WaterskinItem;
import com.harold.realisticsurvival.survival.SurvivalState;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModRegistry {
    private static final String M = RealisticSurvival.MODID;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(M);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(M);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, M);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(M);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, M);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, M);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, M);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, M);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, M);
    public static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, M);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, M);

    // ---- brick oven recipes -------------------------------------------------------------------
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeType<?>, net.minecraft.world.item.crafting.RecipeType<net.minecraft.world.item.crafting.CraftingRecipe>> OVEN_RECIPE =
            RECIPE_TYPES.register("oven", () -> net.minecraft.world.item.crafting.RecipeType.simple(RealisticSurvival.id("oven")));
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, net.minecraft.world.item.crafting.RecipeSerializer<com.harold.realisticsurvival.recipe.OvenRecipe>> OVEN_SHAPED =
            RECIPE_SERIALIZERS.register("oven_shaped", () -> com.harold.realisticsurvival.recipe.OvenRecipe.SHAPED);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, net.minecraft.world.item.crafting.RecipeSerializer<com.harold.realisticsurvival.recipe.OvenRecipe>> OVEN_SHAPELESS =
            RECIPE_SERIALIZERS.register("oven_shapeless", () -> com.harold.realisticsurvival.recipe.OvenRecipe.SHAPELESS);
    public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>, net.minecraft.world.item.crafting.RecipeSerializer<com.harold.realisticsurvival.recipe.SaltingRecipe>> SALTING =
            RECIPE_SERIALIZERS.register("salting", () -> com.harold.realisticsurvival.recipe.SaltingRecipe.SERIALIZER);

    // ---- tags ---------------------------------------------------------------------------
    public static final TagKey<net.minecraft.world.item.Item> FLINT_REPAIR = TagKey.create(Registries.ITEM, RealisticSurvival.id("flint_repair"));
    public static final TagKey<net.minecraft.world.item.Item> FUR_REPAIR = TagKey.create(Registries.ITEM, RealisticSurvival.id("fur_repair"));
    public static final TagKey<net.minecraft.world.item.Item> WARM_CLOTHING = TagKey.create(Registries.ITEM, RealisticSurvival.id("warm_clothing"));
    public static final TagKey<net.minecraft.world.item.Item> KINDLING = TagKey.create(Registries.ITEM, RealisticSurvival.id("kindling"));

    // ---- materials ----------------------------------------------------------------------
    public static final ToolMaterial FLINT = new ToolMaterial(BlockTags.INCORRECT_FOR_WOODEN_TOOL, 96, 3.0F, 0.5F, 8, FLINT_REPAIR);

    public static final ResourceKey<EquipmentAsset> FUR_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, RealisticSurvival.id("fur"));
    public static final ArmorMaterial FUR = new ArmorMaterial(7,
            Map.of(ArmorType.BOOTS, 1, ArmorType.LEGGINGS, 2, ArmorType.CHESTPLATE, 3, ArmorType.HELMET, 1, ArmorType.BODY, 3),
            12, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, FUR_REPAIR, FUR_ASSET);

    // ---- data components ------------------------------------------------------------------
    /** Drinks left in a waterskin (0..4). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WATER_AMOUNT =
            COMPONENTS.registerComponentType("water_amount", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    /** True if the water in a waterskin is unsafe. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> WATER_DIRTY =
            COMPONENTS.registerComponentType("water_dirty", b -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    /** When a food was made (game time, rounded to a quarter day). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> FOOD_START =
            COMPONENTS.registerComponentType("food_start", b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));
    /** Food sitting in cold storage. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.harold.realisticsurvival.survival.Spoilage.Cold>> FOOD_COLD =
            COMPONENTS.registerComponentType("food_cold", b -> b.persistent(com.harold.realisticsurvival.survival.Spoilage.Cold.CODEC)
                    .networkSynchronized(com.harold.realisticsurvival.survival.Spoilage.Cold.STREAM_CODEC));
    /** Salted (1) and/or smoked (2). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PRESERVED =
            COMPONENTS.registerComponentType("preserved", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /** How keen a tool's edge is, 1..99; missing means fully sharp. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SHARPNESS =
            COMPONENTS.registerComponentType("sharpness", b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    // ---- effects ----------------------------------------------------------------------------
    public static final DeferredHolder<MobEffect, MobEffect> BLEEDING = EFFECTS.register("bleeding", BleedingEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> DYSENTERY = EFFECTS.register("dysentery", DysenteryEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> FOOD_POISONING = EFFECTS.register("food_poisoning", com.harold.realisticsurvival.effect.FoodPoisoningEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> BROKEN_LEG = EFFECTS.register("broken_leg", () ->
            new SimpleEffect(MobEffectCategory.HARMFUL, 0x8a6a4a)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, RealisticSurvival.id("broken_leg_speed"), -0.45, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, RealisticSurvival.id("broken_leg_jump"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect, MobEffect> SPLINTED = EFFECTS.register("splinted", () ->
            new SimpleEffect(MobEffectCategory.NEUTRAL, 0xc8a46a)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, RealisticSurvival.id("splinted_speed"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect, MobEffect> HYPOTHERMIA = EFFECTS.register("hypothermia", () ->
            new SimpleEffect(MobEffectCategory.HARMFUL, 0x7ec8ff)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, RealisticSurvival.id("hypothermia_speed"), -0.12, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, RealisticSurvival.id("hypothermia_dig"), -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    public static final DeferredHolder<MobEffect, MobEffect> HEATSTROKE = EFFECTS.register("heatstroke", () ->
            new SimpleEffect(MobEffectCategory.HARMFUL, 0xff7a2a)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, RealisticSurvival.id("heatstroke_attack"), -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, RealisticSurvival.id("heatstroke_speed"), -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    // ---- attachments ------------------------------------------------------------------------
    /** Full survival state, server side and saved with the player. */
    public static final Supplier<AttachmentType<SurvivalState>> STATE = ATTACHMENTS.register("state",
            () -> AttachmentType.builder(SurvivalState::new).serialize(SurvivalState.CODEC.fieldOf("state")).build());
    /** Thirst shown on the HUD (0..20), synced to the player's own client. */
    public static final Supplier<AttachmentType<Integer>> THIRST_HUD = ATTACHMENTS.register("thirst_hud",
            () -> AttachmentType.builder(() -> 20).sync(ByteBufCodecs.VAR_INT).build());
    /** Body temperature in tenths of a degree C, synced for the HUD. */
    public static final Supplier<AttachmentType<Integer>> TEMP_HUD = ATTACHMENTS.register("temp_hud",
            () -> AttachmentType.builder(() -> 370).sync(ByteBufCodecs.VAR_INT).build());

    /** Stamina for the HUD: tenths of a point (0..200), plus 1000 while out of breath. */
    public static final Supplier<AttachmentType<Integer>> STAMINA_HUD = ATTACHMENTS.register("stamina_hud",
            () -> AttachmentType.builder(() -> 200).sync(ByteBufCodecs.VAR_INT).build());

    // ---- entities ---------------------------------------------------------------------------
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownSpear>> THROWN_SPEAR = ENTITIES.registerEntityType(
            "thrown_spear", ThrownSpear::new, MobCategory.MISC,
            b -> b.sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20));
    /** A tree toppling over after its trunk was cut through. */
    public static final DeferredHolder<EntityType<?>, EntityType<FallingTree>> FALLING_TREE = ENTITIES.registerEntityType(
            "falling_tree", FallingTree::new, MobCategory.MISC,
            b -> b.sized(1.0F, 1.0F).clientTrackingRange(12).updateInterval(40).noSummon());

    /** Kindling, fuel and embers of a campfire, stored on the campfire's block entity. */
    public static final Supplier<AttachmentType<CampfireData>> CAMPFIRE_DATA = ATTACHMENTS.register("campfire",
            () -> AttachmentType.builder(CampfireData::new).serialize(CampfireData.CODEC.fieldOf("campfire")).build());

    // ---- blocks -----------------------------------------------------------------------------
    public static final DeferredBlock<GroundItemBlock> ROCK_BLOCK = BLOCKS.registerBlock("rock",
            p -> new GroundItemBlock(p, Block.box(3, 0, 2, 9, 2, 11)),
            p -> p.mapColor(MapColor.STONE).instabreak().noCollision().noOcclusion().sound(SoundType.STONE).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<GroundItemBlock> STICK_ON_GROUND = BLOCKS.registerBlock("stick_on_ground",
            p -> new GroundItemBlock(p, Block.box(0, 0, 0, 16, 1, 16)),
            p -> p.mapColor(MapColor.WOOD).instabreak().noCollision().noOcclusion().sound(SoundType.WOOD).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<CookingPotBlock> COOKING_POT_BLOCK = BLOCKS.registerBlock("cooking_pot",
            CookingPotBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.5F).noOcclusion().sound(SoundType.METAL));

    /** Replaces the vanilla campfire: needs kindling and fuel, burns down, has a menu. */
    public static final DeferredBlock<RealisticCampfireBlock> CAMPFIRE_BLOCK = BLOCKS.registerBlock("campfire",
            RealisticCampfireBlock::new,
            p -> p.mapColor(MapColor.PODZOL).instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS)
                    .strength(2.0F).sound(SoundType.WOOD).lightLevel(RealisticCampfireBlock::lightLevel).noOcclusion().ignitedByLava());

    /** Wet clay bricks set out to dry, up to 4 to a pile. */
    public static final DeferredBlock<ClayBrickPileBlock> CLAY_BRICK_PILE = BLOCKS.registerBlock("clay_brick_pile", ClayBrickPileBlock::new,
            p -> p.mapColor(MapColor.CLAY).strength(0.3F).sound(SoundType.MUD_BRICKS).noOcclusion().pushReaction(PushReaction.POPPED));
    /** Dried clay bricks built into a wall; part of the brick oven. */
    public static final DeferredBlock<OvenWallBlock> OVEN_WALL_BLOCK = BLOCKS.registerBlock("oven_wall", OvenWallBlock::new,
            p -> p.mapColor(MapColor.TERRACOTTA_ORANGE).strength(2.0F, 6.0F).sound(SoundType.MUD_BRICKS).requiresCorrectToolForDrops());

    /** Small decorative blocks made from your rock and stick textures. */
    public static final DeferredBlock<net.minecraft.world.level.block.SlabBlock> RUBBLE_SLAB_BLOCK = BLOCKS.registerBlock("rubble_slab",
            net.minecraft.world.level.block.SlabBlock::new, p -> p.mapColor(MapColor.STONE).strength(1.0F, 4.0F).sound(SoundType.STONE));
    public static final DeferredBlock<net.minecraft.world.level.block.WallBlock> RUBBLE_WALL_BLOCK = BLOCKS.registerBlock("rubble_wall",
            net.minecraft.world.level.block.WallBlock::new, p -> p.mapColor(MapColor.STONE).strength(1.0F, 4.0F).sound(SoundType.STONE));
    public static final DeferredBlock<net.minecraft.world.level.block.FenceBlock> STICK_FENCE_BLOCK = BLOCKS.registerBlock("stick_fence",
            net.minecraft.world.level.block.FenceBlock::new, p -> p.mapColor(MapColor.WOOD).strength(1.0F, 2.0F).sound(SoundType.WOOD).ignitedByLava());

    /** Part-way chopped bottom logs, one per tree wood. */
    public static final String[] CHOPPABLE_WOODS = { "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak" };
    public static final Map<String, DeferredBlock<ChoppedLogBlock>> CHOPPED_LOGS = new java.util.LinkedHashMap<>();

    static {
        for (String wood : CHOPPABLE_WOODS) {
            Identifier logId = Identifier.withDefaultNamespace(wood + "_log");
            CHOPPED_LOGS.put(wood, BLOCKS.registerBlock("chopped_" + wood + "_log",
                    p -> new ChoppedLogBlock(p, () -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(logId)),
                    p -> p.mapColor(MapColor.WOOD).instrument(net.minecraft.world.level.block.state.properties.NoteBlockInstrument.BASS)
                            .strength(2.0F).sound(SoundType.WOOD).ignitedByLava().requiresCorrectToolForDrops()));
        }
    }

    /** The chopped version of a log block, or null if it has none. */
    public static ChoppedLogBlock choppedFor(Block log) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(log);
        if (!id.getNamespace().equals("minecraft") || !id.getPath().endsWith("_log")) return null;
        DeferredBlock<ChoppedLogBlock> b = CHOPPED_LOGS.get(id.getPath().substring(0, id.getPath().length() - 4));
        return b == null ? null : b.get();
    }

    // ---- items ------------------------------------------------------------------------------
    public static final DeferredItem<BlockItem> ROCK= ITEMS.registerSimpleBlockItem("rock", ROCK_BLOCK);
    public static final DeferredItem<BlockItem> RUBBLE_SLAB = ITEMS.registerSimpleBlockItem("rubble_slab", RUBBLE_SLAB_BLOCK);
    public static final DeferredItem<BlockItem> RUBBLE_WALL = ITEMS.registerSimpleBlockItem("rubble_wall", RUBBLE_WALL_BLOCK);
    public static final DeferredItem<BlockItem> STICK_FENCE = ITEMS.registerSimpleBlockItem("stick_fence", STICK_FENCE_BLOCK);
    public static final DeferredItem<BlockItem> COOKING_POT = ITEMS.registerSimpleBlockItem("cooking_pot", COOKING_POT_BLOCK);

    // early game
    public static final DeferredItem<com.harold.realisticsurvival.item.WhetstoneItem> WHETSTONE = ITEMS.registerItem("whetstone",
            com.harold.realisticsurvival.item.WhetstoneItem::new, p -> p.durability(48));
    /** A stick wound with string, spun between your hands: lights a fire if you rub the mouse fast enough. */
    public static final DeferredItem<Item> HAND_DRILL = ITEMS.registerItem("hand_drill", Item::new, p -> p.durability(10));
    /** A flint struck on a rock: one go at lighting a fire. */
    public static final DeferredItem<net.minecraft.world.item.FlintAndSteelItem> FLINT_STRIKER = ITEMS.registerItem("flint_striker",
            net.minecraft.world.item.FlintAndSteelItem::new, p -> p.durability(1));
    /** Very good kindling and fuel: a stripped log bundled with fibre. */
    public static final DeferredItem<Item> FIRE_STARTER = ITEMS.registerSimpleItem("fire_starter");
    public static final DeferredItem<Item> FLINT_SHARD = ITEMS.registerSimpleItem("flint_shard");
    public static final DeferredItem<Item> PLANT_FIBRE = ITEMS.registerSimpleItem("plant_fibre");
    public static final DeferredItem<Item> FIBRE_STRING = ITEMS.registerSimpleItem("fibre_string");
    public static final DeferredItem<Item> FLINT_KNIFE = ITEMS.registerItem("flint_knife", Item::new, p -> p.sword(FLINT, 1.5F, -2.0F));
    public static final DeferredItem<Item> FLINT_HATCHET = ITEMS.registerItem("flint_hatchet", Item::new, p -> p.axe(FLINT, 5.0F, -3.2F));
    public static final DeferredItem<Item> FLINT_PICKAXE = ITEMS.registerItem("flint_pickaxe", Item::new, p -> p.pickaxe(FLINT, 1.0F, -2.8F));

    // spears: melee damage, thrown damage, durability
    public static final DeferredItem<SpearItem> FLINT_SPEAR = ITEMS.registerItem("flint_spear",
            p -> new SpearItem(p, 4.0F, 7.0F), p -> p.durability(120).attributes(SpearItem.attributes(4.0F)));
    public static final DeferredItem<SpearItem> IRON_SPEAR = ITEMS.registerItem("iron_spear",
            p -> new SpearItem(p, 5.5F, 9.0F), p -> p.durability(320).attributes(SpearItem.attributes(5.5F)));
    public static final DeferredItem<SpearItem> DIAMOND_SPEAR = ITEMS.registerItem("diamond_spear",
            p -> new SpearItem(p, 6.5F, 11.0F), p -> p.durability(1100).attributes(SpearItem.attributes(6.5F)));

    // water
    public static final DeferredItem<Item> UNFIRED_CLAY_CUP = ITEMS.registerSimpleItem("unfired_clay_cup");
    public static final DeferredItem<CupItem> CLAY_CUP = ITEMS.registerItem("clay_cup", p -> new CupItem(p, CupItem.Kind.EMPTY));
    public static final DeferredItem<CupItem> DIRTY_WATER_CUP = ITEMS.registerItem("dirty_water_cup", p -> new CupItem(p, CupItem.Kind.DIRTY), p -> p.stacksTo(1));
    public static final DeferredItem<CupItem> CLEAN_WATER_CUP = ITEMS.registerItem("clean_water_cup", p -> new CupItem(p, CupItem.Kind.CLEAN), p -> p.stacksTo(1));
    public static final DeferredItem<WaterskinItem> WATERSKIN = ITEMS.registerItem("waterskin", WaterskinItem::new,
            p -> p.stacksTo(1).component(WATER_AMOUNT, 0).component(WATER_DIRTY, false));

    // medical
    public static final DeferredItem<BandageItem> BANDAGE = ITEMS.registerItem("bandage", BandageItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<SplintItem> SPLINT = ITEMS.registerItem("splint", SplintItem::new, p -> p.stacksTo(8));

    // warmth
    public static final DeferredItem<Item> FUR_PELT = ITEMS.registerSimpleItem("fur_pelt");
    public static final DeferredItem<Item> FUR_HAT = ITEMS.registerItem("fur_hat", Item::new, p -> p.humanoidArmor(FUR, ArmorType.HELMET));
    public static final DeferredItem<Item> FUR_COAT = ITEMS.registerItem("fur_coat", Item::new, p -> p.humanoidArmor(FUR, ArmorType.CHESTPLATE));
    public static final DeferredItem<Item> FUR_TROUSERS = ITEMS.registerItem("fur_trousers", Item::new, p -> p.humanoidArmor(FUR, ArmorType.LEGGINGS));
    public static final DeferredItem<Item> FUR_BOOTS = ITEMS.registerItem("fur_boots", Item::new, p -> p.humanoidArmor(FUR, ArmorType.BOOTS));

    // fire
    public static final DeferredItem<BlockItem> CAMPFIRE = ITEMS.registerSimpleBlockItem("campfire", CAMPFIRE_BLOCK);
    public static final DeferredItem<Item> BOW_DRILL = ITEMS.registerItem("bow_drill", Item::new, p -> p.durability(24));

    // brick oven
    public static final DeferredItem<BlockItem> WET_CLAY_BRICK = ITEMS.registerSimpleBlockItem("wet_clay_brick", CLAY_BRICK_PILE);
    public static final DeferredItem<Item> DRY_CLAY_BRICK = ITEMS.registerSimpleItem("dry_clay_brick");
    public static final DeferredItem<BlockItem> OVEN_WALL = ITEMS.registerSimpleBlockItem("oven_wall", OVEN_WALL_BLOCK);

    // ---- food -------------------------------------------------------------------------------
    /** Meat and fish hung up to dry in the sun or smoke over a fire. */
    public static final DeferredBlock<com.harold.realisticsurvival.block.DryingRackBlock> DRYING_RACK_BLOCK = BLOCKS.registerBlock("drying_rack",
            com.harold.realisticsurvival.block.DryingRackBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
    public static final DeferredItem<BlockItem> DRYING_RACK = ITEMS.registerSimpleBlockItem("drying_rack", DRYING_RACK_BLOCK);
    public static final Supplier<net.minecraft.world.level.block.entity.BlockEntityType<com.harold.realisticsurvival.block.DryingRackBlockEntity>> DRYING_RACK_BE =
            BLOCK_ENTITIES.register("drying_rack", () -> new net.minecraft.world.level.block.entity.BlockEntityType<>(
                    com.harold.realisticsurvival.block.DryingRackBlockEntity::new, DRYING_RACK_BLOCK.get()));
    public static final DeferredItem<Item> JERKY = ITEMS.registerItem("jerky", Item::new,
            p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationModifier(0.5F).build()));
    public static final DeferredItem<Item> DRIED_FISH = ITEMS.registerItem("dried_fish", Item::new,
            p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationModifier(0.5F).build()));
    public static final DeferredItem<Item> DRIED_FRUIT = ITEMS.registerItem("dried_fruit", Item::new,
            p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationModifier(0.4F).build()));
    public static final DeferredItem<Item> ROTTEN_FOOD = ITEMS.registerItem("rotten_food", Item::new,
            p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).build())
                    .compostable(net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders.COMPOSTABLE_MEDIUM));
    public static final DeferredItem<Item> SALT = ITEMS.registerSimpleItem("salt");
    public static final DeferredItem<CupItem> SALT_WATER_CUP = ITEMS.registerItem("salt_water_cup", p -> new CupItem(p, CupItem.Kind.SALT), p -> p.stacksTo(1));

    // ---- menus --------------------------------------------------------------------------------
    public static final DeferredHolder<MenuType<?>, MenuType<com.harold.realisticsurvival.block.OvenMenu>> OVEN_MENU = MENUS.register("oven",
            () -> IMenuTypeExtension.create(com.harold.realisticsurvival.block.OvenMenu::fromNetwork));
    public static final DeferredHolder<MenuType<?>, MenuType<CampfireMenu>> CAMPFIRE_MENU = MENUS.register("campfire",
            () -> IMenuTypeExtension.create(CampfireMenu::fromNetwork));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.realisticsurvival"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> FLINT_SPEAR.get().getDefaultInstance())
            .displayItems((params, out) -> ITEMS.getEntries().forEach(e -> out.accept(e.get())))
            .build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        EFFECTS.register(bus);
        ENTITIES.register(bus);
        ATTACHMENTS.register(bus);
        COMPONENTS.register(bus);
        TABS.register(bus);
        MENUS.register(bus);
        RECIPE_TYPES.register(bus);
        RECIPE_SERIALIZERS.register(bus);
        BLOCK_ENTITIES.register(bus);
        // the realistic campfire reuses the vanilla campfire block entity (cooking slots, renderer)
        bus.addListener((net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent e) ->
                e.modify(net.minecraft.world.level.block.entity.BlockEntityTypes.CAMPFIRE, CAMPFIRE_BLOCK.get()));
    }

    private ModRegistry() {}
}
