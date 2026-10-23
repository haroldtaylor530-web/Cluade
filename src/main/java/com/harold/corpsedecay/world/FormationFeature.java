package com.harold.corpsedecay.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.block.FossilBlocks;
import com.harold.corpsedecay.block.FossilBlocks.Host;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A rock formation: a great lens of layered rock (strata, sometimes steeply tilted) laid into the
 * ground, each one modelled on a real fossil site and holding the fossils of its time. Where it
 * meets a cave, a cliff or a hillside its layers show. Each formation only turns up where it makes
 * sense: Burgess Shale in the mountains, chalk and the Blue Lias on the coasts, coal measures under
 * the swamps and forests, red beds in the badlands, the Flaming Cliffs under the desert, permafrost
 * in the snow.
 */
public class FormationFeature extends Feature<NoneFeatureConfiguration> {
    record Layer(Supplier<BlockState> block, int thickness) {
    }

    private static Layer l(Supplier<net.minecraft.world.level.block.Block> b, int t) {
        return new Layer(() -> b.get().defaultBlockState(), t);
    }

    public enum Formation {
        /** Cambrian, half a billion years ago: dark shale full of strange sea creatures, tilted up in the mountains. */
        BURGESS_SHALE(new Layer[] {l(FossilBlocks.SHALE, 3), l(FossilBlocks.LIMESTONE, 1), l(FossilBlocks.SHALE, 2), l(() -> Blocks.CALCITE, 1)},
                0.3F, 0.7F, new String[] {}, 0, 0,
                new String[] {"opabinia", "hallucigenia", "wiwaxia", "marrella", "pikaia", "jellyfish", "archaeocyath", "burrows",
                        "trilobite", "trilobite", "trilobite"}, 0.12F),
        /** Devonian: a reef of limestone, crowded with sea lilies, shells and trilobites. */
        REEF_LIMESTONE(new Layer[] {l(FossilBlocks.LIMESTONE, 4), l(() -> Blocks.CALCITE, 1), l(FossilBlocks.LIMESTONE, 2)},
                0F, 0.1F, new String[] {}, 0, 0,
                new String[] {"crinoid", "crinoid", "crinoid", "trilobite", "trilobite", "scallop", "scallop", "snail", "fish", "shark_tooth",
                        "archaeocyath", "ammonite", "starfish"}, 0.14F),
        /** Carboniferous: cycles of sandstone, shale and coal, with the plants and giant insects of the coal swamps. */
        COAL_MEASURES(new Layer[] {l(() -> Blocks.SANDSTONE, 2), l(FossilBlocks.SHALE, 2), l(FossilBlocks.COAL_SEAM, 1), l(FossilBlocks.SHALE, 2)},
                0F, 0.12F, new String[] {"giant_lizard"}, 0, 1,
                new String[] {"fern", "fern", "fern", "leaf", "leaf", "dragonfly", "dragonfly", "snail", "fish", "shark_tooth"}, 0.08F),
        /** Triassic and Jurassic: banded red sandstone and mudstone, where long necks, plated backs and their hunters lie. */
        RED_BEDS(new Layer[] {l(() -> Blocks.TERRACOTTA, 2), l(() -> Blocks.RED_SANDSTONE, 2), l(() -> Blocks.RED_TERRACOTTA, 1),
                l(() -> Blocks.TERRACOTTA, 2), l(() -> Blocks.LIGHT_GRAY_TERRACOTTA, 1), l(() -> Blocks.RED_SANDSTONE, 1),
                l(() -> Blocks.PURPLE_TERRACOTTA, 1)},
                0F, 0.08F, new String[] {"sauropod", "sauropod", "plated", "sail_back", "tyrant", "small_hunter", "giant_lizard", "turtle"}, 1, 3,
                new String[] {"fern", "leaf", "snail", "fish"}, 0.04F),
        /** Jurassic seas: grey limestone and shale, full of ammonites, with fish lizards and sea serpents. */
        BLUE_LIAS(new Layer[] {l(FossilBlocks.LIMESTONE, 1), l(FossilBlocks.SHALE, 2)},
                0F, 0.15F, new String[] {"fish_lizard", "fish_lizard", "sea_serpent"}, 1, 2,
                new String[] {"ammonite", "ammonite", "ammonite", "ammonite", "crinoid", "fish", "fish", "scallop"}, 0.12F),
        /** Jurassic lagoon: fine limestone slabs keeping the most delicate fossils, fliers, feathers, insects. */
        SOLNHOFEN(new Layer[] {l(FossilBlocks.LIMESTONE, 2), l(() -> Blocks.CALCITE, 1)},
                0F, 0.06F, new String[] {"flier", "flier", "small_hunter"}, 1, 2,
                new String[] {"feather", "feather", "dragonfly", "dragonfly", "crab", "jellyfish", "fish", "starfish", "ammonite"}, 0.1F),
        /** Cretaceous seas: white chalk with bands of flint, sea urchins and shark teeth, sea reptiles and turtles. */
        CHALK(new Layer[] {l(FossilBlocks.CHALK, 4), l(FossilBlocks.CHALK_FLINT, 1)},
                0F, 0.05F, new String[] {"fish_lizard", "sea_serpent", "turtle", "flier"}, 1, 2,
                new String[] {"urchin", "urchin", "urchin", "shark_tooth", "shark_tooth", "shark_tooth", "ammonite", "scallop", "scallop",
                        "starfish", "crab", "fish"}, 0.1F),
        /** Late Cretaceous river plains: sandstone, mudstone and lignite with the last great dinosaurs. */
        HELL_CREEK(new Layer[] {l(() -> Blocks.SANDSTONE, 3), l(() -> Blocks.LIGHT_GRAY_TERRACOTTA, 1), l(FossilBlocks.LIGNITE, 1),
                l(() -> Blocks.SANDSTONE, 2), l(() -> Blocks.BROWN_TERRACOTTA, 1)},
                0F, 0.06F, new String[] {"tyrant", "horned", "horned", "duckbill", "duckbill", "raptor", "turtle", "giant_lizard"}, 1, 3,
                new String[] {"leaf", "leaf", "fern", "snail", "fish"}, 0.06F),
        /** Cretaceous desert: orange sandstone of old dunes, with raptors, horned dinosaurs and their nests. */
        FLAMING_CLIFFS(new Layer[] {l(() -> Blocks.RED_SANDSTONE, 3), l(() -> Blocks.SANDSTONE, 1)},
                0F, 0.1F, new String[] {"raptor", "raptor", "horned", "small_hunter"}, 1, 3,
                new String[] {"snail"}, 0.02F),
        /** Eocene: volcanic ash and paper-thin shale that kept leaves and insects, round a forest of stone stumps. */
        ASH_BEDS(new Layer[] {l(() -> Blocks.TUFF, 2), l(() -> Blocks.CALCITE, 1), l(FossilBlocks.SHALE, 1), l(() -> Blocks.TUFF, 1)},
                0F, 0.08F, new String[] {"terror_bird"}, 0, 1,
                new String[] {"leaf", "leaf", "leaf", "dragonfly", "dragonfly", "feather", "fish", "snail"}, 0.12F),
        /** Ice Age: frozen silt with ice lenses, where mammoths and sabre-tooths lie frozen. */
        PERMAFROST(new Layer[] {l(FossilBlocks.PERMAFROST, 3), l(() -> Blocks.GRAVEL, 1), l(() -> Blocks.PACKED_ICE, 1), l(FossilBlocks.PERMAFROST, 2)},
                0F, 0.04F, new String[] {"mammoth", "mammoth", "sabre_cat"}, 1, 2,
                new String[] {"feather", "leaf", "snail"}, 0.03F);

