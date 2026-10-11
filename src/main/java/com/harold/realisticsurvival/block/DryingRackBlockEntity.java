package com.harold.realisticsurvival.block;

import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.survival.Spoilage;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Four hooks. Meat, fish and fruit hung here:
 * <ul>
 * <li>over a campfire (directly below, or two below with air between) are smoked: raw meat and
 *     fish come out cooked and smoked (keeps 8x as long), fruit comes out dried;</li>
 * <li>in the open on a dry day they dry: meat to jerky, fish to dried fish, fruit to dried fruit;</li>
 * <li>in the shade or at night they dry slowly, and rain soaks them and undoes some of the work.</li>
 * </ul>
 * They keep ageing while they hang, so meat left too long goes off before it's done.
 */
public class DryingRackBlockEntity extends BlockEntity {
    public static final int SLOTS = 4;
    public static final int MAX_PER_HOOK = 4;
    private static final int INTERVAL = 20;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final int[] progress = new int[SLOTS];

    public DryingRackBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.DRYING_RACK_BE.get(), pos, state);
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public int progress(int slot) {
        return progress[slot];
    }

    // ---- what can hang here --------------------------------------------------------------------

    public static boolean isMeat(ItemStack s) {
        return s.is(ItemTags.MEAT) && !s.is(Items.ROTTEN_FLESH);
    }

    public static boolean isFish(ItemStack s) {
        return s.is(ItemTags.FISHES) && !s.is(Items.PUFFERFISH);
    }

    public static boolean isFruit(ItemStack s) {
        return s.is(Items.APPLE) || s.is(Items.SWEET_BERRIES) || s.is(Items.GLOW_BERRIES) || s.is(Items.MELON_SLICE);
    }

    public static boolean accepts(ItemStack s) {
        return isMeat(s) || isFish(s) || isFruit(s);
    }

    /** Smoke from a fire under the rack. */
    public static boolean smoky(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (fire(below)) return true;
        return below.isAir() && fire(level.getBlockState(pos.below(2)));
    }

    private static boolean fire(BlockState s) {
        if (!s.is(BlockTags.CAMPFIRES)) return false;
        if (s.getValue(BlockStateProperties.LIT)) return true;
        return s.hasProperty(RealisticCampfireBlock.EMBERS) && s.getValue(RealisticCampfireBlock.EMBERS);
    }

    /** Ticks of good conditions to finish, or 0 if nothing would happen. */
    public static int timeNeeded(ItemStack s, boolean smoking) {
        if (smoking) {
            if (isFruit(s)) return 6000;
            if (Spoilage.smoked(s)) return 0;
            boolean raw = Spoilage.rawRisk(s) > 0.0F;
            return raw ? 6000 : 3000;
        }
        if (isFruit(s)) return 9000;
        return isMeat(s) || isFish(s) ? 12000 : 0;
    }

    /** What a hook's food turns into. */
    public static ItemStack result(ServerLevel level, ItemStack s, boolean smoking) {
        long now = level.getGameTime();
        ItemStack out;
        if (smoking && !isFruit(s)) {
            out = s.copy();
            Optional<RecipeHolder<SmokingRecipe>> cook = level.getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.SMOKING, new SingleRecipeInput(s.copyWithCount(1)), level);
            if (cook.isPresent()) {
                ItemStack cooked = cook.get().value().assemble(new SingleRecipeInput(s.copyWithCount(1)));
                out = cooked.copyWithCount(s.getCount());
                out.set(ModRegistry.PRESERVED.get(), Spoilage.preserved(s));
            }
            out.set(ModRegistry.PRESERVED.get(), Spoilage.preserved(out) | Spoilage.SMOKED);
        } else if (isFruit(s)) {
            out = new ItemStack(ModRegistry.DRIED_FRUIT.get(), s.getCount());
        } else if (isFish(s)) {
            out = new ItemStack(ModRegistry.DRIED_FISH.get(), s.getCount());
        } else {
            out = new ItemStack(ModRegistry.JERKY.get(), s.getCount());
        }
        out.remove(ModRegistry.FOOD_COLD.get());
        out.set(ModRegistry.FOOD_START.get(), Spoilage.round(now));
        return out;
    }

    // ---- ticking -------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, DryingRackBlockEntity rack) {
        if (!(level instanceof ServerLevel sl) || (level.getGameTime() + pos.asLong()) % INTERVAL != 0) return;
        rack.work(sl, INTERVAL);
    }

    public enum Weather { SMOKE, SUN, RAIN, SHADE }

    /** What the rack is doing right now. */
    public static Weather weather(Level level, BlockPos pos) {
        if (smoky(level, pos)) return Weather.SMOKE;
        if (!level.canSeeSky(pos.above())) return Weather.SHADE;
        if (level.isRainingAt(pos.above())) return Weather.RAIN;
        return level.isBrightOutside() ? Weather.SUN : Weather.SHADE;
    }

    /** Advances the rack by {@code ticks}. Returns true if anything changed. */
    public boolean work(ServerLevel level, int ticks) {
        return work(level, ticks, weather(level, worldPosition));
    }

    public boolean work(ServerLevel level, int ticks, Weather weather) {
        long now = level.getGameTime();
        boolean smoking = weather == Weather.SMOKE;
        boolean rain = weather == Weather.RAIN;
        boolean sunny = weather == Weather.SUN;
        boolean changed = false;
        boolean dirty = false;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            ItemStack aged = Spoilage.update(s, now);
            if (aged != s) {
                changed |= !ItemStack.isSameItem(aged, s);
                items.set(i, aged);
                s = aged;
                dirty = true;
            }
            int need = timeNeeded(s, smoking);
            if (need <= 0) continue;
            int rate;
            if (smoking) rate = ticks;
            else if (rain) rate = -ticks * 2;
            else if (sunny) rate = ticks;
            else rate = ticks / 4;
            int before = progress[i];
            progress[i] = Math.max(0, progress[i] + rate);
            if (progress[i] != before) dirty = true;
            if (progress[i] >= need) {
                items.set(i, result(level, s, smoking));
                progress[i] = 0;
                changed = true;
            }
        }
        if (changed) markUpdated();
        else if (dirty) setChanged();
        return changed;
    }

    // ---- hooks ---------------------------------------------------------------------------------

    /** Hangs one piece. Returns false if there's no room. */
    public boolean hang(ItemStack piece) {
        for (int i = 0; i < SLOTS; i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, piece) && s.getCount() < MAX_PER_HOOK) {
                // the new piece is wet: the hook's progress is shared out
                progress[i] = progress[i] * s.getCount() / (s.getCount() + 1);
                s.grow(1);
                markUpdated();
                return true;
            }
        }
        for (int i = 0; i < SLOTS; i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, piece.copyWithCount(1));
                progress[i] = 0;
                markUpdated();
                return true;
            }
        }
        return false;
    }

    /** Takes down the last hook's food. */
    public ItemStack takeLast() {
        for (int i = SLOTS - 1; i >= 0; i--) {
            ItemStack s = items.get(i);
            if (!s.isEmpty()) {
                items.set(i, ItemStack.EMPTY);
                progress[i] = 0;
                markUpdated();
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    public void markUpdated() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, items);
    }

    // ---- saving and syncing --------------------------------------------------------------------

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.clear();
        ContainerHelper.loadAllItems(input, items);
        java.util.Arrays.fill(progress, 0);
        input.getIntArray("Progress").ifPresent(p -> System.arraycopy(p, 0, progress, 0, Math.min(SLOTS, p.length)));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items, true);
        output.putIntArray("Progress", progress);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), com.harold.realisticsurvival.RealisticSurvival.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            ContainerHelper.saveAllItems(output, items, true);
            return output.buildResult();
        }
    }
}
