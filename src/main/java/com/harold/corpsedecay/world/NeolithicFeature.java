package com.harold.corpsedecay.world;

import java.util.ArrayList;
import java.util.List;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.CorpseDecay;
import com.harold.corpsedecay.block.FossilBlocks;
import com.harold.corpsedecay.block.FossilBlocks.Host;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Traces of the first farmers and hunters, from the New Stone Age:
 * <ul>
 * <li>painted caves: aurochs, deer, horses, mammoths, hunters, handprints and spirals in ochre and
 * charcoal on a cave wall, a cold hearth below, sometimes the painter;</li>
 * <li>stone circles: a ring of standing stones, some fallen, sometimes a dolmen in the middle or a
 * grave beneath;</li>
 * <li>dolmens: a great capstone on standing stones over a burial;</li>
 * <li>barrows: grassy burial mounds with a stone chamber sealed inside, a body with its flint axe,
 * pots and a chest of grave goods;</li>
 * <li>mammoth-bone huts in the cold: a ring of bones around a hearth.</li>
 * </ul>
 */
public class NeolithicFeature extends Feature<NoneFeatureConfiguration> {
    public enum Kind { PAINTED_CAVE, STONE_CIRCLE, DOLMEN, BARROW, BONE_HUT }

    public static final ResourceLocation LOOT = new ResourceLocation(CorpseDecay.MODID, "chests/neolithic");

    private final Kind kind;

