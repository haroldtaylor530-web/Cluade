package com.harold.realisticsurvival.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The bottom log of a tree part-way chopped through: a notch around the middle that gets
 * deeper with every layer you cut. Stage 3 is the core; cutting that fells the tree.
 * Breaking it any other way (sneaking, no axe) just gives you the log back.
 */
public class ChoppedLogBlock extends Block {
    public static final IntegerProperty STAGE = IntegerProperty.create("stage", 1, 3);
    private static final VoxelShape[] SHAPES = new VoxelShape[4];

    static {
        for (int s = 1; s <= 3; s++) {
            double m = 2 * s;
            SHAPES[s] = Shapes.or(Block.box(0, 0, 0, 16, 4, 16), Block.box(m, 4, m, 16 - m, 12, 16 - m), Block.box(0, 12, 0, 16, 16, 16));
        }
    }

    private final Supplier<Block> log;

    public ChoppedLogBlock(Properties properties, Supplier<Block> log) {
        super(properties);
        this.log = log;
        registerDefaultState(stateDefinition.any().setValue(STAGE, 1));
    }

    /** The log this was cut from. */
    public Block log() {
        return log.get();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(STAGE)];
    }
}
