package com.harold.realisticsurvival;

import com.harold.realisticsurvival.block.CookingPotBlock;
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
    }

    private SelfTest() {}
}
