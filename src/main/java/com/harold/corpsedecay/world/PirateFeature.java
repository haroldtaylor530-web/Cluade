package com.harold.corpsedecay.world;

import java.util.ArrayList;
import java.util.List;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The crews of shipwrecks. Wherever a shipwreck was generated, some of the people aboard went down
 * with it: three to six of them lie on the decks and in the hold, old bones in the clothes of a
 * pirate crew (a captain in a dark coat and tricorn with a cutlass and a spyglass, hands in
 * bandanas, shirts, boots, boarding axes, bows, crossbows). Which spots they lie in is fixed by the
 * ship, so each chunk the ship covers adds only the ones that are in it.
 */
public class PirateFeature extends Feature<NoneFeatureConfiguration> {
    private static final boolean DEBUG = Boolean.getBoolean("corpsedecay.gentest");
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    public PirateFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (DEBUG) LOG.info("PIRATE feature runs in chunk {}", new ChunkPos(ctx.origin()));
        if (!Config.REMAINS.get()) return false;
        WorldGenLevel level = ctx.level();
        ChunkPos here = new ChunkPos(ctx.origin());
        ChunkAccess chunk = level.getChunk(here.x, here.z);
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        boolean any = false;
        for (var e : chunk.getAllReferences().entrySet()) {
            if (!isShipwreck(registry, e.getKey())) continue;
            for (long packed : e.getValue()) {
                ChunkPos sp = new ChunkPos(packed);
                StructureStart start = level.getChunk(sp.x, sp.z, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(e.getKey());
                if (start == null || !start.isValid()) continue;
                List<BoundingBox> boxes = new ArrayList<>();
                for (StructurePiece piece : start.getPieces()) boxes.add(piece.getBoundingBox());
                if (DEBUG) LOG.info("PIRATE chunk {} sees ship started in {} with {} pieces, box {}", here, sp, boxes.size(), boxes.get(0));
                RandomSource rand = RandomSource.create(level.getSeed() ^ (packed * 0x9E3779B97F4A7C15L) ^ 0x5EA10FL);
                any |= crewIn(level, boxes, here, rand) > 0;
            }
        }
        return any;
    }

    public static boolean isShipwreck(Registry<Structure> registry, Structure s) {
        return registry.wrapAsHolder(s).is(StructureTags.SHIPWRECK);
    }

    /**
     * The crew of a ship made of these boxes. Each of them has a spot fixed by {@code rand}; those
     * whose spot is in {@code only} (or all of them if {@code only} is null) are placed, on a plank
     * floor in their spot's chunk. Returns how many.
     */
    public static int crewIn(WorldGenLevel level, List<BoundingBox> boxes, ChunkPos only, RandomSource rand) {
        if (boxes.isEmpty()) return 0;
        int crew = 3 + rand.nextInt(4);
        int placed = 0;
        List<BlockPos> used = new ArrayList<>();
        for (int i = 0; i < crew; i++) {
            RandomSource mine = RandomSource.create(rand.nextLong());
            BoundingBox box = boxes.get(mine.nextInt(boxes.size()));
            int hx = box.minX() + mine.nextInt(box.getXSpan()), hz = box.minZ() + mine.nextInt(box.getZSpan());
            boolean captain = i == 0 && mine.nextFloat() < 0.75F;
            ChunkPos home = new ChunkPos(new BlockPos(hx, 0, hz));
            if (only != null && !home.equals(only)) continue;
            if (DEBUG) LOG.info("PIRATE member {} home {} in box {}", i, home, box);
            // somewhere on a plank floor in that chunk, starting at its spot
            int minX = Math.max(box.minX(), home.getMinBlockX()), maxX = Math.min(box.maxX(), home.getMaxBlockX());
            int minZ = Math.max(box.minZ(), home.getMinBlockZ()), maxZ = Math.min(box.maxZ(), home.getMaxBlockZ());
            for (int tries = 0; tries < 80; tries++) {
                int x = tries == 0 ? hx : minX + mine.nextInt(maxX - minX + 1);
                int z = tries == 0 ? hz : minZ + mine.nextInt(maxZ - minZ + 1);
                BlockPos floor = floorAt(level, x, z, box);
                if (floor == null) continue;
                boolean crowded = false;
                for (BlockPos u : used) crowded |= Math.abs(u.getX() - floor.getX()) + Math.abs(u.getZ() - floor.getZ()) <= 1 && Math.abs(u.getY() - floor.getY()) <= 1;
                if (crowded) continue;
                used.add(floor);
                if (DEBUG) LOG.info("PIRATE member {} at {}", i, floor);
                if (crewMember(level, floor, mine, captain) != null) placed++;
                break;
            }
        }
        return placed;
    }

