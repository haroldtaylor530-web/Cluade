package com.harold.corpsedecay.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * A ghost ship in the frozen seas: a merchant brig whole and undamaged, sails still set, locked in
 * the pack ice with snow on her decks, and her crew frozen where they stood. The helmsman still has
 * his hands on the wheel, the lookout his spyglass to his eye, men lean on the rail and sit against
 * the mast, the cook sits slumped at the galley table and others lie in their bedrolls in the hold.
 * The captain sits at the chart table on the quarterdeck with his log open in front of him.
 */
public class GhostShipFeature extends Feature<NoneFeatureConfiguration> {
    public GhostShipFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    /** What was built, for the tests. */
    public record Ship(String name, List<CorpseEntity> crew, ItemFrame journal, int hull) {
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.REMAINS.get()) return false;
        WorldGenLevel level = ctx.level();
        ChunkPos cp = new ChunkPos(ctx.origin());
        RandomSource rand = ctx.random();
        Direction fwd = Direction.from2DDataValue(rand.nextInt(4));
        BlockPos centre = new BlockPos(cp.getMiddleBlockX(), level.getSeaLevel() - 1, cp.getMiddleBlockZ());
        if (!fits(level, centre, fwd)) return false;
        return build(level, centre, fwd, rand) != null;
    }

    /** Open, deep enough water with nothing (an iceberg) standing up out of it where she'd lie. */
    public static boolean fits(WorldGenLevel level, BlockPos centre, Direction fwd) {
        Frame f = new Frame(centre, fwd);
        int w = centre.getY();
        for (int u = -16; u <= 20; u += 4) {
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

    private static final int HALF = 14;

    /** Half her beam at station u, at full width. */
    private static float beam(int u) {
        if (u > 5) return 4F * (1F - 0.85F * (float) Math.pow((u - 5) / 10.0, 1.4));
        if (u < -10) return 3.7F;
        return 4F;
    }

    /** Half her width at station u and height y, or -1 outside her length; narrower towards the keel. */
    private static int hw(int u, int y, int keel) {
        if (u < -HALF || u > HALF) return -1;
        int rel = y - keel;
        float g = rel <= 0 ? 0.45F : rel == 1 ? 0.65F : rel == 2 ? 0.85F : 1F;
        return Math.max(0, Math.round(beam(u) * g));
    }

    // ---- building her -----------------------------------------------------------------------------

    private static final class Builder {
        final WorldGenLevel level;
        final Frame f;
        final RandomSource rand;
        final int keel, deck;
        final List<BlockPos> shaped = new ArrayList<>();
        final Set<BlockPos> keepClear = new HashSet<>();
        int hull;

        Builder(WorldGenLevel level, Frame f, RandomSource rand, int sea) {
            this.level = level;
            this.f = f;
            this.rand = rand;
            this.keel = sea - 3;
            this.deck = sea + 2;
        }

        int water() {
            return keel + 3;
        }

        void set(int u, int y, int v, BlockState s) {
            BlockPos p = f.at(u, y, v);
            level.setBlock(p, s, 2);
            Block b = s.getBlock();
            if (b == Blocks.SPRUCE_FENCE || b == Blocks.GLASS_PANE || b == Blocks.DARK_OAK_FENCE) shaped.add(p);
        }

        void set(int u, int y, int v, Block b) {
            set(u, y, v, b.defaultBlockState());
        }

        BlockState get(int u, int y, int v) {
            return level.getBlockState(f.at(u, y, v));
        }

        boolean inside(int u, int v, int y) {
            return Math.abs(v) <= hw(u, y, keel);
        }

        boolean interior(int u, int v, int y) {
            int a = Math.abs(v);
            return u > -HALF && u < HALF && a < hw(u, y, keel) && a < hw(u, y - 1, keel) && a < hw(u - 1, y, keel) && a < hw(u + 1, y, keel);
        }

        BlockState log(Direction.Axis axis) {
            return Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
        }

        void hullAndDecks() {
            for (int u = -HALF; u <= HALF; u++) {
                for (int v = -5; v <= 5; v++) {
                    for (int y = keel; y <= deck; y++) {
                        if (!inside(u, v, y)) continue;
                        if (y == keel || !interior(u, v, y)) {
                            set(u, y, v, y == water() + 1 ? Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()
                                    .setValue(RotatedPillarBlock.AXIS, f.fwd.getAxis()) : Blocks.DARK_OAK_PLANKS.defaultBlockState());
                            hull++;
                        } else if (y == deck) {
                            set(u, y, v, Blocks.SPRUCE_PLANKS);
                        } else if (y == keel + 1) {
                            set(u, y, v, Blocks.SPRUCE_PLANKS);
                        } else {
                            set(u, y, v, Blocks.AIR);
                        }
                    }
                    if (!inside(u, v, deck)) continue;
                    boolean edge = !interior(u, v, deck);
                    // the bulwark and its rail, and clear air above the deck
                    for (int y = deck + 1; y <= deck + 3; y++) set(u, y, v, Blocks.AIR);
                    if (edge && u > -8) {
                        set(u, deck + 1, v, Blocks.DARK_OAK_PLANKS);
                        set(u, deck + 2, v, Blocks.SPRUCE_FENCE);
                    }
                }
            }
        }

        /** The captain's cabin under the quarterdeck, and the quarterdeck on top. */
        void quarterdeck() {
            int top = deck + 4;
            for (int u = -HALF; u <= -8; u++) {
                for (int v = -5; v <= 5; v++) {
                    if (!inside(u, v, deck)) continue;
                    boolean wall = !interior(u, v, deck) || u == -8;
                    for (int y = deck + 1; y <= deck + 3; y++) set(u, y, v, wall ? Blocks.DARK_OAK_PLANKS : Blocks.AIR);
                    set(u, top, v, Blocks.SPRUCE_PLANKS);
                    if (wall && !(u == -8 && v == 2)) set(u, top + 1, v, Blocks.SPRUCE_FENCE);
                }
            }
            // a door forward, windows astern and down the sides
            set(-8, deck + 1, 0, Blocks.AIR);
            set(-8, deck + 2, 0, Blocks.AIR);
            for (int v : new int[] {-2, 0, 2}) set(-HALF, deck + 2, v, Blocks.GLASS_PANE);
            for (int u : new int[] {-12, -10}) {
                int e = hw(u, deck, keel);
                set(u, deck + 2, e, Blocks.GLASS_PANE);
                set(u, deck + 2, -e, Blocks.GLASS_PANE);
            }
            // a ladder up to it
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.fwd);
            for (int y = deck + 1; y <= top; y++) set(-7, y, 2, ladder);
            // in the cabin: the captain's sea chest, his books, a lantern burning blue
            set(-13, deck + 1, 2, Blocks.CHEST.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ChestBlock.FACING, f.side.getOpposite()));
            RandomizableContainerBlockEntity.setLootTable(level, rand, f.at(-13, deck + 1, 2), new ResourceLocation("minecraft", "chests/shipwreck_map"));
            set(-13, deck + 1, -2, Blocks.BOOKSHELF);
            set(-13, deck + 2, -2, Blocks.BOOKSHELF);
            set(-10, deck + 3, 2, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            // up top: the wheel, the chart table with a stool, a lantern on the stern rail
            set(-9, top + 1, 0, Blocks.DARK_OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true)
                    .setValue(TrapDoorBlock.FACING, f.fwd.getOpposite()));
            set(-12, top + 1, -2, Blocks.CARTOGRAPHY_TABLE);
            set(-12, top + 1, -1, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            set(-HALF, top + 2, 0, Blocks.SOUL_LANTERN);
            keepClear.add(f.at(-12, top + 2, -2));
            keepClear.add(f.at(-12, top + 1, -1));
            keepClear.add(f.at(-12, top + 2, -1));
        }

        void mast(int u, int from, int to, int[][] yards, int lowest) {
            for (int y = from; y <= to; y++) set(u, y, 0, log(Direction.Axis.Y));
            Direction.Axis across = f.side.getAxis();
            for (int k = 0; k < yards.length; k++) {
                int yy = yards[k][0], span = yards[k][1];
                for (int v = -span; v <= span; v++) if (v != 0) set(u, yy, v, log(across));
                // hang a sail from it, down to the next yard or to just above the rail
                int bottom = k + 1 < yards.length ? yards[k + 1][0] + 1 : lowest;
                int rows = yy - bottom;
                for (int y = yy - 1; y >= bottom; y--) {
                    boolean belly = y < yy - 1 && y > bottom && rows > 2;
                    for (int v = -(span - 1); v <= span - 1; v++) set(u + (belly ? 2 : 1), y, v, sailcloth());
                }
                // icicles off the yard ends
                for (int v : new int[] {-span, span}) {
                    if (rand.nextFloat() < 0.5F) set(u, yy - 1, v, Blocks.ICE);
                }
            }
        }

        BlockState sailcloth() {
            float r = rand.nextFloat();
            return (r < 0.1F ? Blocks.SNOW_BLOCK : r < 0.16F ? Blocks.LIGHT_GRAY_WOOL : Blocks.WHITE_WOOL).defaultBlockState();
        }

        void rig() {
            mast(0, keel + 2, deck + 20, new int[][] {{deck + 17, 3}, {deck + 12, 4}, {deck + 7, 5}}, deck + 3);
            mast(8, keel + 2, deck + 16, new int[][] {{deck + 11, 3}, {deck + 6, 4}}, deck + 3);
            mast(-11, deck + 1, deck + 15, new int[][] {{deck + 12, 3}}, deck + 8);
            // the bowsprit
            BlockState sprit = log(f.fwd.getAxis());
            for (int u = HALF + 1; u <= HALF + 5; u++) set(u, deck + 1 + (u - HALF - 1) / 2, 0, sprit);
        }

        /** Below decks: a hatch and ladder, the galley, cargo, bedrolls; frost over everything. */
        void hold() {
            int floor = keel + 2;
            for (int y = floor; y < deck; y++) set(4, y, 0, log(Direction.Axis.Y));
            BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.fwd.getOpposite());
            for (int y = floor; y <= deck; y++) set(3, y, 0, ladder);
            // galley
            set(-6, floor, -2, Blocks.SMOKER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.SmokerBlock.FACING, f.fwd));
            set(-5, floor, -1, Blocks.BARREL);
            set(-5, floor, 0, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
            keepClear.add(f.at(-5, floor, 0));
            keepClear.add(f.at(-5, floor + 1, 0));
            // cargo: barrels of salt fish and lamp oil, a chest of stores
            for (int u = -2; u <= -1; u++) {
                set(u, floor, 2, Blocks.BARREL);
                set(u, floor, -2, Blocks.BARREL);
            }
            set(-1, floor + 1, 2, Blocks.BARREL);
            set(-3, floor, 2, Blocks.CHEST.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ChestBlock.FACING, f.side.getOpposite()));
            RandomizableContainerBlockEntity.setLootTable(level, rand, f.at(-3, floor, 2), new ResourceLocation("minecraft", "chests/shipwreck_supply"));
            // bedrolls forward
            for (int u = 7; u <= 10; u++) {
                for (int v : new int[] {-1, 1}) {
                    if (interior(u, v, floor)) set(u, floor, v, (v < 0 ? Blocks.BROWN_CARPET : Blocks.LIGHT_GRAY_CARPET).defaultBlockState());
                }
            }
            set(9, deck - 1, 0, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            // frost: patches of ice and drifts of snow that blew in through the hatch
            for (int u = -HALF + 1; u < HALF; u++) {
                for (int v = -3; v <= 3; v++) {
                    if (!interior(u, v, floor) || !get(u, floor, v).isAir() || keepClear.contains(f.at(u, floor, v))) continue;
                    float r = rand.nextFloat();
                    if (r < 0.08F) set(u, floor - 1, v, Blocks.ICE);
                    else if (r < 0.3F) set(u, floor, v, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
                }
            }
        }

        /** The sea frozen round her, ice built up on her sides, snow on everything that faces the sky. */
        void freeze() {
            int sea = water();
            for (int u = -22; u <= 22; u++) {
                for (int v = -12; v <= 12; v++) {
                    if (inside(u, v, sea)) continue;
                    double out = Math.abs(u) <= HALF ? Math.abs(v) - hw(u, sea, keel) : Math.abs(u) - HALF + Math.abs(v) * 0.7;
                    double reach = 6 + 3 * Math.sin(u * 0.7 + v * 0.4) + rand.nextFloat() * 2;
                    if (out > reach) continue;
                    BlockPos p = f.at(u, sea, v);
                    BlockState s = level.getBlockState(p);
                    if (!s.is(Blocks.WATER) && !s.is(Blocks.ICE)) continue;
                    level.setBlock(p, (rand.nextFloat() < 0.15F ? Blocks.PACKED_ICE : Blocks.ICE).defaultBlockState(), 2);
                    BlockPos up = p.above();
                    if (level.getBlockState(up).isAir()) {
                        if (out < 1.5 && rand.nextFloat() < 0.5F) level.setBlock(up, Blocks.ICE.defaultBlockState(), 2); // ice heaped against the hull
                        else if (rand.nextFloat() < 0.45F) level.setBlock(up, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1 + rand.nextInt(2)), 2);
                    }
                }
            }
            // snow on what faces the sky
            for (int u = -HALF; u <= HALF + 5; u++) {
                for (int v = -6; v <= 6; v++) {
                    for (int y = deck + 22; y >= deck; y--) {
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
            for (int k = 1; k <= 3; k++) a.add(FloatTag.valueOf(((Number) kv[i + k]).floatValue()));
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

    private static void dressSailor(CorpseEntity c, RandomSource rand) {
        if (rand.nextFloat() < 0.75F) c.giveGear(EquipmentSlot.HEAD, PirateFeature.dyed(Items.LEATHER_HELMET, CAPS[rand.nextInt(CAPS.length)], rand));
        c.giveGear(EquipmentSlot.CHEST, PirateFeature.dyed(Items.LEATHER_CHESTPLATE, COATS[rand.nextInt(COATS.length)], rand));
        if (rand.nextFloat() < 0.8F) c.giveGear(EquipmentSlot.LEGS, PirateFeature.dyed(Items.LEATHER_LEGGINGS, 0x4A3B2C, rand));
        c.giveGear(EquipmentSlot.FEET, PirateFeature.dyed(Items.LEATHER_BOOTS, 0x2B2118, rand));
    }

    private static void dressCaptain(CorpseEntity c, RandomSource rand) {
        c.giveGear(EquipmentSlot.HEAD, PirateFeature.dyed(Items.LEATHER_HELMET, 0x1B1B20, rand));
        c.giveGear(EquipmentSlot.CHEST, PirateFeature.dyed(Items.LEATHER_CHESTPLATE, 0x20304F, rand));
        c.giveGear(EquipmentSlot.LEGS, PirateFeature.dyed(Items.LEATHER_LEGGINGS, 0xCFC6AE, rand));
        c.giveGear(EquipmentSlot.FEET, PirateFeature.dyed(Items.LEATHER_BOOTS, 0x17130F, rand));
    }

    private static CorpseEntity stand(Builder b, List<CorpseEntity> crew, EntityType<?> mob, double u, double y, double v, double du, double dv,
                                      float lean, CompoundTag pose, boolean snow) {
        CorpseEntity c = CorpseEntity.figure(b.level.getLevel(), mob, b.f.point(u, y, v), b.f.yaw(du, dv), lean, 0F, CorpseEntity.ICE_MUMMY, pose);
        if (c == null) return null;
        if (snow) c.addGrowth(Blocks.SNOW, 0.45F, b.rand);
        crew.add(c);
        return c;
    }

    private static List<CorpseEntity> crew(Builder b) {
        List<CorpseEntity> crew = new ArrayList<>();
        RandomSource r = b.rand;
        int d = b.deck + 1, q = b.deck + 5, floor = b.keel + 2;
        // the captain at the chart table, bent over his log
        CorpseEntity cap = stand(b, crew, EntityType.PILLAGER, -12, q + 0.5 - 0.75, -1, 0, -1, 0.3F,
                with(SEATED_LEGS, pose("right_arm", -1.2, 0.15, 0, "left_arm", -1.05, -0.25, 0, "head", 0.65, 0, 0)), true);
        if (cap != null) dressCaptain(cap, r);
        // the helmsman, hands still on the wheel
        CorpseEntity helm = stand(b, crew, EntityType.PILLAGER, -10, q, 0, 1, 0, 0.05F,
                pose("right_arm", -1.25, 0.12, 0, "left_arm", -1.25, -0.12, 0, "head", 0.1, 0, 0), true);
        if (helm != null) dressSailor(helm, r);
        // Mr Pike, the mate, alone at the bow with his glass to his eye
        CorpseEntity pike = stand(b, crew, EntityType.PILLAGER, 11, d, 0, 1, 0, 0F,
                pose("right_arm", -2.1, -0.55, 0, "left_arm", -0.5, 0, 0, "head", -0.2, 0, 0), true);
        if (pike != null) {
            dressSailor(pike, r);
            pike.giveGear(EquipmentSlot.MAINHAND, new ItemStack(Items.SPYGLASS));
        }
        // two at the rail looking out over the ice
        for (int s : new int[] {-1, 1}) {
            CorpseEntity c = stand(b, crew, EntityType.PILLAGER, 4 + (s > 0 ? 1 : 0), d, 3 * s, 0, s, 0.35F,
                    pose("right_arm", -1.0, 0.1, 0, "left_arm", -1.1, -0.1, 0, "head", 0.35, 0, 0), true);
            if (c != null) dressSailor(c, r);
        }
        // one sat with his back to the mainmast, arms wrapped round himself
        CorpseEntity sat = stand(b, crew, EntityType.PILLAGER, -1, d - 0.75, 0, -1, 0, -0.12F,
                with(pose("right_leg", -1.25, 0.25, 0, "left_leg", -1.25, -0.25, 0),
                        pose("right_arm", -0.85, 0.55, 0, "left_arm", -0.85, -0.55, 0, "head", 0.75, 0, 0)), true);
        if (sat != null) dressSailor(sat, r);
        // one hauling on a line at the foremast
        CorpseEntity haul = stand(b, crew, EntityType.PILLAGER, 7, d, -1, 1, 0, -0.1F,
                pose("right_arm", -2.7, 0, 0.1, "left_arm", -2.4, 0, -0.1, "head", -0.35, 0, 0, "right_leg", -0.3, 0, 0, "left_leg", 0.35, 0, 0), false);
        if (haul != null) dressSailor(haul, r);
        // one caught mid-stride crossing the deck
        CorpseEntity walk = stand(b, crew, EntityType.PILLAGER, -4, d, -2, 1, 0, 0.08F,
                pose("right_leg", -0.5, 0, 0, "left_leg", 0.45, 0, 0, "right_arm", 0.45, 0, 0, "left_arm", -0.5, 0, 0), true);
        if (walk != null) dressSailor(walk, r);
        // below: the cook, asleep at the galley table
        CorpseEntity cook = stand(b, crew, EntityType.VILLAGER, -5, floor + 0.5 - 0.75, 0, 0, -1, 0.55F,
                with(SEATED_LEGS, pose("head", 0.9, 0, 0)), false);
        if (cook != null) cook.giveGear(EquipmentSlot.CHEST, PirateFeature.dyed(Items.LEATHER_CHESTPLATE, 0xD8D2C0, r));
        // three who didn't leave their bedrolls, and one in the cabin
        int[][] beds = {{8, -1}, {10, 1}, {7, 1}, {-11, -2}};
        for (int i = 0; i < beds.length; i++) {
            int u = beds[i][0], v = beds[i][1];
            int y = i < 3 ? floor : b.deck + 1;
            CorpseEntity c = RemainsFeature.placeBody(b.level.getLevel(), b.level, b.f.at(u, y - 1, v), EntityType.PILLAGER, r, CorpseEntity.ICE_MUMMY, 0.05);
            if (c == null) continue;
            dressSailor(c, r);
            crew.add(c);
        }
        for (CorpseEntity c : crew) b.level.addFreshEntity(c);
        return crew;
    }

    // ---- the log ------------------------------------------------------------------------------------

    private static final String[] NAMES = {"Wintermere", "Grey Petrel", "Northern Lantern", "Halcyon", "Mary Ellen"};
    private static final String[] CAPTAINS = {"A. Halloran", "J. Crane", "T. Whitlock", "E. Marsh"};

    static ItemStack journal(String ship, String captain) {
        String up = ship.toUpperCase(java.util.Locale.ROOT);
        String[] pages = {
                "Log of the brig " + up + ".\n\nBound north with fourteen souls, salt fish and lamp oil, to trade at the ice settlements before the winter shuts the sea.\n\n Capt. " + captain,
                "Day 9.\nFair wind. First ice at dawn, small floes like broken plates. Mr Pike says the old whalers never came so far this late in the year. I told him we will be home before the first snows.",
                "Day 14.\nThe wind has died. Fog thick as wool. We heard the ice before we saw it, a long groaning all night. By morning the sea had closed up behind us.",
                "Day 17.\nWe are held fast. The men cut at the ice with axes for six hours and gained four yards. It froze again by supper. I have ordered the stove kept lit in the hold.",
                "Day 23.\nOil running low. We burn the spare spars. Young Tom asked me if she will float again come spring. I told him yes. I do not know.",
                "Day 31.\nThe cold is in everything. The ink freezes in the well; I warm it in my hand to write. Three men no longer leave their bedrolls. Mr Pike keeps the watch alone at the bow.",
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
    public static Ship build(WorldGenLevel level, BlockPos centre, Direction fwd, RandomSource rand) {
        Frame f = new Frame(centre, fwd);
        Builder b = new Builder(level, f, rand, centre.getY());
        b.hullAndDecks();
        b.quarterdeck();
        b.rig();
        b.hold();
        b.freeze();
        b.finish();
        List<CorpseEntity> crew = crew(b);
        String name = NAMES[rand.nextInt(NAMES.length)];
        String captain = CAPTAINS[rand.nextInt(CAPTAINS.length)];
        ItemFrame frame = new ItemFrame(level.getLevel(), f.at(-12, b.deck + 6, -2), Direction.UP);
        frame.setItem(journal(name, captain), false);
        frame.setInvisible(true);
        level.addFreshEntity(frame);
        return new Ship(name, crew, frame, b.hull);
    }
}
