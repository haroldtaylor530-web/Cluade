package com.harold.corpsedecay.world;

import java.util.ArrayList;
import java.util.List;

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
 * A Cambrian bed, deep down in the oldest rock: a thin layer crowded with the strange sea creatures
 * of half a billion years ago (opabinia with its five eyes, spiny hallucigenia and wiwaxia, lacy
 * marrella, little pikaia, jellyfish, reef cups, worm burrows, trilobites) and now and then the
 * great hunter of that sea, anomalocaris, two blocks long.
 */
public class CambrianBedFeature extends Feature<NoneFeatureConfiguration> {
    public CambrianBedFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.FOSSILS.get()) return false;
        return bed(ctx.level(), ctx.origin(), ctx.random()) > 0;
    }

    /** Lays a Cambrian bed around {@code origin}; returns how many fossils went in. */
    public static int bed(WorldGenLevel level, BlockPos origin, RandomSource rand) {
        List<String> creatures = new ArrayList<>();
        for (String p : FossilBlocks.pieces(FossilBlocks.CAMBRIAN)) if (!p.startsWith("anomalocaris")) creatures.add(p);
        creatures.add("trilobite");
        creatures.add("trilobite");
        int rx = 4 + rand.nextInt(3), rz = 4 + rand.nextInt(3);
        int placed = 0;
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-rx, -1, -rz), origin.offset(rx, 1, rz))) {
            double dx = (p.getX() - origin.getX()) / (rx + 0.5), dz = (p.getZ() - origin.getZ()) / (rz + 0.5);
            double dy = (p.getY() - origin.getY()) / 1.5;
            if (dx * dx + dy * dy + dz * dz > 1) continue;
            Host h = FossilBlocks.hostFor(level.getBlockState(p));
            if (h == null) continue;
            spots.add(p.immutable());
            if (rand.nextFloat() > 0.3F) continue;
            String piece = creatures.get(rand.nextInt(creatures.size()));
            level.setBlock(p, FossilBlocks.state(h, piece, Direction.from2DDataValue(rand.nextInt(4))), 2);
            placed++;
        }
        int hunters = rand.nextInt(3);
        for (int i = 0; i < hunters && !spots.isEmpty(); i++) placed += anomalocaris(level, spots.get(rand.nextInt(spots.size())), rand);
        return placed;
    }

    /** An anomalocaris, head at {@code head} and its tail behind; returns how many blocks went in. */
    public static int anomalocaris(WorldGenLevel level, BlockPos head, RandomSource rand) {
        Direction d = Direction.from2DDataValue(rand.nextInt(4));
        BlockPos tail = head.relative(d.getOpposite());
        Host h1 = FossilBlocks.hostFor(level.getBlockState(head)), h2 = FossilBlocks.hostFor(level.getBlockState(tail));
        if (h1 == null || h2 == null) return 0;
        level.setBlock(head, FossilBlocks.state(h1, "anomalocaris_head", d), 2);
        level.setBlock(tail, FossilBlocks.state(h2, "anomalocaris_tail", d), 2);
        return 2;
    }
}
