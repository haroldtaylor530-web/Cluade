package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.block.ChoppedLogBlock;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import com.harold.realisticsurvival.entity.FallingTree;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jspecify.annotations.Nullable;

/**
 * Chop a tree's bottom log with an axe: each cut takes a layer off, and cutting through the core
 * makes the whole tree topple over, away from you. Sneak to cut a single log instead.
 * Only natural trees fall (they need leaves that weren't placed by a player).
 */
public final class TreeFelling {
    private static final int MAX_LOGS = 300;
    private static final int MAX_LEAVES = 2000;
    private static final int MAX_RADIUS = 12;
    private static final int MAX_HEIGHT = 48;
    private static final int LEAF_DEPTH = 6;

    private static boolean wantsFelling(Player player) {
        return Config.TREE_FELLING.getAsBoolean() && !player.isCreative() && !player.isShiftKeyDown()
                && player.getMainHandItem().is(ItemTags.AXES);
    }

    /** Each layer of a thick trunk (2x2 trees) takes longer to cut. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        BlockState state = event.getState();
        if (!(state.is(BlockTags.LOGS) || state.getBlock() instanceof ChoppedLogBlock) || !wantsFelling(player) || event.getPosition().isEmpty()) return;
        BlockPos pos = event.getPosition().get();
        Level level = player.level();
        int width = 1;
        for (Direction d : Direction.Plane.HORIZONTAL) if (level.getBlockState(pos.relative(d)).is(BlockTags.LOGS)) width++;
        if (width > 1) event.setNewSpeed(event.getNewSpeed() / 1.5F);
    }

    /**
     * Breaking the bottom log of a tree with an axe cuts a layer off instead: the log becomes a
     * notched log, each further cut goes deeper, and cutting through the core fells the tree.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(BreakBlockEvent event) {
        if (event.isCanceled()) return;
        Player player = event.getPlayer();
        if (player == null || !(event.getLevel() instanceof ServerLevel level)) return;
        if (!wantsFelling(player)) return;
        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        if (state.getBlock() instanceof ChoppedLogBlock chopped) {
            int stage = state.getValue(ChoppedLogBlock.STAGE);
            if (stage < 3) {
                cutLayer(event, level, player, pos, state.setValue(ChoppedLogBlock.STAGE, stage + 1), chopped.log().defaultBlockState());
            } else {
                fellAndTire(level, pos, player);
            }
            return;
        }

        if (!state.is(BlockTags.LOGS)) return;
        ChoppedLogBlock chopped = ModRegistry.choppedFor(state.getBlock());
        boolean upright = !state.hasProperty(BlockStateProperties.AXIS) || state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y;
        if (chopped != null && upright) {
            if (scan(level, pos) != null) {
                cutLayer(event, level, player, pos, chopped.defaultBlockState(), state);
            }
            return;
        }
        // logs without a notched model (modded trees): the old way, one cut and it falls
        fellAndTire(level, pos, player);
    }

    private static void cutLayer(BreakBlockEvent event, ServerLevel level, Player player, BlockPos pos, BlockState next, BlockState particles) {
        event.setCanceled(true);
        event.setNotifyClient(false);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.levelEvent(2001, pos, Block.getId(particles));
        level.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 0.9F, 0.8F + level.getRandom().nextFloat() * 0.3F);
        player.getMainHandItem().hurtAndBreak(1, player, InteractionHand.MAIN_HAND.asEquipmentSlot());
        if (Config.WORK_TIRES.getAsBoolean()) {
            player.causeFoodExhaustion(0.05F);
            if (Config.THIRST.getAsBoolean()) SurvivalState.get(player).addThirst(-0.05F);
        }
    }

    private static void fellAndTire(ServerLevel level, BlockPos pos, Player player) {
        FallingTree tree = fell(level, pos, player.getX(), player.getZ());
        if (tree != null) {
            int logs = (int) tree.parts().stream().filter(p -> p.state().is(BlockTags.LOGS)).count();
            player.getMainHandItem().hurtAndBreak(logs / 4, player, InteractionHand.MAIN_HAND.asEquipmentSlot());
            if (Config.WORK_TIRES.getAsBoolean()) {
                player.causeFoodExhaustion(0.05F * logs);
                if (Config.THIRST.getAsBoolean()) SurvivalState.get(player).addThirst(-0.05F * logs);
            }
        }
    }

    /**
     * Fell the tree standing on the log at cut (which is about to be broken), falling away from (fromX, fromZ).
     * Returns the falling tree, or null if this isn't a natural tree.
     */
    public static @Nullable FallingTree fell(ServerLevel level, BlockPos cut, double fromX, double fromZ) {
        Scan scan = scan(level, cut);
        if (scan == null) return null;
        Set<BlockPos> logs = scan.logs(), leaves = scan.leaves();
        List<FallingTree.Part> parts = new ArrayList<>(logs.size() + leaves.size());
        for (BlockPos p : logs) parts.add(new FallingTree.Part(p.subtract(cut), level.getBlockState(p)));
        for (BlockPos p : leaves) parts.add(new FallingTree.Part(p.subtract(cut), level.getBlockState(p)));

        // take the tree out of the world: leaves first so they don't start decaying, then the wood
        for (BlockPos p : leaves) level.setBlock(p, level.getBlockState(p).getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        for (BlockPos p : logs) level.setBlock(p, level.getBlockState(p).getFluidState().createLegacyBlock(), Block.UPDATE_ALL);

        FallingTree tree = FallingTree.create(level, cut, parts, (float) (cut.getX() + 0.5 - fromX), (float) (cut.getZ() + 0.5 - fromZ));
        level.addFreshEntity(tree);
        return tree;
    }

    public record Scan(Set<BlockPos> logs, Set<BlockPos> leaves) {}

    /** The logs above a cut and the tree's own leaves, or null if this isn't a natural tree. */
    public static @Nullable Scan scan(ServerLevel level, BlockPos cut) {
        // the trunk and branches above the cut
        Set<BlockPos> logs = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(cut);
        Set<BlockPos> seen = new HashSet<>();
        seen.add(cut);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                BlockPos n = p.offset(dx, dy, dz);
                if (!seen.add(n)) continue;
                if (n.getY() < cut.getY() || n.getY() - cut.getY() > MAX_HEIGHT
                        || Math.abs(n.getX() - cut.getX()) > MAX_RADIUS || Math.abs(n.getZ() - cut.getZ()) > MAX_RADIUS) continue;
                if (!level.getBlockState(n).is(BlockTags.LOGS)) continue;
                logs.add(n);
                if (logs.size() > MAX_LOGS) return null;
                queue.add(n);
            }
        }
        if (logs.isEmpty()) return null;

