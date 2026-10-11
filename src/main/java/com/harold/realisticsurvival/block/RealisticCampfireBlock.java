package com.harold.realisticsurvival.block;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * A campfire that behaves like a real fire:
 * - it is built cold; it needs kindling (plant fibre, paper, dry grass...) and fuel (sticks, logs, coal...),
 * - you light it with a bow drill or flint and steel through a little timing game,
 * - it burns through its fuel and dies down to embers, which relight if you add fuel in time,
 * - rain puts it out unless it has a roof over it, and water or a shovel douses it,
 * - small, medium or large: bigger fires burn fuel faster, give more heat, smoke and light, cook faster, and hurt more.
 * It keeps the vanilla campfire's cooking slots.
 */
public class RealisticCampfireBlock extends CampfireBlock {
    /** 1 small, 2 medium, 3 large. */
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 1, 3);
    /** Out of fuel but still glowing. */
    public static final BooleanProperty EMBERS = BooleanProperty.create("embers");
    public static final int EMBER_TICKS = 20 * 45;

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(BlockPos pos, long time) {}

    private static RecipeManager.@Nullable CachedCheck<SingleRecipeInput, CampfireCookingRecipe> cookCheck;

    public RealisticCampfireBlock(Properties properties) {
        super(false, 1, properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(SIZE, 2).setValue(EMBERS, false));
    }

    public static int lightLevel(BlockState state) {
        if (state.getValue(LIT)) return state.getValue(SIZE) == 1 ? 11 : state.getValue(SIZE) == 2 ? 14 : 15;
        return state.getValue(EMBERS) ? 4 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SIZE, EMBERS);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : state.setValue(LIT, false).setValue(EMBERS, false).setValue(SIZE, 2);
    }

    // ---- fuel ------------------------------------------------------------------------------------

    /** Ticks a piece of fuel burns on a medium fire. */
    public static int fuelTicks(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.COAL_BLOCK)) return 20 * 60 * 60;
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 20 * 60 * 6;
        if (stack.is(ModRegistry.FIRE_STARTER.get())) return 20 * 60 * 4;
        if (stack.is(ItemTags.LOGS_THAT_BURN)) return 20 * 120;
        if (stack.is(ItemTags.PLANKS)) return 20 * 40;
        if (stack.is(ItemTags.WOODEN_SLABS)) return 20 * 20;
        if (stack.is(Items.STICK)) return 20 * 15;
        if (stack.is(ItemTags.SAPLINGS) || stack.is(Items.BAMBOO) || stack.is(Items.DRIED_KELP_BLOCK)) return 20 * 8;
        if (stack.is(Items.BLAZE_ROD)) return 20 * 60 * 3;
        if (stack.has(DataComponents.COOKING_FUEL)) return 20 * 20;
        return 0;
    }

    public static boolean isKindling(ItemStack stack) {
        return stack.is(ModRegistry.KINDLING);
    }

    /** Fuel used per tick: small fires sip it, large ones gulp it. */
    public static float burnRate(int size) {
        float r = size == 1 ? 0.5F : size == 2 ? 1.0F : 2.5F;
        return r * (float) Config.CAMPFIRE_FUEL_RATE.getAsDouble();
    }

    public static CampfireData data(CampfireBlockEntity be) {
        return be.getData(ModRegistry.CAMPFIRE_DATA);
    }

    /** Seconds the fire can still burn at its current size with what it holds. */
    public static int secondsLeft(CampfireData d, int size) {
        float total = Math.max(0.0F, d.burn) + (float) fuelTicks(d.fuel) * d.fuel.getCount();
        return Math.round(total / burnRate(size) / 20.0F);
    }

    private static boolean takeFuel(CampfireData d) {
        int ticks = fuelTicks(d.fuel);
        if (ticks <= 0) return false;
        d.burn += ticks;
        d.burnTotal = d.burn;
        var template = d.fuel.getItem().getCraftingRemainder();
        ItemStack remainder = template == null ? ItemStack.EMPTY : template.create();
        d.fuel.shrink(1);
        if (d.fuel.isEmpty() && !remainder.isEmpty()) d.fuel = remainder;
        return true;
    }

    // ---- ticking ---------------------------------------------------------------------------------

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level instanceof ServerLevel) {
            return createTickerHelper(type, BlockEntityTypes.CAMPFIRE, (l, pos, st, be) -> serverTick((ServerLevel) l, pos, st, be));
        }
        if (state.getValue(LIT)) {
            return createTickerHelper(type, BlockEntityTypes.CAMPFIRE, RealisticCampfireBlock::clientTick);
        }
        return null;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, CampfireBlockEntity be) {
        if (!(state.getBlock() instanceof RealisticCampfireBlock)) return;
        CampfireData d = data(be);
        int size = state.getValue(SIZE);
        boolean everySecond = (level.getGameTime() + pos.asLong()) % 20 == 0;

        if (state.getValue(LIT)) {
            // cooking: a small fire cooks at half speed, a big one at double
            if (cookCheck == null) cookCheck = RecipeManager.createCheck(RecipeType.CAMPFIRE_COOKING);
            int cooks = size == 1 ? (level.getGameTime() % 2 == 0 ? 1 : 0) : size == 2 ? 1 : 2;
            for (int i = 0; i < cooks; i++) CampfireBlockEntity.cookTick(level, pos, state, be, cookCheck);

            if (d.burn <= 0.0F && !takeFuel(d)) {
                // out of fuel: it dies down to embers
                d.embers = EMBER_TICKS;
                d.burn = 0.0F;
                level.setBlock(pos, state.setValue(LIT, false).setValue(EMBERS, true), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 0.6F);
                be.setChanged();
                return;
            }
            d.burn -= burnRate(size);

            if (everySecond) {
                be.setChanged();
                if (level.isRainingAt(pos.above())) {
                    float chance = size == 1 ? 0.2F : size == 2 ? 0.08F : 0.03F;
                    if (level.getRandom().nextFloat() < chance) douse(level, pos, state, d);
                }
            }
        } else {
            CampfireBlockEntity.cooldownTick(level, pos, state, be);
            if (d.embers > 0) {
                d.embers--;
                if (everySecond && level.isRainingAt(pos.above())) d.embers = 0;
                if (d.embers > 0 && fuelTicks(d.fuel) > 0 && !state.getValue(WATERLOGGED)) {
                    // fresh fuel on hot embers catches by itself
                    takeFuel(d);
                    d.embers = 0;
                    level.setBlock(pos, state.setValue(LIT, true).setValue(EMBERS, false), Block.UPDATE_ALL);
                    level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 0.8F);
                } else if (d.embers <= 0) {
                    level.setBlock(pos, state.setValue(EMBERS, false), Block.UPDATE_ALL);
                }
                if (everySecond) be.setChanged();
            } else if (state.getValue(EMBERS)) {
                level.setBlock(pos, state.setValue(EMBERS, false), Block.UPDATE_ALL);
            }
        }
    }

    private static void clientTick(Level level, BlockPos pos, BlockState state, CampfireBlockEntity be) {
        int size = state.getValue(SIZE);
        if (size == 1 && level.getGameTime() % 2 == 0) return;
        CampfireBlockEntity.particleTick(level, pos, state, be);
        if (size == 3) {
            CampfireBlockEntity.particleTick(level, pos, state, be);
            RandomSource r = level.getRandom();
            if (r.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.3 + r.nextDouble() * 0.4, pos.getY() + 0.5, pos.getZ() + 0.3 + r.nextDouble() * 0.4, 0, 0.04, 0);
            }
            if (r.nextInt(6) == 0) {
                level.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0, 0);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (!state.getValue(LIT) && state.getValue(EMBERS)) {
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.2, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.02, 0);
            }
            if (random.nextInt(12) == 0) {
                level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.1, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.005, 0);
            }
        }
    }

    // ---- lighting and putting out ---------------------------------------------------------------

    /** Why a fire can't be lit right now, or null if it can. */
    public static @Nullable String notReadyReason(BlockState state, CampfireData d) {
        if (state.getValue(LIT)) return "campfire.lit";
        if (state.getValue(WATERLOGGED)) return "campfire.wet";
        if (d.kindling.isEmpty()) return "campfire.no_kindling";
        if (d.burn <= 0.0F && fuelTicks(d.fuel) <= 0) return "campfire.no_fuel";
        return null;
    }

    /** Light it, using up one piece of kindling. Returns false if it wasn't ready. */
    public static boolean light(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RealisticCampfireBlock) || !(level.getBlockEntity(pos) instanceof CampfireBlockEntity be)) return false;
        CampfireData d = data(be);
        if (notReadyReason(state, d) != null) return false;
        d.kindling.shrink(1);
        if (d.burn <= 0.0F) takeFuel(d);
        d.embers = 0;
        level.setBlock(pos, state.setValue(LIT, true).setValue(EMBERS, false), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
        be.setChanged();
        return true;
    }

    private static void douse(ServerLevel level, BlockPos pos, BlockState state, CampfireData d) {
        d.embers = 0;
        level.setBlock(pos, state.setValue(LIT, false).setValue(EMBERS, false), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.02);
    }

    /** Is this something you can start a fire with by friction or sparks? */
    public static boolean isFireStarter(ItemStack stack) {
        return stack.is(Items.FLINT_AND_STEEL) || stack.is(ModRegistry.FLINT_STRIKER.get()) || stack.is(ModRegistry.BOW_DRILL.get());
    }

    /** Hits needed in the lighting game: a fire starter as kindling takes one fewer (never under 2). */
    public static int hitsFor(ItemStack tool, ItemStack kindling) {
        int hits = tool.is(ModRegistry.BOW_DRILL.get()) ? 4 : 3;
        return kindling.is(ModRegistry.FIRE_STARTER.get()) ? Math.max(2, hits - 1) : hits;
    }

    /** How wide the sweet spot is: a fire starter as kindling makes it half as wide again. */
    public static float zoneFor(ItemStack tool, ItemStack kindling) {
        float zone = tool.is(ModRegistry.BOW_DRILL.get()) ? 0.16F : 0.22F;
        return kindling.is(ModRegistry.FIRE_STARTER.get()) ? zone * 1.5F : zone;
    }

    /** Start the lighting game for a player: checks the fire is ready, then tells their client to open it. */
    public static void startLighting(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        BlockState state = level.getBlockState(pos);
        if (!(level.getBlockEntity(pos) instanceof CampfireBlockEntity be)) return;
        String reason = notReadyReason(state, data(be));
        if (reason != null) {
            player.sendOverlayMessage(Component.translatable("message.realisticsurvival." + reason));
            return;
        }
        ItemStack tool = findStarter(player);
        if (tool.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.realisticsurvival.campfire.no_starter"));
            return;
        }
        boolean bowDrill = tool.is(ModRegistry.BOW_DRILL.get());
        ItemStack kindling = data(be).kindling;
        int hits = hitsFor(tool, kindling);
        float zone = zoneFor(tool, kindling);
        boolean wetWeather = level.isRainingAt(pos.above());
        if (wetWeather) zone *= 0.6F;
        PENDING.put(player.getUUID(), new Pending(pos.immutable(), level.getGameTime()));
        PacketDistributor.sendToPlayer(player, new CampfireNet.OpenLighting(pos, hits, zone, bowDrill, wetWeather));
    }

    /** The player's client says how the lighting game went. */
    public static void finishLighting(ServerPlayer player, BlockPos pos, boolean success) {
        Pending pending = PENDING.remove(player.getUUID());
        ServerLevel level = (ServerLevel) player.level();
        if (pending == null || !pending.pos().equals(pos)) return;
        long elapsed = level.getGameTime() - pending.time();
        if (elapsed > 20 * 120 || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 64) return;
        ItemStack tool = findStarter(player);
        if (tool.isEmpty()) return;
        tool.hurtAndBreak(1, level, player, item -> {});
        if (success && elapsed >= 10 && light(level, pos)) {
            player.sendOverlayMessage(Component.translatable("message.realisticsurvival.campfire.caught"));
        } else {
            player.sendOverlayMessage(Component.translatable("message.realisticsurvival.campfire.failed"));
        }
    }

    private static ItemStack findStarter(Player player) {
        if (isFireStarter(player.getMainHandItem())) return player.getMainHandItem();
        if (isFireStarter(player.getOffhandItem())) return player.getOffhandItem();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (isFireStarter(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    public static boolean hasStarter(Player player) {
        return !findStarter(player).isEmpty();
    }

    // ---- interaction -----------------------------------------------------------------------------

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CampfireBlockEntity be)) return InteractionResult.PASS;
        // food goes on the grill; shovels douse it (vanilla behaviour)
        if (level.recipeAccess().propertySet(RecipePropertySet.CAMPFIRE_INPUT).test(stack)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        CampfireData d = data(be);
        if (stack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHOVEL_DOUSE) && (state.getValue(LIT) || state.getValue(EMBERS))) {
            if (level instanceof ServerLevel sl) {
                douse(sl, pos, state, d);
                stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
            }
            return InteractionResult.SUCCESS;
        }
        if (isFireStarter(stack)) {
            if (player instanceof ServerPlayer sp) startLighting(sp, pos);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.FIRE_CHARGE)) {
            if (!level.isClientSide()) {
                String reason = notReadyReason(state, d);
                if (reason == null && light(level, pos)) {
                    stack.consume(1, player);
                } else if (reason != null) {
                    player.sendOverlayMessage(Component.translatable("message.realisticsurvival." + reason));
                }
            }
            return InteractionResult.SUCCESS;
        }
        // put kindling or fuel straight on
        if (isKindling(stack) && insert(d, true, stack, player) || fuelTicks(stack) > 0 && insert(d, false, stack, player)) {
            if (!level.isClientSide()) {
                be.setChanged();
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    private static boolean insert(CampfireData d, boolean kindling, ItemStack held, Player player) {
        ItemStack slot = kindling ? d.kindling : d.fuel;
        int max = held.getMaxStackSize();
        if (!slot.isEmpty() && (!ItemStack.isSameItemSameComponents(slot, held) || slot.getCount() >= max)) return false;
        if (player.level().isClientSide()) return true;
        int move = player.isShiftKeyDown() ? Math.min(held.getCount(), max - slot.getCount()) : 1;
        if (slot.isEmpty()) {
            ItemStack copy = held.copyWithCount(move);
            if (kindling) d.kindling = copy; else d.fuel = copy;
        } else {
            slot.grow(move);
        }
        if (!player.getAbilities().instabuild) held.shrink(move);
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CampfireBlockEntity be)) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) {
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> CampfireMenu.forCampfire(id, inv, be, pos),
                    Component.translatable("container.realisticsurvival.campfire")), pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean isPrecise) {
        if (state.getValue(LIT) && entity instanceof LivingEntity && level instanceof ServerLevel sl) {
            int size = state.getValue(SIZE);
            entity.hurtServer(sl, level.damageSources().campfire(), size == 3 ? 2.0F : 1.0F);
            if (size == 3) entity.igniteForSeconds(4.0F);
        }
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (level instanceof ServerLevel sl && projectile.isOnFire() && projectile.mayInteract(sl, hit.getBlockPos())) {
            light(level, hit.getBlockPos());
        }
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        boolean placed = super.placeLiquid(level, pos, state, fluid);
        if (placed) {
            BlockState now = level.getBlockState(pos);
            if (now.getBlock() == this) level.setBlock(pos, now.setValue(EMBERS, false), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof CampfireBlockEntity be) data(be).embers = 0;
        }
        return placed;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CampfireBlockEntity be) {
            CampfireData d = data(be);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), d.kindling);
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), d.fuel);
            d.kindling = ItemStack.EMPTY;
            d.fuel = ItemStack.EMPTY;
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
