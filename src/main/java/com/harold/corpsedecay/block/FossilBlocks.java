package com.harold.corpsedecay.block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.harold.corpsedecay.CorpseDecay;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Fossils in the rock: bones pressed into stone, deepslate, sandstone, red sandstone and
 * terracotta. Skulls of many kinds, necks, backbones, ribcages, back plates, shells, tails, wings,
 * tusks, legs, feet, claws and flippers build whole skeletons in the ground; shells, sea creatures,
 * insects, leaves and feathers turn up on their own like ores; footprints run in trails. Then
 * amber (sometimes with something caught in it), petrified wood and fossil eggs. Mining any of them
 * gives the block itself, to build a display with. The list of pieces comes from
 * {@link FossilShapes}, which tools/gen_fossils.py writes along with the textures.
 */
public final class FossilBlocks {
    public enum Host {
        STONE("stone", () -> Blocks.STONE), DEEPSLATE("deepslate", () -> Blocks.DEEPSLATE), SANDSTONE("sandstone", () -> Blocks.SANDSTONE),
        RED_SANDSTONE("red_sandstone", () -> Blocks.RED_SANDSTONE), SHALE("shale", () -> FossilBlocks.SHALE.get()),
        LIMESTONE("limestone", () -> FossilBlocks.LIMESTONE.get()), CHALK("chalk", () -> FossilBlocks.CHALK.get()),
        PERMAFROST("permafrost", () -> FossilBlocks.PERMAFROST.get()), TERRACOTTA("terracotta", () -> Blocks.TERRACOTTA);

        public final String id;
        private final java.util.function.Supplier<Block> rock;

        Host(String id, java.util.function.Supplier<Block> rock) {
            this.id = id;
            this.rock = rock;
        }

        /** The plain rock these fossils are set in. */
        public Block rock() {
            return rock.get();
        }
    }

