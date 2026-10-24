package com.harold.corpsedecay.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.Vec3;

/**
 * Ghost ships in the frozen seas: a ship whole and undamaged, sails still set, locked in the pack
 * ice with snow on her decks and her crew frozen where they stood. She may be a little one-masted
 * sloop, a two-masted brigantine, a three-masted merchantman, a frigate with her gunports along a
 * yellow stripe and guns on deck, or a towering galleon with a two-storey stern castle. The
 * helmsman still has his hands on the wheel, the lookout his spyglass to his eye, men lean on the
 * rail and sit against the mast, the cook sits slumped at the galley table and others lie in their
 * bedrolls in the hold. The captain sits at the chart table on the quarterdeck with his log open
 * in front of him.
 */
public class GhostShipFeature extends Feature<NoneFeatureConfiguration> {
    public GhostShipFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** What was built, for the tests. */
    public record Ship(Kind kind, String name, List<CorpseEntity> crew, ItemFrame journal, int hull) {
    }

    /**
     * A mast at station u, rising to {@code top} above the deck. A square-rigged mast has yards
     * ({height, half span} above the deck) with sails hung from them; a fore-and-aft one has a gaff
     * sail along the ship's length behind it, {@code boom} long, and maybe a jib forward of it.
     */
    record Mast(int u, int top, int[][] yards, int boom, boolean jib) {
        static Mast square(int u, int top, int[][] yards) {
            return new Mast(u, top, yards, 0, false);
        }

        static Mast foreAft(int u, int top, int boom, boolean jib) {
            return new Mast(u, top, new int[0][], boom, jib);
        }
    }

    public enum Kind {
        SLOOP("sloop", 8, 2.8F, 4, 1, 0, false, false, 6, 25,
                new Mast[] {Mast.foreAft(1, 14, 6, true)}),
        BRIGANTINE("brigantine", 11, 3.4F, 5, 1, 0, false, false, 10, 25,
                new Mast[] {Mast.square(5, 17, new int[][] {{14, 3}, {9, 4}}), Mast.foreAft(-2, 17, 6, false)}),
        MERCHANTMAN("merchantman", 14, 4F, 6, 1, 0, false, false, 14, 20,
                new Mast[] {Mast.square(0, 20, new int[][] {{17, 3}, {12, 4}, {7, 5}}), Mast.square(8, 16, new int[][] {{11, 3}, {6, 4}}),
                        Mast.square(-11, 15, new int[][] {{12, 3}})}),
        FRIGATE("frigate", 16, 4.4F, 7, 1, 4, true, false, 40, 15,
                new Mast[] {Mast.square(1, 23, new int[][] {{20, 3}, {14, 4}, {8, 6}}), Mast.square(9, 19, new int[][] {{16, 3}, {11, 4}, {6, 5}}),
                        Mast.square(-12, 17, new int[][] {{14, 3}, {9, 4}})}),
        GALLEON("galleon", 16, 4.6F, 7, 2, 5, false, true, 30, 15,
                new Mast[] {Mast.square(1, 21, new int[][] {{17, 4}, {10, 5}}), Mast.square(8, 18, new int[][] {{14, 3}, {9, 4}}),
                        Mast.square(-12, 18, new int[][] {{15, 3}})});

        final String word;
        final int half, qdLen, qdLevels, fcLen, souls, weight;
        final float beam;
        final boolean guns, gilded;
        final Mast[] masts;

        Kind(String word, int half, float beam, int qdLen, int qdLevels, int fcLen, boolean guns, boolean gilded, int souls, int weight, Mast[] masts) {
            this.word = word;
            this.half = half;
            this.beam = beam;
            this.qdLen = qdLen;
            this.qdLevels = qdLevels;
            this.fcLen = fcLen;
            this.guns = guns;
            this.gilded = gilded;
            this.souls = souls;
            this.weight = weight;
            this.masts = masts;
        }

        static Kind pick(RandomSource rand) {
            int total = 0;
            for (Kind k : values()) total += k.weight;
            int r = rand.nextInt(total);
            for (Kind k : values()) {
                r -= k.weight;
                if (r < 0) return k;
            }
            return MERCHANTMAN;
        }

        /** the mast carrying the most canvas (the mainmast) */
        Mast main() {
            Mast best = masts[0];
            for (Mast m : masts) if (m.top > best.top) best = m;
            return best;
        }

        /** a second mast forward of the main, if there is one */
        Mast fore() {
            Mast main = main(), best = null;
            for (Mast m : masts) if (m.u > main.u && (best == null || m.u < best.u)) best = m;
            return best;
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.REMAINS.get()) return false;
        WorldGenLevel level = ctx.level();
        ChunkPos cp = new ChunkPos(ctx.origin());
        RandomSource rand = ctx.random();
        Kind kind = Kind.pick(rand);
        Direction fwd = Direction.from2DDataValue(rand.nextInt(4));
        BlockPos centre = new BlockPos(cp.getMiddleBlockX(), level.getSeaLevel() - 1, cp.getMiddleBlockZ());
        if (!fits(level, centre, fwd, kind)) return false;
        return build(level, centre, fwd, kind, rand) != null;
    }

