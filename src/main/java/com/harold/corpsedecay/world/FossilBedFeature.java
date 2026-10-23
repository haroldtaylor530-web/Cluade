package com.harold.corpsedecay.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.block.FossilBlocks;
import com.harold.corpsedecay.block.FossilBlocks.Host;
import com.harold.corpsedecay.block.FossilShapes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Fossils set into the rock. Mostly a whole skeleton seen side-on, one of many kinds of creature
 * (long necks, horned and crested heads, plated backs, fliers, swimmers, sabre-tooths, mammoths,
 * turtles, serpents, whales...), each a bit longer or shorter than the last. Sometimes a bone bed
 * instead: a jumble of loose bones and shells where many things died together. Only plain rock is
 * replaced, so where it meets a cave or an ore the bones are simply missing, as if worn away.
 */
public class FossilBedFeature extends Feature<NoneFeatureConfiguration> {
    private static final Map<Character, String> LETTERS = new HashMap<>();

    static {
        for (String[] l : FossilShapes.LETTERS) LETTERS.put(l[0].charAt(0), l[1]);
    }

    public FossilBedFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.FOSSILS.get()) return false;
        RandomSource rand = ctx.random();
        if (rand.nextFloat() < 0.15F) return boneBed(ctx.level(), ctx.origin(), rand) > 0;
        String[] rows = vary(pick(rand), rand);
        return placeSkeleton(ctx.level(), ctx.origin(), rand, rows, 0.08F, 0.75F) > 0;
    }

    /** A random skeleton drawing (rows only). */
    public static String[] pick(RandomSource rand) {
        String[] s = FossilShapes.SKELETONS[rand.nextInt(FossilShapes.SKELETONS.length)];
        String[] rows = new String[s.length - 1];
        System.arraycopy(s, 1, rows, 0, rows.length);
        return rows;
    }

    /** The drawing of the skeleton with this name, or null. */
    public static String[] named(String name) {
        for (String[] s : FossilShapes.SKELETONS) {
            if (s[0].equals(name)) {
                String[] rows = new String[s.length - 1];
                System.arraycopy(s, 1, rows, 0, rows.length);
                return rows;
            }
        }
        return null;
    }

    /**
     * Makes each one a little different: a longer or shorter body or tail, by repeating or
     * dropping plain stretches of backbone, ribs or tail.
     */
    public static String[] vary(String[] rows, RandomSource rand) {
        List<StringBuilder> b = new ArrayList<>();
        int spine = 0;
        for (int r = 0; r < rows.length; r++) {
            b.add(new StringBuilder(rows[r]));
            if (rows[r].charAt(0) == '>') spine = r;
        }
        int width = 0;
        for (StringBuilder sb : b) width = Math.max(width, sb.length());
        for (StringBuilder sb : b) while (sb.length() < width) sb.append(' ');
        int changes = rand.nextInt(4);
        for (int i = 0; i < changes; i++) {
            List<Integer> plain = new ArrayList<>();
            for (int c = 1; c < width; c++) {
                char ch = b.get(spine).charAt(c);
                if ("TSRQ".indexOf(ch) < 0) continue;
                boolean alone = true;
                for (int r = 0; r < b.size(); r++) if (r != spine && b.get(r).charAt(c) != ' ') alone = false;
                if (alone) plain.add(c);
            }
            if (plain.isEmpty()) break;
            int c = plain.get(rand.nextInt(plain.size()));
            boolean grow = rand.nextFloat() < 0.65F || plain.size() < 3;
            for (StringBuilder sb : b) {
                if (grow) sb.insert(c, sb.charAt(c));
                else sb.deleteCharAt(c);
            }
            width += grow ? 1 : -1;
        }
        String[] out = new String[b.size()];
        for (int i = 0; i < out.length; i++) out[i] = b.get(i).toString();
        return out;
    }

    public static int placeSkeleton(WorldGenLevel level, BlockPos origin, RandomSource rand, String[] rows, float wear, float minRock) {
        return placeSkeleton(level, origin, rand, rows, wear, minRock, Direction.from2DDataValue(rand.nextInt(4)), null);
    }

    public static int placeSkeleton(WorldGenLevel level, BlockPos origin, RandomSource rand, String[] rows, float wear, float minRock, Direction head) {
        return placeSkeleton(level, origin, rand, rows, wear, minRock, head, null);
    }

    /**
     * Sets one skeleton into the rock around {@code origin} (the middle of its backbone row),
     * with its head pointing {@code head}, and returns how many fossil blocks went in. Each bone
     * is missing with {@code wear} chance, and nothing is placed unless {@code minRock} of its
     * blocks would be in rock. With a {@code host} the bones go into any solid block, in that rock.
     */
    public static int placeSkeleton(WorldGenLevel level, BlockPos origin, RandomSource rand, String[] rows, float wear, float minRock,
                                    Direction head, Host host) {
        int width = 0;
        int spineRow = 0;
        for (int r = 0; r < rows.length; r++) {
            width = Math.max(width, rows[r].length() - 1);
            if (rows[r].charAt(0) == '>') spineRow = r;
        }
        int cells = 0, inRock = 0;
        for (int pass = 0; pass < 2; pass++) {
            int placed = 0;
            for (int r = 0; r < rows.length; r++) {
                String row = rows[r];
                for (int c = 1; c < row.length(); c++) {
                    String piece = LETTERS.get(row.charAt(c));
                    if (piece == null) continue;
                    BlockPos p = origin.relative(head, c - 1 - width / 2).above(spineRow - r);
                    BlockState here = level.getBlockState(p);
                    Host h = host != null ? (here.isSolid() ? host : null) : FossilBlocks.hostFor(here);
                    if (pass == 0) {
                        cells++;
                        if (h != null) inRock++;
                        continue;
                    }
                    if (h == null || rand.nextFloat() < wear) continue;
                    if (FossilBlocks.place(level, p, h, piece, head)) placed++;
                }
            }
            if (pass == 0 && (cells == 0 || inRock < cells * minRock)) return 0;
            if (pass == 1) return placed;
        }
        return 0;
    }

    /** A pocket of loose bones and shells jumbled together. Returns how many went in. */
    public static int boneBed(WorldGenLevel level, BlockPos origin, RandomSource rand) {
        List<String> bones = FossilBlocks.pieces(FossilBlocks.BONE);
        List<String> small = FossilBlocks.pieces(FossilBlocks.SMALL);
        int rx = 3 + rand.nextInt(2), ry = 1 + rand.nextInt(2), rz = 3 + rand.nextInt(2);
        int placed = 0;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-rx, -ry, -rz), origin.offset(rx, ry, rz))) {
            double d = sq((p.getX() - origin.getX()) / (rx + 0.5)) + sq((p.getY() - origin.getY()) / (ry + 0.5))
                    + sq((p.getZ() - origin.getZ()) / (rz + 0.5));
            if (d > 1 || rand.nextFloat() > 0.32F) continue;
            Host h = FossilBlocks.hostFor(level.getBlockState(p));
            if (h == null) continue;
            String piece = rand.nextFloat() < 0.2F ? small.get(rand.nextInt(small.size())) : bones.get(rand.nextInt(bones.size()));
            if (FossilBlocks.place(level, p, h, piece, Direction.from2DDataValue(rand.nextInt(4)))) placed++;
        }
        return placed;
    }

    private static double sq(double v) {
        return v * v;
    }
}