    public static final int BONE = 0, SMALL = 1, PRINT = 2, CAMBRIAN = 3, PAINTING = 4;

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CorpseDecay.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CorpseDecay.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CorpseDecay.MODID);

    private static final Map<Host, Map<String, RegistryObject<Block>>> ALL = new EnumMap<>(Host.class);
    private static final Map<String, Integer> KIND = new LinkedHashMap<>();
    private static final Map<String, Boolean> POINTS = new LinkedHashMap<>();
    private static final List<RegistryObject<Block>> EXTRAS = new ArrayList<>();

    public static final RegistryObject<Block> SHALE;
    public static final RegistryObject<Block> LIMESTONE;
    public static final RegistryObject<Block> CHALK;
    public static final RegistryObject<Block> CHALK_FLINT;
    public static final RegistryObject<Block> COAL_SEAM;
    public static final RegistryObject<Block> LIGNITE;
    public static final RegistryObject<Block> PERMAFROST;
    public static final RegistryObject<Block> TAR;
    private static final List<RegistryObject<Block>> ROCKS = new ArrayList<>();
    public static final RegistryObject<Block> AMBER;
    public static final RegistryObject<Block> AMBER_MOSQUITO;
    public static final RegistryObject<Block> AMBER_BEETLE;
    public static final RegistryObject<Block> PETRIFIED_LOG;
    public static final RegistryObject<Block> PETRIFIED_WOOD;
    public static final RegistryObject<Block> FOSSIL_EGG;
    /** bone turned to stone, as the old whales in the desert are */
    public static final RegistryObject<Block> FOSSIL_BONE;
    public static final RegistryObject<Item> FLINT_HAND_AXE;
    public static final RegistryObject<Item> BONE_FLUTE;
    public static final RegistryObject<Item> OCHRE;
    private static final List<RegistryObject<Item>> LOOSE_ITEMS = new ArrayList<>();

    static {
        for (Object[] p : FossilShapes.PIECES) {
            KIND.put((String) p[0], (Integer) p[1]);
            POINTS.put((String) p[0], (Boolean) p[2]);
        }
        // the rocks of the formations, registered before the fossils set in them
        SHALE = rock("shale", () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE).mapColor(MapColor.COLOR_GRAY)));
        LIMESTONE = rock("limestone", () -> new Block(BlockBehaviour.Properties.copy(Blocks.STONE).mapColor(MapColor.SAND)));
        CHALK = rock("chalk", () -> new Block(BlockBehaviour.Properties.copy(Blocks.CALCITE).mapColor(MapColor.QUARTZ)));
        CHALK_FLINT = rock("chalk_flint", () -> new Block(BlockBehaviour.Properties.copy(Blocks.CALCITE).mapColor(MapColor.QUARTZ)));
        COAL_SEAM = rock("coal_seam", () -> new net.minecraft.world.level.block.DropExperienceBlock(
                BlockBehaviour.Properties.copy(Blocks.COAL_ORE).mapColor(MapColor.COLOR_BLACK), net.minecraft.util.valueproviders.UniformInt.of(0, 2)));
        LIGNITE = rock("lignite", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.0F, 3F)
                .sound(SoundType.TUFF)));
        PERMAFROST = rock("permafrost", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(1.2F, 3F)
                .sound(SoundType.GRAVEL).friction(0.8F)));
        TAR = rock("tar", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.6F)
                .sound(SoundType.HONEY_BLOCK).speedFactor(0.35F).jumpFactor(0.4F)));
        Map<String, java.util.Set<String>> only = new java.util.HashMap<>();
        for (String[] hp : FossilShapes.HOST_PIECES) {
            only.put(hp[0], new java.util.HashSet<>(java.util.Arrays.asList(hp).subList(1, hp.length)));
        }
        for (Host host : Host.values()) {
            Map<String, RegistryObject<Block>> m = new LinkedHashMap<>();
            for (String piece : KIND.keySet()) {
                if (only.containsKey(host.id) && !only.get(host.id).contains(piece)) continue;
                String name = host.id + "_" + piece + "_fossil";
                int kind = KIND.get(piece);
                boolean points = POINTS.get(piece);
                RegistryObject<Block> b = BLOCKS.register(name, () -> {
                    BlockBehaviour.Properties p = BlockBehaviour.Properties.copy(host.rock());
                    return kind == PAINTING ? new Painting(p) : kind == PRINT ? new Print(p) : points ? new Pointing(p) : new Block(p);
                });
                ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
                m.put(piece, b);
            }
            ALL.put(host, m);
        }
        AMBER = extra("amber", () -> new Block(amberProps()));
        AMBER_MOSQUITO = extra("amber_mosquito", () -> new Block(amberProps()));
        AMBER_BEETLE = extra("amber_beetle", () -> new Block(amberProps()));
        PETRIFIED_LOG = extra("petrified_log", () -> new RotatedPillarBlock(petrifiedProps()));
        PETRIFIED_WOOD = extra("petrified_wood", () -> new Block(petrifiedProps()));
        FOSSIL_BONE = extra("fossil_bone", () -> new RotatedPillarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                .strength(2.5F, 6F).sound(SoundType.BONE_BLOCK).requiresCorrectToolForDrops()));
        FOSSIL_EGG = extra("fossil_egg", () -> new FossilEggBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                .strength(1.5F, 6F).sound(SoundType.BONE_BLOCK).noOcclusion()));
        FLINT_HAND_AXE = item("flint_hand_axe", () -> new net.minecraft.world.item.AxeItem(net.minecraft.world.item.Tiers.STONE, 6F, -3.2F,
                new Item.Properties()));
        BONE_FLUTE = item("bone_flute", () -> new BoneFluteItem(new Item.Properties().stacksTo(1)));
        OCHRE = item("ochre", () -> new Item(new Item.Properties()));
        TABS.register("fossils", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + CorpseDecay.MODID + ".fossils"))
                .icon(() -> new ItemStack(get(Host.STONE, "horn_skull")))
                .displayItems((params, out) -> {
                    for (RegistryObject<Block> b : ROCKS) out.accept(b.get());
                    for (Host h : Host.values()) {
                        for (RegistryObject<Block> b : ALL.get(h).values()) out.accept(b.get());
                    }
                    for (RegistryObject<Block> b : EXTRAS) out.accept(b.get());
                    for (RegistryObject<Item> i : LOOSE_ITEMS) out.accept(i.get());
                })
                .build());
    }

    private static BlockBehaviour.Properties amberProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.2F, 3F).sound(SoundType.CALCITE);
    }

    private static BlockBehaviour.Properties petrifiedProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_BROWN).strength(2.5F, 6F).sound(SoundType.STONE)
                .requiresCorrectToolForDrops();
    }

    private static RegistryObject<Block> rock(String name, java.util.function.Supplier<Block> make) {
        RegistryObject<Block> b = BLOCKS.register(name, make);
        if (name.equals("lignite")) {
            ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()) {
                @Override
                public int getBurnTime(ItemStack stack, net.minecraft.world.item.crafting.RecipeType<?> type) {
                    return 4000; // brown coal: burns a while, less than a block of coal
                }
            });
        } else {
            ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
        }
        ROCKS.add(b);
        return b;
    }

    private static RegistryObject<Block> extra(String name, java.util.function.Supplier<Block> make) {
        RegistryObject<Block> b = BLOCKS.register(name, make);
        ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
        EXTRAS.add(b);
        return b;
    }

    private static RegistryObject<Item> item(String name, java.util.function.Supplier<Item> make) {
        RegistryObject<Item> i = ITEMS.register(name, make);
        LOOSE_ITEMS.add(i);
        return i;
    }

    private FossilBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }

    /** Every piece id, in order. */
    public static List<String> pieces() {
        return new ArrayList<>(KIND.keySet());
    }

    /** Piece ids of one kind: {@link #BONE}, {@link #SMALL} or {@link #PRINT}. */
    public static List<String> pieces(int kind) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : KIND.entrySet()) if (e.getValue() == kind) out.add(e.getKey());
        return out;
    }

    public static Block get(Host host, String piece) {
        return ALL.get(host).get(piece).get();
    }

    /** Puts the fossil for this piece in this rock at {@code pos}; false (and nothing placed) if it doesn't come in that rock. */
    public static boolean place(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, Host host, String piece, Direction d) {
        BlockState s = state(host, piece, d);
        if (s == null) return false;
        level.setBlock(pos, s, 2);
        return true;
    }

    /** Whether fossils of this piece come in this rock. */
    public static boolean has(Host host, String piece) {
        return ALL.get(host).containsKey(piece);
    }

    /** All the fossil blocks for one rock. */
    public static List<Block> all(Host host) {
        List<Block> out = new ArrayList<>();
        for (RegistryObject<Block> b : ALL.get(host).values()) out.add(b.get());
        return out;
    }

    public static List<Block> extras() {
        List<Block> out = new ArrayList<>();
        for (RegistryObject<Block> b : EXTRAS) out.add(b.get());
        return out;
    }

    /** The rock a fossil would take on in place of this block, or null if fossils don't go in it. */
    public static Host hostFor(BlockState rock) {
        if (rock.is(Blocks.DEEPSLATE)) return Host.DEEPSLATE;
        if (rock.is(Blocks.STONE) || rock.is(Blocks.ANDESITE) || rock.is(Blocks.DIORITE) || rock.is(Blocks.GRANITE)) return Host.STONE;
        if (rock.is(Blocks.SANDSTONE) || rock.is(Blocks.SAND)) return Host.SANDSTONE;
        if (rock.is(Blocks.RED_SANDSTONE) || rock.is(Blocks.RED_SAND)) return Host.RED_SANDSTONE;
        if (rock.is(Blocks.TERRACOTTA)) return Host.TERRACOTTA;
        if (SHALE.isPresent() && rock.is(SHALE.get())) return Host.SHALE;
        if (LIMESTONE.isPresent() && rock.is(LIMESTONE.get())) return Host.LIMESTONE;
        if (CHALK.isPresent() && rock.is(CHALK.get())) return Host.CHALK;
        if (PERMAFROST.isPresent() && rock.is(PERMAFROST.get())) return Host.PERMAFROST;
        return null;
    }

    /** The fossil block for this piece in this rock, pointing that way; null if it doesn't come in that rock. */
    public static BlockState state(Host host, String piece, Direction pointing) {
        if (!has(host, piece)) return null;
        BlockState s = get(host, piece).defaultBlockState();
        return s.hasProperty(Pointing.FACING) ? s.setValue(Pointing.FACING, pointing) : s;
    }

    /** A bone that points along the skeleton (the way the head is). */
    public static class Pointing extends HorizontalDirectionalBlock {
        public Pointing(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            // placed side-on to whoever places it, so they see the bones in profile
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getClockWise());
        }
    }

    /** A painting on the face of the rock, facing whoever put it there. */
    public static class Painting extends Pointing {
        public Painting(Properties p) {
            super(p);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }
    }

    /** A footprint in the top of the rock; the toes point the way it was walking. */
    public static class Print extends Pointing {
        public Print(Properties p) {
            super(p);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
        }
    }
}
