package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.RealisticSurvival;
import com.harold.realisticsurvival.block.CampfireData;
import com.harold.realisticsurvival.block.CampfireMenu;
import com.harold.realisticsurvival.block.CookingPotBlock;
import com.harold.realisticsurvival.block.RealisticCampfireBlock;
import com.harold.realisticsurvival.survival.TreeFelling;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import com.harold.realisticsurvival.entity.ThrownSpear;
import com.harold.realisticsurvival.survival.SurvivalState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development check, only active with -Drealisticsurvival.clienttest=true (the build's
 * virtual-screen test). Joins a test world, builds a small scene, takes screenshots, quits.
 */
public final class ClientSelfTest {
    private static int ticks;

    public static boolean enabled() {
        return System.getProperty("realisticsurvival.clienttest") != null;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ticks++;
        try {
            if (ticks == 80) setUpScene(mc);
            if (ticks == 200) shot(mc, "1_scene.png");
            if (ticks == 220) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            if (ticks == 260) shot(mc, "2_third_person.png");
            if (ticks == 280) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.gui.setScreen(new InventoryScreen(mc.player));
            }
            if (ticks == 320) shot(mc, "3_inventory.png");
            if (ticks == 340) mc.gui.setScreen(null);
            // ---- campfire menu, lighting game, a falling tree
            if (ticks == 360) setUpFireAndTree(mc);
            if (ticks == 400) openCampfireMenu(mc);
            if (ticks == 440) shot(mc, "4_campfire_menu.png");
            if (ticks == 450) mc.gui.setScreen(null);
            if (ticks == 460) mc.gui.setScreen(new LightingScreen(base.offset(0, 0, 3), 3, 0.22F, false, false));
            if (ticks == 485) shot(mc, "5_lighting_game.png");
            if (ticks == 495) mc.gui.setScreen(null);
            if (ticks == 505) shot(mc, "8_chop_stages.png");
            if (ticks == 520) fellTree(mc);
            if (ticks == 540) shot(mc, "6_tree_falling.png");
            if (ticks == 600) shot(mc, "7_tree_fallen.png");
            if (ticks == 610) buildOvenScene(mc);
            if (ticks == 650) shot(mc, "9_brick_oven.png");
            if (ticks == 660) openOven(mc);
            if (ticks == 690) shot(mc, "10_oven_menu.png");
            if (ticks == 700) mc.gui.setScreen(null);
            if (ticks == 720) buildFoodScene(mc);
            if (ticks == 770) shot(mc, "11_drying_and_salt.png");
            if (ticks == 780) {
                fillFoodInventory(mc);
            }
            if (ticks == 800) mc.gui.setScreen(new InventoryScreen(mc.player));
            if (ticks == 830) {
                shot(mc, "12_food_freshness.png");
                logTooltips(mc);
            }
            if (ticks == 840) mc.gui.setScreen(null);
            if (ticks == 860) startMining(mc);
            if (mc.gui.screen() instanceof MiningScreen ms) {
                if (miningOpened < 0) miningOpened = ticks;
                if (ticks == miningOpened + 6) shot(mc, "13_mining_start.png");
                if (ticks > miningOpened + 10) ms.botStep();
                if (ticks == miningOpened + 140) shot(mc, "14_mining_dig.png");
                lastMining = ms;
            } else if (lastMining != null && miningClosed < 0) {
                miningClosed = ticks;
                RealisticSurvival.LOGGER.info("CLIENTTEST mining {}/{} ore, {} pieces left, {} swings in {} ticks",
                        lastMining.collected, lastMining.totalOre, lastMining.piecesLeft(), lastMining.actions, ticks - miningOpened);
            }
            if (miningClosed > 0 && ticks == miningClosed + 15) shot(mc, "15_mined.png");
            if (miningClosed > 0 && ticks == miningClosed + 25) buildBlocksScene(mc);
            if (miningClosed > 0 && ticks == miningClosed + 70) {
                shot(mc, "16_rubble_and_fence.png");
                var ctx = net.minecraft.world.item.Item.TooltipContext.of(mc.level);
                ItemStack dull = mc.player.getInventory().getItem(3);
                var lines = dull.getTooltipLines(ctx, mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
                RealisticSurvival.LOGGER.info("CLIENTTEST tooltip {}", String.join(" | ", lines.stream().map(c -> c.getString()).toList()));
            }
            if (miningClosed > 0 && ticks == miningClosed + 74) {
                var friction = new FrictionScreen(base, 24, 0.125F, false);
                mc.gui.setScreen(friction);
                friction.simulate(15);
            }
            if (miningClosed > 0 && ticks == miningClosed + 80) {
                shot(mc, "17_hand_drill.png");
                if (mc.gui.screen() instanceof FrictionScreen f) {
                    RealisticSurvival.LOGGER.info("CLIENTTEST friction heat {}", f.heat());
                    f.simulate(20);
                    RealisticSurvival.LOGGER.info("CLIENTTEST friction success {}", f.succeeded());
                }
            }
            if ((miningClosed > 0 && ticks == miningClosed + 100) || ticks == 2400) {
                RealisticSurvival.LOGGER.info("CLIENTTEST DONE");
                mc.stop();
            }
        } catch (Throwable t) {
            RealisticSurvival.LOGGER.error("CLIENTTEST exception", t);
            mc.stop();
        }
    }

