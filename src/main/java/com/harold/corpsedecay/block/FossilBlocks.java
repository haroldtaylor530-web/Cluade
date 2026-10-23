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
        STONE("stone", Blocks.STONE), DEEPSLATE("deepslate", Blocks.DEEPSLATE), SANDSTONE("sandstone", Blocks.SANDSTONE),
        RED_SANDSTONE("red_sandstone", Blocks.RED_SANDSTONE), TERRACOTTA("terracotta", Blocks.TERRACOTTA);

        public final String id;
        public final Block rock;

        Host(String id, Block rock) {
            this.id = id;
            this.rock = rock;
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

    public static final RegistryObject<Block> AMBER;
    public static final RegistryObject<Block> AMBER_MOSQUITO;
    public static final RegistryObject<Block> AMBER_BEETLE;
    public static final RegistryObject<Block> PETRIFIED_LOG;
    public static final RegistryObject<Block> PETRIFIED_WOOD;
    public static final RegistryObject<Block> FOSSIL_EGG;
    public static final RegistryObject<Item> FLINT_HAND_AXE;
    public static final RegistryObject<Item> BONE_FLUTE;
    public static final RegistryObject<Item> OCHRE;
    private static final List<RegistryObject<Item>> LOOSE_ITEMS = new ArrayList<>();

    static {
        for (Object[] p : FossilShapes.PIECES) {
            KIND.put((String) p[0], (Integer) p[1]);
            POINTS.put((String) p[0], (Boolean) p[2]);
        }
        for (Host host : Host.values()) {
            Map<String, RegistryObject<Block>> m = new LinkedHashMap<>();
            for (String piece : KIND.keySet()) {
                String name = host.id + "_" + piece + "_fossil";
                int kind = KIND.get(piece);
                boolean points = POINTS.get(piece);
                RegistryObject<Block> b = BLOCKS.register(name, () -> {
                    BlockBehaviour.Properties p = BlockBehaviour.Properties.copy(host.rock);
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
        if (rock.is(Blocks.DEEPSLATE) || rock.is(Blocks.TUFF)) return Host.DEEPSLATE;
        if (rock.is(Blocks.STONE) || rock.is(Blocks.ANDESITE) || rock.is(Blocks.DIORITE) || rock.is(Blocks.GRANITE)) return Host.STONE;
        if (rock.is(Blocks.SANDSTONE) || rock.is(Blocks.SAND)) return Host.SANDSTONE;
        if (rock.is(Blocks.RED_SANDSTONE) || rock.is(Blocks.RED_SAND)) return Host.RED_SANDSTONE;
        if (rock.is(BlockTags.TERRACOTTA)) return Host.TERRACOTTA;
        return null;
    }

    public static BlockState state(Host host, String piece, Direction pointing) {
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
