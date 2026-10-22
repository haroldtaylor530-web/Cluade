package com.harold.corpsedecay.block;

import java.util.EnumMap;
import java.util.Map;

import com.harold.corpsedecay.CorpseDecay;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Fossils in the rock: bones pressed into stone, deepslate and sandstone. Skull, spine, ribs, tail,
 * leg bones and feet build whole skeletons in the ground; ammonites, trilobites, fish and ferns turn
 * up on their own like ores. Mining one gives the block itself, to build a display with.
 */
public final class FossilBlocks {
    public enum Host {
        STONE("stone", Blocks.STONE), DEEPSLATE("deepslate", Blocks.DEEPSLATE), SANDSTONE("sandstone", Blocks.SANDSTONE);

        public final String id;
        public final Block rock;

        Host(String id, Block rock) {
            this.id = id;
            this.rock = rock;
        }
    }

    public enum Piece {
        SKULL(true), SPINE(true), RIBS(true), TAIL(true), LIMB(false), FOOT(false),
        AMMONITE(false), TRILOBITE(false), FISH(false), FERN(false);

        /** pieces of a skeleton that point along the body */
        public final boolean directional;

        Piece(boolean directional) {
            this.directional = directional;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CorpseDecay.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CorpseDecay.MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CorpseDecay.MODID);

    private static final Map<Host, Map<Piece, RegistryObject<Block>>> ALL = new EnumMap<>(Host.class);

    static {
        for (Host host : Host.values()) {
            Map<Piece, RegistryObject<Block>> m = new EnumMap<>(Piece.class);
            for (Piece piece : Piece.values()) {
                String name = host.id + "_" + piece.id() + "_fossil";
                RegistryObject<Block> b = BLOCKS.register(name, () -> {
                    BlockBehaviour.Properties p = BlockBehaviour.Properties.copy(host.rock);
                    return piece.directional ? new Pointing(p) : new Block(p);
                });
                ITEMS.register(name, () -> new BlockItem(b.get(), new Item.Properties()));
                m.put(piece, b);
            }
            ALL.put(host, m);
        }
        TABS.register("fossils", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + CorpseDecay.MODID + ".fossils"))
                .icon(() -> new ItemStack(get(Host.STONE, Piece.SKULL)))
                .displayItems((params, out) -> {
                    for (Host h : Host.values()) {
                        for (Piece p : Piece.values()) out.accept(get(h, p));
                    }
                })
                .build());
    }

    private FossilBlocks() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TABS.register(bus);
    }

    public static Block get(Host host, Piece piece) {
        return ALL.get(host).get(piece).get();
    }

    /** The fossil block of this piece for the rock it replaces, or null if it can't go there. */
    public static Host hostFor(BlockState rock) {
        if (rock.is(Blocks.DEEPSLATE) || rock.is(Blocks.TUFF)) return Host.DEEPSLATE;
        if (rock.is(Blocks.STONE) || rock.is(Blocks.ANDESITE) || rock.is(Blocks.DIORITE) || rock.is(Blocks.GRANITE)) return Host.STONE;
        if (rock.is(Blocks.SANDSTONE) || rock.is(Blocks.SAND)) return Host.SANDSTONE;
        return null;
    }

    public static BlockState state(Host host, Piece piece, Direction pointing) {
        BlockState s = get(host, piece).defaultBlockState();
        return piece.directional ? s.setValue(Pointing.FACING, pointing) : s;
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
}
