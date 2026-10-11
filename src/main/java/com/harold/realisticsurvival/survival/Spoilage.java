package com.harold.realisticsurvival.survival;

import com.harold.realisticsurvival.Config;
import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.RealisticSurvival;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Food freshness. A food stack remembers when it was made (rounded to a quarter of a day so food
 * gathered around the same time still stacks). Its progress towards rotten is the time since then
 * divided by its shelf life; drying, smoking and salting make the shelf life much longer, and food
 * kept in a cold container ages more slowly.
 *
 * Nothing ticks the food itself: it is brought up to date whenever it's looked at (your inventory
 * every two seconds, a container when it's opened, a drying rack every second).
 */
public final class Spoilage {
    public static final long DAY = 24000L;
    /** Starting times are rounded down to this so stacks gathered close together still merge. */
    public static final long BUCKET = 6000L;
    public static final int SALTED = 1, SMOKED = 2;

    public static final TagKey<Item> NEVER_SPOILS = TagKey.create(Registries.ITEM, RealisticSurvival.id("never_spoils"));

    public enum Stage { FRESH, STALE, GOING_OFF, ROTTEN }

    /** Kept in a cold place since {@code since}, ageing at {@code rate} (0..1) of the normal speed. */
    public record Cold(long since, float rate) {
        public static final Codec<Cold> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("since").forGetter(Cold::since),
                Codec.FLOAT.fieldOf("rate").forGetter(Cold::rate)).apply(i, Cold::new));
        public static final StreamCodec<ByteBuf, Cold> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Cold::since, ByteBufCodecs.FLOAT, Cold::rate, Cold::new);
    }

    // ---- shelf lives (in-game days) ------------------------------------------------------------
    private static final Map<Item, Float> DAYS = new HashMap<>();
    /** Chance of food poisoning when eaten raw. */
    private static final Map<Item, Float> RAW_RISK = new HashMap<>();

    private static void days(float d, Item... items) {
        for (Item i : items) DAYS.put(i, d);
    }

    private static Map<Item, Float> days() {
        if (DAYS.isEmpty()) {
            days(3, Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.RABBIT);
            days(2, Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH);
            days(5, Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.COOKED_MUTTON, Items.COOKED_CHICKEN, Items.COOKED_RABBIT);
            days(4, Items.COOKED_COD, Items.COOKED_SALMON);
            days(10, Items.BREAD);
            days(15, Items.COOKIE);
            days(7, Items.PUMPKIN_PIE);
            days(14, Items.APPLE, Items.BEETROOT);
            days(20, Items.CARROT);
            days(25, Items.POTATO, Items.POISONOUS_POTATO);
            days(6, Items.BAKED_POTATO);
            days(4, Items.MELON_SLICE, Items.MUSHROOM_STEW, Items.RABBIT_STEW, Items.BEETROOT_SOUP, Items.SUSPICIOUS_STEW);
            days(5, Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.SPIDER_EYE);
            days(30, Items.CHORUS_FRUIT);
            days(40, ModRegistry.JERKY.get(), ModRegistry.DRIED_FISH.get(), ModRegistry.DRIED_FRUIT.get());

            RAW_RISK.put(Items.CHICKEN, 0.6F);
            RAW_RISK.put(Items.PORKCHOP, 0.35F);
            RAW_RISK.put(Items.BEEF, 0.25F);
            RAW_RISK.put(Items.MUTTON, 0.25F);
            RAW_RISK.put(Items.RABBIT, 0.3F);
            RAW_RISK.put(Items.COD, 0.2F);
            RAW_RISK.put(Items.SALMON, 0.2F);
            RAW_RISK.put(Items.TROPICAL_FISH, 0.3F);
            RAW_RISK.put(Items.ROTTEN_FLESH, 0.5F);
            RAW_RISK.put(ModRegistry.ROTTEN_FOOD.get(), 1.0F);
        }
        return DAYS;
    }

    /** Food you haven't cooked: the chance it makes you ill. */
    public static float rawRisk(ItemStack stack) {
        days();
        return RAW_RISK.getOrDefault(stack.getItem(), 0.0F);
    }

    public static boolean spoils(ItemStack stack) {
        if (stack.isEmpty() || !Config.SPOILAGE.getAsBoolean()) return false;
        if (!stack.has(DataComponents.FOOD) || stack.is(NEVER_SPOILS)) return false;
        return baseDays(stack) > 0;
    }

    /** Unlisted foods (other mods) keep about a week. */
    public static float baseDays(ItemStack stack) {
        return days().getOrDefault(stack.getItem(), 7.0F);
    }

    public static int preserved(ItemStack stack) {
        return stack.getOrDefault(ModRegistry.PRESERVED.get(), 0);
    }

    public static boolean salted(ItemStack stack) {
        return (preserved(stack) & SALTED) != 0;
    }

    public static boolean smoked(ItemStack stack) {
        return (preserved(stack) & SMOKED) != 0;
    }

    /** Shelf life in ticks. */
    public static long lifetime(ItemStack stack) {
        int p = preserved(stack);
        float mult = (p & SALTED) != 0 && (p & SMOKED) != 0 ? 12.0F : (p & SMOKED) != 0 ? 8.0F : (p & SALTED) != 0 ? 6.0F : 1.0F;
        double rate = Math.max(0.1, Config.SPOILAGE_RATE.getAsDouble());
        return Math.max(1L, (long) (baseDays(stack) * DAY * mult / rate));
    }

    public static long round(long time) {
        return Math.floorDiv(time, BUCKET) * BUCKET;
    }

    /** 0 = just made, 1 = rotten. */
    public static float progress(ItemStack stack, long now) {
        if (!spoils(stack)) return 0.0F;
        Long start = stack.get(ModRegistry.FOOD_START.get());
        if (start == null) return 0.0F;
        double age = now - start;
        Cold cold = stack.get(ModRegistry.FOOD_COLD.get());
        if (cold != null && now > cold.since()) age -= (now - cold.since()) * (1.0 - cold.rate());
        return (float) Math.max(0.0, age / lifetime(stack));
    }

    public static Stage stage(float progress) {
        if (progress >= 1.0F) return Stage.ROTTEN;
        if (progress >= 0.8F) return Stage.GOING_OFF;
        if (progress >= 0.5F) return Stage.STALE;
        return Stage.FRESH;
    }

    /** Ticks until it rots at the speed it's ageing now. */
    public static long ticksLeft(ItemStack stack, long now) {
        float p = progress(stack, now);
        Cold cold = stack.get(ModRegistry.FOOD_COLD.get());
        double speed = cold == null ? 1.0 : Math.max(0.05, cold.rate());
        return (long) (Math.max(0.0F, 1.0F - p) * lifetime(stack) / speed);
    }

    /** What a food turns into when it rots. */
    public static ItemStack rotten(ItemStack stack) {
        boolean flesh = stack.is(ItemTags.MEAT) || stack.is(ItemTags.FISHES) || stack.is(ModRegistry.JERKY.get()) || stack.is(ModRegistry.DRIED_FISH.get());
        return new ItemStack(flesh ? Items.ROTTEN_FLESH : ModRegistry.ROTTEN_FOOD.get(), stack.getCount());
    }

    /**
     * Brings a stack up to date: gives new food a start time, settles time spent in a cold place,
     * and turns rotten food into rotten flesh / rotten food. Returns the same stack when nothing
     * changed, so callers can compare with ==.
     */
    public static ItemStack update(ItemStack stack, long now) {
        if (!spoils(stack)) return stack;
        Long start = stack.get(ModRegistry.FOOD_START.get());
        Cold cold = stack.get(ModRegistry.FOOD_COLD.get());
        if (start != null && cold == null && progress(stack, now) < 1.0F) return stack;
        ItemStack out = stack.copy();
        if (start == null) out.set(ModRegistry.FOOD_START.get(), round(now));
        if (cold != null) settleCold(out, now);
        if (progress(out, now) >= 1.0F) return rotten(out);
        return out;
    }

    /** Takes a stack out of cold storage: the time it saved is added to its start time. */
    public static void settleCold(ItemStack stack, long now) {
        Cold cold = stack.remove(ModRegistry.FOOD_COLD.get());
        Long start = stack.get(ModRegistry.FOOD_START.get());
        if (cold == null || start == null || now <= cold.since()) return;
        long saved = (long) ((now - cold.since()) * (1.0 - cold.rate()));
        stack.set(ModRegistry.FOOD_START.get(), round(start + saved));
    }

    /** Puts a food stack in cold storage (container closed in a cold place). */
    public static void markCold(ItemStack stack, long now, float rate) {
        if (!spoils(stack) || rate >= 1.0F) return;
        if (!stack.has(ModRegistry.FOOD_START.get())) stack.set(ModRegistry.FOOD_START.get(), round(now));
        if (!stack.has(ModRegistry.FOOD_COLD.get())) stack.set(ModRegistry.FOOD_COLD.get(), new Cold(now, rate));
    }

    /**
     * How fast food ages in a container here: next to ice or snow 0.25 (blue ice 0.15), in a
     * snowy biome 0.4, in a cellar well below ground 0.6, otherwise 1.
     */
    public static float coldness(Level level, BlockPos pos) {
        float rate = 1.0F;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.BLUE_ICE)) rate = Math.min(rate, 0.15F);
            else if (s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) rate = Math.min(rate, 0.25F);
        }
        if (level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel())) rate = Math.min(rate, 0.4F);
        if (pos.getY() <= level.getSeaLevel() - 3 && !level.canSeeSky(pos.above())) rate = Math.min(rate, 0.6F);
        return rate;
    }

    private Spoilage() {}
}
