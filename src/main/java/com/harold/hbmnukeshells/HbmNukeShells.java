package com.harold.hbmnukeshells;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(HbmNukeShells.MOD_ID)
public final class HbmNukeShells {
    public static final String MOD_ID = "hbmnukeshells";

    public HbmNukeShells() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ShellConfig.SPEC);
        if (Boolean.getBoolean("hbmnukeshells.selftest")) MinecraftForge.EVENT_BUS.register(SelfTest.class);
    }
}
