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
 * Holds up to 4 cups of water. Sea water (a cup filled in the ocean) keeps boiling after it's
 * boiled until the water's gone, leaving salt in the bottom to scrape out.
 *
 * Holds up to 4 cups of water. Put it on a lit campfire (or fire, lava, magma) and dirty water
 * boils clean in about 15 seconds. Fill it with a water bucket, water bottles or cups of dirty
 * water; take water out with a clay cup, a waterskin or a glass bottle.
 */
public class CookingPotBlock extends Block {
    public static final IntegerProperty WATER = IntegerProperty.create("water", 0, 4);
    public static final BooleanProperty BOILED = BooleanProperty.create("boiled");
    public static final IntegerProperty HEAT = IntegerProperty.create("heat", 0, 3);
    /** Sitting on a campfire: the model drops 9 pixels to rest on the logs. */
    public static final BooleanProperty ON_CAMPFIRE = BooleanProperty.create("on_campfire");
    /** The water in it is sea water. */
    public static final BooleanProperty SALTY = BooleanProperty.create("salty");
    /** Salt left in the bottom after sea water boiled away. */
    public static final IntegerProperty SALT = IntegerProperty.create("salt", 0, 4);
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 12, 15);
    private static final VoxelShape SHAPE_LOWERED = Block.box(1, 0, 1, 15, 3, 15);
    private static final int TICK = 100;

    public CookingPotBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WATER, 0).setValue(BOILED, false).setValue(HEAT, 0).setValue(ON_CAMPFIRE, false)
                .setValue(SALTY, false).setValue(SALT, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATER, BOILED, HEAT, ON_CAMPFIRE, SALTY, SALT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(ON_CAMPFIRE) ? SHAPE_LOWERED : SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState below = context.getLevel().getBlockState(context.getClickedPos().below());
        return defaultBlockState().setValue(ON_CAMPFIRE, below.is(BlockTags.CAMPFIRES));
    }

    @Override
    protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
            BlockPos pos, net.minecraft.core.Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (direction == net.minecraft.core.Direction.DOWN) {
            state = state.setValue(ON_CAMPFIRE, neighbourState.is(BlockTags.CAMPFIRES));
            if (needsTicks(state)) ticks.scheduleTick(pos, this, TICK);
        }
        return state;
    }

    public static boolean isHeated(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(BlockTags.CAMPFIRES)) return below.getValue(BlockStateProperties.LIT);
        return below.is(BlockTags.FIRE) || below.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || below.getFluidState().is(net.minecraft.tags.FluidTags.LAVA);
    }

    private static boolean needsTicks(BlockState state) {
        return state.getValue(WATER) > 0 && (!state.getValue(BOILED) || state.getValue(SALTY));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return scrapeSalt(state, level, pos, player) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    /** Takes the salt out of an empty pot. */
    private static boolean scrapeSalt(BlockState state, Level level, BlockPos pos, Player player) {
        int salt = state.getValue(SALT);
        if (salt <= 0 || state.getValue(WATER) > 0) return false;
        if (!level.isClientSide()) {
            ItemStack out = new ItemStack(ModRegistry.SALT.get(), salt);
            if (!player.getInventory().add(out)) player.drop(out, false, net.minecraft.util.Prediction.PREDICTED);
            level.setBlock(pos, state.setValue(SALT, 0), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.8F, 1.4F);
        }
        return true;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int water = state.getValue(WATER);
        boolean boiled = state.getValue(BOILED);
        boolean salty = state.getValue(SALTY);

        // ---- filling
        int add = 0;
        boolean addSalty = false;
        ItemStack giveBack = null;
        if (stack.is(Items.WATER_BUCKET)) { add = 4; giveBack = new ItemStack(Items.BUCKET); }
        else if (stack.is(ModRegistry.DIRTY_WATER_CUP.get())) { add = 1; giveBack = new ItemStack(ModRegistry.CLAY_CUP.get()); }
        else if (stack.is(ModRegistry.SALT_WATER_CUP.get())) { add = 1; addSalty = true; giveBack = new ItemStack(ModRegistry.CLAY_CUP.get()); }
        else if (stack.is(Items.POTION)) { add = 1; giveBack = new ItemStack(Items.GLASS_BOTTLE); }
        if (add > 0) {
            if (state.getValue(SALT) > 0) {
                if (scrapeSalt(state, level, pos, player)) return InteractionResult.SUCCESS;
            }
            if (water >= 4) return InteractionResult.FAIL;
            if (!level.isClientSide()) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, giveBack));
                state = state.setValue(SALTY, salty || addSalty);
                setWater(level, pos, state, Math.min(4, water + add), false);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (!isHeated(level, pos)) player.sendOverlayMessage(Component.translatable("message.realisticsurvival.pot_boiling"));
            }
            return InteractionResult.SUCCESS;
        }

        // ---- taking water out
        if (water <= 0) {
            return scrapeSalt(state, level, pos, player) ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (salty) {
            // sea water: only a clay cup can take it back out
            if (stack.is(ModRegistry.CLAY_CUP.get())) {
                if (!level.isClientSide()) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(ModRegistry.SALT_WATER_CUP.get())));
                    setWater(level, pos, state, water - 1, boiled);
                    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            }
            if (stack.is(Items.GLASS_BOTTLE) || stack.is(ModRegistry.WATERSKIN.get())) {
                if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.realisticsurvival.pot_salty"));
                return InteractionResult.FAIL;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
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
        BlockState next = state.setValue(WATER, water).setValue(BOILED, water > 0 && boiled).setValue(HEAT, boiled ? state.getValue(HEAT) : 0)
                .setValue(SALTY, water > 0 && state.getValue(SALTY));
        level.setBlock(pos, next, Block.UPDATE_ALL);
        if (needsTicks(next)) level.scheduleTick(pos, this, TICK);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (needsTicks(state)) level.scheduleTick(pos, this, TICK);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!needsTicks(state)) return;
        if (state.getValue(BOILED)) {
            // boiling sea water down: a cup boils away every 20 seconds, leaving its salt
            if (isHeated(level, pos)) {
                int heat = state.getValue(HEAT);
                if (heat > 0) {
                    state = state.setValue(HEAT, heat - 1);
                } else {
                    int water = state.getValue(WATER) - 1;
                    state = state.setValue(WATER, water).setValue(SALT, Math.min(4, state.getValue(SALT) + 1)).setValue(HEAT, 3);
                    if (water <= 0) state = state.setValue(BOILED, false).setValue(SALTY, false).setValue(HEAT, 0);
                    level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.6F);
                }
                level.setBlock(pos, state, Block.UPDATE_ALL);
            }
            if (needsTicks(state)) level.scheduleTick(pos, this, TICK);
            return;
        }
        if (isHeated(level, pos)) {
            int heat = state.getValue(HEAT) + 1;
            if (heat >= 3) {
                level.setBlock(pos, state.setValue(BOILED, true).setValue(HEAT, 3), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (state.getValue(SALTY)) level.scheduleTick(pos, this, TICK);
                return;
            }
            level.setBlock(pos, state.setValue(HEAT, heat), Block.UPDATE_ALL);
        }
        level.scheduleTick(pos, this, TICK);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(WATER) <= 0 || !isHeated(level, pos)) return;
        double y = pos.getY() + 0.15 + state.getValue(WATER) * 0.15 - (state.getValue(ON_CAMPFIRE) ? 0.5625 : 0.0);
        if (state.getValue(HEAT) > 0 && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.BUBBLE_POP, pos.getX() + 0.25 + random.nextDouble() * 0.5, y, pos.getZ() + 0.25 + random.nextDouble() * 0.5, 0, 0.02, 0);
        }
        if (state.getValue(BOILED) && random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.CLOUD, pos.getX() + 0.3 + random.nextDouble() * 0.4, y + 0.1, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.04, 0);
        }
    }
}