    /** Open, deep enough water with nothing (an iceberg) standing up out of it where she'd lie. */
    public static boolean fits(WorldGenLevel level, BlockPos centre, Direction fwd, Kind kind) {
        Frame f = new Frame(centre, fwd);
        int w = centre.getY();
        for (int u = -kind.half - 2; u <= kind.half + 6; u += 4) {
            for (int v = -6; v <= 6; v += 3) {
                for (int y = w - 4; y <= w; y++) {
                    BlockState s = level.getBlockState(f.at(u, y, v));
                    boolean ok = s.is(Blocks.WATER) || (y == w && (s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE)));
                    if (!ok) return false;
                }
                for (int y = w + 1; y <= w + 4; y++) {
                    BlockState s = level.getBlockState(f.at(u, y, v));
                    if (!s.isAir() && !s.is(Blocks.SNOW)) return false;
                }
            }
        }
        return true;
    }

    // ---- the frame of the ship -------------------------------------------------------------------

    /** Ship space: u along her length (bow +), v across (starboard +), y as in the world. */
    static final class Frame {
        final int cx, cz, fx, fz, sx, sz;
        final Direction fwd, side;

        Frame(BlockPos centre, Direction fwd) {
            this.cx = centre.getX();
            this.cz = centre.getZ();
            this.fwd = fwd;
            this.side = fwd.getClockWise();
            this.fx = fwd.getStepX();
            this.fz = fwd.getStepZ();
            this.sx = side.getStepX();
            this.sz = side.getStepZ();
        }

        BlockPos at(int u, int y, int v) {
            return new BlockPos(cx + u * fx + v * sx, y, cz + u * fz + v * sz);
        }

        Vec3 point(double u, double y, double v) {
            return new Vec3(cx + 0.5 + u * fx + v * sx, y, cz + 0.5 + u * fz + v * sz);
        }

        /** The yaw (as a corpse figure takes it) of something facing along ship direction (du, dv). */
        float yaw(double du, double dv) {
            double dx = du * fx + dv * sx, dz = du * fz + dv * sz;
            return (float) Math.atan2(dx, dz);
        }
    }

    // ---- building her -----------------------------------------------------------------------------

    private static final class Builder {
        final WorldGenLevel level;
        final Frame f;
        final RandomSource rand;
        final Kind k;
        final int keel, deck, half, stern, qdFront, top, fcStart, fcTop;
        final List<BlockPos> shaped = new ArrayList<>();
        final Set<BlockPos> keepClear = new HashSet<>();
        int hull;

        Builder(WorldGenLevel level, Frame f, RandomSource rand, int sea, Kind k) {
            this.level = level;
            this.f = f;
            this.rand = rand;
            this.k = k;
            this.keel = sea - 3;
            this.deck = sea + 2;
            this.half = k.half;
            this.stern = -k.half;
            this.qdFront = -k.half + k.qdLen;
            this.top = deck + 4 * k.qdLevels;
            this.fcStart = k.fcLen > 0 ? k.half - k.fcLen : Integer.MAX_VALUE;
            this.fcTop = deck + 3;
        }

        int water() {
            return keel + 3;
        }

        /** Half her beam at station u, at full width. */
        float beam(int u) {
            if (u > half - 9) return k.beam * (1F - 0.85F * (float) Math.pow((u - (half - 9)) / 10.0, 1.4));
            if (u < stern + 4) return k.beam * 0.925F;
            return k.beam;
        }

        /** Half her width at station u and height y, or -1 outside her length; narrower towards the keel. */
        int hw(int u, int y) {
            if (u < stern || u > half) return -1;
            int rel = y - keel;
            float g = rel <= 0 ? 0.45F : rel == 1 ? 0.65F : rel == 2 ? 0.85F : 1F;
            return Math.max(0, Math.round(beam(u) * g));
        }

        void set(int u, int y, int v, BlockState s) {
            BlockPos p = f.at(u, y, v);
            level.setBlock(p, s, 2);
            Block b = s.getBlock();
            if (b == Blocks.SPRUCE_FENCE || b == Blocks.GLASS_PANE) shaped.add(p);
        }

        void set(int u, int y, int v, Block b) {
            set(u, y, v, b.defaultBlockState());
        }

        BlockState get(int u, int y, int v) {
            return level.getBlockState(f.at(u, y, v));
        }

        boolean inside(int u, int v, int y) {
            return Math.abs(v) <= hw(u, y);
        }

        boolean interior(int u, int v, int y) {
            int a = Math.abs(v);
            return u > stern && u < half && a < hw(u, y) && a < hw(u, y - 1) && a < hw(u - 1, y) && a < hw(u + 1, y);
        }

        BlockState log(Direction.Axis axis) {
            return Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        }

        BlockState hullBlock(int u, int y) {
            int sea = water();
            if (k.gilded) {
                if (y == sea + 1) return Blocks.RED_TERRACOTTA.defaultBlockState();
                if (y == deck) return Blocks.YELLOW_TERRACOTTA.defaultBlockState();
            }
            if (k.guns && y == deck - 1) {
                // the gun deck: a yellow stripe with black ports along it
                return ((u & 1) == 0 && u > stern + 2 && u < half - 3 ? Blocks.BLACK_TERRACOTTA : Blocks.YELLOW_TERRACOTTA).defaultBlockState();
            }
            if (y == sea + 1) return Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, f.fwd.getAxis());
            return Blocks.DARK_OAK_PLANKS.defaultBlockState();
        }

        void hullAndDecks() {
            for (int u = stern; u <= half; u++) {
                for (int v = -6; v <= 6; v++) {
                    for (int y = keel; y <= deck; y++) {
                        if (!inside(u, v, y)) continue;
                        if (y == keel || !interior(u, v, y)) {
                            set(u, y, v, hullBlock(u, y));
                            hull++;
                        } else if (y == deck || y == keel + 1) {
                            set(u, y, v, Blocks.SPRUCE_PLANKS);
                        } else {
                            set(u, y, v, Blocks.AIR);
                        }
                    }
                    if (!inside(u, v, deck)) continue;
                    boolean edge = !interior(u, v, deck);
                    for (int y = deck + 1; y <= deck + 3; y++) set(u, y, v, Blocks.AIR);
                    if (edge && u > qdFront) {
                        set(u, deck + 1, v, k.gilded ? Blocks.RED_TERRACOTTA.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
                        set(u, deck + 2, v, Blocks.SPRUCE_FENCE);
                    }
                }
            }
        }

        /** The captain's cabin (two storeys of it on a galleon) under the quarterdeck, and the quarterdeck on top. */
        void quarterdeck() {
            for (int level = 0; level < k.qdLevels; level++) {
                int base = deck + 4 * level;
                for (int u = stern; u <= qdFront; u++) {
                    for (int v = -6; v <= 6; v++) {
                        if (!inside(u, v, deck)) continue;
                        boolean wall = !interior(u, v, deck) || u == qdFront;
                        for (int y = base + 1; y <= base + 3; y++) set(u, y, v, wall ? Blocks.DARK_OAK_PLANKS : Blocks.AIR);
                        set(u, base + 4, v, Blocks.SPRUCE_PLANKS);
                        if (k.gilded && wall) set(u, base + 4, v, Blocks.YELLOW_TERRACOTTA);
                    }
                }
                // windows astern and down the sides
                for (int v = -2; v <= 2; v += 2) set(stern, base + 2, v, Blocks.GLASS_PANE);
                for (int u = stern + 2; u < qdFront - 1; u += 2) {
                    int e = hw(u, deck);
                    set(u, base + 2, e, Blocks.GLASS_PANE);
                    set(u, base + 2, -e, Blocks.GLASS_PANE);
                }
                if (level > 0) {
                    set(qdFront, base + 2, -1, Blocks.GLASS_PANE);
                    set(qdFront, base + 2, 1, Blocks.GLASS_PANE);
                }
            }
            for (int u = stern; u <= qdFront; u++) {
                for (int v = -6; v <= 6; v++) {
                    if (!inside(u, v, deck)) continue;
                    if ((!interior(u, v, deck) || u == qdFront) && !(u == qdFront && v == 2)) set(u, top + 1, v, Blocks.SPRUCE_FENCE);
                }
            }
            // the cabin door, and a ladder up the front of the quarterdeck
            set(qdFront, deck + 1, 0, Blocks.AIR);
            set(qdFront, deck + 2, 0, Blocks.AIR);
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.fwd);
            for (int y = deck + 1; y <= top; y++) set(qdFront + 1, y, 2, ladder);
            // in the cabin: the captain's sea chest, his books, a lantern burning blue
            set(stern + 1, deck + 1, 2, Blocks.CHEST.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ChestBlock.FACING, f.side.getOpposite()));
            RandomizableContainerBlockEntity.setLootTable(level, rand, f.at(stern + 1, deck + 1, 2),
                    new ResourceLocation("minecraft", k.gilded ? "chests/shipwreck_treasure" : "chests/shipwreck_map"));
            set(stern + 1, deck + 1, -2, Blocks.BOOKSHELF);
            set(stern + 1, deck + 2, -2, Blocks.BOOKSHELF);
            set(qdFront - 2, deck + 3, 1, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            // up top: the wheel, the chart table with a stool, lanterns on the stern rail
            set(qdFront - 1, top + 1, 0, Blocks.DARK_OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true)
                    .setValue(TrapDoorBlock.FACING, f.fwd.getOpposite()));
            set(stern + 2, top + 1, -2, Blocks.CARTOGRAPHY_TABLE);
            set(stern + 2, top + 1, -1, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            set(stern, top + 2, 0, Blocks.SOUL_LANTERN);
            if (k.gilded) {
                int e = hw(stern, deck);
                set(stern, top + 2, e, Blocks.SOUL_LANTERN);
                set(stern, top + 2, -e, Blocks.SOUL_LANTERN);
            }
            keepClear.add(f.at(stern + 2, top + 2, -2));
            keepClear.add(f.at(stern + 2, top + 1, -1));
            keepClear.add(f.at(stern + 2, top + 2, -1));
            keepClear.add(f.at(qdFront - 2, top + 1, 0));
        }

        /** A raised forecastle at the bow, with a ladder up it. */
        void forecastle() {
            if (k.fcLen <= 0) return;
            for (int u = fcStart; u <= half; u++) {
                for (int v = -6; v <= 6; v++) {
                    if (!inside(u, v, deck)) continue;
                    boolean wall = !interior(u, v, deck) || u == fcStart;
                    for (int y = deck + 1; y <= deck + 2; y++) set(u, y, v, wall ? (k.gilded && y == deck + 1 ? Blocks.RED_TERRACOTTA : Blocks.DARK_OAK_PLANKS) : Blocks.AIR);
                    set(u, fcTop, v, Blocks.SPRUCE_PLANKS);
                    if (wall && !(u == fcStart && v == 2)) set(u, fcTop + 1, v, Blocks.SPRUCE_FENCE);
                }
            }
            set(fcStart, deck + 1, 0, Blocks.AIR);
            set(fcStart, deck + 2, 0, Blocks.AIR);
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.fwd.getOpposite());
            for (int y = deck + 1; y <= fcTop; y++) set(fcStart - 1, y, 2, ladder);
        }

        /** Whatever she has standing on her decks above this station: the quarterdeck, the forecastle. */
        int floorAbove(int u) {
            if (u <= qdFront) return top + 1;
            if (u >= fcStart) return fcTop + 1;
            return deck + 1;
        }

        int lowestSail(int fromU, int toU) {
            int low = deck + 3;
            for (int u = Math.min(fromU, toU); u <= Math.max(fromU, toU); u++) low = Math.max(low, floorAbove(u) + 2);
            return low;
        }

        BlockState sailcloth() {
            float r = rand.nextFloat();
            return (r < 0.1F ? Blocks.SNOW_BLOCK : r < 0.16F ? Blocks.LIGHT_GRAY_WOOL : Blocks.WHITE_WOOL).defaultBlockState();
        }

        void mast(Mast m) {
            int from = m.u <= qdFront ? deck + 1 : keel + 2;
            for (int y = from; y <= deck + m.top; y++) set(m.u, y, 0, log(Direction.Axis.Y));
            Direction.Axis across = f.side.getAxis(), along = f.fwd.getAxis();
            // square sails hung from yards
            for (int i = 0; i < m.yards.length; i++) {
                int yy = deck + m.yards[i][0], span = m.yards[i][1];
                for (int v = -span; v <= span; v++) if (v != 0) set(m.u, yy, v, log(across));
                int bottom = i + 1 < m.yards.length ? deck + m.yards[i + 1][0] + 1 : lowestSail(m.u + 1, m.u + 2);
                int rows = yy - bottom;
                for (int y = yy - 1; y >= bottom; y--) {
                    boolean belly = y < yy - 1 && y > bottom && rows > 2;
                    for (int v = -(span - 1); v <= span - 1; v++) set(m.u + (belly ? 2 : 1), y, v, sailcloth());
                }
                for (int v : new int[] {-span, span}) if (rand.nextFloat() < 0.5F) set(m.u, yy - 1, v, Blocks.ICE);
            }
            if (m.boom > 0) {
                // a gaff sail along her length behind the mast, between a boom and a gaff
                int low = lowestSail(m.u - m.boom, m.u - 1);
                int high = deck + m.top - 2;
                for (int u = m.u - 1; u >= m.u - m.boom; u--) set(u, low - 1, 0, log(along));
                int gaff = Math.max(2, m.boom - 2);
                for (int u = m.u - 1; u >= m.u - gaff; u--) set(u, high, 0, log(along));
                for (int y = low; y < high; y++) {
                    float t = (y - low) / (float) Math.max(1, high - low);
                    int len = Math.round(m.boom - (m.boom - gaff) * t);
                    for (int u = m.u - 1; u >= m.u - len; u--) set(u, y, 0, sailcloth());
                }
            }
            if (m.jib) {
                // a jib running forward from the mast to the bowsprit
                int low = lowestSail(m.u + 1, half) + 1;
                int high = deck + m.top - 3;
                for (int y = low; y <= high; y++) {
                    float t = (y - low) / (float) Math.max(1, high - low);
                    int len = Math.round((half - m.u + 2) * (1F - t));
                    for (int u = m.u + 1; u <= m.u + len; u++) set(u, y, 0, sailcloth());
                }
            }
        }

        void rig() {
            for (Mast m : k.masts) mast(m);
            BlockState sprit = log(f.fwd.getAxis());
            int base = floorAbove(half);
            for (int u = half + 1; u <= half + 5; u++) set(u, base + (u - half - 1) / 2, 0, sprit);
        }

        /** Guns run out along the rails of a frigate. */
        void guns() {
            if (!k.guns) return;
            int main = k.main().u;
            for (int u = qdFront + 3; u < Math.min(fcStart - 1, half - 4); u += 3) {
                if (u == main + 4 || u == main + 5 || u == qdFront + 6) continue;
                for (int s : new int[] {-1, 1}) {
                    int v = s * (hw(u, deck) - 1);
                    if (!interior(u, v, deck)) continue;
                    BlockPos p = f.at(u, deck + 1, v);
                    if (keepClear.contains(p)) continue;
                    set(u, deck + 1, v, Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, f.fwd));
                }
            }
        }

        /** Below decks: a hatch and ladder, the galley, cargo, bedrolls; frost over everything. */
        void hold() {
            int floor = keel + 2;
            int main = k.main().u;
            int hatch = main + 3;
            for (int y = floor; y < deck; y++) set(hatch + 1, y, 0, log(Direction.Axis.Y));
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.fwd.getOpposite());
            for (int y = floor; y <= deck; y++) set(hatch, y, 0, ladder);
            // galley
            int g = qdFront + 2;
            set(g, floor, -2, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.SmokerBlock.FACING, f.fwd));
            set(g + 1, floor, -1, Blocks.BARREL);
            set(g + 1, floor, 0, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            keepClear.add(f.at(g + 1, floor, 0));
            keepClear.add(f.at(g + 1, floor + 1, 0));
            // cargo
            for (int u = main - 2; u <= main - 1; u++) {
                for (int v : new int[] {-2, 2}) if (interior(u, v, floor)) set(u, floor, v, Blocks.BARREL);
            }
            if (interior(main - 3, 2, floor)) {
                set(main - 3, floor, 2, Blocks.CHEST.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.ChestBlock.FACING, f.side.getOpposite()));
                RandomizableContainerBlockEntity.setLootTable(level, rand, f.at(main - 3, floor, 2), new ResourceLocation("minecraft", "chests/shipwreck_supply"));
            }
            // bedrolls forward
            for (int u = bedsFrom(); u <= bedsFrom() + 3; u++) {
                for (int v : new int[] {-1, 1}) {
                    if (interior(u, v, floor) && get(u, floor, v).isAir()) set(u, floor, v, (v < 0 ? Blocks.BROWN_CARPET : Blocks.LIGHT_GRAY_CARPET).defaultBlockState());
                }
            }
            set(bedsFrom() + 1, deck - 1, 0, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            // frost: patches of ice and drifts of snow that blew in through the hatch
            for (int u = stern + 1; u < half; u++) {
                for (int v = -4; v <= 4; v++) {
                    if (!interior(u, v, floor) || !get(u, floor, v).isAir() || keepClear.contains(f.at(u, floor, v))) continue;
                    float r = rand.nextFloat();
                    if (r < 0.08F) set(u, floor - 1, v, Blocks.ICE);
                    else if (r < 0.3F) set(u, floor, v, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
                }
            }
        }

        int bedsFrom() {
            return Math.min(k.main().u + 6, half - 7);
        }

        /** The sea frozen round her, ice built up on her sides, snow on everything that faces the sky. */
        void freeze() {
            int sea = water();
            for (int u = Math.max(-22, stern - 8); u <= Math.min(22, half + 8); u++) {
                for (int v = -12; v <= 12; v++) {
                    if (inside(u, v, sea)) continue;
                    double out = u >= stern && u <= half ? Math.abs(v) - hw(u, sea) : (u > half ? u - half : stern - u) + Math.abs(v) * 0.7;
                    double reach = 6 + 3 * Math.sin(u * 0.7 + v * 0.4) + rand.nextFloat() * 2;
                    if (out > reach) continue;
                    BlockPos p = f.at(u, sea, v);
                    BlockState s = level.getBlockState(p);
                    if (!s.is(Blocks.WATER) && !s.is(Blocks.ICE)) continue;
                    level.setBlock(p, (rand.nextFloat() < 0.15F ? Blocks.PACKED_ICE : Blocks.ICE).defaultBlockState(), 2);
                    BlockPos up = p.above();
                    if (level.getBlockState(up).isAir()) {
                        if (out < 1.5 && rand.nextFloat() < 0.5F) level.setBlock(up, Blocks.ICE.defaultBlockState(), 2);
                        else if (rand.nextFloat() < 0.45F) level.setBlock(up, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + rand.nextInt(2)), 2);
                    }
                }
            }
            for (int u = stern; u <= half + 5; u++) {
                for (int v = -7; v <= 7; v++) {
                    for (int y = deck + 26; y >= deck; y--) {
                        BlockPos p = f.at(u, y, v);
                        BlockState s = level.getBlockState(p);
                        if (s.isAir()) continue;
                        BlockPos up = p.above();
                        if (keepClear.contains(up) || !level.getBlockState(up).isAir()) break;
                        BlockState snow = Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + (rand.nextFloat() < 0.3F ? 1 : 0));
                        if (rand.nextFloat() < 0.7F && snow.canSurvive(level, up)) level.setBlock(up, snow, 2);
                        break;
                    }
                }
            }
        }

        void finish() {
            for (BlockPos p : shaped) {
                BlockState s = level.getBlockState(p);
                level.setBlock(p, Block.updateFromNeighbourShapes(s, level, p), 2);
            }
        }
    }

    // ---- the crew -----------------------------------------------------------------------------------

    private static CompoundTag pose(Object... kv) {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i < kv.length; i += 4) {
            ListTag a = new ListTag();
            for (int j = 1; j <= 3; j++) a.add(FloatTag.valueOf(((Number) kv[i + j]).floatValue()));
            t.put((String) kv[i], a);
        }
        return t;
    }

    private static final CompoundTag SEATED_LEGS = pose("right_leg", -1.5, 0.12, 0, "left_leg", -1.5, -0.12, 0);

    private static CompoundTag with(CompoundTag base, CompoundTag more) {
        CompoundTag t = base.copy();
        t.merge(more);
        return t;
    }

    private static final int[] COATS = {0x2B3A55, 0x5A4632, 0x6A6A66, 0x3A3A3E, 0xCFC5AB};
    private static final int[] CAPS = {0x3B4A66, 0x2B2B2B, 0x6E6A5A, 0x5F4B32};

    private static void dressSailor(CorpseEntity c, Kind kind, RandomSource rand) {
        if (rand.nextFloat() < 0.75F) c.giveGear(EquipmentSlot.HEAD, PirateFeature.dyed(Items.LEATHER_HELMET, CAPS[rand.nextInt(CAPS.length)], rand));
        int coat = kind == Kind.FRIGATE ? (rand.nextBoolean() ? 0x23314F : 0xD8D2C0) : kind == Kind.GALLEON && rand.nextFloat() < 0.4F ? 0x7A2A26
                : COATS[rand.nextInt(COATS.length)];
        c.giveGear(EquipmentSlot.CHEST, PirateFeature.dyed(Items.LEATHER_CHESTPLATE, coat, rand));
        if (rand.nextFloat() < 0.8F) c.giveGear(EquipmentSlot.LEGS, PirateFeature.dyed(Items.LEATHER_LEGGINGS, kind == Kind.FRIGATE ? 0xD8D2C0 : 0x4A3B2C, rand));
        c.giveGear(EquipmentSlot.FEET, PirateFeature.dyed(Items.LEATHER_BOOTS, 0x2B2118, rand));
    }

    private static void dressCaptain(CorpseEntity c, Kind kind, RandomSource rand) {
        c.giveGear(EquipmentSlot.HEAD, PirateFeature.dyed(Items.LEATHER_HELMET, 0x1B1B20, rand));
        c.giveGear(EquipmentSlot.CHEST, PirateFeature.dyed(Items.LEATHER_CHESTPLATE, kind == Kind.GALLEON ? 0x6E1F1C : 0x20304F, rand));
        c.giveGear(EquipmentSlot.LEGS, PirateFeature.dyed(Items.LEATHER_LEGGINGS, 0xCFC6AE, rand));
        c.giveGear(EquipmentSlot.FEET, PirateFeature.dyed(Items.LEATHER_BOOTS, 0x17130F, rand));
        if (kind == Kind.FRIGATE || kind == Kind.GALLEON) c.giveGear(EquipmentSlot.MAINHAND, new ItemStack(kind == Kind.GALLEON ? Items.GOLDEN_SWORD : Items.IRON_SWORD));
    }

    private static CorpseEntity stand(Builder b, List<CorpseEntity> crew, EntityType<?> mob, double u, double y, double v, double du, double dv,
                                      float lean, CompoundTag pose, boolean snow) {
        int iu = (int) Math.floor(u), iv = (int) Math.round(v);
        if (!b.interior(iu, iv, b.deck)) return null;
        CorpseEntity c = CorpseEntity.figure(b.level.getLevel(), mob, b.f.point(u, y, v), b.f.yaw(du, dv), lean, 0F, CorpseEntity.ICE_MUMMY, pose);
        if (c == null) return null;
        if (snow) c.addGrowth(Blocks.SNOW, 0.45F, b.rand);
        crew.add(c);
        return c;
    }

    private static boolean isMast(Kind k, int u) {
        for (Mast m : k.masts) if (m.u == u) return true;
        return false;
    }

    private static List<CorpseEntity> crew(Builder b) {
        List<CorpseEntity> crew = new ArrayList<>();
        RandomSource r = b.rand;
        Kind k = b.k;
        int d = b.deck + 1, q = b.top + 1, floor = b.keel + 2;
        int main = k.main().u;
        // the captain at the chart table, bent over his log
        CorpseEntity cap = stand(b, crew, EntityType.PILLAGER, b.stern + 2, q + 0.5 - 0.75, -1, 0, -1, 0.3F,
                with(SEATED_LEGS, pose("right_arm", -1.2, 0.15, 0, "left_arm", -1.05, -0.25, 0, "head", 0.65, 0, 0)), true);
        if (cap != null) dressCaptain(cap, k, r);
        // the helmsman, hands still on the wheel
        CorpseEntity helm = stand(b, crew, EntityType.PILLAGER, b.qdFront - 2, q, 0, 1, 0, 0.05F,
                pose("right_arm", -1.25, 0.12, 0, "left_arm", -1.25, -0.12, 0, "head", 0.1, 0, 0), true);
        if (helm != null) dressSailor(helm, k, r);
        // Mr Pike, the mate, alone at the bow with his glass to his eye
        int bow = b.half - 3;
        CorpseEntity pike = stand(b, crew, EntityType.PILLAGER, bow, b.floorAbove(bow), 0, 1, 0, 0F,
                pose("right_arm", -2.1, -0.55, 0, "left_arm", -0.5, 0, 0, "head", -0.2, 0, 0), true);
        if (pike != null) {
            dressSailor(pike, k, r);
            pike.giveGear(EquipmentSlot.MAINHAND, new ItemStack(Items.SPYGLASS));
        }
        // two at the rail looking out over the ice
        for (int s : new int[] {-1, 1}) {
            int u = main + 4 + (s > 0 ? 1 : 0);
            if (isMast(k, u) || u >= b.fcStart) continue;
            int v = s * (b.hw(u, b.deck) - 1);
            if (Math.abs(v) < 1) continue;
            CorpseEntity c = stand(b, crew, EntityType.PILLAGER, u, d, v, 0, s, 0.35F,
                    pose("right_arm", -1.0, 0.1, 0, "left_arm", -1.1, -0.1, 0, "head", 0.35, 0, 0), true);
            if (c != null) dressSailor(c, k, r);
        }
        // one sat with his back to the mainmast, arms wrapped round himself
        CorpseEntity sat = stand(b, crew, EntityType.PILLAGER, main - 1, d - 0.75, 0, -1, 0, -0.12F,
                with(pose("right_leg", -1.25, 0.25, 0, "left_leg", -1.25, -0.25, 0),
                        pose("right_arm", -0.85, 0.55, 0, "left_arm", -0.85, -0.55, 0, "head", 0.75, 0, 0)), true);
        if (sat != null) dressSailor(sat, k, r);
        // one hauling on a line at the foremast
        Mast fore = k.fore();
        if (fore != null && fore.u - 1 < b.fcStart) {
            CorpseEntity haul = stand(b, crew, EntityType.PILLAGER, fore.u - 1, d, -1, 1, 0, -0.1F,
                    pose("right_arm", -2.7, 0, 0.1, "left_arm", -2.4, 0, -0.1, "head", -0.35, 0, 0, "right_leg", -0.3, 0, 0, "left_leg", 0.35, 0, 0), false);
            if (haul != null) dressSailor(haul, k, r);
        }
        // one caught mid-stride crossing the deck
        int walkU = b.qdFront + 3;
        if (!isMast(k, walkU)) {
            CorpseEntity walk = stand(b, crew, EntityType.PILLAGER, walkU, d, -2, 1, 0, 0.08F,
                    pose("right_leg", -0.5, 0, 0, "left_leg", 0.45, 0, 0, "right_arm", 0.45, 0, 0, "left_arm", -0.5, 0, 0), true);
            if (walk != null) dressSailor(walk, k, r);
        }
        // a gunner crouched by his gun
        if (k.guns) {
            int u = b.qdFront + 6;
            int v = b.hw(u, b.deck) - 2;
            CorpseEntity gunner = stand(b, crew, EntityType.PILLAGER, u, d - 0.4, v, 0, 1, 0.5F,
                    pose("right_leg", -1.1, 0, 0, "left_leg", -0.2, 0, 0, "right_arm", -1.3, 0, 0, "left_arm", -1.0, 0, 0, "head", 0.3, 0, 0), true);
            if (gunner != null) dressSailor(gunner, k, r);
        }
        // below: the cook, asleep at the galley table
        CorpseEntity cook = stand(b, crew, EntityType.VILLAGER, b.qdFront + 3, floor + 0.5 - 0.75, 0, 0, -1, 0.55F,
                with(SEATED_LEGS, pose("head", 0.9, 0, 0)), false);
        if (cook != null) cook.giveGear(EquipmentSlot.CHEST, PirateFeature.dyed(Items.LEATHER_CHESTPLATE, 0xD8D2C0, r));
        // those who didn't leave their bedrolls, and one in the cabin
        int beds = b.bedsFrom();
        int[][] spots = {{beds + 1, -1}, {beds + 3, 1}, {beds, 1}, {b.qdFront - 3, -2}};
        for (int i = 0; i < spots.length; i++) {
            int u = spots[i][0], v = spots[i][1];
            int y = i < 3 ? floor : b.deck + 1;
            if (!b.interior(u, v, y - 1) && !b.interior(u, v, b.deck)) continue;
            if (!b.get(u, y, v).isAir() && !b.get(u, y, v).is(Blocks.BROWN_CARPET) && !b.get(u, y, v).is(Blocks.LIGHT_GRAY_CARPET)
                    && !b.get(u, y, v).is(Blocks.SNOW)) continue;
            CorpseEntity c = RemainsFeature.placeBody(b.level.getLevel(), b.level, b.f.at(u, y - 1, v), EntityType.PILLAGER, r, CorpseEntity.ICE_MUMMY, 0.05);
            if (c == null) continue;
            dressSailor(c, k, r);
            crew.add(c);
        }
        for (CorpseEntity c : crew) b.level.addFreshEntity(c);
        return crew;
    }

    // ---- the log ------------------------------------------------------------------------------------

    private static final String[][] NAMES = {
            {"Kittiwake", "Little Wren", "Patience", "Grey Gull"},
            {"Wintermere", "Swift", "Brannock", "Good Intent"},
            {"Northern Lantern", "Mary Ellen", "Halcyon", "Grey Petrel"},
            {"Resolute", "Valiant", "Indomitable", "Glacier"},
            {"San Isidro", "Nuestra Senora del Mar", "Santa Lucia", "Golden Hind"},
    };
    private static final String[] CAPTAINS = {"A. Halloran", "J. Crane", "T. Whitlock", "E. Marsh", "R. Vane"};

    private static String opening(Kind kind, String up, String captain) {
        String errand = switch (kind) {
            case SLOOP -> "Bound north with six souls and a hold of furs to trade, a small boat but a quick one, to be in and out before the ice comes down.";
            case BRIGANTINE -> "Bound north with ten souls, rope, tools and tea, to trade at the ice settlements before the winter shuts the sea.";
            case MERCHANTMAN -> "Bound north with fourteen souls, salt fish and lamp oil, to trade at the ice settlements before the winter shuts the sea.";
            case FRIGATE -> "Ordered north with forty men and twenty-eight guns, to find what became of the whaling fleet that did not come home.";
            case GALLEON -> "Sailed with thirty souls and a hold of silver, taking the northern passage to be clear of the pirates in the south.";
        };
        return "Log of the " + kind.word + " " + up + ".\n\n" + errand + "\n\n Capt. " + captain;
    }

    static ItemStack journal(Kind kind, String ship, String captain) {
        String up = ship.toUpperCase(Locale.ROOT);
        String[] pages = {
                opening(kind, up, captain),
                "Day 9.\nFair wind. First ice at dawn, small floes like broken plates. Mr Pike says the old whalers never came so far this late in the year. I told him we will be home before the first snows.",
                "Day 14.\nThe wind has died. Fog thick as wool. We heard the ice before we saw it, a long groaning all night. By morning the sea had closed up behind us.",
                "Day 17.\nWe are held fast. The men cut at the ice with axes for six hours and gained four yards. It froze again by supper. I have ordered the stove kept lit in the hold.",
                "Day 23.\nOil running low. We burn the spare spars. Young Tom asked me if she will float again come spring. I told him yes. I do not know.",
                "Day 31.\nThe cold is in everything. The ink freezes in the well; I warm it in my hand to write. Some of the men no longer leave their bedrolls. Mr Pike keeps the watch alone at the bow.",
                "Day 38.\nThe stove went out in the night and no one woke to tend it. I found Cook at the galley table as if asleep. We are very quiet now.",
                "Day 41.\nClear sky at last. The sun lies low over the ice and there is no sail anywhere. The men stand at their posts out of habit. I have stopped ordering them below.",
                "Day 4?\nI have lost count. My hand will not close properly. Whoever finds this: we did not abandon her. We held to the ship.",
                "Tell my wife Mary I kept her letter in my coat. It is strangely warm up here now. I think I will rest a moment before I write the",
        };
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag t = book.getOrCreateTag();
        t.putString("title", "Log of the " + ship);
        t.putString("author", "Capt. " + captain);
        t.putInt("generation", 2);
        t.putBoolean("resolved", true);
        ListTag list = new ListTag();
        for (String p : pages) list.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(p))));
        t.put("pages", list);
        return book;
    }

    // ---- all of it -----------------------------------------------------------------------------------

    /** Builds her with the waterline at {@code centre}'s height, bow towards {@code fwd}. */
    public static Ship build(WorldGenLevel level, BlockPos centre, Direction fwd, Kind kind, RandomSource rand) {
        Frame f = new Frame(centre, fwd);
        Builder b = new Builder(level, f, rand, centre.getY(), kind);
        b.hullAndDecks();
        b.quarterdeck();
        b.forecastle();
        b.rig();
        b.guns();
        b.hold();
        b.freeze();
        b.finish();
        List<CorpseEntity> crew = crew(b);
        String[] names = NAMES[kind.ordinal()];
        String name = names[rand.nextInt(names.length)];
        String captain = CAPTAINS[rand.nextInt(CAPTAINS.length)];
        ItemFrame frame = new ItemFrame(level.getLevel(), f.at(b.stern + 2, b.top + 2, -2), Direction.UP);
        frame.setItem(journal(kind, name, captain), false);
        frame.setInvisible(true);
        level.addFreshEntity(frame);
        return new Ship(kind, name, crew, frame, b.hull);
    }
}