        // its own leaves, grown not placed
        Set<BlockPos> leaves = new HashSet<>();
        ArrayDeque<BlockPos> leafQueue = new ArrayDeque<>();
        java.util.Map<BlockPos, Integer> depth = new java.util.HashMap<>();
        for (BlockPos log : logs) {
            for (Direction d : Direction.values()) {
                BlockPos n = log.relative(d);
                if (!leaves.contains(n) && isNaturalLeaf(level.getBlockState(n))) {
                    leaves.add(n);
                    depth.put(n, 1);
                    leafQueue.add(n);
                }
            }
        }
        while (!leafQueue.isEmpty()) {
            BlockPos p = leafQueue.poll();
            int dp = depth.get(p);
            if (dp >= LEAF_DEPTH) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (leaves.contains(n) || n.getY() < cut.getY() || !isNaturalLeaf(level.getBlockState(n))) continue;
                leaves.add(n);
                depth.put(n, dp + 1);
                leafQueue.add(n);
                if (leaves.size() > MAX_LEAVES) return null;
            }
        }
        if (leaves.size() < 4) return null; // a log wall or a dead stump, not a tree
        return new Scan(logs, leaves);
    }

    private static boolean isNaturalLeaf(BlockState state) {
        if (!state.is(BlockTags.LEAVES)) return false;
        return !state.hasProperty(BlockStateProperties.PERSISTENT) || !state.getValue(BlockStateProperties.PERSISTENT);
    }

    private TreeFelling() {}
}
