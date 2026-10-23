package com.harold.corpsedecay.world;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.block.FossilBlocks;
import com.harold.corpsedecay.block.FossilBlocks.Host;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A trail of footprints left in mud that turned to rock, across bare rock on the surface (badlands,
 * stony peaks and shores) or across a cave floor: left, right, left, a stride apart, wandering a
 * little as it goes.
 */
public class FootprintTrailFeature extends Feature<NoneFeatureConfiguration> {
    private final boolean cave;

    public FootprintTrailFeature(boolean cave) {
        super(NoneFeatureConfiguration.CODEC);
        this.cave = cave;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.FOSSILS.get()) return false;
        WorldGenLevel level = ctx.level();
        BlockPos start = cave ? caveFloor(level, ctx.origin()) : ctx.origin().below();
        if (start == null) return false;
        return trail(level, start, ctx.random(), Direction.from2DDataValue(ctx.random().nextInt(4)), 5 + ctx.random().nextInt(9)) >= 3;
    }

    /** Lays a trail starting on the rock at {@code start}; returns how many prints went down. */
    public static int trail(WorldGenLevel level, BlockPos start, RandomSource rand, Direction dir, int steps) {
        int placed = 0;
        int stride = 1 + rand.nextInt(2);
        BlockPos at = start;
        for (int i = 0; i < steps; i++) {
            Direction side = i % 2 == 0 ? dir.getClockWise() : dir.getCounterClockWise();
            BlockPos foot = onFloor(level, i % 2 == 0 ? at : at.relative(side));
            if (foot != null) {
                Host h = FossilBlocks.hostFor(level.getBlockState(foot));
                if (h != null) {
                    level.setBlock(foot, FossilBlocks.state(h, "footprint", dir), 2);
                    placed++;
                }
                at = foot;
            }
            if (rand.nextFloat() < 0.15F) dir = rand.nextBoolean() ? dir.getClockWise() : dir.getCounterClockWise();
            at = at.relative(dir, stride);
        }
        return placed;
    }

    /** The open floor nearest {@code p} (rock with air above), within a block or two up or down. */
    private static BlockPos onFloor(WorldGenLevel level, BlockPos p) {
        for (int dy : new int[] {0, 1, -1, 2, -2}) {
            BlockPos q = p.above(dy);
            if (level.getBlockState(q).isSolidRender(level, q) && level.getBlockState(q.above()).isAir()) return q;
        }
        return null;
    }

    private static BlockPos caveFloor(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos p = origin.mutable();
        for (int i = 0; i < 24; i++) {
            if (level.getBlockState(p).isSolidRender(level, p) && level.getBlockState(p.above()).isAir()
                    && !level.canSeeSkyFromBelowWater(p.above())) {
                return p.immutable();
            }
            p.move(0, -1, 0);
            if (p.getY() <= level.getMinBuildHeight() + 2) return null;
        }
        return null;
    }
}
