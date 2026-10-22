package com.harold.corpsedecay.client;

import com.harold.corpsedecay.ModRegistry;
import com.harold.corpsedecay.corpse.CorpseEntity;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

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
        if (Boolean.getBoolean("corpsedecay.clienttest")) {
            MinecraftForge.EVENT_BUS.register(ClientSelfTest.class);
        }
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModRegistry.CORPSE.get(), CorpseRenderer::new);
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
