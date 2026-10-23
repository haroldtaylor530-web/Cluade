package com.harold.corpsedecay.world;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.block.FossilBlocks;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * An Ice Age tar pit: a pool of sticky tar that seeped up from below and trapped whatever walked
 * in. Skeletons of the animals it caught lie half sunk in it, blackened by the tar; bones poke out
 * round the edges. Walking in it is slow going.
 */
public class TarPitFeature extends Feature<NoneFeatureConfiguration> {
    private static final EntityType<?>[] VICTIMS = {EntityType.HORSE, EntityType.HORSE, EntityType.WOLF, EntityType.WOLF, EntityType.CAMEL,
            EntityType.COW, EntityType.LLAMA, EntityType.RABBIT, EntityType.GOAT, EntityType.FOX, EntityType.POLAR_BEAR};

    public TarPitFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.FOSSILS.get()) return false;
        if (!NeolithicFeature.flat(ctx.level(), ctx.origin(), 4, 1)) return false;
        return pit(ctx.level(), ctx.origin(), ctx.random()) > 0;
    }

    /** Makes a tar pit at ground level {@code centre}; returns how many animals it holds. */
    public static int pit(WorldGenLevel level, BlockPos centre, RandomSource rand) {
        double ra = 3 + rand.nextInt(3), rb = 2.5 + rand.nextInt(2);
        BlockPos floor = centre.below();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double f = (dx / ra) * (dx / ra) + (dz / rb) * (dz / rb);
                BlockPos col = floor.offset(dx, 0, dz);
                if (f <= 1) {
                    int depth = 1 + (int) Math.round((1 - f) * 2);
                    for (int y = 0; y > -depth; y--) level.setBlock(col.above(y), FossilBlocks.TAR.get().defaultBlockState(), 2);
                    for (int y = 1; y < 3; y++) level.setBlock(col.above(y), Blocks.AIR.defaultBlockState(), 2);
                } else if (f <= 1.7) {
                    // tar-stained ground round the edge, and old bones sticking out of it
                    float r = rand.nextFloat();
                    level.setBlock(col, (r < 0.35F ? Blocks.COARSE_DIRT : r < 0.55F ? Blocks.GRAVEL : r < 0.65F ? FossilBlocks.TAR.get()
                            : Blocks.DIRT).defaultBlockState(), 2);
                    if (rand.nextFloat() < 0.06F && level.getBlockState(col.above()).canBeReplaced()) {
                        level.setBlock(col.above(), Blocks.BONE_BLOCK.defaultBlockState(), 2);
                    }
                }
            }
        }
        int animals = 1 + rand.nextInt(4);
        int placed = 0;
        for (int i = 0; i < animals; i++) {
            BlockPos at = floor.offset((int) Math.round((rand.nextDouble() - 0.5) * ra), 0, (int) Math.round((rand.nextDouble() - 0.5) * rb));
            CorpseEntity body = RemainsFeature.placeBody(level.getLevel(), level, at, VICTIMS[rand.nextInt(VICTIMS.length)], rand,
                    CorpseEntity.DARK, 0.35 + rand.nextDouble() * 0.3);
            if (body != null && level.addFreshEntity(body)) placed++;
        }
        return placed;
    }
}
