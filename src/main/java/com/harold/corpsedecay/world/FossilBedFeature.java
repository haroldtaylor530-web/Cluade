package com.harold.corpsedecay.world;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.block.FossilBlocks;
import com.harold.corpsedecay.block.FossilBlocks.Host;
import com.harold.corpsedecay.block.FossilBlocks.Piece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * A whole skeleton of fossil blocks set into the rock, seen side-on: a row of backbone with the
 * ribcage, tail and skull, and leg bones hanging down. It replaces only plain rock, so where it
 * meets a cave or an ore the bones are simply missing there, as if worn away.
 */
public class FossilBedFeature extends Feature<NoneFeatureConfiguration> {
    /**
     * Skeletons drawn side-on, head to the right. K skull, S spine, R ribs, T tail, L leg, F foot.
     */
    public static final String[][] SKELETONS = {
        { // long neck and tail
            "           SK",
            "          S  ",
            "TTTTTSRRRRS  ",
            "      L  L   ",
            "      F  F   ",
        },
        { // big two-legged hunter
            "TTTTSRRSK",
            "     L   ",
            "     L   ",
            "     F   ",
        },
        { // four-legged beast with a short tail
            "TSRRRSSK",
            "  L  L  ",
            "  F  F  ",
        },
        { // swimmer with flippers
            "TTTSRRRSSSK",
            "    F  F   ",
        },
        { // horned plant eater
            "TTTSRRRRSK",
            "    L  L  ",
            "    L  L  ",
            "    F  F  ",
        },
        { // something small
            "TTSRK",
            "   F ",
        },
    };

    public FossilBedFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.FOSSILS.get()) return false;
        return placeSkeleton(ctx.level(), ctx.origin(), ctx.random(), SKELETONS[ctx.random().nextInt(SKELETONS.length)], 0.08F, 0.75F) > 0;
    }

    /**
     * Sets one skeleton into the rock around {@code origin} (the middle of its backbone row) and
     * returns how many fossil blocks went in. Each bone is missing with {@code wear} chance, and
     * nothing is placed unless {@code minRock} of its blocks would be in rock.
     */
    public static int placeSkeleton(WorldGenLevel level, BlockPos origin, RandomSource rand, String[] rows, float wear, float minRock) {
        return placeSkeleton(level, origin, rand, rows, wear, minRock, Direction.from2DDataValue(rand.nextInt(4)));
    }

    /** As above, with the head pointing {@code head}. */
    public static int placeSkeleton(WorldGenLevel level, BlockPos origin, RandomSource rand, String[] rows, float wear, float minRock, Direction head) {
        int width = 0;
        int spineRow = 0;
        for (int r = 0; r < rows.length; r++) {
            width = Math.max(width, rows[r].length());
            if (rows[r].indexOf('R') >= 0) spineRow = r;
        }
        int cells = 0, inRock = 0;
        for (int pass = 0; pass < 2; pass++) {
            int placed = 0;
            for (int r = 0; r < rows.length; r++) {
                String row = rows[r];
                for (int c = 0; c < row.length(); c++) {
                    Piece piece = piece(row.charAt(c));
                    if (piece == null) continue;
                    BlockPos p = origin.relative(head, c - width / 2).above(spineRow - r);
                    Host host = FossilBlocks.hostFor(level.getBlockState(p));
                    if (pass == 0) {
                        cells++;
                        if (host != null) inRock++;
                        continue;
                    }
                    if (host == null || rand.nextFloat() < wear) continue;
                    level.setBlock(p, FossilBlocks.state(host, piece, head), 2);
                    placed++;
                }
            }
            if (pass == 0 && (cells == 0 || inRock < cells * minRock)) return 0;
            if (pass == 1) return placed;
        }
        return 0;
    }

    private static Piece piece(char ch) {
        return switch (ch) {
            case 'K' -> Piece.SKULL;
            case 'S' -> Piece.SPINE;
            case 'R' -> Piece.RIBS;
            case 'T' -> Piece.TAIL;
            case 'L' -> Piece.LIMB;
            case 'F' -> Piece.FOOT;
            default -> null;
        };
    }
}
