package com.harold.realisticsurvival;

import com.harold.realisticsurvival.block.BrickOven;
import com.harold.realisticsurvival.block.CampfireData;
import com.harold.realisticsurvival.block.ClayBrickPileBlock;
import com.harold.realisticsurvival.block.OvenMenu;
import com.harold.realisticsurvival.recipe.OvenRecipe;
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
        oven(server, level, ground.offset(0, 0, 14));
        food(server, level, ground.offset(12, 0, 14));
    }

    private static void food(MinecraftServer server, ServerLevel level, BlockPos at) {
        long now = level.getGameTime();
        // ---- spoiling
        ItemStack beef = com.harold.realisticsurvival.survival.Spoilage.update(new ItemStack(Items.BEEF, 5), now);
        Long start = beef.get(ModRegistry.FOOD_START.get());
        check(start != null && start == com.harold.realisticsurvival.survival.Spoilage.round(now), "new beef gets a start time");
        long life = com.harold.realisticsurvival.survival.Spoilage.lifetime(beef);
        check(life == 3 * 24000L, "raw beef keeps 3 days (" + life + ")");
        check(com.harold.realisticsurvival.survival.Spoilage.lifetime(new ItemStack(Items.BREAD)) == 10 * 24000L, "bread keeps 10 days");
        var stageOf = (java.util.function.LongFunction<com.harold.realisticsurvival.survival.Spoilage.Stage>) t ->
                com.harold.realisticsurvival.survival.Spoilage.stage(com.harold.realisticsurvival.survival.Spoilage.progress(beef, t));
        check(stageOf.apply(start + 10000) == com.harold.realisticsurvival.survival.Spoilage.Stage.FRESH, "beef is fresh at first");
        check(stageOf.apply(start + 40000) == com.harold.realisticsurvival.survival.Spoilage.Stage.STALE, "beef is stale after 1.7 days");
        check(stageOf.apply(start + 60000) == com.harold.realisticsurvival.survival.Spoilage.Stage.GOING_OFF, "beef is going off after 2.5 days");
        ItemStack rotten = com.harold.realisticsurvival.survival.Spoilage.update(beef, start + life + 1);
        check(rotten.is(Items.ROTTEN_FLESH) && rotten.getCount() == 5, "rotten beef turns into rotten flesh (" + rotten + ")");
        ItemStack bread = com.harold.realisticsurvival.survival.Spoilage.update(new ItemStack(Items.BREAD, 2), now);
        check(com.harold.realisticsurvival.survival.Spoilage.update(bread, now + 11 * 24000L).is(ModRegistry.ROTTEN_FOOD.get()), "rotten bread turns into rotten food");
        check(!com.harold.realisticsurvival.survival.Spoilage.spoils(new ItemStack(Items.DRIED_KELP)), "dried kelp never spoils");
        check(!com.harold.realisticsurvival.survival.Spoilage.spoils(new ItemStack(Items.GOLDEN_APPLE)), "golden apples never spoil");
        check(com.harold.realisticsurvival.survival.Spoilage.update(beef, now) == beef, "an up-to-date stack is left alone");

        // ---- cold storage
        ItemStack cold = beef.copy();
        com.harold.realisticsurvival.survival.Spoilage.markCold(cold, start, 0.25F);
        float pc = com.harold.realisticsurvival.survival.Spoilage.progress(cold, start + 48000);
        check(Math.abs(pc - 12000F / life) < 0.001F, "two days in the cold ages beef by half a day (" + pc + ")");
        ItemStack settled = com.harold.realisticsurvival.survival.Spoilage.update(cold, start + 48000);
        check(!settled.has(ModRegistry.FOOD_COLD.get()) && settled.get(ModRegistry.FOOD_START.get()) == start + 36000,
                "taking it out of the cold moves its start time on (" + settled.get(ModRegistry.FOOD_START.get()) + ")");

        clear(level, at, 4);
        BlockPos chestPos = at.above();
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());
        level.setBlockAndUpdate(chestPos.east(), Blocks.PACKED_ICE.defaultBlockState());
        check(com.harold.realisticsurvival.survival.Spoilage.coldness(level, chestPos) <= 0.25F, "a chest next to packed ice is cold");
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chestPos);
        chest.setItem(0, beef.copy());
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        var menu = net.minecraft.world.inventory.ChestMenu.threeRows(1, player.getInventory(), chest);
        com.harold.realisticsurvival.survival.FoodEvents.onClose(new net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Close(player, menu));
        check(chest.getItem(0).has(ModRegistry.FOOD_COLD.get()), "closing a cold chest keeps the food in it cold");
        com.harold.realisticsurvival.survival.FoodEvents.onOpen(new net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open(player, menu));
        check(!chest.getItem(0).has(ModRegistry.FOOD_COLD.get()), "opening it again settles the cold time");
        level.setBlockAndUpdate(chestPos.east(), Blocks.AIR.defaultBlockState());

        // ---- eating
        var fd = player.getFoodData();
        fd.setFoodLevel(10);
        fd.setSaturation(0);
        ItemStack staleBread = bread.copy();
        staleBread.set(ModRegistry.FOOD_START.get(), now - 6 * 24000L);
        com.harold.realisticsurvival.survival.FoodEvents.afterEating(player, staleBread, staleBread.get(net.minecraft.core.component.DataComponents.FOOD), 0.99F);
        check(fd.getFoodLevel() == 9, "stale bread fills you less (" + fd.getFoodLevel() + ")");
        player.removeAllEffects();
        ItemStack chicken = com.harold.realisticsurvival.survival.Spoilage.update(new ItemStack(Items.CHICKEN), now);
        com.harold.realisticsurvival.survival.FoodEvents.afterEating(player, chicken, chicken.get(net.minecraft.core.component.DataComponents.FOOD), 0.1F);
        check(player.hasEffect(ModRegistry.FOOD_POISONING), "raw chicken can give you food poisoning");
        player.removeAllEffects();
        ItemStack smoked = chicken.copy();
        smoked.set(ModRegistry.PRESERVED.get(), com.harold.realisticsurvival.survival.Spoilage.SMOKED);
        com.harold.realisticsurvival.survival.FoodEvents.afterEating(player, smoked, smoked.get(net.minecraft.core.component.DataComponents.FOOD), 0.0F);
        check(!player.hasEffect(ModRegistry.FOOD_POISONING), "smoked meat is safe");
        ItemStack steak = com.harold.realisticsurvival.survival.Spoilage.update(new ItemStack(Items.COOKED_BEEF), now);
        com.harold.realisticsurvival.survival.FoodEvents.afterEating(player, steak, steak.get(net.minecraft.core.component.DataComponents.FOOD), 0.0F);
        check(!player.hasEffect(ModRegistry.FOOD_POISONING), "fresh cooked food is safe");

        // ---- salting
        var rm = server.getRecipeManager();
        List<ItemStack> grid = new ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
        grid.set(0, new ItemStack(ModRegistry.SALT.get()));
        for (int i = 1; i <= 3; i++) grid.set(i, beef.copyWithCount(1));
        var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, grid);
        var salting = rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
        ItemStack salted = salting.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
        check(salted.is(Items.BEEF) && salted.getCount() == 3 && com.harold.realisticsurvival.survival.Spoilage.salted(salted), "salt and 3 beef make 3 salted beef (" + salted + ")");
        check(com.harold.realisticsurvival.survival.Spoilage.lifetime(salted) == 6 * life, "salted beef keeps 6 times as long");
        grid.set(4, new ItemStack(Items.PORKCHOP));
        check(rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, net.minecraft.world.item.crafting.CraftingInput.of(3, 3, grid), level).isEmpty(), "can't salt two kinds of meat at once");

        // ---- drying rack: smoking over a fire
        BlockPos fire = at.offset(2, 1, 0);
        level.setBlockAndUpdate(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        BlockPos rackPos = fire.above();
        level.setBlockAndUpdate(rackPos, ModRegistry.DRYING_RACK_BLOCK.get().defaultBlockState());
        var rack = (com.harold.realisticsurvival.block.DryingRackBlockEntity) level.getBlockEntity(rackPos);
        check(rack != null, "drying rack has its block entity");
        check(com.harold.realisticsurvival.block.DryingRackBlockEntity.smoky(level, rackPos), "a rack over a lit campfire is smoky");
        check(rack.hang(beef.copyWithCount(1)) && rack.hang(beef.copyWithCount(1)), "hang beef on the rack");
        check(rack.getItems().get(0).getCount() == 2, "two pieces share a hook");
        check(rack.hang(new ItemStack(Items.APPLE)), "hang an apple on the rack");
        rack.work(level, 6000);
        ItemStack smokedBeef = rack.getItems().get(0);
        check(smokedBeef.is(Items.COOKED_BEEF) && smokedBeef.getCount() == 2 && com.harold.realisticsurvival.survival.Spoilage.smoked(smokedBeef),
                "smoking turns raw beef into smoked cooked beef (" + smokedBeef + ")");
        check(com.harold.realisticsurvival.survival.Spoilage.lifetime(smokedBeef) == 40 * 24000L, "smoked beef keeps 40 days");
        check(rack.getItems().get(1).is(ModRegistry.DRIED_FRUIT.get()), "the apple dried over the fire");
        ItemStack old = beef.copyWithCount(1);
        old.set(ModRegistry.FOOD_START.get(), now - 4 * 24000L);
        rack.takeLast();
        rack.hang(old);
        rack.work(level, 20);
        check(rack.getItems().get(1).is(Items.ROTTEN_FLESH), "meat that's too old rots on the rack");
        // drying in the shade: slow, but it gets there
        level.setBlockAndUpdate(fire, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(rackPos.above(), Blocks.OAK_PLANKS.defaultBlockState());
        rack.takeLast();
        rack.takeLast();
        rack.hang(com.harold.realisticsurvival.survival.Spoilage.update(new ItemStack(Items.SALMON), now));
        rack.work(level, 20000, com.harold.realisticsurvival.block.DryingRackBlockEntity.Weather.SHADE);
        check(rack.getItems().get(0).is(Items.SALMON) && rack.progress(0) == 5000, "fish dries slowly in the shade (" + rack.progress(0) + ")");
        rack.work(level, 1000, com.harold.realisticsurvival.block.DryingRackBlockEntity.Weather.RAIN);
        check(rack.progress(0) == 3000, "rain soaks it and undoes some drying (" + rack.progress(0) + ")");
        rack.work(level, 9000, com.harold.realisticsurvival.block.DryingRackBlockEntity.Weather.SUN);
        check(rack.getItems().get(0).is(ModRegistry.DRIED_FISH.get()), "salmon dried in the sun into dried fish");
        check(com.harold.realisticsurvival.block.DryingRackBlockEntity.weather(level, rackPos) != com.harold.realisticsurvival.block.DryingRackBlockEntity.Weather.SMOKE,
                "no smoke once the fire's gone");
        level.destroyBlock(rackPos, true);

        // ---- salt from sea water
        BlockPos fire2 = at.offset(-2, 1, 0);
        level.setBlockAndUpdate(fire2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        BlockPos potPos = fire2.above();
        level.setBlockAndUpdate(potPos, ModRegistry.COOKING_POT_BLOCK.get().defaultBlockState()
                .setValue(CookingPotBlock.WATER, 2).setValue(CookingPotBlock.SALTY, true));
        for (int i = 0; i < 14; i++) level.getBlockState(potPos).tick(level, potPos, level.getRandom());
        var pot = level.getBlockState(potPos);
        check(pot.getValue(CookingPotBlock.WATER) == 0 && pot.getValue(CookingPotBlock.SALT) == 2 && !pot.getValue(CookingPotBlock.SALTY),
                "2 cups of sea water boil down to 2 salt (" + pot + ")");
        player.getInventory().clearContent();
        pot.useWithoutItem(level, player, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(potPos), net.minecraft.core.Direction.UP, potPos, false));
        check(player.getInventory().countItem(ModRegistry.SALT.get()) == 2, "scrape the salt out of the pot");
        check(level.getBlockState(potPos).getValue(CookingPotBlock.SALT) == 0, "the pot is empty after");
    }

    private static void clear(ServerLevel level, BlockPos at, int r) {
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            level.setBlockAndUpdate(at.offset(x, 0, z), Blocks.STONE.defaultBlockState());
            for (int y = 1; y <= 5; y++) level.setBlockAndUpdate(at.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
    }

    /** Build a brick oven with its front facing the given way. Used by the tests. */
    public static BrickOven.Oven buildOven(ServerLevel level, BlockPos origin, net.minecraft.core.Direction front, boolean lit) {
        BrickOven.Oven o = new BrickOven.Oven(origin, front);
        for (int x = -1; x <= 4; x++) for (int z = -1; z <= 4; z++) {
            level.setBlockAndUpdate(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState());
            for (int y = 0; y <= 6; y++) level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
        }
        for (int ly = 0; ly < 5; ly++) for (int lz = 0; lz < 4; lz++) for (int lx = 0; lx < 4; lx++) {
            boolean ring = lx == 0 || lx == 3 || lz == 0 || lz == 3;
            boolean doorway = lz == 0 && (lx == 1 || lx == 2) && (ly == 1 || ly == 2);
            var state = Blocks.AIR.defaultBlockState();
            if (ring && !doorway) state = ModRegistry.OVEN_WALL_BLOCK.get().defaultBlockState();
            else if (!ring && ly == 0) state = Blocks.GRAVEL.defaultBlockState();
            else if (!ring && ly == 1 && lz == 2) state = Blocks.CAMPFIRE.defaultBlockState().setValue(net.minecraft.world.level.block.CampfireBlock.LIT, lit);
            level.setBlockAndUpdate(o.at(lx, ly, lz), state);
        }
        return o;
    }

    private static void oven(MinecraftServer server, ServerLevel level, BlockPos at) {
        // recipes moved to the oven
        var rm = server.getRecipeManager();
        for (String id : new String[] { "furnace", "iron_pickaxe", "bucket", "anvil", "golden_sword", "copper_helmet", "shield" }) {
            var r = rm.byKey(ResourceKey.create(Registries.RECIPE, net.minecraft.resources.Identifier.withDefaultNamespace(id)));
            check(r.isPresent() && r.get().value() instanceof OvenRecipe, id + " is an oven recipe");
        }
        for (String id : new String[] { "flint_and_steel", "stone_pickaxe", "crafting_table" }) {
            var r = rm.byKey(ResourceKey.create(Registries.RECIPE, net.minecraft.resources.Identifier.withDefaultNamespace(id)));
            check(r.isPresent() && !(r.get().value() instanceof OvenRecipe), id + " is still a crafting table recipe");
        }
        var cob = new ItemStack(Items.COBBLESTONE);
        var grid = java.util.List.of(cob, cob, cob, cob, ItemStack.EMPTY, cob, cob, cob, cob);
        var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, grid);
        check(rm.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level).isEmpty(), "crafting table can't make a furnace");
        check(rm.getRecipeFor(ModRegistry.OVEN_RECIPE.get(), input, level).isPresent(), "the oven can make a furnace");

        // bricks dry
        server.setWeatherParameters(24000, 0, false, false);
        BlockPos pilePos = at.offset(8, 0, 0);
        level.setBlockAndUpdate(pilePos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pilePos, ModRegistry.CLAY_BRICK_PILE.get().defaultBlockState().setValue(ClayBrickPileBlock.COUNT, 3));
        check(level.getBlockTicks().hasScheduledTick(pilePos, ModRegistry.CLAY_BRICK_PILE.get()), "wet bricks are scheduled to dry");
        level.getBlockState(pilePos).tick(level, pilePos, level.getRandom());
        var pile = level.getBlockState(pilePos);
        check(pile.getValue(ClayBrickPileBlock.DRY), "bricks dried");
        var drops = net.minecraft.world.level.block.Block.getDrops(pile, level, pilePos, null);
        check(drops.size() == 1 && drops.get(0).is(ModRegistry.DRY_CLAY_BRICK.get()) && drops.get(0).getCount() == 3, "a pile of 3 dry bricks drops 3 dried bricks (" + drops + ")");
        int t = ClayBrickPileBlock.dryingTime(level.getRandom());
        check(t >= 24000 && t <= 72000, "drying takes 1-3 days (" + t + " ticks)");

        // the multiblock, built facing each way
        net.minecraft.core.Direction[] fronts = { net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
                net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST };
        for (int i = 0; i < 4; i++) {
            BlockPos origin = at.offset(i * 7, 0, 0);
            BrickOven.Oven built = buildOven(level, origin, fronts[i], false);
            BrickOven.Oven found = BrickOven.find(level, built.at(3, 4, 3));
            check(found != null && found.front() == fronts[i] && found.origin().equals(origin), "oven facing " + fronts[i] + " is recognised");
            if (found == null) continue;
            check(!found.lit(level), "unlit oven is cold");
            BlockPos fire = found.campfires()[0];
            level.setBlockAndUpdate(fire, level.getBlockState(fire).setValue(net.minecraft.world.level.block.CampfireBlock.LIT, true));
            check(found.lit(level), "one lit campfire heats the oven");
        }
        BrickOven.Oven o = BrickOven.find(level, at);
        if (o == null) return;
        // the workbench makes a furnace
        FakePlayer smith = FakePlayerFactory.getMinecraft(level);
        smith.setPos(o.at(1, 1, 0).getX() + 0.5, o.at(1, 1, 0).getY(), o.at(1, 1, 0).getZ() + 0.5);
        BlockPos wallPos = o.at(0, 1, 0);
        OvenMenu menu = new OvenMenu(0, smith.getInventory(), net.minecraft.world.inventory.ContainerLevelAccess.create(level, wallPos), wallPos);
        for (int i = 0; i < 9; i++) if (i != 4) menu.getSlot(1 + i).set(new ItemStack(Items.COBBLESTONE));
        check(menu.getSlot(0).getItem().is(Items.FURNACE), "oven workbench shows a furnace (" + menu.getSlot(0).getItem() + ")");
        menu.getSlot(0).onTake(smith, menu.getSlot(0).getItem());
        check(menu.getSlot(1).getItem().isEmpty(), "making it used up the cobblestone");
        check(menu.stillValid(smith), "workbench stays open while the oven is hot");
        menu.removed(smith);

        // break a wall: no longer an oven, and it says what's missing
        level.setBlockAndUpdate(o.at(3, 2, 2), Blocks.AIR.defaultBlockState());
        check(BrickOven.find(level, o.at(0, 0, 0)) == null, "an oven with a missing wall isn't an oven");
        var missing = BrickOven.whatsMissing(level, o.at(0, 0, 0));
        var contents = (net.minecraft.network.chat.contents.TranslatableContents) missing.getContents();
        check(contents.getKey().endsWith("missing.wall") && "3".equals(String.valueOf(contents.getArgs()[0])),
                "it says what's missing: " + contents.getKey() + " layer " + contents.getArgs()[0]);
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
        var campfireRecipe = server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, net.minecraft.resources.Identifier.withDefaultNamespace("campfire")));
        check(campfireRecipe.isPresent() && campfireRecipe.get().value() instanceof net.minecraft.world.item.crafting.ShapedRecipe sr
                && sr.pattern.ingredients().stream().anyMatch(i -> i.isPresent() && i.get().test(new ItemStack(ModRegistry.ROCK.get()))),
                "campfire recipe is the realistic one (rocks, not coal)");
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
        chopper.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_AXE));
        chopper.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FLINT_HATCHET.get()));
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
        // better axes take fewer cuts
        int[] expect = { 4, 3, 2 };
        net.minecraft.world.item.Item[] axes = { Items.WOODEN_AXE, Items.IRON_AXE, Items.DIAMOND_AXE };
        for (int i = 0; i < axes.length; i++) {
            chopper.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(axes[i]));
            check(TreeFelling.cutsToFell(chopper, Blocks.OAK_LOG.defaultBlockState()) == expect[i], axes[i] + " fells in " + expect[i] + " cuts");
        }
        chopper.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.FLINT_HATCHET.get()));
        check(TreeFelling.cutsToFell(chopper, Blocks.OAK_LOG.defaultBlockState()) == 4, "flint hatchet fells in 4 cuts");
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
