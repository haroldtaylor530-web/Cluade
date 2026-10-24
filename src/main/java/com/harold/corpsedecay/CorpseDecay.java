package com.harold.corpsedecay;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CorpseDecay.MODID)
public class CorpseDecay {
    public static final String MODID = "corpsedecay";

    public CorpseDecay() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        MinecraftForge.EVENT_BUS.register(DeathHandler.class);
        MinecraftForge.EVENT_BUS.register(com.harold.corpsedecay.world.LateCrews.class);
        if (Boolean.getBoolean("corpsedecay.selftest")) {
            MinecraftForge.EVENT_BUS.register(com.harold.corpsedecay.test.SelfTest.class);
        }
        if (Boolean.getBoolean("corpsedecay.gentest")) {
            MinecraftForge.EVENT_BUS.register(com.harold.corpsedecay.test.GenTest.class);
        }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.harold.corpsedecay.client.ClientSetup.init(modBus));
    }
}
