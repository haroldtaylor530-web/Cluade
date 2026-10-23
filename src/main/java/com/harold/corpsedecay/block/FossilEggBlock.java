package com.harold.corpsedecay.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A clutch of one to three eggs turned to stone. Place more on it to add to the clutch. */
public class FossilEggBlock extends Block {
    public static final IntegerProperty EGGS = IntegerProperty.create("eggs", 1, 3);
    private static final VoxelShape ONE = Block.box(4, 0, 4, 12, 9, 12);
    private static final VoxelShape MANY = Block.box(1, 0, 1, 15, 9, 15);

    public FossilEggBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(EGGS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(EGGS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState here = ctx.getLevel().getBlockState(ctx.getClickedPos());
        if (here.is(this)) return here.setValue(EGGS, Math.min(3, here.getValue(EGGS) + 1));
        return super.getStateForPlacement(ctx);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        if (!ctx.isSecondaryUseActive() && ctx.getItemInHand().is(asItem()) && state.getValue(EGGS) < 3) return true;
        return super.canBeReplaced(state, ctx);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(EGGS) > 1 ? MANY : ONE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        return dir == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, other, level, pos, otherPos);
    }
}
