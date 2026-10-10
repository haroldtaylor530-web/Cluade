package com.harold.realisticsurvival.client;

import com.harold.realisticsurvival.ModRegistry;
import com.harold.realisticsurvival.RealisticSurvival;
import com.harold.realisticsurvival.block.CookingPotBlock;
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
            if (ticks == 360) {
                RealisticSurvival.LOGGER.info("CLIENTTEST DONE");
                mc.stop();
            }
        } catch (Throwable t) {
            RealisticSurvival.LOGGER.error("CLIENTTEST exception", t);
            mc.stop();
        }
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
            BlockPos base = p.blockPosition();
            // flat stage in front (south) of the player
            for (int x = -3; x <= 3; x++) {
                for (int z = 1; z <= 6; z++) {
                    level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState());
                    for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(base.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(base.offset(0, 0, 3), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
            level.setBlockAndUpdate(base.offset(0, 1, 3), ModRegistry.COOKING_POT_BLOCK.get().defaultBlockState()
                    .setValue(CookingPotBlock.WATER, 3).setValue(CookingPotBlock.BOILED, true));
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
