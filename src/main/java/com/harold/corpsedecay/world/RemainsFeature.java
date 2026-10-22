package com.harold.corpsedecay.world;

import java.util.Optional;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.CorpseDecay;
import com.harold.corpsedecay.CorpseKind;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.Vec3;

/**
 * Leaves the remains of something that died long ago: an adventurer's skeleton still in its
 * battered armour with its sword or bow, sometimes with a chest of belongings beside it, or the
 * bones of an animal. What grows on and around them depends on where they lie: moss in lush caves,
 * mushrooms in dark places, cobwebs in caves, dead bushes and cactus in the desert, snow in the
 * cold, roots and fungus in the nether. Old bones in the end are stained dark purple.
 */
public class RemainsFeature extends Feature<NoneFeatureConfiguration> {
    public enum Where { CAVE, SURFACE, NETHER, END }

    public static final ResourceLocation CHEST_LOOT = new ResourceLocation(CorpseDecay.MODID, "chests/remains");
    private static final TagKey<Biome> SANDY = TagKey.create(net.minecraft.core.registries.Registries.BIOME,
            new ResourceLocation(CorpseDecay.MODID, "sandy"));

    private final Where where;

    public RemainsFeature(Where where) {
        super(NoneFeatureConfiguration.CODEC);
        this.where = where;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.REMAINS.get()) return false;
        WorldGenLevel level = ctx.level();
        RandomSource rand = ctx.random();
        BlockPos floor = switch (where) {
            case CAVE -> caveSpot(level, ctx.origin(), true);
            case NETHER -> caveSpot(level, ctx.origin(), false);
            case SURFACE, END -> surfaceSpot(level, ctx.origin());
        };
        if (floor == null) return false;
        if (where == Where.END && !level.getBlockState(floor).is(Blocks.END_STONE)) return false;
        return placeRemains(level.getLevel(), level, floor, rand, where) != null;
    }

    /** Lays out a whole find (body, gear, what grows there, maybe a chest) on top of {@code floor}. */
    public static CorpseEntity placeRemains(ServerLevel server, WorldGenLevel level, BlockPos floor, RandomSource rand, Where where) {
        Holder<Biome> biome = level.getBiome(floor.above());
        boolean adventurer = rand.nextFloat() < switch (where) {
            case CAVE -> 0.6F;
            case SURFACE -> 0.5F;
            case NETHER -> 0.55F;
            case END -> 0.85F;
        };
        EntityType<?> mob = adventurer ? adventurerType(rand, where) : pickMob(rand, where);
        if (mob == null) return null;
        int material = material(level, floor, biome, rand, where);
        boolean sandy = biome.is(SANDY) && where == Where.SURFACE;
        double sink = sandy ? 0.22 + rand.nextDouble() * 0.18 : 0.03 + rand.nextDouble() * 0.09;
        CorpseEntity body = placeBody(server, level, floor, mob, rand, material, sink);
        if (body == null) return null;
        if (adventurer) equip(body, rand, where, mob == EntityType.PIGLIN);
        int arrows = rand.nextFloat() < (adventurer ? 0.35F : 0.3F) ? 1 + rand.nextInt(adventurer ? 3 : 2) : 0;
        for (int i = 0; i < arrows; i++) body.addOldArrow(rand);
        dress(level, floor, biome, rand, where, body);
        float chest = adventurer ? switch (where) {
            case CAVE, NETHER -> 0.25F;
            case SURFACE -> 0.2F;
            case END -> 0.4F;
        } : 0F;
        if (rand.nextFloat() < chest) placeChest(level, floor, rand);
        level.addFreshEntity(body);
        return body;
    }

    /** Just the body, lying on {@code floor}; also used by the tests. */
    public static CorpseEntity placeBody(ServerLevel server, ServerLevelAccessor level, BlockPos floor, EntityType<?> mob,
                                         RandomSource rand, int material, double sink) {
        Vec3 ground = new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5);
        return CorpseEntity.remains(server, mob, ground, rand, material, sink);
    }

    // ---- who --------------------------------------------------------------------------------------

    private static EntityType<?> adventurerType(RandomSource rand, Where where) {
        return switch (where) {
            case NETHER -> rand.nextFloat() < 0.45F ? EntityType.PIGLIN : EntityType.SKELETON;
            case END -> EntityType.SKELETON;
            default -> rand.nextFloat() < 0.25F ? EntityType.ZOMBIE : EntityType.SKELETON;
        };
    }

    private static EntityType<?> pickMob(RandomSource rand, Where where) {
        TagKey<EntityType<?>> tag = switch (where) {
            case NETHER -> CorpseKind.NETHER_REMAINS_MOBS;
            case END -> CorpseKind.END_REMAINS_MOBS;
            default -> CorpseKind.REMAINS_MOBS;
        };
        Optional<HolderSet.Named<EntityType<?>>> set = BuiltInRegistries.ENTITY_TYPE.getTag(tag);
        if (set.isEmpty() || set.get().size() == 0) return null;
        return set.get().getRandomElement(rand).map(Holder::value).orElse(null);
    }

    private static int material(WorldGenLevel level, BlockPos floor, Holder<Biome> biome, RandomSource rand, Where where) {
        BlockState below = level.getBlockState(floor);
        return switch (where) {
            case END -> CorpseEntity.ENDER;
            case NETHER -> biome.is(Biomes.SOUL_SAND_VALLEY) ? CorpseEntity.OLD
                    : biome.is(Biomes.BASALT_DELTAS) || rand.nextBoolean() ? CorpseEntity.CHARRED : CorpseEntity.OLD;
            case CAVE -> biome.is(Biomes.LUSH_CAVES) ? CorpseEntity.MOSSY
                    : below.is(Blocks.DEEPSLATE) || below.is(Blocks.TUFF) ? CorpseEntity.DARK
                    : below.is(BlockTags.SAND) || below.is(Blocks.SANDSTONE) ? CorpseEntity.SANDY
                    : rand.nextFloat() < 0.4F ? CorpseEntity.DARK : CorpseEntity.OLD;
            case SURFACE -> biome.is(SANDY) || biome.value().coldEnoughToSnow(floor.above()) ? CorpseEntity.BLEACHED
                    : lush(biome) || rand.nextFloat() < 0.3F ? CorpseEntity.MOSSY : CorpseEntity.OLD;
        };
    }

    private static boolean lush(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_JUNGLE) || biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(Biomes.DARK_FOREST)
                || biome.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biome.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA) || biome.is(Biomes.MUSHROOM_FIELDS);
    }

    // ---- gear -------------------------------------------------------------------------------------

    private static void equip(CorpseEntity body, RandomSource rand, Where where, boolean piglin) {
        EquipmentSlot[] armour = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            if (rand.nextFloat() >= 0.45F) continue;
            Item[] set = piglin ? GOLD : armourSet(rand, where);
            body.giveGear(armour[i], worn(new ItemStack(set[i]), rand));
        }
        if (rand.nextFloat() < 0.7F) {
            Item[] weapons = switch (where) {
                case NETHER -> piglin ? new Item[] {Items.GOLDEN_SWORD, Items.CROSSBOW, Items.GOLDEN_AXE}
                        : new Item[] {Items.IRON_SWORD, Items.BOW, Items.STONE_SWORD, Items.IRON_PICKAXE};
                case END -> new Item[] {Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.BOW, Items.IRON_PICKAXE, Items.CROSSBOW};
                default -> new Item[] {Items.IRON_SWORD, Items.STONE_SWORD, Items.BOW, Items.BOW, Items.CROSSBOW, Items.IRON_PICKAXE,
                        Items.STONE_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.TORCH};
            };
            body.giveGear(EquipmentSlot.MAINHAND, worn(new ItemStack(weapons[rand.nextInt(weapons.length)]), rand));
        }
        if (rand.nextFloat() < 0.2F) {
            Item off = where == Where.END || rand.nextBoolean() ? Items.SHIELD : Items.TORCH;
            body.giveGear(EquipmentSlot.OFFHAND, worn(new ItemStack(off), rand));
        }
    }

    private static final Item[] LEATHER = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
    private static final Item[] CHAIN = {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS};
    private static final Item[] IRON = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
    private static final Item[] GOLD = {Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS};
    private static final Item[] DIAMOND = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};

    private static Item[] armourSet(RandomSource rand, Where where) {
        float r = rand.nextFloat();
        return switch (where) {
            case NETHER -> r < 0.55F ? GOLD : r < 0.75F ? CHAIN : IRON;
            case END -> r < 0.45F ? IRON : r < 0.7F ? CHAIN : DIAMOND;
            default -> r < 0.35F ? LEATHER : r < 0.6F ? CHAIN : r < 0.9F ? IRON : GOLD;
        };
    }

    /** Battered: well used, but not quite broken. */
    private static ItemStack worn(ItemStack s, RandomSource rand) {
        if (s.isDamageableItem()) s.setDamageValue((int) (s.getMaxDamage() * (0.5F + rand.nextFloat() * 0.45F)));
        return s;
    }

    // ---- what grows there ---------------------------------------------------------------------------

    private static void dress(WorldGenLevel level, BlockPos floor, Holder<Biome> biome, RandomSource rand, Where where, CorpseEntity body) {
        switch (where) {
            case END -> {
            }
            case NETHER -> {
                if (biome.is(Biomes.CRIMSON_FOREST)) {
                    growOn(body, rand, Blocks.CRIMSON_ROOTS, Blocks.CRIMSON_FUNGUS);
                    around(level, floor, rand, 2 + rand.nextInt(3), Blocks.CRIMSON_ROOTS, Blocks.CRIMSON_FUNGUS);
                } else if (biome.is(Biomes.WARPED_FOREST)) {
                    growOn(body, rand, Blocks.WARPED_ROOTS, Blocks.WARPED_FUNGUS);
                    around(level, floor, rand, 2 + rand.nextInt(3), Blocks.WARPED_ROOTS, Blocks.WARPED_FUNGUS);
                }
            }
            case CAVE -> {
                if (biome.is(Biomes.LUSH_CAVES)) {
                    for (int i = 0; i < 1 + rand.nextInt(2); i++) body.addGrowth(Blocks.MOSS_CARPET, 0.45F, rand);
                    around(level, floor, rand, 3 + rand.nextInt(4), Blocks.MOSS_CARPET);
                } else if (biome.is(Biomes.DRIPSTONE_CAVES)) {
                    around(level, floor, rand, 1 + rand.nextInt(2), Blocks.POINTED_DRIPSTONE);
                } else {
                    if (rand.nextFloat() < 0.45F) body.addGrowth(Blocks.COBWEB, 0.9F, rand);
                    if (rand.nextFloat() < 0.35F) {
                        body.addGrowth(Blocks.BROWN_MUSHROOM, 0.5F, rand);
                        around(level, floor, rand, 1 + rand.nextInt(2), Blocks.BROWN_MUSHROOM);
                    }
                }
            }
            case SURFACE -> {
                if (biome.is(Biomes.MUSHROOM_FIELDS)) {
                    for (int i = 0; i < 2 + rand.nextInt(2); i++) growOn(body, rand, Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM);
                    around(level, floor, rand, 2 + rand.nextInt(3), Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM);
                } else if (biome.is(SANDY)) {
                    around(level, floor, rand, rand.nextInt(3), Blocks.DEAD_BUSH);
                    if (rand.nextFloat() < 0.3F) cactus(level, floor, rand);
                } else if (biome.value().coldEnoughToSnow(floor.above())) {
                    for (int i = 0; i < 2; i++) body.addGrowth(Blocks.SNOW, 0.6F, rand);
                    around(level, floor, rand, 3 + rand.nextInt(3), Blocks.SNOW);
                } else if (lush(biome)) {
                    growOn(body, rand, Blocks.BROWN_MUSHROOM, Blocks.MOSS_CARPET);
                    if (rand.nextBoolean()) growOn(body, rand, Blocks.MOSS_CARPET, Blocks.FERN);
                    around(level, floor, rand, 2 + rand.nextInt(3), Blocks.FERN, Blocks.BROWN_MUSHROOM, Blocks.GRASS);
                } else if (!biome.is(BiomeTags.IS_BEACH)) {
                    if (rand.nextBoolean()) body.addGrowth(Blocks.GRASS, 0.55F, rand);
                    around(level, floor, rand, 2 + rand.nextInt(3), Blocks.GRASS, Blocks.GRASS, Blocks.FERN);
                }
            }
        }
    }

    private static void growOn(CorpseEntity body, RandomSource rand, Block... choices) {
        Block b = choices[rand.nextInt(choices.length)];
        body.addGrowth(b, b == Blocks.MOSS_CARPET ? 0.45F : 0.5F + rand.nextFloat() * 0.15F, rand);
    }

    /** Scatters plants on the ground around the body, where they can grow. */
    private static void around(WorldGenLevel level, BlockPos floor, RandomSource rand, int count, Block... choices) {
        for (int i = 0, tries = 0; i < count && tries < count * 6; tries++) {
            BlockPos p = floor.offset(rand.nextInt(5) - 2, 1, rand.nextInt(5) - 2);
            if (p.getX() == floor.getX() && p.getZ() == floor.getZ()) continue;
            p = settle(level, p);
            if (p == null) continue;
            BlockState s = choices[rand.nextInt(choices.length)].defaultBlockState();
            if (!s.canSurvive(level, p)) continue;
            level.setBlock(p, s, 2);
            i++;
        }
    }

    /** The open spot resting on the ground nearest {@code p}, within a block up or down. */
    private static BlockPos settle(WorldGenLevel level, BlockPos p) {
        for (int dy : new int[] {0, 1, -1}) {
            BlockPos q = p.above(dy);
            if (level.getBlockState(q).isAir() && level.getFluidState(q).isEmpty()
                    && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), Direction.UP)) {
                return q;
            }
        }
        return null;
    }

    private static void cactus(WorldGenLevel level, BlockPos floor, RandomSource rand) {
        Direction d = Direction.from2DDataValue(rand.nextInt(4));
        BlockPos p = settle(level, floor.above().relative(d));
        if (p == null) return;
        int height = 1 + rand.nextInt(2);
        for (int i = 0; i < height; i++) {
            BlockPos q = p.above(i);
            if (!level.getBlockState(q).isAir() || !Blocks.CACTUS.defaultBlockState().canSurvive(level, q)) return;
            level.setBlock(q, Blocks.CACTUS.defaultBlockState(), 2);
        }
    }

    private static void placeChest(WorldGenLevel level, BlockPos floor, RandomSource rand) {
        for (int tries = 0; tries < 8; tries++) {
            Direction d = Direction.from2DDataValue(rand.nextInt(4));
            BlockPos p = settle(level, floor.above().relative(d));
            if (p == null) continue;
            BlockState chest = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.from2DDataValue(rand.nextInt(4)));
            level.setBlock(p, chest, 2);
            RandomizableContainerBlockEntity.setLootTable(level, rand, p, CHEST_LOOT);
            return;
        }
    }

    // ---- where --------------------------------------------------------------------------------------

    /** A floor: solid ground with two blocks of open air above it, at or below the given height. */
    private static BlockPos caveSpot(WorldGenLevel level, BlockPos origin, boolean noSky) {
        BlockPos.MutableBlockPos p = origin.mutable();
        for (int i = 0; i < 24; i++) {
            BlockState here = level.getBlockState(p);
            if (here.isFaceSturdy(level, p, Direction.UP) && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()
                    && level.getFluidState(p.above()).isEmpty() && !(noSky && level.canSeeSkyFromBelowWater(p.above()))) {
                return p.immutable();
            }
            p.move(0, -1, 0);
            if (p.getY() <= level.getMinBuildHeight() + 2) return null;
        }
        return null;
    }

    /** Dry open ground on the surface (not in water, not on leaves). */
    private static BlockPos surfaceSpot(WorldGenLevel level, BlockPos origin) {
        BlockPos top = origin.below();
        BlockState state = level.getBlockState(top);
        if (!state.isFaceSturdy(level, top, Direction.UP) || state.is(BlockTags.LEAVES) || state.is(BlockTags.ICE)) return null;
        if (!level.getBlockState(origin).isAir() || !level.getFluidState(origin).isEmpty()) return null;
        return top;
    }

    /** The resource key of a placed feature, for tests. */
    public static ResourceKey<?> key(String name) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.PLACED_FEATURE, new ResourceLocation(CorpseDecay.MODID, name));
    }
}
