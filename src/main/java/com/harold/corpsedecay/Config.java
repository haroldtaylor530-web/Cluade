package com.harold.corpsedecay;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;

/** Common config: config/corpsedecay-common.toml */
public final class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue ONLY_PLAYER_KILLS;
    public static final ForgeConfigSpec.DoubleValue MINUTES_TO_SKELETON;
    public static final ForgeConfigSpec.DoubleValue SKELETON_MINUTES;
    public static final ForgeConfigSpec.IntValue MAX_CORPSES;
    public static final ForgeConfigSpec.DoubleValue RAGDOLL_FORCE;
    public static final ForgeConfigSpec.BooleanValue LOOT_ALL_EQUIPMENT;
    public static final ForgeConfigSpec.BooleanValue FLIES;
    public static final ForgeConfigSpec.BooleanValue REMAINS;
    public static final ForgeConfigSpec.BooleanValue FOSSILS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        ENABLED = b.comment("Leave corpses when mobs die.").define("enabled", true);
        ONLY_PLAYER_KILLS = b.comment("Only leave corpses for mobs killed by a player.")
                .define("onlyPlayerKills", false);
        MINUTES_TO_SKELETON = b.comment("Real-time minutes for a fresh body to rot down to a bare skeleton (20 = one Minecraft day).")
                .defineInRange("minutesToSkeleton", 20.0, 0.1, 100000.0);
        SKELETON_MINUTES = b.comment("How many more minutes the skeleton lies there before crumbling away. 0 = skeletons stay forever.")
                .defineInRange("skeletonMinutes", 60.0, 0.0, 1000000.0);
        MAX_CORPSES = b.comment("Most corpses allowed loaded in one dimension; the oldest crumble first when there are more (keeps mob farms from lagging).")
                .defineInRange("maxCorpses", 60, 1, 10000);
        RAGDOLL_FORCE = b.comment("How hard killing blows throw the body (1 = normal).")
                .defineInRange("ragdollForce", 1.0, 0.0, 10.0);
        LOOT_ALL_EQUIPMENT = b.comment("Let players take every piece of armour and held item off a corpse, not only what the mob would normally drop.")
                .define("lootAllEquipment", false);
        FLIES = b.comment("Flies buzz around rotting bodies.").define("flies", true);
        REMAINS = b.comment("New chunks can have the remains of things that died long ago: adventurers in their armour, animals, with moss, mushrooms or cobwebs growing on them.")
                .define("remains", true);
        FOSSILS = b.comment("New chunks can have fossils in the rock: whole skeletons of fossil blocks, and ammonites, trilobites, fish and ferns.")
                .define("fossils", true);
        EXCLUDED = b.comment("Entity ids that never leave a corpse, e.g. [\"minecraft:bat\"]. The tag #corpsedecay:no_corpse works too.")
                .defineListAllowEmpty("excludedMobs", List.of(), o -> o instanceof String);
        SPEC = b.build();
    }

    private Config() {
    }
}
