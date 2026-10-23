package com.harold.corpsedecay.world;

import java.util.HashSet;
import java.util.Set;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.block.FossilBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluids;

/**
 * Whale skeletons, full size.
 * <p>
 * Under the sea, a whale fall: somewhere out in the ocean whales have gone down to die in the same
 * stretch of sea floor, and their skeletons lie there together (as whale remains entities, drawn
 * bone by bone): the long spine raised on arching ribs, the great flat skull, the jaw bones fallen
 * to either side, the flippers splayed. Life gathers on them: glowing sea pickles, red plumes of
 * bone worms, white mats spreading over the mud.
 * <p>
 * In the desert, the valley of whales: long, snake-like early whales, turned to stone and half buried
 * in the sand where an ancient sea once was, with their tiny hind legs still there, among
 * wind-carved pillars of sandstone.
 */
public class WhaleFeature extends Feature<NoneFeatureConfiguration> {
    public enum Where { SEA, DESERT }

    /** Whale falls come in gatherings: the sea floor is split into cells this many chunks across ... */
    public static final int CELL = 5;
    /** ... and this many cells in a thousand hold one */
    public static final int CELLS_PER_THOUSAND = 30;
    /** each chunk of a gathering's cell has a whale in it this often */
    public static final float WHALE_CHANCE = 0.3F;

    public enum Kind { BLUE, HUMPBACK, SPERM, BEAKED, BASILOSAURUS }

    private final Where where;

