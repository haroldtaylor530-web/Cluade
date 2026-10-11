package com.harold.realisticsurvival.block;

import com.harold.realisticsurvival.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * The brick oven multiblock. Seen from the front it is:
 * <pre>
 *   a 4x4 ring of oven walls, 5 high, open at the top (the chimney);
 *   a 2-wide opening in the middle of the front wall on the 2nd and 3rd layers;
 *   inside (2x2): a gravel floor on the bottom layer, two campfires along the back on the
 *   layer above it, and the rest of the inside empty.
 * </pre>
 * Local coordinates: lx 0..3 across, ly 0..4 up, lz 0..3 from front (0) to back (3).
 */
public final class BrickOven {
    public static final int SIZE = 4, HEIGHT = 5;

    /** A found oven: world corner with the smallest x/y/z, and which way its front faces. */
    public record Oven(BlockPos origin, Direction front) {
        public BlockPos at(int lx, int ly, int lz) {
            return switch (front) {
                case NORTH -> origin.offset(lx, ly, lz);
                case SOUTH -> origin.offset(3 - lx, ly, 3 - lz);
                case WEST -> origin.offset(lz, ly, 3 - lx);
                default -> origin.offset(3 - lz, ly, lx); // EAST
            };
        }

        public BlockPos[] campfires() {
            return new BlockPos[] { at(1, 1, 2), at(2, 1, 2) };
        }

        /** Hot enough to work: at least one of the back campfires is burning. */
        public boolean lit(BlockGetter level) {
            for (BlockPos p : campfires()) {
                BlockState s = level.getBlockState(p);
                if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT)) return true;
            }
            return false;
        }
    }

    private record Result(int matched, @Nullable String problem) {}

    private static boolean isWall(BlockState s) {
        return s.getBlock() instanceof OvenWallBlock;
    }

    private static boolean clear(BlockGetter level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    private static boolean notFull(BlockGetter level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return !isWall(s) && !s.isCollisionShapeFullBlock(level, p);
    }

    /** How well a candidate fits; problem is null if it is a complete oven. */
    private static Result check(BlockGetter level, Oven o) {
        int matched = 0;
        String problem = null;
        for (int ly = 0; ly < HEIGHT; ly++) {
            for (int lz = 0; lz < SIZE; lz++) {
                for (int lx = 0; lx < SIZE; lx++) {
                    BlockPos p = o.at(lx, ly, lz);
                    boolean ring = lx == 0 || lx == 3 || lz == 0 || lz == 3;
                    boolean ok;
                    String need;
                    if (ring) {
                        boolean doorway = lz == 0 && (lx == 1 || lx == 2) && (ly == 1 || ly == 2);
                        if (doorway) {
                            ok = clear(level, p);
                            need = "doorway";
                        } else {
                            ok = isWall(level.getBlockState(p));
                            need = "wall";
                        }
                    } else if (ly == 0) {
                        ok = level.getBlockState(p).is(Blocks.GRAVEL);
                        need = "floor";
                    } else if (ly == 1 && lz == 2) {
                        ok = level.getBlockState(p).is(BlockTags.CAMPFIRES);
                        need = "campfire";
                    } else if (ly == 1) {
                        ok = clear(level, p);
                        need = "inside";
                    } else {
                        ok = notFull(level, p);
                        need = "inside";
                    }
                    if (ok) matched++;
                    else if (problem == null) problem = need + ":" + (ly + 1);
                }
            }
        }
        return new Result(matched, problem);
    }

    /** The complete oven this block is part of, if any. */
    public static @Nullable Oven find(BlockGetter level, BlockPos part) {
        Search s = search(level, part);
        return s.problem == null ? s.best : null;
    }

    private record Search(@Nullable Oven best, @Nullable String problem, int matched) {}

    private static Search search(BlockGetter level, BlockPos part) {
        Oven best = null;
        String problem = "wall:1";
        int bestMatched = -1;
        for (Direction front : Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy < HEIGHT; dy++) {
                for (int dx = 0; dx < SIZE; dx++) {
                    for (int dz = 0; dz < SIZE; dz++) {
                        Oven o = new Oven(part.offset(-dx, -dy, -dz), front);
                        Result r = check(level, o);
                        if (r.problem() == null) return new Search(o, null, r.matched());
                        if (r.matched() > bestMatched) {
                            bestMatched = r.matched();
                            best = o;
                            problem = r.problem();
                        }
                    }
                }
            }
        }
        return new Search(best, problem, bestMatched);
    }

    /** A message saying what is still missing, for the best-fitting candidate around part. */
    public static Component whatsMissing(BlockGetter level, BlockPos part) {
        Search s = search(level, part);
        if (s.problem() == null) return Component.translatable("message.realisticsurvival.oven.complete");
        String[] bits = s.problem().split(":");
        return Component.translatable("message.realisticsurvival.oven.missing." + bits[0], bits[1]);
    }

    /** Light up all the walls of a finished oven (or put them back to plain). */
    public static void setFormed(Level level, Oven o, boolean formed) {
        for (int ly = 0; ly < HEIGHT; ly++) for (int lz = 0; lz < SIZE; lz++) for (int lx = 0; lx < SIZE; lx++) {
            BlockPos p = o.at(lx, ly, lz);
            BlockState s = level.getBlockState(p);
            if (isWall(s) && s.getValue(OvenWallBlock.FORMED) != formed) {
                level.setBlock(p, s.setValue(OvenWallBlock.FORMED, formed), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Clear the formed look from walls around a part of an oven that just broke. */
    public static void unformAround(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -4, -3), pos.offset(3, 4, 3))) {
            BlockState s = level.getBlockState(p);
            if (isWall(s) && s.getValue(OvenWallBlock.FORMED)) {
                level.setBlock(p, s.setValue(OvenWallBlock.FORMED, false), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** After placing a wall, gravel or campfire: did that finish an oven? */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState placed = event.getPlacedBlock();
        if (!isWall(placed) && !placed.is(Blocks.GRAVEL) && !placed.is(BlockTags.CAMPFIRES)) return;
        BlockPos pos = event.getPos();
        // gravel and campfires sit inside: look from the walls next to them
        BlockPos probe = pos;
        if (!isWall(placed)) {
            probe = null;
            for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -1, -2), pos.offset(2, 1, 2))) {
                if (isWall(level.getBlockState(p))) {
                    probe = p.immutable();
                    break;
                }
            }
            if (probe == null) return;
        }
        Oven o = find(level, probe);
        if (o == null) return;
        boolean wasFormed = level.getBlockState(o.at(0, 0, 0)).getValue(OvenWallBlock.FORMED);
        setFormed(level, o, true);
        if (!wasFormed) {
            BlockPos mid = o.at(1, 2, 1);
            level.playSound(null, mid, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 0.8F);
            if (event.getEntity() instanceof Player player) {
                player.sendOverlayMessage(Component.translatable("message.realisticsurvival.oven.complete"));
            }
        }
    }

    public static boolean isWallBlock(BlockState state) {
        return state.is(ModRegistry.OVEN_WALL_BLOCK.get());
    }

    private BrickOven() {}
}
