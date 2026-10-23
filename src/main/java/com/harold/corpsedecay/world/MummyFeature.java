package com.harold.corpsedecay.world;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.block.FossilBlocks;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Bodies that never rotted. In swamps, bog bodies: people (and now and then an animal) who went into
 * the peat long ago and came out tanned dark as old leather, still in their skin caps and cloaks,
 * lying sunk in the mud. In frozen places, mummies freeze-dried in the ice: hunters in their furs
 * with an axe or bow, sometimes an arrow still in their back, or animals caught by the cold, some
 * lying in the snow and some still locked in a lump of clear ice.
 */
public class MummyFeature extends Feature<NoneFeatureConfiguration> {
    public enum Where { BOG, ICE }

    private final Where where;

    public MummyFeature(Where where) {
        super(NoneFeatureConfiguration.CODEC);
        this.where = where;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.REMAINS.get()) return false;
        WorldGenLevel level = ctx.level();
        BlockPos floor = ctx.origin().below();
        BlockState ground = level.getBlockState(floor);
        if (!ground.isSolid() || ground.is(BlockTags.LEAVES) || ground.is(BlockTags.LOGS)) return false;
        return (where == Where.BOG ? bogBody(level, floor, ctx.random()) : iceMummy(level, floor, ctx.random(), ctx.random().nextFloat() < 0.55F)) != null;
    }

    // ---- bog bodies ---------------------------------------------------------------------------------

    private static final EntityType<?>[] BOG_ANIMALS = {EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.HORSE,
            EntityType.WOLF, EntityType.FOX, EntityType.GOAT};

    /** A bog body lying in a patch of mud at {@code floor}; returns it, or null. */
    public static CorpseEntity bogBody(WorldGenLevel level, BlockPos floor, RandomSource rand) {
        // the peat it lay in: mud round about, under water or not
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz > 6 + rand.nextInt(4)) continue;
                BlockPos p = floor.offset(dx, 0, dz);
                BlockState s = level.getBlockState(p);
                if (s.is(BlockTags.DIRT) || s.is(Blocks.CLAY) || s.is(Blocks.GRAVEL) || s.is(BlockTags.SAND)) {
                    level.setBlock(p, Blocks.MUD.defaultBlockState(), 2);
                    if (level.getBlockState(p.above()).is(Blocks.GRASS) || level.getBlockState(p.above()).is(Blocks.FERN)) {
                        level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
        boolean person = rand.nextFloat() < 0.72F;
        EntityType<?> mob = person ? (rand.nextFloat() < 0.8F ? EntityType.VILLAGER : EntityType.PILLAGER)
                : BOG_ANIMALS[rand.nextInt(BOG_ANIMALS.length)];
        boolean wet = !level.getFluidState(floor.above()).isEmpty();
        double sink = wet ? 0.45 + rand.nextDouble() * 0.2 : 0.35 + rand.nextDouble() * 0.15;
        CorpseEntity body = RemainsFeature.placeBody(level.getLevel(), level, floor, mob, rand, CorpseEntity.BOG_MUMMY, sink);
        if (body == null) return null;
        if (person) {
            // a pointed skin cap, a leather cape, sometimes a belt and shoes
            if (rand.nextFloat() < 0.6F) body.giveGear(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, 0x5A3E26 + rand.nextInt(0x101010)));
            if (rand.nextFloat() < 0.45F) body.giveGear(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, 0x4E3A28));
            if (rand.nextFloat() < 0.3F) body.giveGear(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, 0x3E2C1C));
            if (rand.nextFloat() < 0.15F) body.addOldArrow(rand);
        }
        if (!wet) {
            body.addGrowth(Blocks.MOSS_CARPET, 0.45F, rand);
            if (rand.nextBoolean()) body.addGrowth(Blocks.BROWN_MUSHROOM, 0.5F, rand);
        } else {
            // lily pads on the water over it
            for (int i = 0; i < 2 + rand.nextInt(3); i++) {
                BlockPos p = surface(level, floor.offset(rand.nextInt(5) - 2, 1, rand.nextInt(5) - 2));
                if (p != null) level.setBlock(p, Blocks.LILY_PAD.defaultBlockState(), 2);
            }
        }
        level.addFreshEntity(body);
        return body;
    }

    /** The air block on top of the water column at {@code p}, if it's open to the air. */
    private static BlockPos surface(WorldGenLevel level, BlockPos p) {
        BlockPos.MutableBlockPos m = p.mutable();
        for (int i = 0; i < 6 && !level.getFluidState(m).isEmpty(); i++) m.move(0, 1, 0);
        if (!level.getBlockState(m).isAir() || !level.getBlockState(m.below()).is(Blocks.WATER)) return null;
        return m.immutable();
    }

    // ---- frozen mummies ------------------------------------------------------------------------------

    private static final EntityType<?>[] ICE_ANIMALS = {EntityType.WOLF, EntityType.FOX, EntityType.POLAR_BEAR, EntityType.GOAT,
            EntityType.HORSE, EntityType.LLAMA, EntityType.RABBIT, EntityType.SHEEP};

    /**
     * A freeze-dried body at {@code floor}: lying in the snow, or (if {@code inIce}) locked in a lump
     * of clear ice half sunk in the ground. Returns it, or null.
     */
    public static CorpseEntity iceMummy(WorldGenLevel level, BlockPos floor, RandomSource rand, boolean inIce) {
        boolean person = rand.nextFloat() < 0.55F;
        EntityType<?> mob = person ? (rand.nextFloat() < 0.75F ? EntityType.VILLAGER : EntityType.PILLAGER)
                : ICE_ANIMALS[rand.nextInt(ICE_ANIMALS.length)];
        CorpseEntity body = RemainsFeature.placeBody(level.getLevel(), level, floor, mob, rand, CorpseEntity.ICE_MUMMY,
                inIce ? 0.04 : 0.08 + rand.nextDouble() * 0.1);
        if (body == null) return null;
        if (person) {
            // furs, and what a hunter carried
            int fur = rand.nextBoolean() ? 0x8A6A48 : 0xC8B8A0;
            Item[] furs = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < 4; i++) if (rand.nextFloat() < 0.7F) body.giveGear(slots[i], dyed(furs[i], fur));
            float r = rand.nextFloat();
            if (r < 0.4F) body.giveGear(EquipmentSlot.MAINHAND, new ItemStack(FossilBlocks.FLINT_HAND_AXE.get()));
            else if (r < 0.7F) body.giveGear(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            if (rand.nextFloat() < 0.3F) body.addOldArrow(rand); // shot in the back, like the iceman
        }
        if (inIce) {
            encase(level, body, rand);
        } else {
            for (int i = 0; i < 2; i++) body.addGrowth(Blocks.SNOW, 0.6F, rand);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = floor.offset(dx, 1, dz);
                    if (rand.nextFloat() < 0.6F && level.getBlockState(p).isAir() && Blocks.SNOW.defaultBlockState().canSurvive(level, p)) {
                        level.setBlock(p, Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS,
                                1 + rand.nextInt(2)), 2);
                    }
                }
            }
        }
        level.addFreshEntity(body);
        return body;
    }

    /** A block of clear ice frozen round the body where it lies, with the odd chunk of packed ice. */
    private static void encase(WorldGenLevel level, CorpseEntity body, RandomSource rand) {
        var box = body.getBoundingBox();
        int floorY = (int) Math.floor(box.minY + 0.05);
        int top = (int) Math.floor(box.maxY + 0.6);
        int ax = (int) Math.floor(box.minX - 0.6), bx = (int) Math.floor(box.maxX + 0.6);
        int az = (int) Math.floor(box.minZ - 0.6), bz = (int) Math.floor(box.maxZ + 0.6);
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                boolean edgeX = x == ax || x == bx, edgeZ = z == az || z == bz;
                int h = top - (edgeX && edgeZ ? 1 : 0) - (rand.nextFloat() < 0.3F ? 1 : 0);
                for (int y = floorY; y <= Math.max(floorY, h); y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState s = level.getBlockState(p);
                    if (s.isAir() || s.canBeReplaced()) {
                        boolean packed = (edgeX || edgeZ) && y == floorY && rand.nextFloat() < 0.35F;
                        level.setBlock(p, (packed ? Blocks.PACKED_ICE : Blocks.ICE).defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static ItemStack dyed(Item item, int colour) {
        ItemStack s = new ItemStack(item);
        if (item instanceof DyeableLeatherItem d) d.setColor(s, colour);
        s.setDamageValue((int) (s.getMaxDamage() * 0.7F));
        return s;
    }
}
