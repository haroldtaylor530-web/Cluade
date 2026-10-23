package com.harold.corpsedecay.world;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.CorpseDecay;
import com.harold.corpsedecay.block.FossilBlocks.Host;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * An abandoned dig: a pit dug down beside a fossil skeleton half freed from the wall, with a ladder
 * and scaffolding in the pit, suspicious sand or gravel on its floor still waiting to be brushed,
 * and the diggers' camp left at the top: fence posts with torches, a chest of their things, a cold
 * campfire. Now and then the unlucky digger is still there.
 */
public class DigSiteFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceLocation CHEST_LOOT = new ResourceLocation(CorpseDecay.MODID, "chests/dig_site");
    public static final ResourceLocation BRUSH_LOOT = new ResourceLocation(CorpseDecay.MODID, "archaeology/dig_site");
    private static final TagKey<Biome> SANDY = TagKey.create(net.minecraft.core.registries.Registries.BIOME,
            new ResourceLocation(CorpseDecay.MODID, "sandy"));

    static final int HALF_LENGTH = 5, WIDTH = 4, DEPTH = 4;

    public DigSiteFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!Config.FOSSILS.get()) return false;
        return dig(ctx.level(), ctx.origin(), ctx.random(), Direction.from2DDataValue(ctx.random().nextInt(4)), true);
    }

    /**
     * Digs the site with its long side running along {@code along}, at ground level {@code origin}
     * (the first air above the ground). Returns false if the ground isn't flat enough.
     */
    public static boolean dig(WorldGenLevel level, BlockPos origin, RandomSource rand, Direction along, boolean checkGround) {
        Direction across = along.getClockWise();
        int y0 = origin.getY();
        if (checkGround) {
            for (int a = -HALF_LENGTH - 1; a <= HALF_LENGTH + 1; a += 2) {
                for (int b = -2; b <= WIDTH + 1; b += 2) {
                    BlockPos p = origin.relative(along, a).relative(across, b);
                    int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, p.getX(), p.getZ());
                    if (Math.abs(h - y0) > 1) return false;
                    if (!level.getFluidState(p.below()).isEmpty()) return false;
                }
            }
        }
        Holder<Biome> biome = level.getBiome(origin);
        boolean badlands = biome.is(BiomeTags.IS_BADLANDS);
        boolean sandy = biome.is(SANDY) && !badlands;
        Host host = badlands ? Host.TERRACOTTA : sandy ? Host.SANDSTONE : Host.STONE;

        // the pit
        for (int a = -HALF_LENGTH; a <= HALF_LENGTH; a++) {
            for (int b = 0; b < WIDTH; b++) {
                for (int y = -DEPTH; y <= 3; y++) {
                    level.setBlock(at(origin, along, across, a, b, y), Blocks.AIR.defaultBlockState(), 2);
                }
                BlockPos floor = at(origin, along, across, a, b, -DEPTH - 1);
                BlockState f = rand.nextFloat() < 0.12F ? (sandy || badlands ? Blocks.SUSPICIOUS_SAND : Blocks.SUSPICIOUS_GRAVEL).defaultBlockState()
                        : (sandy ? Blocks.SAND : badlands ? Blocks.RED_SAND : Blocks.COARSE_DIRT).defaultBlockState();
                level.setBlock(floor, f, 2);
                if (level.getBlockEntity(floor) instanceof BrushableBlockEntity be) be.setLootTable(BRUSH_LOOT, rand.nextLong());
            }
        }
        // the skeleton they were digging out, in the far wall
        String[] rows = FossilBedFeature.vary(FossilBedFeature.pick(rand), rand);
        BlockPos spine = at(origin, along, across, 0, WIDTH, -2);
        Direction head = rand.nextBoolean() ? along : along.getOpposite();
        FossilBedFeature.placeSkeleton(level, spine, rand, rows, 0.05F, 0F, head, host);

        // a ladder down one end, scaffolding at the other
        for (int y = -DEPTH; y < 0; y++) {
            BlockPos l = at(origin, along, across, -HALF_LENGTH, 1, y);
            if (level.getBlockState(l.relative(along.getOpposite())).isSolid()) {
                level.setBlock(l, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, along), 2);
            }
            level.setBlock(at(origin, along, across, HALF_LENGTH, WIDTH - 1, y), Blocks.SCAFFOLDING.defaultBlockState(), 2);
        }
        // the camp at the top
        int[][] posts = {{-HALF_LENGTH - 1, -1}, {HALF_LENGTH + 1, -1}, {-HALF_LENGTH - 1, WIDTH + 1}, {HALF_LENGTH + 1, WIDTH + 1}};
        for (int[] post : posts) {
            BlockPos p = ground(level, at(origin, along, across, post[0], post[1], 0));
            level.setBlock(p, Blocks.OAK_FENCE.defaultBlockState(), 2);
            if (rand.nextFloat() < 0.6F) level.setBlock(p.above(), Blocks.TORCH.defaultBlockState(), 2);
        }
        BlockPos chest = ground(level, at(origin, along, across, -2, -2, 0));
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, across), 2);
        RandomizableContainerBlockEntity.setLootTable(level, rand, chest, CHEST_LOOT);
        level.setBlock(ground(level, at(origin, along, across, 0, -2, 0)), Blocks.CRAFTING_TABLE.defaultBlockState(), 2);
        level.setBlock(ground(level, at(origin, along, across, 3, -3, 0)),
                Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 2);
        if (rand.nextFloat() < 0.3F) {
            level.setBlock(ground(level, at(origin, along, across, -4, -3, 0)), Blocks.BARREL.defaultBlockState(), 2);
        }
        // the digger who never left
        if (rand.nextFloat() < 0.3F) {
            BlockPos floor = at(origin, along, across, 2, 1, -DEPTH - 1);
            CorpseEntity body = RemainsFeature.placeBody(level.getLevel(), level, floor, EntityType.ZOMBIE, rand,
                    sandy ? CorpseEntity.BLEACHED : CorpseEntity.OLD, 0.05);
            if (body != null) {
                body.giveGear(EquipmentSlot.MAINHAND, new ItemStack(Items.BRUSH));
                body.giveGear(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                level.addFreshEntity(body);
            }
        }
        return true;
    }

    private static BlockPos at(BlockPos origin, Direction along, Direction across, int a, int b, int y) {
        return origin.relative(along, a).relative(across, b).above(y);
    }

    /** The first open spot on the ground at or near {@code p}. */
    private static BlockPos ground(WorldGenLevel level, BlockPos p) {
        BlockPos.MutableBlockPos m = p.mutable().move(0, 2, 0);
        for (int i = 0; i < 5; i++) {
            if (!level.getBlockState(m.below()).isAir() && !level.getBlockState(m.below()).canBeReplaced()) {
                if (level.getBlockState(m).canBeReplaced()) return m.immutable();
            }
            m.move(0, -1, 0);
        }
        return p;
    }
}
