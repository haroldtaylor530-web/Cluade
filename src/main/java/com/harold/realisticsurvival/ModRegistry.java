package com.harold.realisticsurvival;

import com.harold.realisticsurvival.block.CookingPotBlock;
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
import net.minecraft.world.item.AxeItem;
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

    // ---- tags ---------------------------------------------------------------------------
    public static final TagKey<net.minecraft.world.item.Item> FLINT_REPAIR = TagKey.create(Registries.ITEM, RealisticSurvival.id("flint_repair"));
    public static final TagKey<net.minecraft.world.item.Item> FUR_REPAIR = TagKey.create(Registries.ITEM, RealisticSurvival.id("fur_repair"));
    public static final TagKey<net.minecraft.world.item.Item> WARM_CLOTHING = TagKey.create(Registries.ITEM, RealisticSurvival.id("warm_clothing"));

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

    // ---- effects ----------------------------------------------------------------------------
    public static final DeferredHolder<MobEffect, MobEffect> BLEEDING = EFFECTS.register("bleeding", BleedingEffect::new);
    public static final DeferredHolder<MobEffect, MobEffect> DYSENTERY = EFFECTS.register("dysentery", DysenteryEffect::new);
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

    // ---- entities ---------------------------------------------------------------------------
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownSpear>> THROWN_SPEAR = ENTITIES.registerEntityType(
            "thrown_spear", ThrownSpear::new, MobCategory.MISC,
            b -> b.sized(0.5F, 0.5F).clientTrackingRange(4).updateInterval(20));

    // ---- blocks -----------------------------------------------------------------------------
    public static final DeferredBlock<GroundItemBlock> ROCK_BLOCK = BLOCKS.registerBlock("rock",
            p -> new GroundItemBlock(p, Block.box(3, 0, 2, 9, 2, 11)),
            p -> p.mapColor(MapColor.STONE).instabreak().noCollision().noOcclusion().sound(SoundType.STONE).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<GroundItemBlock> STICK_ON_GROUND = BLOCKS.registerBlock("stick_on_ground",
            p -> new GroundItemBlock(p, Block.box(0, 0, 0, 16, 1, 16)),
            p -> p.mapColor(MapColor.WOOD).instabreak().noCollision().noOcclusion().sound(SoundType.WOOD).pushReaction(PushReaction.DESTROY));
    public static final DeferredBlock<CookingPotBlock> COOKING_POT_BLOCK = BLOCKS.registerBlock("cooking_pot",
            CookingPotBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.5F).noOcclusion().sound(SoundType.METAL));

    // ---- items ------------------------------------------------------------------------------
    public static final DeferredItem<BlockItem> ROCK = ITEMS.registerSimpleBlockItem("rock", ROCK_BLOCK);
    public static final DeferredItem<BlockItem> COOKING_POT = ITEMS.registerSimpleBlockItem("cooking_pot", COOKING_POT_BLOCK);

    // early game
    public static final DeferredItem<Item> FLINT_SHARD = ITEMS.registerSimpleItem("flint_shard");
    public static final DeferredItem<Item> PLANT_FIBRE = ITEMS.registerSimpleItem("plant_fibre");
    public static final DeferredItem<Item> FIBRE_STRING = ITEMS.registerSimpleItem("fibre_string");
    public static final DeferredItem<Item> FLINT_KNIFE = ITEMS.registerItem("flint_knife", Item::new, p -> p.sword(FLINT, 1.5F, -2.0F));
    public static final DeferredItem<AxeItem> FLINT_HATCHET = ITEMS.registerItem("flint_hatchet", p -> new AxeItem(FLINT, 5.0F, -3.2F, p));
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
    }

    private ModRegistry() {}
}
