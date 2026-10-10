package com.harold.realisticsurvival;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/realisticsurvival-common.toml */
public final class Config {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue THIRST = B
            .comment("Thirst bar, drinking and dirty water")
            .define("thirst", true);
    public static final ModConfigSpec.DoubleValue THIRST_RATE = B
            .comment("How fast you get thirsty. 1 = a full bar lasts about 16 minutes resting")
            .defineInRange("thirstRate", 1.0, 0.0, 10.0);

    public static final ModConfigSpec.BooleanValue TEMPERATURE = B
            .comment("Body temperature: cold biomes, nights, rain and water chill you; deserts and lava heat you")
            .define("temperature", true);

    public static final ModConfigSpec.BooleanValue INJURIES = B
            .comment("Broken legs from falls and bleeding from wounds")
            .define("injuries", true);

    public static final ModConfigSpec.BooleanValue EARLY_GAME = B
            .comment("Harder early game: logs need an axe, flint knapping, fibre from grass, sticks from leaves")
            .define("harderEarlyGame", true);

    public static final ModConfigSpec.BooleanValue WORK_TIRES = B
            .comment("Mining and chopping make you hungrier and thirstier")
            .define("workTiresYou", true);

    static final ModConfigSpec SPEC = B.build();

    private Config() {}
}