    private static BlockPos base = BlockPos.ZERO;

    private static void setUpFireAndTree(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            for (int x = -7; x <= 7; x++) for (int z = 1; z <= 18; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 0; y <= 10; y++) {
                    BlockPos q = base.offset(x, y, z);
                    if (!(x == 0 && z == 3 && y <= 1)) level.setBlockAndUpdate(q, Blocks.AIR.defaultBlockState());
                }
            }
            // a big realistic campfire under the pot
            BlockPos fire = base.offset(0, 0, 3);
            level.setBlockAndUpdate(fire, ModRegistry.CAMPFIRE_BLOCK.get().defaultBlockState());
            if (level.getBlockEntity(fire) instanceof CampfireBlockEntity be) {
                CampfireData d = RealisticCampfireBlock.data(be);
                d.kindling = new ItemStack(ModRegistry.PLANT_FIBRE.get(), 6);
                d.fuel = new ItemStack(net.minecraft.world.item.Items.OAK_LOG, 12);
                RealisticCampfireBlock.light(level, fire);
                level.setBlockAndUpdate(fire, level.getBlockState(fire).setValue(RealisticCampfireBlock.SIZE, 3));
            }
            level.setBlockAndUpdate(fire.above(), ModRegistry.COOKING_POT_BLOCK.get().defaultBlockState()
                    .setValue(CookingPotBlock.WATER, 3).setValue(CookingPotBlock.BOILED, true).setValue(CookingPotBlock.ON_CAMPFIRE, true));
            // an oak tree to cut down
            BlockPos tree = base.offset(3, 0, 9);
            var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.DISTANCE, 1);
            for (int y = 3; y <= 6; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                if (y == 6 && (Math.abs(x) > 1 || Math.abs(z) > 1)) continue;
                level.setBlock(tree.offset(x, y, z), leaves, 2);
            }
            for (int y = 0; y <= 5; y++) level.setBlock(tree.above(y), Blocks.OAK_LOG.defaultBlockState(), 2);
            p.getInventory().setItem(8, new ItemStack(ModRegistry.BOW_DRILL.get()));
            // three trunks chopped one, two and three layers deep
            for (int i = 0; i < 3; i++) {
                BlockPos stump = base.offset(-2 - i * 2, 0, 6);
                level.setBlockAndUpdate(stump, ModRegistry.CHOPPED_LOGS.get("oak").get().defaultBlockState()
                        .setValue(com.harold.realisticsurvival.block.ChoppedLogBlock.STAGE, i + 1));
                level.setBlockAndUpdate(stump.above(), Blocks.OAK_LOG.defaultBlockState());
            }
        });
    }

    private static void openCampfireMenu(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            BlockPos fire = base.offset(0, 0, 3);
            if (p.level().getBlockEntity(fire) instanceof CampfireBlockEntity be) {
                p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> CampfireMenu.forCampfire(id, inv, be, fire),
                        net.minecraft.network.chat.Component.translatable("container.realisticsurvival.campfire")), fire);
            }
        });
    }

    private static void fellTree(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            p.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, java.util.Set.of(), -38.0F, -8.0F, true);
            BlockPos tree = base.offset(3, 0, 9);
            // fall to the east so it crosses the view
            TreeFelling.fell(level, tree, tree.getX() + 0.5 - 6, tree.getZ() + 0.5);
            level.setBlockAndUpdate(tree, Blocks.AIR.defaultBlockState());
        });
    }

    private static BlockPos ovenWall = BlockPos.ZERO;

    private static void buildOvenScene(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            var o = com.harold.realisticsurvival.SelfTest.buildOven(level, base.offset(-6, 0, 10), net.minecraft.core.Direction.NORTH, true);
            com.harold.realisticsurvival.block.BrickOven.setFormed(level, o, true);
            ovenWall = o.at(0, 1, 0);
            // clear the chopped stumps out of the way of the camera
            for (int x = -7; x <= 0; x++) for (int y = 0; y <= 2; y++) level.setBlockAndUpdate(base.offset(x, y, 6), Blocks.AIR.defaultBlockState());
            // bricks drying in front of it
            for (int i = 0; i < 2; i++) {
                BlockPos pile = base.offset(-1 - i, 0, 8);
                level.setBlockAndUpdate(pile.below(), Blocks.GRASS_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(pile, ModRegistry.CLAY_BRICK_PILE.get().defaultBlockState()
                        .setValue(com.harold.realisticsurvival.block.ClayBrickPileBlock.COUNT, 4)
                        .setValue(com.harold.realisticsurvival.block.ClayBrickPileBlock.DRY, i == 1));
            }
            p.teleportTo(level, base.getX() - 3.0, base.getY(), base.getZ() + 3.5, java.util.Set.of(), 0.0F, 8.0F, true);
        });
    }

    private static void openOven(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            BlockPos wall = ovenWall;
            p.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new com.harold.realisticsurvival.block.OvenMenu(id, inv,
                    net.minecraft.world.inventory.ContainerLevelAccess.create(p.level(), wall), wall),
                    net.minecraft.network.chat.Component.translatable("container.realisticsurvival.oven")), wall);
            if (p.containerMenu instanceof com.harold.realisticsurvival.block.OvenMenu menu) {
                for (int i = 0; i < 9; i++) if (i != 4) menu.getSlot(1 + i).set(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
            }
        });
    }

    private static void buildFoodScene(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "kill @e[type=item]");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "kill @e[type=realisticsurvival:falling_tree]");
            for (int x = -1; x <= 9; x++) for (int z = 8; z <= 17; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 0; y <= 6; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            long now = level.getGameTime();
            // smoking over a campfire
            BlockPos fire = base.offset(3, 0, 13);
            level.setBlockAndUpdate(fire, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
            BlockPos rack = fire.above();
            level.setBlockAndUpdate(rack, ModRegistry.DRYING_RACK_BLOCK.get().defaultBlockState());
            if (level.getBlockEntity(rack) instanceof com.harold.realisticsurvival.block.DryingRackBlockEntity r) {
                r.hang(new ItemStack(net.minecraft.world.item.Items.BEEF));
                r.hang(new ItemStack(net.minecraft.world.item.Items.SALMON));
                r.hang(new ItemStack(net.minecraft.world.item.Items.PORKCHOP));
                r.hang(new ItemStack(net.minecraft.world.item.Items.APPLE));
            }
            // drying in the sun
            BlockPos sun = base.offset(6, 0, 13);
            level.setBlockAndUpdate(sun, ModRegistry.DRYING_RACK_BLOCK.get().defaultBlockState());
            if (level.getBlockEntity(sun) instanceof com.harold.realisticsurvival.block.DryingRackBlockEntity r) {
                r.hang(new ItemStack(net.minecraft.world.item.Items.MUTTON));
                r.hang(new ItemStack(net.minecraft.world.item.Items.MUTTON));
                r.hang(new ItemStack(net.minecraft.world.item.Items.COD));
                r.hang(new ItemStack(ModRegistry.JERKY.get()));
            }
            // a pot of sea water boiled down to salt
            BlockPos fire2 = base.offset(0, 0, 13);
            level.setBlockAndUpdate(fire2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
            level.setBlockAndUpdate(fire2.above(), ModRegistry.COOKING_POT_BLOCK.get().defaultBlockState()
                    .setValue(CookingPotBlock.SALT, 3).setValue(CookingPotBlock.ON_CAMPFIRE, true));
            // an ice-cooled chest
            level.setBlockAndUpdate(base.offset(8, 0, 13), Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(base.offset(8, 0, 14), Blocks.PACKED_ICE.defaultBlockState());
            level.setBlockAndUpdate(base.offset(8, 1, 14), Blocks.PACKED_ICE.defaultBlockState());
            p.teleportTo(level, base.getX() + 4.5, base.getY(), base.getZ() + 9.0, java.util.Set.of(), 0.0F, 18.0F, true);
        });
    }

    private static ItemStack aged(net.minecraft.world.item.Item item, int count, long now, double days, int preserved) {
        ItemStack s = new ItemStack(item, count);
        s.set(ModRegistry.FOOD_START.get(), com.harold.realisticsurvival.survival.Spoilage.round(now - (long) (days * 24000)));
        if (preserved != 0) s.set(ModRegistry.PRESERVED.get(), preserved);
        return s;
    }

    private static void fillFoodInventory(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            long now = p.level().getGameTime();
            var inv = p.getInventory();
            inv.clearContent();
            inv.setItem(0, aged(net.minecraft.world.item.Items.BEEF, 6, now, 0.2, 0));
            inv.setItem(1, aged(net.minecraft.world.item.Items.BEEF, 3, now, 1.8, 0));
            inv.setItem(2, aged(net.minecraft.world.item.Items.BEEF, 2, now, 2.6, 0));
            inv.setItem(3, aged(net.minecraft.world.item.Items.COOKED_BEEF, 4, now, 1.0, 0));
            inv.setItem(4, aged(net.minecraft.world.item.Items.BEEF, 8, now, 0.5, com.harold.realisticsurvival.survival.Spoilage.SALTED));
            inv.setItem(5, aged(net.minecraft.world.item.Items.COOKED_PORKCHOP, 5, now, 3.0, com.harold.realisticsurvival.survival.Spoilage.SMOKED));
            inv.setItem(6, aged(ModRegistry.JERKY.get(), 12, now, 4.0, 0));
            inv.setItem(7, aged(net.minecraft.world.item.Items.BREAD, 3, now, 6.0, 0));
            inv.setItem(8, aged(ModRegistry.DRIED_FRUIT.get(), 9, now, 1.0, 0));
            inv.setItem(9, new ItemStack(ModRegistry.SALT.get(), 7));
            inv.setItem(10, new ItemStack(ModRegistry.ROTTEN_FOOD.get(), 2));
            inv.setItem(11, new ItemStack(ModRegistry.SALT_WATER_CUP.get()));
            inv.setItem(12, aged(ModRegistry.DRIED_FISH.get(), 5, now, 2.0, 0));
            inv.setItem(13, new ItemStack(ModRegistry.DRYING_RACK.get(), 2));
            inv.setItem(14, aged(net.minecraft.world.item.Items.APPLE, 10, now, 3.0, 0));
            inv.setItem(15, new ItemStack(net.minecraft.world.item.Items.ROTTEN_FLESH, 4));
        });
    }

    private static void logTooltips(Minecraft mc) {
        var ctx = net.minecraft.world.item.Item.TooltipContext.of(mc.level);
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.isEmpty()) continue;
            var lines = s.getTooltipLines(ctx, mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
            RealisticSurvival.LOGGER.info("CLIENTTEST tooltip {}", String.join(" | ", lines.stream().map(c -> c.getString()).toList()));
        }
    }

    private static int miningOpened = -1, miningClosed = -1;
    private static MiningScreen lastMining;

    private static void startMining(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            BlockPos ore = base.offset(4, 0, 11);
            level.setBlockAndUpdate(ore, Blocks.DIAMOND_ORE.defaultBlockState());
            p.getInventory().setItem(p.getInventory().getSelectedSlot(), new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
            p.teleportTo(level, ore.getX() + 0.5, ore.getY(), ore.getZ() - 1.5, java.util.Set.of(), 0.0F, 30.0F, true);
            com.harold.realisticsurvival.survival.OreMining.start(p, ore, level.getBlockState(ore));
        });
    }

    /** The rubble slab, rubble wall and stick fence, side by side. */
    private static void buildBlocksScene(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            for (int x = -5; x <= 5; x++) for (int z = 14; z <= 23; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
            }
            var slab = ModRegistry.RUBBLE_SLAB_BLOCK.get().defaultBlockState();
            level.setBlockAndUpdate(base.offset(-1, 0, 18), slab.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM));
            level.setBlockAndUpdate(base.offset(0, 0, 18), slab.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.DOUBLE));
            level.setBlockAndUpdate(base.offset(1, 0, 18), slab.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
            for (int x = 1; x <= 4; x++) level.setBlockAndUpdate(base.offset(x, 0, 21), ModRegistry.RUBBLE_WALL_BLOCK.get().defaultBlockState());
            level.setBlockAndUpdate(base.offset(4, 0, 22), ModRegistry.RUBBLE_WALL_BLOCK.get().defaultBlockState());
            level.setBlockAndUpdate(base.offset(2, 1, 21), ModRegistry.RUBBLE_WALL_BLOCK.get().defaultBlockState());
            for (int x = -4; x <= -1; x++) level.setBlockAndUpdate(base.offset(x, 0, 21), ModRegistry.STICK_FENCE_BLOCK.get().defaultBlockState());
            level.setBlockAndUpdate(base.offset(-4, 0, 22), ModRegistry.STICK_FENCE_BLOCK.get().defaultBlockState());
            level.setBlockAndUpdate(base.offset(-3, 1, 21), ModRegistry.STICK_FENCE_BLOCK.get().defaultBlockState());
            p.getInventory().setItem(0, new ItemStack(ModRegistry.RUBBLE_SLAB.get(), 6));
            p.getInventory().setItem(1, new ItemStack(ModRegistry.RUBBLE_WALL.get(), 6));
            p.getInventory().setItem(2, new ItemStack(ModRegistry.STICK_FENCE.get(), 3));
            ItemStack dull = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
            com.harold.realisticsurvival.survival.ToolWear.setSharpness(dull, 22);
            p.getInventory().setItem(3, dull);
            p.getInventory().setItem(6, new ItemStack(ModRegistry.FLINT_STRIKER.get()));
            p.getInventory().setItem(7, new ItemStack(ModRegistry.FIRE_STARTER.get(), 3));
            p.getInventory().setItem(4, new ItemStack(ModRegistry.WHETSTONE.get()));
            com.harold.realisticsurvival.survival.SurvivalState.get(p).stamina = 9.0F;
            p.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 15.0, java.util.Set.of(), 0.0F, 14.0F, true);
        });
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
                msg -> RealisticSurvival.LOGGER.info("CLIENTTEST screenshot {}", name));
    }

    private static void setUpScene(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            RealisticSurvival.LOGGER.error("CLIENTTEST no integrated server");
            return;
        }
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = (ServerLevel) p.level();
            p.setGameMode(GameType.SURVIVAL);
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "weather clear");
            base = p.blockPosition();
            // flat stage in front (south) of the player
            for (int x = -3; x <= 3; x++) {
                for (int z = 1; z <= 6; z++) {
                    level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(base.offset(0, 0, 3), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
            level.setBlockAndUpdate(base.offset(0, 1, 3), ModRegistry.COOKING_POT_BLOCK.get().defaultBlockState()
                    .setValue(CookingPotBlock.WATER, 3).setValue(CookingPotBlock.BOILED, true).setValue(CookingPotBlock.ON_CAMPFIRE, true));
            level.setBlockAndUpdate(base.offset(-2, 0, 2), ModRegistry.ROCK_BLOCK.get().defaultBlockState());
            level.setBlockAndUpdate(base.offset(2, 0, 2), ModRegistry.STICK_ON_GROUND.get().defaultBlockState());
            level.setBlockAndUpdate(base.offset(-1, 0, 4), ModRegistry.COOKING_POT_BLOCK.get().defaultBlockState()
                    .setValue(CookingPotBlock.WATER, 4));

            // a spear hanging in the air, pointing east
            ThrownSpear spear = new ThrownSpear(level, p, new ItemStack(ModRegistry.FLINT_SPEAR.get()), 7.0F);
            spear.setNoGravity(true);
            spear.setPos(base.getX() + 2.0, base.getY() + 1.6, base.getZ() + 2.5);
            spear.setDeltaMovement(Vec3.ZERO);
            spear.setYRot(-90.0F);
            spear.setXRot(0.0F);
            level.addFreshEntity(spear);

            p.teleportTo(level, base.getX() + 0.5, base.getY(), base.getZ() + 0.5, java.util.Set.of(), 0.0F, 25.0F, true);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(ModRegistry.IRON_SPEAR.get()));
            ModRegistry.ITEMS.getEntries().forEach(e -> p.getInventory().add(new ItemStack(e.get())));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(ModRegistry.FUR_HAT.get()));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(ModRegistry.FUR_COAT.get()));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(ModRegistry.FUR_TROUSERS.get()));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(ModRegistry.FUR_BOOTS.get()));

            SurvivalState s = SurvivalState.get(p);
            s.thirst = 13.0F;
            s.bodyTemp = 34.6F;
            p.addEffect(new MobEffectInstance(ModRegistry.BLEEDING, 20 * 60, 0));
        });
        mc.player.getInventory().setSelectedSlot(0);
    }

    private ClientSelfTest() {}
}
