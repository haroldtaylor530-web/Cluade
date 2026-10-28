package com.harold.fieldkit.forge;

import java.nio.file.Path;

import com.harold.fieldkit.Fieldkit;
import com.harold.fieldkit.Platform;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(Fieldkit.MOD_ID)
public final class FieldkitForge {
    public FieldkitForge() {
        if (FMLEnvironment.dist != Dist.CLIENT) return; // client-side only; does nothing on a server
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegisterKeyMappingsEvent e) -> {
            e.register(Fieldkit.OPEN);
            e.register(Fieldkit.TOGGLE_HUD);
        });
        Fieldkit.init(new Platform() {
            @Override
            public String loader() {
                return "Forge";
            }

            @Override
            public String loaderVersion() {
                return FMLLoader.versionInfo().forgeVersion();
            }

            @Override
            public int modCount() {
                return ModList.get().size();
            }

            @Override
            public Path configDir() {
                return FMLPaths.CONFIGDIR.get();
            }

            @Override
            public Path gameDir() {
                return FMLPaths.GAMEDIR.get();
            }

            @Override
            public boolean debugScreenOpen() {
                return Minecraft.getInstance().options.renderDebug;
            }

            @Override
            public float averageTickMs() {
                MinecraftServer s = Minecraft.getInstance().getSingleplayerServer();
                return s == null ? -1 : s.getAverageTickTime();
            }

            @Override
            public void createTestWorld(String name, long seed) {
                Minecraft.getInstance().createWorldOpenFlows().createFreshLevel(name,
                        new net.minecraft.world.level.LevelSettings(name, net.minecraft.world.level.GameType.SURVIVAL, false,
                                net.minecraft.world.Difficulty.NORMAL, true, new net.minecraft.world.level.GameRules(),
                                net.minecraft.world.level.WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(seed, true, false),
                        net.minecraft.world.level.levelgen.presets.WorldPresets::createNormalWorldDimensions);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) Fieldkit.tick();
        });
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> Fieldkit.renderHud(e.getGuiGraphics(), e.getPartialTick()));
        MinecraftForge.EVENT_BUS.addListener((ClientChatReceivedEvent e) -> Fieldkit.chat(e.getMessage(), !e.isSystem()));
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn e) -> Fieldkit.joining());
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> Fieldkit.left());
        MinecraftForge.EVENT_BUS.addListener((AttackEntityEvent e) -> {
            if (e.getEntity().level().isClientSide && e.getEntity() == Minecraft.getInstance().player) Fieldkit.attacked(e.getTarget());
        });
        if (Boolean.getBoolean("fieldkit.clienttest")) MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) com.harold.fieldkit.screen.ClientTest.tick();
        });
    }
}