    public WhaleFeature(Where where) {
        super(NoneFeatureConfiguration.CODEC);
        this.where = where;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rand = ctx.random();
        BlockPos o = ctx.origin();
        // build from the middle of the chunk so a whole whale fits in the area a feature may touch
        int x = (o.getX() & ~15) + 8, z = (o.getZ() & ~15) + 8;
        if (where == Where.SEA) {
            if (!Config.REMAINS.get()) return false;
            if (!graveyard(level.getSeed(), o.getX() >> 4, o.getZ() >> 4) || rand.nextFloat() >= WHALE_CHANCE) return false;
            BlockPos floor = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1, z);
            if (!level.getFluidState(floor.above(6)).is(Fluids.WATER)) return false; // too shallow
            float r = rand.nextFloat();
            Kind k = r < 0.25F ? Kind.SPERM : r < 0.4F ? Kind.BEAKED : r < 0.65F ? Kind.HUMPBACK : Kind.BLUE;
            return seaWhale(level, floor, rand, k, Direction.from2DDataValue(rand.nextInt(4))) != null;
        }
        if (!Config.FOSSILS.get()) return false;
        return valley(level, new BlockPos(x, o.getY(), z), rand) > 0;
    }

    /** Whether a whale gathering lies in the cell holding this chunk. */
    public static boolean graveyard(long seed, int chunkX, int chunkZ) {
        long cx = Math.floorDiv(chunkX, CELL), cz = Math.floorDiv(chunkZ, CELL);
        long h = seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL) ^ 0x5DEECE66DL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return Math.floorMod(h, 1000) < CELLS_PER_THOUSAND;
    }

    // ---- building -----------------------------------------------------------------------------------

    /**
     * Lays bones out in a whale's own frame: {@code u} along the body (head towards +u), {@code v}
     * across it, {@code y} up from the first block above the floor.
     */
    static final class Builder {
        final WorldGenLevel level;
        final BlockPos base;
        final Direction along, across;
        final Block bone;
        final boolean sea;
        final RandomSource rand;
        final Set<BlockPos> placed = new HashSet<>();

        Builder(WorldGenLevel level, BlockPos base, Direction along, Block bone, boolean sea, RandomSource rand) {
            this.level = level;
            this.base = base;
            this.along = along;
            this.across = along.getClockWise();
            this.bone = bone;
            this.sea = sea;
            this.rand = rand;
        }

        BlockPos at(int u, int v, int y) {
            return base.relative(along, u).relative(across, v).above(y);
        }

        /** {@code axis}: 'u' along the body, 'v' across it, 'y' up. */
        boolean put(int u, int v, int y, char axis) {
            BlockPos p = at(u, v, y);
            BlockState s = level.getBlockState(p);
            if (!fits(s)) return false;
            Direction.Axis a = axis == 'u' ? along.getAxis() : axis == 'v' ? across.getAxis() : Direction.Axis.Y;
            level.setBlock(p, bone.defaultBlockState().setValue(RotatedPillarBlock.AXIS, a), 2);
            placed.add(p);
            return true;
        }

        /** Water, air, plants and loose sediment give way to bone; rock doesn't. */
        boolean fits(BlockState s) {
            return s.isAir() || s.is(Blocks.WATER) || s.canBeReplaced() || s.is(Blocks.KELP) || s.is(Blocks.KELP_PLANT)
                    || s.is(Blocks.SEAGRASS) || s.is(Blocks.TALL_SEAGRASS) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY)
                    || s.is(BlockTags.DIRT) || s.is(Blocks.SANDSTONE) || s.is(Blocks.CACTUS) || s.is(Blocks.DEAD_BUSH);
        }

        /** A rib from beside the spine down to the floor, arching out {@code w} blocks, at height {@code h}. */
        void rib(int u, int side, int w, int h) {
            java.util.List<int[]> pts = new java.util.ArrayList<>();
            for (int k = 0; k <= 12; k++) {
                double t = k / 12.0 * Math.PI / 2;
                int[] p = {(int) Math.round(1 + (w - 1) * Math.sin(t)), (int) Math.round(h * Math.cos(t))};
                if (pts.isEmpty() || pts.get(pts.size() - 1)[0] != p[0] || pts.get(pts.size() - 1)[1] != p[1]) pts.add(p);
            }
            for (int i = 0; i < pts.size(); i++) {
                int[] p = pts.get(i);
                boolean flat = (i > 0 && pts.get(i - 1)[1] == p[1]) || (i < pts.size() - 1 && pts.get(i + 1)[1] == p[1]);
                put(u, side * p[0], p[1], flat ? 'v' : 'y');
            }
        }
    }

    /**
     * One whale's skeleton lying on the sea floor (the top of {@code floor} is the mud it lies on):
     * a whale remains entity, with sea pickles glowing and white mats spreading on the mud round it.
     * Returns it, or null.
     */
    public static com.harold.corpsedecay.corpse.WhaleRemainsEntity seaWhale(WorldGenLevel level, BlockPos floor, RandomSource rand, Kind kind, Direction facing) {
        return seaWhale(level, floor, rand, kind, facing, -1F);
    }

    /**
     * As above, with {@code flesh} (0..1) of the whale still on its bones; below 0, whatever stage
     * the whale has got to: still with much of its flesh (scavengers' time), scraps of it left
     * (bacteria and bone worms' time), or bare bones gone pale and furred with bacteria.
     */
    public static com.harold.corpsedecay.corpse.WhaleRemainsEntity seaWhale(WorldGenLevel level, BlockPos floor, RandomSource rand, Kind kind,
                                                                         Direction facing, float flesh) {
        float whole;
        if (flesh < 0) {
            float r = rand.nextFloat();
            flesh = r < 0.3F ? 0.5F + rand.nextFloat() * 0.35F : r < 0.7F ? 0.1F + rand.nextFloat() * 0.25F : 0F;
        }
        whole = flesh > 0.45F ? 0.92F + rand.nextFloat() * 0.08F : flesh > 0 ? 0.8F + rand.nextFloat() * 0.15F : 0.65F + rand.nextFloat() * 0.2F;
        var whale = com.harold.corpsedecay.ModRegistry.WHALE.get().create(level.getLevel());
        if (whale == null) return null;
        float length = switch (kind) {
            case SPERM -> 17 + rand.nextFloat() * 5;
            case HUMPBACK -> 19 + rand.nextFloat() * 5;
            case BEAKED -> 8 + rand.nextFloat() * 3;
            default -> 23 + rand.nextFloat() * 7;
        };
        int k = kind == Kind.SPERM ? com.harold.corpsedecay.corpse.WhaleRemainsEntity.SPERM
                : kind == Kind.HUMPBACK ? com.harold.corpsedecay.corpse.WhaleRemainsEntity.HUMPBACK
                : kind == Kind.BEAKED ? com.harold.corpsedecay.corpse.WhaleRemainsEntity.BEAKED : com.harold.corpsedecay.corpse.WhaleRemainsEntity.BLUE;
        float angle = (float) Math.atan2(facing.getStepZ(), facing.getStepX()) + (rand.nextFloat() - 0.5F) * 0.6F;
        whale.setPos(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5);
        whale.setup(k, length, angle, rand.nextInt(), whole, flesh);
        if (!level.addFreshEntity(whale)) return null;
        // the mud round it: white and rust-orange mats of bacteria feeding off it, and sea pickles lighting it up
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        int pickles = 0;
        for (int i = 0; i < 60; i++) {
            float u = (rand.nextFloat() - 0.5F) * length, v = (rand.nextFloat() - 0.5F) * length * 0.3F;
            int x = floor.getX() + Math.round(u * c - v * s), z = floor.getZ() + Math.round(u * s + v * c);
            BlockPos top = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1, z);
            if (Math.abs(top.getY() - floor.getY()) > 3) continue;
            BlockState g = level.getBlockState(top);
            if (g.is(BlockTags.SAND) || g.is(Blocks.GRAVEL) || g.is(Blocks.CLAY) || g.is(BlockTags.DIRT)) {
                float m = rand.nextFloat();
                level.setBlock(top, (m < 0.45F ? Blocks.CALCITE : m < 0.75F ? Blocks.TERRACOTTA : Blocks.BROWN_TERRACOTTA).defaultBlockState(), 2);
            }
            if (pickles < 6 && i % 5 == 0 && level.getBlockState(top.above()).is(Blocks.WATER)) {
                level.setBlock(top.above(), Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + rand.nextInt(4))
                        .setValue(SeaPickleBlock.WATERLOGGED, true), 2);
                pickles++;
            }
        }
        return whale;
    }

    // ---- the valley of whales -------------------------------------------------------------------------

    /**
     * A stretch of desert with one to three stone whales half buried in the sand at {@code centre}
     * (any height: each whale finds the sand), and wind-carved pillars of sandstone about them.
     */
    public static int valley(WorldGenLevel level, BlockPos centre, RandomSource rand) {
        int whales = 1 + rand.nextInt(3), placed = 0;
        for (int i = 0; i < whales; i++) {
            int x = centre.getX() + rand.nextInt(13) - 6, z = centre.getZ() + rand.nextInt(13) - 6;
            BlockPos top = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
            if (!level.getBlockState(top).is(BlockTags.SAND)) continue;
            placed += desertWhale(level, top, rand, Direction.from2DDataValue(rand.nextInt(4)));
        }
        if (placed == 0) return 0;
        for (int i = 0, n = 1 + rand.nextInt(3); i < n; i++) {
            int x = centre.getX() + rand.nextInt(31) - 15, z = centre.getZ() + rand.nextInt(31) - 15;
            BlockPos top = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1, z);
            if (level.getBlockState(top).is(BlockTags.SAND)) pillar(level, top, rand);
        }
        return placed;
    }

    /**
     * A basilosaurus: a long thin early whale, turned to stone, lying in the sand with its top at
     * {@code top}; the bones at the surface, some sticking up, some still under the sand, with sea
     * shells and shark teeth weathering out of the sandstone beneath. Returns how many bones.
     */
    public static int desertWhale(WorldGenLevel level, BlockPos top, RandomSource rand, Direction facing) {
        Builder b = new Builder(level, top, facing, FossilBlocks.FOSSIL_BONE.get(), false, rand);
        int length = 14 + rand.nextInt(9);
        int tail = -length / 2, snout = length / 2, skullLen = 3, skull = snout - skullLen;
        double phase = rand.nextDouble() * Math.PI * 2;
        int prevV = 0;
        for (int u = tail; u < skull; u++) {
            // lying in a gentle S, the way it settled
            int v = (int) Math.round(1.6 * Math.sin(u * 0.32 + phase));
            if (rand.nextFloat() < 0.15F) {
                prevV = v;
                continue; // still under the sand
            }
            boolean up = rand.nextFloat() < 0.3F;
            b.put(u, v, up ? 1 : 0, 'u');
            if (v != prevV) b.put(u, prevV, 0, 'u');
            prevV = v;
            // short ribs along the chest, curving out of the sand
            if (u > skull - Math.round(length * 0.4F) && (u & 1) == 0) {
                for (int side : new int[] {-1, 1}) {
                    if (rand.nextFloat() < 0.25F) continue;
                    b.put(u, v + side, 1, 'v');
                    b.put(u, v + side * 2, 0, 'v');
                }
            }
            // its tiny hind legs
            if (u == tail + Math.round(length * 0.32F)) {
                b.put(u, v + 1, 0, 'v');
                b.put(u, v - 1, 0, 'v');
                b.put(u - 1, v + 2, 0, 'u');
            }
            // front flippers
            if (u == skull - 2) {
                b.put(u, v + 2, 0, 'v');
                b.put(u + 1, v + 3, 0, 'v');
                b.put(u, v - 2, 0, 'v');
                b.put(u + 1, v - 3, 0, 'v');
            }
        }
        // the skull with its long toothed jaws
        int v0 = prevV;
        for (int u = skull; u <= snout; u++) {
            b.put(u, v0, u == skull ? 1 : 0, 'u');
            if (u < snout) {
                b.put(u, v0 - 1, 0, 'u');
                b.put(u, v0 + 1, 0, 'u');
            }
        }
        // sand drifted over some of it, and small fossils in the sandstone under it
        for (BlockPos p : new java.util.ArrayList<>(b.placed)) {
            if (rand.nextFloat() < 0.12F && level.getBlockState(p.above()).isAir() && !b.placed.contains(p.above())) {
                level.setBlock(p.above(), Blocks.SAND.defaultBlockState(), 2);
            }
            BlockPos under = p.below(1 + rand.nextInt(2));
            if (rand.nextFloat() < 0.1F && FossilBlocks.hostFor(level.getBlockState(under)) == FossilBlocks.Host.SANDSTONE) {
                String[] small = {"shark_tooth", "urchin", "scallop", "snail", "fish"};
                FossilBlocks.place(level, under, FossilBlocks.Host.SANDSTONE, small[rand.nextInt(small.length)],
                        Direction.from2DDataValue(rand.nextInt(4)));
            }
        }
        return b.placed.size();
    }

    /** A pillar of sandstone carved by the wind: a narrow neck under a wider cap. */
    static void pillar(WorldGenLevel level, BlockPos top, RandomSource rand) {
        int height = 3 + rand.nextInt(3);
        boolean thick = rand.nextBoolean();
        for (int y = 1; y <= height; y++) {
            for (int dx = 0; dx <= (thick ? 1 : 0); dx++) {
                for (int dz = 0; dz <= (thick ? 1 : 0); dz++) {
                    level.setBlock(top.offset(dx, y, dz), (y == 1 ? Blocks.SANDSTONE : Blocks.SMOOTH_SANDSTONE).defaultBlockState(), 2);
                }
            }
        }
        int cap = top.getY() + height + 1;
        for (int dx = -1; dx <= (thick ? 2 : 1); dx++) {
            for (int dz = -1; dz <= (thick ? 2 : 1); dz++) {
                boolean corner = (dx == -1 || dx == (thick ? 2 : 1)) && (dz == -1 || dz == (thick ? 2 : 1));
                if (corner && rand.nextFloat() < 0.7F) continue;
                level.setBlock(new BlockPos(top.getX() + dx, cap, top.getZ() + dz), Blocks.SANDSTONE.defaultBlockState(), 2);
                if (!corner && rand.nextFloat() < 0.4F) {
                    level.setBlock(new BlockPos(top.getX() + dx, cap + 1, top.getZ() + dz), Blocks.SANDSTONE_SLAB.defaultBlockState(), 2);
                }
            }
        }
    }
}