    /**
     * Something a body can lie on aboard a ship: planks, a log lying on its side, the upper half of
     * a slab (other mods' wrecks are built of all sorts of wood).
     */
    static boolean isDeck(net.minecraft.world.level.block.state.BlockState s) {
        if (s.is(BlockTags.PLANKS)) return true;
        if (s.is(BlockTags.LOGS) && s.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)) {
            return s.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) != net.minecraft.core.Direction.Axis.Y;
        }
        if (s.is(BlockTags.WOODEN_SLABS) && s.hasProperty(net.minecraft.world.level.block.SlabBlock.TYPE)) {
            return s.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) != net.minecraft.world.level.block.state.properties.SlabType.BOTTOM;
        }
        return false;
    }

    /** The top of a plank floor in this column of the ship with room to lie on it, or null. */
    private static BlockPos floorAt(WorldGenLevel level, int x, int z, BoundingBox box) {
        for (int y = box.maxY(); y >= box.minY(); y--) {
            BlockPos p = new BlockPos(x, y, z);
            if (!isDeck(level.getBlockState(p))) continue;
            if (!level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) continue;
            return p;
        }
        return null;
    }

    // ---- the crew ------------------------------------------------------------------------------------

    /** A drowned sailor lying with their head on this deck block, in a pirate's clothes. */
    public static CorpseEntity crewMember(WorldGenLevel level, BlockPos floor, RandomSource rand, boolean captain) {
        boolean wet = !level.getFluidState(floor.above()).isEmpty();
        float r = rand.nextFloat();
        EntityType<?> mob = captain ? (rand.nextBoolean() ? EntityType.PILLAGER : EntityType.VINDICATOR)
                : r < 0.40F ? EntityType.PILLAGER : r < 0.55F ? EntityType.VINDICATOR : r < 0.80F ? EntityType.SKELETON
                : wet && r < 0.92F ? EntityType.DROWNED : EntityType.ZOMBIE;
        int material = !wet ? CorpseEntity.BLEACHED : rand.nextFloat() < 0.7F ? CorpseEntity.OLD : CorpseEntity.MOSSY;
        CorpseEntity body = RemainsFeature.placeBody(level.getLevel(), level, floor, mob, rand, material, 0.05 + rand.nextDouble() * 0.1);
        if (body == null) return null;
        if (captain) dressCaptain(body, rand);
        else dressSailor(body, rand);
        if (rand.nextFloat() < 0.12F) body.addOldArrow(rand);
        if (wet) {
            if (rand.nextFloat() < 0.35F) body.addGrowth(Blocks.SEA_PICKLE, 0.5F, rand);
            if (rand.nextFloat() < 0.2F) body.addGrowth(Blocks.DEAD_BRAIN_CORAL, 0.4F, rand);
        } else if (rand.nextFloat() < 0.4F) {
            body.addGrowth(Blocks.COBWEB, 0.8F, rand);
        }
        level.addFreshEntity(body);
        return body;
    }

    /** A tricorn, a long dark coat, breeches, boots, a cutlass, and a spyglass or a compass. */
    private static void dressCaptain(CorpseEntity body, RandomSource rand) {
        body.giveGear(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, 0x1B1B20, rand));
        body.giveGear(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, rand.nextBoolean() ? 0x20304F : 0x2B2B33, rand));
        body.giveGear(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, 0xCFC6AE, rand));
        body.giveGear(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, 0x17130F, rand));
        body.giveGear(EquipmentSlot.MAINHAND, worn(new ItemStack(Items.IRON_SWORD), rand));
        if (rand.nextFloat() < 0.8F) body.giveGear(EquipmentSlot.OFFHAND, new ItemStack(rand.nextFloat() < 0.7F ? Items.SPYGLASS : Items.COMPASS));
    }

    private static final int[] BANDANAS = {0x44546E, 0xB8AE96, 0x5F4B32, 0x2B2B2B, 0x6E6A5A};
    private static final int[] SHIRTS = {0xCFC5AB, 0x8A8472, 0x3F4F66, 0xA39A80, 0x5E5648};

    /** A bandana, a shirt, maybe breeches and boots, and a cutlass, boarding axe, bow or crossbow. */
    private static void dressSailor(CorpseEntity body, RandomSource rand) {
        if (rand.nextFloat() < 0.55F) body.giveGear(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, BANDANAS[rand.nextInt(BANDANAS.length)], rand));
        if (rand.nextFloat() < 0.7F) body.giveGear(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, SHIRTS[rand.nextInt(SHIRTS.length)], rand));
        if (rand.nextFloat() < 0.5F) body.giveGear(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, 0x4A3B2C, rand));
        if (rand.nextFloat() < 0.5F) body.giveGear(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, 0x2B2118, rand));
        float w = rand.nextFloat();
        Item weapon = w < 0.35F ? Items.IRON_SWORD : w < 0.5F ? Items.IRON_AXE : w < 0.7F ? Items.BOW : w < 0.85F ? Items.CROSSBOW : null;
        if (weapon != null) body.giveGear(EquipmentSlot.MAINHAND, worn(new ItemStack(weapon), rand));
        if (rand.nextFloat() < 0.1F) body.giveGear(EquipmentSlot.OFFHAND, new ItemStack(Items.COMPASS));
    }

    static ItemStack dyed(Item item, int colour, RandomSource rand) {
        ItemStack s = new ItemStack(item);
        int r = clamp(((colour >> 16) & 255) + rand.nextInt(13) - 6), g = clamp(((colour >> 8) & 255) + rand.nextInt(13) - 6),
                b = clamp((colour & 255) + rand.nextInt(13) - 6);
        if (item instanceof DyeableLeatherItem d) d.setColor(s, (r << 16) | (g << 8) | b);
        return worn(s, rand);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static ItemStack worn(ItemStack s, RandomSource rand) {
        if (s.isDamageableItem()) s.setDamageValue((int) (s.getMaxDamage() * (0.5F + rand.nextFloat() * 0.4F)));
        return s;
    }
}
