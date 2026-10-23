package com.harold.corpsedecay.world;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.block.FossilBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A tree that turned to stone, buried in the rock: usually a fallen trunk lying on its side,
 * sometimes a stump still standing on its roots. Lumps of amber (the old tree's resin) sit against
 * it, now and then with an insect caught inside.
 */
public class PetrifiedTreeFeature extends Feature<NoneFeatureConfiguration> {
    public PetrifiedTreeFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.FOSSILS.get()) return false;
        return grow(ctx.level(), ctx.origin(), ctx.random(), ctx.random().nextFloat() < 0.3F) > 0;
    }

    /** Puts a petrified trunk (or stump) into the rock; returns how many blocks of wood went in. */
    public static int grow(WorldGenLevel level, BlockPos origin, RandomSource rand, boolean standing) {
        Direction along = standing ? Direction.UP : Direction.from2DDataValue(rand.nextInt(4));
        int length = standing ? 2 + rand.nextInt(3) : 4 + rand.nextInt(6);
        int inRock = 0;
        for (int i = 0; i < length; i++) if (FossilBlocks.hostFor(level.getBlockState(origin.relative(along, i))) != null) inRock++;
        if (inRock < length * 0.6F) return 0;
        Block log = FossilBlocks.PETRIFIED_LOG.get();
        int placed = 0;
        for (int i = 0; i < length; i++) {
            BlockPos p = origin.relative(along, i);
            if (FossilBlocks.hostFor(level.getBlockState(p)) == null) continue;
            level.setBlock(p, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along.getAxis()), 2);
            placed++;
            if (rand.nextFloat() < 0.12F) amber(level, p.relative(Direction.getRandom(rand)), rand);
        }
        if (standing) {
            for (Direction d : Direction.Plane.HORIZONTAL) { // roots
                BlockPos r = origin.relative(d).below();
                if (rand.nextFloat() < 0.7F && FossilBlocks.hostFor(level.getBlockState(r)) != null) {
                    level.setBlock(r, FossilBlocks.PETRIFIED_WOOD.get().defaultBlockState(), 2);
                }
            }
        } else if (rand.nextBoolean()) {
            // a broken branch off the trunk
            BlockPos b = origin.relative(along, 1 + rand.nextInt(Math.max(1, length - 2))).relative(along.getClockWise());
            if (FossilBlocks.hostFor(level.getBlockState(b)) != null) {
                level.setBlock(b, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along.getClockWise().getAxis()), 2);
            }
        }
        return placed;
    }

    static void amber(WorldGenLevel level, BlockPos p, RandomSource rand) {
        if (FossilBlocks.hostFor(level.getBlockState(p)) == null) return;
        float r = rand.nextFloat();
        Block b = r < 0.15F ? FossilBlocks.AMBER_MOSQUITO.get() : r < 0.3F ? FossilBlocks.AMBER_BEETLE.get() : FossilBlocks.AMBER.get();
        level.setBlock(p, b.defaultBlockState(), 2);
    }
}
