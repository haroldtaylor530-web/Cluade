package com.harold.realisticsurvival.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Wet clay bricks set out to dry: up to 4 in a pile. They dry in one to three days in the open;
 * rain soaks them again and holds them back. Break the pile to collect the bricks.
 */
public class ClayBrickPileBlock extends Block {
    public static final IntegerProperty COUNT = IntegerProperty.create("count", 1, 4);
    public static final BooleanProperty DRY = BooleanProperty.create("dry");
    public static final int DAY = 24000;
    private static final VoxelShape LOW = Block.box(2, 0, 2, 14, 3, 14);
    private static final VoxelShape HIGH = Block.box(2, 0, 2, 14, 6, 14);

    public ClayBrickPileBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(COUNT, 1).setValue(DRY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COUNT, DRY);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(COUNT) <= 2 ? LOW : HIGH;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState there = context.getLevel().getBlockState(context.getClickedPos());
        if (there.is(this)) return there.setValue(COUNT, Math.min(4, there.getValue(COUNT) + 1));
        return defaultBlockState();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (!context.isSecondaryUseActive() && context.getItemInHand().is(asItem()) && !state.getValue(DRY) && state.getValue(COUNT) < 4) {
            return true;
        }
        return super.canBeReplaced(state, context);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return state;
    }

    /** Ticks until a fresh pile is dry: one to three days. */
    public static int dryingTime(RandomSource random) {
        return DAY + random.nextInt(2 * DAY + 1);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.getValue(DRY) && !oldState.is(this) && !level.isClientSide()) {
            level.scheduleTick(pos, this, dryingTime(level.getRandom()));
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(DRY)) return;
        if (level.isRainingAt(pos.above())) {
            // soaked again: try later
            level.scheduleTick(pos, this, DAY / 8 + random.nextInt(DAY / 8));
            return;
        }
        level.setBlock(pos, state.setValue(DRY, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.MUD_BRICKS_PLACE, SoundSource.BLOCKS, 0.6F, 1.3F);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(DRY) && random.nextInt(40) == 0 && level.isRainingAt(pos.above())) {
            level.addParticle(ParticleTypes.SPLASH, pos.getX() + random.nextDouble(), pos.getY() + 0.3, pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
    }
}