    public NeolithicFeature(Kind kind) {
        super(NoneFeatureConfiguration.CODEC);
        this.kind = kind;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.ANCIENT_SITES.get()) return false;
        WorldGenLevel level = ctx.level();
        RandomSource rand = ctx.random();
        BlockPos o = ctx.origin();
        return switch (kind) {
            case PAINTED_CAVE -> paintedCave(level, o, rand) > 0;
            case STONE_CIRCLE -> stoneCircle(level, o, rand, true);
            case DOLMEN -> flat(level, o, 2, 1) && dolmen(level, o, rand);
            case BARROW -> flat(level, o, 6, 2) && barrow(level, o, rand, Direction.from2DDataValue(rand.nextInt(4)));
            case BONE_HUT -> flat(level, o, 4, 1) && boneHut(level, o, rand, Direction.from2DDataValue(rand.nextInt(4)));
        };
    }

    // ---- painted caves ----------------------------------------------------------------------------

    /** Paints a cave wall near {@code origin}; returns how many paintings went up. */
    public static int paintedCave(WorldGenLevel level, BlockPos origin, RandomSource rand) {
        BlockPos floor = caveFloor(level, origin);
        if (floor == null) return 0;
        BlockPos air = floor.above();
        record Wall(BlockPos rock, Direction facing) {
        }
        List<Wall> walls = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(air.offset(-5, 0, -5), air.offset(5, 3, 5))) {
            if (!level.getBlockState(p).isAir()) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos q = p.relative(d);
                if (FossilBlocks.hostFor(level.getBlockState(q)) != null && level.getBlockState(q).isSolidRender(level, q)) {
                    walls.add(new Wall(q.immutable(), d.getOpposite()));
                }
            }
        }
        if (walls.size() < 3) return 0;
        Wall first = walls.get(rand.nextInt(walls.size()));
        List<String> paintings = FossilBlocks.pieces(FossilBlocks.PAINTING);
        int want = 3 + rand.nextInt(6), placed = 0;
        java.util.Collections.shuffle(walls, new java.util.Random(rand.nextLong()));
        List<BlockPos> used = new ArrayList<>();
        for (Wall w : walls) {
            if (placed >= want) break;
            if (w.facing() != first.facing() || w.rock().distManhattan(first.rock()) > 6 || used.contains(w.rock())) continue;
            Host h = FossilBlocks.hostFor(level.getBlockState(w.rock()));
            if (h == null) continue;
            level.setBlock(w.rock(), FossilBlocks.state(h, paintings.get(rand.nextInt(paintings.size())), w.facing()), 2);
            used.add(w.rock());
            placed++;
        }
        if (placed < 2) return placed;
        BlockPos hearth = first.rock().relative(first.facing(), 2);
        hearth = settle(level, hearth);
        if (hearth != null && rand.nextFloat() < 0.6F) {
            level.setBlock(hearth, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 2);
        }
        if (rand.nextFloat() < 0.3F) {
            BlockPos spot = settle(level, first.rock().relative(first.facing(), 1).relative(first.facing().getClockWise(), 2));
            if (spot != null) person(level, spot.below(), rand, false);
        }
        return placed;
    }

    private static BlockPos caveFloor(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos p = origin.mutable();
        for (int i = 0; i < 24; i++) {
            if (level.getBlockState(p).isFaceSturdy(level, p, Direction.UP) && level.getBlockState(p.above()).isAir()
                    && level.getBlockState(p.above(2)).isAir() && !level.canSeeSkyFromBelowWater(p.above())) {
                return p.immutable();
            }
            p.move(0, -1, 0);
            if (p.getY() <= level.getMinBuildHeight() + 2) return null;
        }
        return null;
    }

    // ---- standing stones --------------------------------------------------------------------------

    /** A ring of standing stones around {@code centre} (the air just above the ground). */
    public static boolean stoneCircle(WorldGenLevel level, BlockPos centre, RandomSource rand, boolean checkGround) {
        int radius = 5 + rand.nextInt(4);
        int stones = 8 + rand.nextInt(5);
        List<BlockPos> spots = new ArrayList<>();
        for (int i = 0; i < stones; i++) {
            double a = i * Math.PI * 2 / stones + (rand.nextDouble() - 0.5) * 0.25;
            int x = centre.getX() + (int) Math.round(Math.cos(a) * radius), z = centre.getZ() + (int) Math.round(Math.sin(a) * radius);
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z), z);
            if (checkGround && (Math.abs(top.getY() - centre.getY()) > 3 || !level.getFluidState(top.below()).isEmpty())) return false;
            spots.add(top);
        }
        for (BlockPos top : spots) {
            Block rock = rock(rand);
            level.setBlock(top.below(), rock.defaultBlockState(), 2);
            if (rand.nextFloat() < 0.2F) {
                // fallen over, lying outwards
                Direction out = Direction.getNearest(top.getX() - centre.getX(), 0, top.getZ() - centre.getZ());
                for (int k = 0; k < 2 + rand.nextInt(2); k++) level.setBlock(top.relative(out, k), rock.defaultBlockState(), 2);
            } else {
                int h = 2 + rand.nextInt(3);
                for (int k = 0; k < h; k++) level.setBlock(top.above(k), rock.defaultBlockState(), 2);
            }
        }
        float middle = rand.nextFloat();
        if (middle < 0.4F) {
            dolmen(level, centre, rand);
        } else if (middle < 0.7F) {
            level.setBlock(centre.below(), Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
            level.setBlock(centre, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 2);
            if (rand.nextFloat() < 0.4F) grave(level, centre.below(5), Direction.from2DDataValue(rand.nextInt(4)), rand);
        } else if (rand.nextBoolean()) {
            grave(level, centre.below(5), Direction.from2DDataValue(rand.nextInt(4)), rand);
        }
        return true;
    }

    /** A capstone on standing stones, with a burial under it. */
    public static boolean dolmen(WorldGenLevel level, BlockPos centre, RandomSource rand) {
        Block rock = rock(rand);
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                if (rand.nextFloat() < 0.15F) continue; // one leg gone
                for (int y = -1; y < 2; y++) level.setBlock(centre.offset(dx, y, dz), rock.defaultBlockState(), 2);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) == 2 && rand.nextFloat() < 0.4F) continue;
                level.setBlock(centre.offset(dx, 2, dz), rock.defaultBlockState(), 2);
            }
        }
        for (int y = 0; y < 2; y++) level.setBlock(centre.above(y), Blocks.AIR.defaultBlockState(), 2);
        if (rand.nextBoolean()) {
            person(level, centre.below(), rand, true);
            level.setBlock(centre.relative(Direction.from2DDataValue(rand.nextInt(4))), Blocks.DECORATED_POT.defaultBlockState(), 2);
        } else {
            grave(level, centre.below(5), Direction.from2DDataValue(rand.nextInt(4)), rand);
        }
        return true;
    }

    private static Block rock(RandomSource rand) {
        Block[] rocks = {Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.TUFF};
        return rocks[rand.nextInt(rocks.length)];
    }

    // ---- burials ------------------------------------------------------------------------------------

    /**
     * A stone burial chamber whose floor centre is {@code floor}, running along {@code along}: the
     * body with its flint axe, pots at its feet, a chest of grave goods.
     */
    public static void grave(WorldGenLevel level, BlockPos floor, Direction along, RandomSource rand) {
        Direction across = along.getClockWise();
        for (int a = -4; a <= 4; a++) {
            for (int b = -2; b <= 2; b++) {
                for (int y = 0; y <= 3; y++) {
                    BlockPos p = floor.relative(along, a).relative(across, b).above(y);
                    boolean wall = Math.abs(a) == 4 || Math.abs(b) == 2 || y == 0 || y == 3;
                    BlockState s = wall ? (rand.nextFloat() < 0.4F ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState()
                            : Blocks.CAVE_AIR.defaultBlockState();
                    level.setBlock(p, s, 2);
                }
            }
        }
        person(level, floor, rand, true);
        level.setBlock(floor.relative(along, 3).relative(across, 1).above(), Blocks.DECORATED_POT.defaultBlockState(), 2);
        level.setBlock(floor.relative(along, 3).relative(across, -1).above(), Blocks.DECORATED_POT.defaultBlockState(), 2);
        BlockPos chest = floor.relative(along, -3).relative(across, 1).above();
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, across.getOpposite()), 2);
        RandomizableContainerBlockEntity.setLootTable(level, rand, chest, LOOT);
    }

    /** A grassy burial mound with a sealed stone chamber inside. */
    public static boolean barrow(WorldGenLevel level, BlockPos centre, RandomSource rand, Direction along) {
        Direction across = along.getClockWise();
        double ra = 6.5, rb = 4.5, height = 4;
        for (int a = -7; a <= 7; a++) {
            for (int b = -5; b <= 5; b++) {
                double f = 1 - (a / ra) * (a / ra) - (b / rb) * (b / rb);
                if (f <= 0) continue;
                int top = (int) Math.round(height * Math.sqrt(f));
                BlockPos col = centre.relative(along, a).relative(across, b);
                for (int y = 0; y < top; y++) {
                    level.setBlock(col.above(y), (y == top - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), 2);
                }
            }
        }
        grave(level, centre.below(), along, rand);
        // the sealed doorway: a stone slab facing out of the end of the mound
        for (int y = 0; y < 2; y++) level.setBlock(centre.relative(along, -6).above(y), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
        level.setBlock(centre.relative(along, -6).above(2), Blocks.STONE.defaultBlockState(), 2);
        if (rand.nextFloat() < 0.3F) {
            for (int y = 0; y < 3; y++) level.setBlock(centre.above(5 + y), rock(rand).defaultBlockState(), 2);
        }
        return true;
    }

    // ---- mammoth-bone huts ---------------------------------------------------------------------------

    /** A ring of piled bones around a hearth, the doorway facing {@code door}. */
    public static boolean boneHut(WorldGenLevel level, BlockPos centre, RandomSource rand, Direction door) {
        BlockState upright = Blocks.BONE_BLOCK.defaultBlockState();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                BlockPos col = centre.offset(dx, 0, dz);
                if (r < 2.6) {
                    for (int y = 0; y < 4; y++) level.setBlock(col.above(y), Blocks.AIR.defaultBlockState(), 2);
                    continue;
                }
                if (r > 3.5) continue;
                boolean doorway = Direction.getNearest(dx, 0, dz) == door && Math.abs(dx * door.getStepZ() - dz * door.getStepX()) < 1;
                for (int y = 0; y < 3; y++) {
                    if (doorway && y < 2) {
                        level.setBlock(col.above(y), Blocks.AIR.defaultBlockState(), 2);
                        continue;
                    }
                    if (y == 2 && rand.nextFloat() < 0.35F) {
                        level.setBlock(col.above(y), Blocks.BROWN_WOOL.defaultBlockState(), 2); // hides over the frame
                        continue;
                    }
                    BlockState s = (dx + dz + y) % 3 == 0
                            ? upright.setValue(RotatedPillarBlock.AXIS, Math.abs(dx) > Math.abs(dz) ? Direction.Axis.Z : Direction.Axis.X)
                            : upright;
                    level.setBlock(col.above(y), s, 2);
                }
            }
        }
        // a ring of hides and bone over the top, open in the middle for the smoke
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r >= 1.6 && r < 2.6) {
                    level.setBlock(centre.offset(dx, 3, dz), (rand.nextFloat() < 0.5F ? Blocks.BROWN_WOOL : Blocks.BONE_BLOCK).defaultBlockState(), 2);
                }
            }
        }
        level.setBlock(centre, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 2);
        BlockPos chest = centre.relative(door.getOpposite(), 2);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, door), 2);
        RandomizableContainerBlockEntity.setLootTable(level, rand, chest, LOOT);
        if (rand.nextFloat() < 0.35F) person(level, centre.relative(door.getClockWise(), 2).below(), rand, true);
        return true;
    }

    // ---- shared -------------------------------------------------------------------------------------

    /** The remains of one of the people of that time, with a flint axe and a hide tunic. */
    static void person(WorldGenLevel level, BlockPos floor, RandomSource rand, boolean tunic) {
        CorpseEntity body = RemainsFeature.placeBody(level.getLevel(), level, floor, EntityType.SKELETON, rand,
                rand.nextBoolean() ? CorpseEntity.OLD : CorpseEntity.DARK, 0.03);
        if (body == null) return;
        body.giveGear(EquipmentSlot.MAINHAND, new ItemStack(FossilBlocks.FLINT_HAND_AXE.get()));
        if (tunic) body.giveGear(EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        if (rand.nextFloat() < 0.3F) body.giveGear(EquipmentSlot.OFFHAND, new ItemStack(FossilBlocks.BONE_FLUTE.get()));
        level.addFreshEntity(body);
    }

    /** True if the ground around {@code centre} is level to within {@code slack} and dry. */
    static boolean flat(WorldGenLevel level, BlockPos centre, int radius, int slack) {
        for (int dx = -radius; dx <= radius; dx += Math.max(1, radius)) {
            for (int dz = -radius; dz <= radius; dz += Math.max(1, radius)) {
                int x = centre.getX() + dx, z = centre.getZ() + dz;
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                if (Math.abs(h - centre.getY()) > slack) return false;
                if (!level.getFluidState(new BlockPos(x, h - 1, z)).isEmpty()) return false;
            }
        }
        return true;
    }

    private static BlockPos settle(WorldGenLevel level, BlockPos p) {
        for (int dy : new int[] {0, -1, 1, -2}) {
            BlockPos q = p.above(dy);
            if (level.getBlockState(q).isAir() && level.getBlockState(q.below()).isFaceSturdy(level, q.below(), Direction.UP)) return q;
        }
        return null;
    }
}
