package com.harold.corpsedecay.world;

import java.util.Optional;

import com.harold.corpsedecay.CorpseKind;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.phys.Vec3;

/**
 * Places the remains of a mob that died long ago: in caves, bones turned to the stone around them,
 * lying on the cave floor; on the surface (deserts, badlands, beaches), old bleached bones half
 * buried in the sand. Which mobs can turn up is the entity tag {@code #corpsedecay:fossil_mobs}.
 */
public class FossilFeature extends Feature<NoneFeatureConfiguration> {
    private final boolean surface;

    public FossilFeature(boolean surface) {
        super(NoneFeatureConfiguration.CODEC);
        this.surface = surface;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        if (!com.harold.corpsedecay.Config.FOSSILS.get()) return false;
        WorldGenLevel level = ctx.level();
        RandomSource rand = ctx.random();
        BlockPos origin = ctx.origin();
        BlockPos floor = surface ? surfaceSpot(level, origin) : caveSpot(level, origin);
        if (floor == null) return false;
        EntityType<?> mob = pickMob(rand);
        if (mob == null) return false;
        BlockState below = level.getBlockState(floor);
        int material;
        double sink;
        if (surface) {
            material = CorpseEntity.BLEACHED;
            sink = 0.3 + rand.nextDouble() * 0.25;
        } else {
            material = rockOf(below);
            sink = 0.05 + rand.nextDouble() * 0.15;
        }
        return placeFossil(level.getLevel(), level, floor, mob, rand, material, sink);
    }

    /** Puts a fossil on top of block {@code floor}. Used by world generation and the tests. */
    public static boolean placeFossil(ServerLevel server, net.minecraft.world.level.ServerLevelAccessor level, BlockPos floor,
                                      EntityType<?> mob, RandomSource rand, int material, double sink) {
        Vec3 ground = new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5);
        CorpseEntity fossil = CorpseEntity.fossil(server, mob, ground, rand, material, sink);
        if (fossil == null) return false;
        return level.addFreshEntity(fossil);
    }

    public static int rockOf(BlockState state) {
        if (state.is(Blocks.DEEPSLATE) || state.is(Blocks.COBBLED_DEEPSLATE) || state.is(Blocks.TUFF) || state.is(Blocks.BASALT)
                || state.is(Blocks.SMOOTH_BASALT) || state.is(Blocks.BLACKSTONE)) {
            return CorpseEntity.DEEPSLATE;
        }
        if (state.is(BlockTags.SAND) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(BlockTags.TERRACOTTA)) {
            return CorpseEntity.SANDSTONE;
        }
        return CorpseEntity.STONE;
    }

    private static EntityType<?> pickMob(RandomSource rand) {
        Optional<HolderSet.Named<EntityType<?>>> tag = BuiltInRegistries.ENTITY_TYPE.getTag(CorpseKind.FOSSIL_MOBS);
        if (tag.isEmpty() || tag.get().size() == 0) return null;
        Optional<Holder<EntityType<?>>> pick = tag.get().getRandomElement(rand);
        return pick.map(Holder::value).orElse(null);
    }

    /** A cave floor: solid rock with two blocks of open air above it, near the given height. */
    private static BlockPos caveSpot(WorldGenLevel level, BlockPos origin) {
        BlockPos.MutableBlockPos p = origin.mutable();
        for (int i = 0; i < 24; i++) {
            BlockState here = level.getBlockState(p);
            BlockState above = level.getBlockState(p.above());
            BlockState above2 = level.getBlockState(p.above(2));
            if (here.isSolidRender(level, p) && above.isAir() && above2.isAir() && level.getFluidState(p.above()).isEmpty()
                    && !level.canSeeSkyFromBelowWater(p.above())) {
                return p.immutable();
            }
            p.move(0, -1, 0);
            if (p.getY() <= level.getMinBuildHeight() + 2) return null;
        }
        return null;
    }

    /** Open sand or terracotta on the surface. */
    private static BlockPos surfaceSpot(WorldGenLevel level, BlockPos origin) {
        BlockPos top = origin.below();
        BlockState state = level.getBlockState(top);
        if (!(state.is(BlockTags.SAND) || state.is(BlockTags.TERRACOTTA) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE))) {
            return null;
        }
        if (!level.getBlockState(top.above()).isAir() || !level.getFluidState(top.above()).isEmpty()) return null;
        return top;
    }
}
