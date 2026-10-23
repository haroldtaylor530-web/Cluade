package com.harold.corpsedecay.world;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.block.FossilBlocks;
import com.harold.corpsedecay.block.FossilEggBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A nest that was buried before it hatched: a small hollow sealed in the rock with a clutch of
 * stone eggs on its floor, and sometimes the parent's skeleton in the wall beside it.
 */
public class FossilNestFeature extends Feature<NoneFeatureConfiguration> {
    public FossilNestFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.FOSSILS.get()) return false;
        return nest(ctx.level(), ctx.origin(), ctx.random()) > 0;
    }

    /** Hollows out a nest whose floor is at {@code floor}; returns how many eggs it holds. */
    public static int nest(WorldGenLevel level, BlockPos floor, RandomSource rand) {
        // only somewhere sealed in solid rock
        for (BlockPos p : BlockPos.betweenClosed(floor.offset(-3, -1, -3), floor.offset(3, 3, 3))) {
            if (FossilBlocks.hostFor(level.getBlockState(p)) == null) return 0;
        }
        for (BlockPos p : BlockPos.betweenClosed(floor.offset(-2, 1, -2), floor.offset(2, 2, 2))) {
            int dx = p.getX() - floor.getX(), dz = p.getZ() - floor.getZ();
            if (dx * dx + dz * dz <= (p.getY() == floor.getY() + 1 ? 5 : 2)) level.setBlock(p, Blocks.CAVE_AIR.defaultBlockState(), 2);
        }
        int eggs = 0;
        for (BlockPos p : BlockPos.betweenClosed(floor.offset(-1, 1, -1), floor.offset(1, 1, 1))) {
            if (rand.nextFloat() < 0.55F) {
                int n = 1 + rand.nextInt(3);
                level.setBlock(p, FossilBlocks.FOSSIL_EGG.get().defaultBlockState().setValue(FossilEggBlock.EGGS, n), 2);
                eggs += n;
            }
        }
        if (eggs == 0) {
            level.setBlock(floor.above(), FossilBlocks.FOSSIL_EGG.get().defaultBlockState().setValue(FossilEggBlock.EGGS, 2), 2);
            eggs = 2;
        }
        if (rand.nextFloat() < 0.35F) {
            // the parent, curled in the wall of the nest
            Direction d = Direction.from2DDataValue(rand.nextInt(4));
            String[] rows = FossilBedFeature.named(rand.nextBoolean() ? "small_hunter" : "raptor");
            FossilBedFeature.placeSkeleton(level, floor.above(2).relative(d.getClockWise(), 3), rand, rows, 0.1F, 0.5F, d);
        }
        return eggs;
    }
}