        final Layer[] layers;
        final float dipMin, dipMax;
        final String[] skeletons;
        final int minSkeletons, maxSkeletons;
        final String[] small;
        final float density;

        Formation(Layer[] layers, float dipMin, float dipMax, String[] skeletons, int minSkeletons, int maxSkeletons, String[] small, float density) {
            this.layers = layers;
            this.dipMin = dipMin;
            this.dipMax = dipMax;
            this.skeletons = skeletons;
            this.minSkeletons = minSkeletons;
            this.maxSkeletons = maxSkeletons;
            this.small = small;
            this.density = density;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private final Formation formation;

    public FormationFeature(Formation formation) {
        super(NoneFeatureConfiguration.CODEC);
        this.formation = formation;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.FOSSILS.get()) return false;
        RandomSource rand = ctx.random();
        return form(ctx.level(), ctx.origin(), rand, formation, 8 + rand.nextInt(5), 4 + rand.nextInt(4), 8 + rand.nextInt(5)) > 0;
    }

    /** What a formation laid down: how many blocks of rock, and how many fossils in them. */
    public record Result(int rock, int fossils) {
    }

    public static int form(WorldGenLevel level, BlockPos origin, RandomSource rand, Formation f, int rx, int ry, int rz) {
        return lay(level, origin, rand, f, rx, ry, rz).rock();
    }

