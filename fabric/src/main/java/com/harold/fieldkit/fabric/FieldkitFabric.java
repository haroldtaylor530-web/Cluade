package com.harold.fieldkit.fabric;

import java.nio.file.Path;

import com.harold.fieldkit.Fieldkit;
import com.harold.fieldkit.Platform;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionResult;

public final class FieldkitFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(Fieldkit.OPEN);
        KeyBindingHelper.registerKeyBinding(Fieldkit.TOGGLE_HUD);
        Fieldkit.init(new Platform() {
            @Override
            public String loader() {
                return "Fabric";
            }

            @Override
            public String loaderVersion() {
                return FabricLoader.getInstance().getModContainer("fabricloader")
                        .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
            }

            @Override
            public int modCount() {
                return FabricLoader.getInstance().getAllMods().size();
            }

            @Override
            public Path configDir() {
                return FabricLoader.getInstance().getConfigDir();
            }

            @Override
            public Path gameDir() {
                return FabricLoader.getInstance().getGameDir();
            }

            @Override
            public boolean debugScreenOpen() {
                return Minecraft.getInstance().getDebugOverlay().showDebugScreen();
            }

            @Override
            public float averageTickMs() {
                MinecraftServer s = Minecraft.getInstance().getSingleplayerServer();
                return s == null ? -1 : s.getAverageTickTimeNanos() / 1_000_000F;
            }

            @Override
            public void createTestWorld(String name, long seed) {
                Minecraft.getInstance().createWorldOpenFlows().createFreshLevel(name,
                        new net.minecraft.world.level.LevelSettings(name, net.minecraft.world.level.GameType.SURVIVAL, false,
                                net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.level.GameRules(),
                                net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(seed, true, false),
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions,
                        Minecraft.getInstance().screen);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> Fieldkit.tick());
        HudRenderCallback.EVENT.register((g, delta) -> Fieldkit.renderHud(g, delta.getGameTimeDeltaPartialTick(true)));
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> Fieldkit.chat(message, true));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) Fieldkit.chat(message, false);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> Fieldkit.joining());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> Fieldkit.left());
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide && player == Minecraft.getInstance().player) Fieldkit.attacked(entity);
            return InteractionResult.PASS;
        });
        if (Boolean.getBoolean("fieldkit.clienttest")) {
            ClientTickEvents.END_CLIENT_TICK.register(mc -> com.harold.fieldkit.screen.ClientTest.tick());
        }
    }
}
