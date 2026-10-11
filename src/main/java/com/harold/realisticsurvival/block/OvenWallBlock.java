package com.harold.realisticsurvival.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A wall of dried clay bricks. Built into a complete brick oven the walls darken (FORMED) and
 * right-clicking any of them opens the oven's workbench while the fires inside are burning.
 */
public class OvenWallBlock extends Block {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public OvenWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        BrickOven.Oven oven = BrickOven.find(level, pos);
        if (oven == null) {
            if (state.getValue(FORMED)) BrickOven.unformAround(level, pos);
            sp.sendOverlayMessage(BrickOven.whatsMissing(level, pos));
            return InteractionResult.SUCCESS;
        }
        BrickOven.setFormed(level, oven, true);
        if (!oven.lit(level)) {
            sp.sendOverlayMessage(Component.translatable("message.realisticsurvival.oven.cold"));
            return InteractionResult.SUCCESS;
        }
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new OvenMenu(id, inv, ContainerLevelAccess.create(level, pos), pos),
                Component.translatable("container.realisticsurvival.oven")), pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (state.getValue(FORMED)) BrickOven.unformAround(level, pos);
    }
}
