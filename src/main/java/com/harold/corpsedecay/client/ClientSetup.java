package com.harold.corpsedecay.client;

import com.harold.corpsedecay.ModRegistry;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import com.harold.corpsedecay.client.config.CorpseConfigScreen;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

public final class ClientSetup {
    /** Bumped on every resource reload so corpses rebuild against the new renderers. */
    public static int epoch;

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(ClientSetup::renderers);
        modBus.addListener(ClientSetup::particles);
        modBus.addListener(ClientSetup::reload);
        CorpseEntity.clientTick = CorpseClient::tick;
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new CorpseConfigScreen(parent)));
        if (Boolean.getBoolean("corpsedecay.clienttest")) {
            MinecraftForge.EVENT_BUS.register(ClientSelfTest.class);
        }
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.CORPSE.get(), CorpseRenderer::new);
        e.registerEntityRenderer(ModRegistry.WHALE.get(), WhaleRenderer::new);
    }

    private static void particles(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ModRegistry.FLY.get(), FlyParticle.Provider::new);
    }

    private static void reload(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener((ResourceManagerReloadListener) rm -> {
            ModelInfo.clearCache();
            DecayTextures.clear();
            epoch++;
        });
    }
}
