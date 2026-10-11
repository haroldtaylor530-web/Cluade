package com.harold.realisticsurvival;

import com.harold.realisticsurvival.block.CampfireData;
import com.harold.realisticsurvival.block.ChoppedLogBlock;
import com.harold.realisticsurvival.block.CookingPotBlock;
import com.harold.realisticsurvival.block.RealisticCampfireBlock;
import com.harold.realisticsurvival.entity.FallingTree;
import com.harold.realisticsurvival.survival.TreeFelling;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import com.harold.realisticsurvival.entity.ThrownSpear;
import com.harold.realisticsurvival.survival.ModDamage;
import com.harold.realisticsurvival.survival.SurvivalState;
import com.harold.realisticsurvival.survival.SurvivalTicker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Development check, only active with -Drealisticsurvival.selftest=true (the build's test
 * server). Verifies the mod's data loaded and exercises its code, logs PASS/FAIL, then stops.
 */
public final class SelfTest {
    private static final List<String> failures = new ArrayList<>();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (System.getProperty("realisticsurvival.selftest") == null) return;
        MinecraftServer server = event.getServer();
        try {
            run(server);
        } catch (Throwable t) {
            fail("exception: " + t);
            RealisticSurvival.LOGGER.error("SELFTEST exception", t);
        }
        if (failures.isEmpty()) RealisticSurvival.LOGGER.info("SELFTEST PASS");
        else failures.forEach(f -> RealisticSurvival.LOGGER.error("SELFTEST FAIL {}", f));
        RealisticSurvival.LOGGER.info("SELFTEST DONE failures={}", failures.size());
        server.halt(false);
    }

    private static void fail(String msg) {
        failures.add(msg);
    }

    private static void check(boolean ok, String msg) {
        if (!ok) fail(msg);
        else RealisticSurvival.LOGGER.info("SELFTEST ok: {}", msg);
    }

    private static void run(MinecraftServer server) {
        // ---- data loaded
        String[] recipes = { "fibre_string", "flint_knife", "flint_hatchet", "flint_pickaxe", "flint_spear", "iron_spear",
                "diamond_spear", "unfired_clay_cup", "clay_cup", "boil_water_furnace", "boil_water_campfire", "waterskin",
                "bandage_from_wool", "splint", "fur_hat", "fur_coat", "fur_trousers", "fur_boots", "cobblestone_from_rocks", "cooking_pot" };
        for (String r : recipes) {
            check(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, RealisticSurvival.id(r))).isPresent(), "recipe " + r);
        }
        check(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, net.minecraft.resources.Identifier.withDefaultNamespace("oak_planks"))).isPresent(), "recipe oak_planks override");

        for (String b : new String[] { "rock", "stick_on_ground", "cooking_pot" }) {
            LootTable t = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, RealisticSurvival.id("blocks/" + b)));
            check(t != LootTable.EMPTY, "loot table " + b);
        }
        for (String f : new String[] { "loose_rocks", "fallen_sticks" }) {
            check(server.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE).get(ResourceKey.create(Registries.PLACED_FEATURE, RealisticSurvival.id(f))).isPresent(), "placed feature " + f);
        }
        ServerLevel level = server.overworld();
        for (var key : List.of(ModDamage.DEHYDRATION, ModDamage.HEATSTROKE, ModDamage.HYPOTHERMIA, ModDamage.BLEEDING)) {
            ModDamage.source(level, key);
            check(true, "damage type " + key.identifier());
        }
        check(new ItemStack(ModRegistry.FLINT_HATCHET.get()).is(ItemTags.AXES), "flint hatchet is an axe");
        check(new ItemStack(ModRegistry.FUR_COAT.get()).is(ModRegistry.WARM_CLOTHING), "fur coat is warm clothing");
        check(new ItemStack(ModRegistry.FIBRE_STRING.get()).is(net.minecraft.tags.TagKey.create(Registries.ITEM, RealisticSurvival.id("binding"))), "binding tag");

        // ---- code paths
        BlockPos spawn = level.getRespawnData().pos();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        BlockPos ground = new BlockPos(spawn.getX(), y, spawn.getZ());

        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.setPos(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        float air = SurvivalTicker.ambient(player, level);
        check(air > -60 && air < 90, "ambient temperature at spawn = " + air);
        SurvivalState s = SurvivalState.get(player);
        s.addThirst(-5);
        check(s.thirst == 15.0F, "thirst state");
        check(SurvivalTicker.insulation(player) == 0.0F, "insulation with no clothes");

        // cooking pot boils over a lit campfire
        BlockPos fire = ground.above(2);
        level.setBlockAndUpdate(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        BlockPos potPos = fire.above();
        level.setBlockAndUpdate(potPos, ModRegistry.COOKING_POT_BLOCK.get().defaultBlockState().setValue(CookingPotBlock.WATER, 4));
        check(CookingPotBlock.isHeated(level, potPos), "pot is heated by campfire");
        for (int i = 0; i < 4; i++) {
            var st = level.getBlockState(potPos);
            st.tick(level, potPos, level.getRandom());
        }
        check(level.getBlockState(potPos).getValue(CookingPotBlock.BOILED), "pot boiled after heating");

        // a thrown spear flies and lands
        ThrownSpear spear = new ThrownSpear(level, player, new ItemStack(ModRegistry.IRON_SPEAR.get()), 9.0F);
        spear.setPos(ground.getX() + 0.5, ground.getY() + 10, ground.getZ() + 0.5);
        spear.setDeltaMovement(new Vec3(0.5, 0.2, 0));
        check(level.addFreshEntity(spear), "spear spawned");
        for (int i = 0; i < 60; i++) spear.tick();
        check(!spear.getItem().isEmpty(), "spear carries its item");
        spear.discard();

        // ground blocks survive only on solid ground
        check(ModRegistry.ROCK_BLOCK.get().defaultBlockState().canSurvive(level, ground), "rock can sit on the ground");

        campfire(server, level, ground.offset(6, 0, 0));
        tree(level, ground.offset(-8, 0, 0));
    }

    /** Clear a flat 7x7 patch of grass with air above it, surface at y. */
    private static BlockPos flat(ServerLevel level, BlockPos at, int height) {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            level.setBlockAndUpdate(at.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
            for (int y = 0; y <= height; y++) level.setBlockAndUpdate(at.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        return at;
    }

    private static void campfire(MinecraftServer server, ServerLevel level, BlockPos at) {
        check(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, RealisticSurvival.id("bow_drill"))).isPresent(), "recipe bow_drill");
        check(server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, net.minecraft.resources.Identifier.withDefaultNamespace("campfire"))).isPresent(), "recipe campfire override");
        BlockPos pos = flat(level, at, 3);
        level.setBlockAndUpdate(pos, ModRegistry.CAMPFIRE_BLOCK.get().defaultBlockState());
        var state = level.getBlockState(pos);
        check(state.is(net.minecraft.tags.BlockTags.CAMPFIRES), "realistic campfire is a campfire");
        check(!state.getValue(RealisticCampfireBlock.LIT), "campfire starts unlit");
        if (!(level.getBlockEntity(pos) instanceof CampfireBlockEntity be)) {
            fail("campfire has a campfire block entity");
            return;
        }
        check(new ItemStack(ModRegistry.PLANT_FIBRE.get()).is(ModRegistry.KINDLING), "plant fibre is kindling");
        CampfireData d = RealisticCampfireBlock.data(be);
        check(RealisticCampfireBlock.notReadyReason(state, d) != null, "empty campfire can't be lit");
        d.kindling = new ItemStack(ModRegistry.PLANT_FIBRE.get(), 2);
        d.fuel = new ItemStack(Items.OAK_LOG, 2);
        check(RealisticCampfireBlock.light(level, pos), "campfire lights with kindling and fuel");
        state = level.getBlockState(pos);
        check(state.getValue(RealisticCampfireBlock.LIT), "campfire is lit");
        check(d.kindling.getCount() == 1 && d.fuel.getCount() == 1 && d.burn > 0, "lighting used kindling and a log");
        float before = d.burn;
        for (int i = 0; i < 20; i++) RealisticCampfireBlock.serverTick(level, pos, level.getBlockState(pos), be);
        check(d.burn < before, "fuel burns down (" + before + " -> " + d.burn + ")");

        // warmth: a large fire warms more than no fire
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.setPos(pos.getX() + 3.5, pos.getY(), pos.getZ() + 0.5);
        float warmMedium = SurvivalTicker.ambient(player, level);
        level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(RealisticCampfireBlock.SIZE, 3));
        float warmLarge = SurvivalTicker.ambient(player, level);
        check(warmLarge > warmMedium, "large fire is warmer (" + warmMedium + " -> " + warmLarge + ")");

        // the cooking pot boils on it
        level.setBlockAndUpdate(pos.above(), ModRegistry.COOKING_POT_BLOCK.get().defaultBlockState().setValue(CookingPotBlock.WATER, 2));
        check(CookingPotBlock.isHeated(level, pos.above()), "pot is heated by the realistic campfire");
        level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());

        // runs out of fuel: embers, then fresh fuel relights it
        d.fuel = ItemStack.EMPTY;
        d.burn = 0.5F;
        for (int i = 0; i < 3; i++) RealisticCampfireBlock.serverTick(level, pos, level.getBlockState(pos), be);
        state = level.getBlockState(pos);
        check(!state.getValue(RealisticCampfireBlock.LIT) && state.getValue(RealisticCampfireBlock.EMBERS) && d.embers > 0, "out of fuel it dies to embers");
        d.fuel = new ItemStack(Items.STICK, 3);
        RealisticCampfireBlock.serverTick(level, pos, level.getBlockState(pos), be);
        check(level.getBlockState(pos).getValue(RealisticCampfireBlock.LIT), "fuel on hot embers relights it");
        check(RealisticCampfireBlock.secondsLeft(d, 2) > 0, "fuel time left = " + RealisticCampfireBlock.secondsLeft(d, 2) + "s");
    }

    private static void tree(ServerLevel level, BlockPos at) {
        BlockPos base = flat(level, at, 9);
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1);
        for (int y = 3; y <= 6; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (y == 6 && (Math.abs(x) > 1 || Math.abs(z) > 1)) continue;
            level.setBlock(base.offset(x, y, z), leaves, 2);
        }
        for (int y = 0; y <= 5; y++) level.setBlock(base.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
        FallingTree tree = TreeFelling.fell(level, base, base.getX() + 0.5 - 3, base.getZ() + 0.5);
        if (tree == null) {
            fail("tree fells");
            return;
        }
        check(true, "tree fells with " + tree.parts().size() + " blocks");
        check(level.getBlockState(base.above(2)).isAir() && level.getBlockState(base.offset(1, 4, 0)).isAir(), "tree blocks left the world");
        // the falling direction is away from the cutter (+x): the top lands east of the stump
        var top = tree.landedCentre(new FallingTree.Part(new BlockPos(0, 5, 0), Blocks.OAK_LOG.defaultBlockState()));
        check(top.x > base.getX() + 3, "tree falls away from the cutter (top lands at x+" + (top.x - base.getX()) + ")");
        for (int i = 0; i < 80 && !tree.isRemoved(); i++) tree.tick();
        check(tree.isRemoved(), "tree landed");
        var box = new net.minecraft.world.phys.AABB(base).inflate(12);
        int logs = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box).stream()
                .filter(e -> e.getItem().is(Items.OAK_LOG)).mapToInt(e -> e.getItem().getCount()).sum();
        check(logs == 5, "fallen tree dropped its logs (" + logs + ")");
        // placed (persistent) leaves on a log column are not a tree
        BlockPos wall = flat(level, base.offset(0, 0, 7), 4);
        for (int y = 0; y <= 3; y++) level.setBlock(wall.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
        level.setBlock(wall.above(4), Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 2);
        check(TreeFelling.fell(level, wall, wall.getX() - 3, wall.getZ()) == null, "log column with placed leaves doesn't fall");

        // layered chopping with a real break, as a player with an axe would
        BlockPos b2 = flat(level, base.offset(0, 0, -9), 9);
        for (int y = 3; y <= 6; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            if (y == 6 && (Math.abs(x) > 1 || Math.abs(z) > 1)) continue;
            level.setBlock(b2.offset(x, y, z), leaves, 2);
        }
        for (int y = 0; y <= 5; y++) level.setBlock(b2.above(y), Blocks.BIRCH_LOG.defaultBlockState(), 2);
        FakePlayer chopper = FakePlayerFactory.getMinecraft(level);
        chopper.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        chopper.setShiftKeyDown(false);
        chopper.setPos(b2.getX() + 0.5, b2.getY(), b2.getZ() - 1.5);
        chopper.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        for (int cut = 1; cut <= 3; cut++) {
            chopper.gameMode.destroyBlock(b2);
            var st = level.getBlockState(b2);
            check(st.getBlock() instanceof ChoppedLogBlock && st.getValue(ChoppedLogBlock.STAGE) == cut, "cut " + cut + " takes a layer off (" + st + ")");
            check(level.getBlockState(b2.above()).is(Blocks.BIRCH_LOG), "tree still standing after cut " + cut);
        }
        int before = level.getEntitiesOfClass(FallingTree.class, new net.minecraft.world.phys.AABB(b2).inflate(4)).size();
        chopper.gameMode.destroyBlock(b2);
        var fallen = level.getEntitiesOfClass(FallingTree.class, new net.minecraft.world.phys.AABB(b2).inflate(4));
        check(fallen.size() == before + 1 && level.getBlockState(b2).isAir() && level.getBlockState(b2.above()).isAir(), "cutting the core fells the tree");
        if (fallen.size() > before) {
            var top2 = fallen.get(fallen.size() - 1).landedCentre(new FallingTree.Part(new BlockPos(0, 5, 0), Blocks.BIRCH_LOG.defaultBlockState()));
            check(top2.z > b2.getZ() + 3, "chopped tree falls away from the chopper (z+" + (top2.z - b2.getZ()) + ")");
            fallen.forEach(net.minecraft.world.entity.Entity::discard);
        }
        // sneaking cuts a single log as normal
        level.setBlock(b2, Blocks.BIRCH_LOG.defaultBlockState(), 2);
        chopper.setShiftKeyDown(true);
        chopper.gameMode.destroyBlock(b2);
        check(level.getBlockState(b2).isAir(), "sneaking breaks a log normally");
        chopper.setShiftKeyDown(false);
        chopper.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private SelfTest() {}
}