    /**
     * Lays formation {@code f} as a lens {@code rx} by {@code ry} by {@code rz} around {@code origin},
     * then puts its fossils in it.
     */
    public static Result lay(WorldGenLevel level, BlockPos origin, RandomSource rand, Formation f, int rx, int ry, int rz) {
        float dip = f.dipMin + rand.nextFloat() * (f.dipMax - f.dipMin);
        double dipAngle = rand.nextDouble() * Math.PI * 2;
        double sx = Math.cos(dipAngle) * dip, sz = Math.sin(dipAngle) * dip;
        int period = 0;
        for (Layer layer : f.layers) period += layer.thickness();
        int phase = rand.nextInt(period);
        double wobble = rand.nextDouble() * Math.PI * 2;
        List<BlockPos> laid = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-rx, -ry - 4, -rz), origin.offset(rx, ry + 4, rz))) {
            int dx = p.getX() - origin.getX(), dz = p.getZ() - origin.getZ();
            double tilt = sx * dx + sz * dz;
            double dy = p.getY() - origin.getY() - tilt;
            double edge = 1 + 0.18 * Math.sin(Math.atan2(dz, dx) * 3 + wobble);
            if ((double) dx * dx / (rx * rx) + (double) dz * dz / (rz * rz) + dy * dy / (ry * ry) > edge) continue;
            BlockState here = level.getBlockState(p);
            if (!replaceable(level, p, here)) continue;
            int k = Mth.floor(dy) + phase;
            k = Math.floorMod(k, period);
            BlockState rock = null;
            for (Layer layer : f.layers) {
                if (k < layer.thickness()) {
                    rock = layer.block().get();
                    break;
                }
                k -= layer.thickness();
            }
            level.setBlock(p, rock, 2);
            laid.add(p.immutable());
        }
        if (laid.isEmpty()) return new Result(0, 0);
        int fossils = 0;
        // small fossils scattered through the rock
        if (f.small.length > 0) {
            for (BlockPos p : laid) {
                if (rand.nextFloat() >= f.density) continue;
                Host h = FossilBlocks.hostFor(level.getBlockState(p));
                if (h != null && FossilBlocks.place(level, p, h, f.small[rand.nextInt(f.small.length)], Direction.from2DDataValue(rand.nextInt(4)))) {
                    fossils++;
                }
            }
        }
        // whole skeletons, near the middle so they stay inside it
        int skeletons = f.maxSkeletons <= 0 ? 0 : f.minSkeletons + rand.nextInt(f.maxSkeletons - f.minSkeletons + 1);
        for (int i = 0; i < skeletons; i++) {
            String[] rows = FossilBedFeature.vary(FossilBedFeature.named(f.skeletons[rand.nextInt(f.skeletons.length)]), rand);
            BlockPos at = origin.offset(rand.nextInt(7) - 3, rand.nextInt(Math.max(1, ry)) - ry / 2, rand.nextInt(7) - 3);
            fossils += FossilBedFeature.placeSkeleton(level, at, rand, rows, 0.06F, 0.5F);
        }
        fossils += extras(level, origin, rand, f, laid, rx, ry);
        return new Result(laid.size(), fossils);
    }

    /** What else each formation holds: anomalocaris, stone tree trunks, amber, nests, footprints. */
    private static int extras(WorldGenLevel level, BlockPos origin, RandomSource rand, Formation f, List<BlockPos> laid, int rx, int ry) {
        int n = 0;
        switch (f) {
            case BURGESS_SHALE -> {
                for (int i = 0; i < 2 + rand.nextInt(4); i++) n += CambrianBedFeature.anomalocaris(level, laid.get(rand.nextInt(laid.size())), rand);
            }
            case COAL_MEASURES, ASH_BEDS -> {
                // trunks still standing where they grew
                for (int i = 0; i < 2 + rand.nextInt(3); i++) n += PetrifiedTreeFeature.grow(level, near(origin, rand, 6, ry), rand, true);
            }
            case RED_BEDS -> {
                for (int i = 0; i < 1 + rand.nextInt(3); i++) n += PetrifiedTreeFeature.grow(level, near(origin, rand, 6, ry), rand, false);
                if (rand.nextBoolean()) n += FossilNestFeature.nest(level, near(origin, rand, 4, ry), rand);
                n += prints(level, laid, rand);
            }
            case HELL_CREEK -> {
                for (BlockPos p : laid) {
                    if (rand.nextFloat() < 0.03F && level.getBlockState(p).is(FossilBlocks.LIGNITE.get())) {
                        PetrifiedTreeFeature.amberIn(level, p, rand);
                        n++;
                    }
                }
                if (rand.nextBoolean()) n += PetrifiedTreeFeature.grow(level, near(origin, rand, 6, ry), rand, false);
                n += prints(level, laid, rand);
            }
            case FLAMING_CLIFFS -> {
                for (int i = 0; i < 1 + rand.nextInt(3); i++) n += FossilNestFeature.nest(level, near(origin, rand, 5, ry), rand);
                n += prints(level, laid, rand);
            }
            default -> {
            }
        }
        return n;
    }

    /** Footprints wherever a layer's top is bare to the air (a cave floor, a ledge). */
    private static int prints(WorldGenLevel level, List<BlockPos> laid, RandomSource rand) {
        int n = 0;
        for (BlockPos p : laid) {
            if (rand.nextFloat() > 0.25F || !level.getBlockState(p.above()).isAir()) continue;
            Host h = FossilBlocks.hostFor(level.getBlockState(p));
            if (h != null && FossilBlocks.place(level, p, h, "footprint", Direction.from2DDataValue(rand.nextInt(4)))) n++;
        }
        return n;
    }

    private static BlockPos near(BlockPos origin, RandomSource rand, int r, int ry) {
        return origin.offset(rand.nextInt(2 * r + 1) - r, rand.nextInt(Math.max(1, ry)) - ry / 2, rand.nextInt(2 * r + 1) - r);
    }

    /**
     * Rock and buried soil can be turned into the formation; the open surface (soil and sand with
     * nothing on top), water, ores, bedrock and anything with a block entity can't.
     */
    static boolean replaceable(WorldGenLevel level, BlockPos p, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty() || s.hasBlockEntity() || s.is(Blocks.BEDROCK)) return false;
        if (s.is(net.minecraftforge.common.Tags.Blocks.ORES)) return false;
        boolean soil = s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.SNOW_BLOCK);
        boolean rock = s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || s.is(Blocks.CALCITE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.ICE);
        if (!soil && !rock) return false;
        if (soil) {
            BlockState above = level.getBlockState(p.above());
            if (!above.isSolid()) return false; // keep the ground people walk on
        }
        return true;
    }
}
