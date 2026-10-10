package com.harold.realisticsurvival.block;

import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.item.WaterskinItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Holds up to 4 cups of water. Put it on a lit campfire (or fire, lava, magma) and dirty water
 * boils clean in about 15 seconds. Fill it with a water bucket, water bottles or cups of dirty
 * water; take water out with a clay cup, a waterskin or a glass bottle.
 */
public class CookingPotBlock extends Block {
    public static final IntegerProperty WATER = IntegerProperty.create("water", 0, 4);
    public static final BooleanProperty BOILED = BooleanProperty.create("boiled");
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 3);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);
    private static final int TICK = 100;

    public CookingPotBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WATER, 0).setValue(BOILED, false).setValue(HEAT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATER, BOILED, HEAT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public static boolean isHeated(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(BlockTags.CAMPFIRES)) return below.getValue(BlockStateProperties.LIT);
        return below.is(BlockTags.FIRE) || below.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || below.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int water = state.getValue(WATER);
        boolean boiled = state.getValue(BOILED);

        // ---- filling
        int add = 0;
        ItemStack giveBack = null;
        if (stack.is(Items.WATER_BUCKET)) { add = 4; giveBack = new ItemStack(Items.BUCKET); }
        else if (stack.is(ModRegistry.DIRTY_WATER_CUP.get())) { add = 1; giveBack = new ItemStack(ModRegistry.CLAY_CUP.get()); }
        else if (stack.is(Items.POTION)) { add = 1; giveBack = new ItemStack(Items.GLASS_BOTTLE); }
        if (add > 0) {
            if (water >= 4) return InteractionResult.FAIL;
            if (!level.isClientSide()) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, giveBack));
                setWater(level, pos, state, Math.min(4, water + add), false);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!isHeated(level, pos)) player.sendOverlayMessage(Component.translatable("message.realisticsurvival.pot_boiling"));
            }
            return InteractionResult.SUCCESS;
        }

        // ---- taking water out
        if (water <= 0) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.is(ModRegistry.CLAY_CUP.get()) || stack.is(Items.GLASS_BOTTLE)) {
            if (!level.isClientSide()) {
                ItemStack out = stack.is(Items.GLASS_BOTTLE)
                        ? net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.WATER)
                        : new ItemStack(boiled ? ModRegistry.CLEAN_WATER_CUP.get() : ModRegistry.DIRTY_WATER_CUP.get());
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, out));
                setWater(level, pos, state, water - 1, boiled);
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModRegistry.WATERSKIN.get())) {
            int have = WaterskinItem.amount(stack);
            int take = Math.min(water, WaterskinItem.CAPACITY - have);
            if (take <= 0) return InteractionResult.FAIL;
            if (!level.isClientSide()) {
                boolean dirty = !boiled || (have > 0 && WaterskinItem.dirty(stack));
                stack.set(ModRegistry.WATER_AMOUNT.get(), have + take);
                stack.set(ModRegistry.WATER_DIRTY.get(), dirty);
                setWater(level, pos, state, water - take, boiled);
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    private void setWater(Level level, BlockPos pos, BlockState state, int water, boolean boiled) {
        BlockState next = state.setValue(WATER, water).setValue(BOILED, water > 0 && boiled).setValue(HEAT, boiled ? state.getValue(HEAT) : 0);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        if (water > 0 && !boiled) level.scheduleTick(pos, this, TICK);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (state.getValue(WATER) > 0 && !state.getValue(BOILED)) level.scheduleTick(pos, this, TICK);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(WATER) <= 0 || state.getValue(BOILED)) return;
        if (isHeated(level, pos)) {
            int heat = state.getValue(HEAT) + 1;
            if (heat >= 3) {
                level.setBlock(pos, state.setValue(BOILED, true).setValue(HEAT, 3), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
                return;
            }
            level.setBlock(pos, state.setValue(HEAT, heat), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, TICK);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(WATER) <= 0 || !isHeated(level, pos)) return;
        double y = pos.getY() + 0.15 + state.getValue(WATER) * 0.15;
        if (state.getValue(HEAT) > 0 && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.25 + random.nextDouble() * 0.5, y, pos.getZ() + 0.25 + random.nextDouble() * 0.5, 0, 0.02, 0);
        }
        if (state.getValue(BOILED) && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.3 + random.nextDouble() * 0.4, y + 0.1, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.04, 0);
        }
    }
}
