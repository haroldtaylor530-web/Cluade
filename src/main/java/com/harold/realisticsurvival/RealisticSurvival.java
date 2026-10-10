package com.harold.realisticsurvival;

import com.harold.realisticsurvival.survival.EarlyGameEvents;
import com.harold.realisticsurvival.survival.InjuryEvents;
import com.harold.realisticsurvival.survival.SurvivalTicker;
import com.harold.realisticsurvival.survival.ThirstEvents;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(RealisticSurvival.MODID)
public class RealisticSurvival {
    public static final String MODID = "realisticsurvival";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RealisticSurvival(IEventBus modBus, ModContainer container) {
        ModRegistry.register(modBus);
        modBus.addListener(ThirstEvents::registerPayloads);
        container.registerConfig(ModConfig.Type.LOCAL, Config.SPEC);

        NeoForge.EVENT_BUS.register(SurvivalTicker.class);
        NeoForge.EVENT_BUS.register(ThirstEvents.class);
        NeoForge.EVENT_BUS.register(InjuryEvents.class);
        NeoForge.EVENT_BUS.register(EarlyGameEvents.class);
        NeoForge.EVENT_BUS.register(SelfTest.class);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
