package com.harold.fieldkit.seed;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

/** What the seed finder looks for near the world origin (0, 0). */
public final class Criteria {
    public static final class BiomeRule {
        public ResourceKey<Biome> biome;
        public int radius = 300;
        public boolean must = true;

        BiomeRule copy() {
            BiomeRule r = new BiomeRule();
            r.biome = biome;
            r.radius = radius;
            r.must = must;
            return r;
        }
    }

    public static final class StructRule {
        public ResourceKey<Structure> structure;
        public int radius = 500;
        public int count = 1;

        StructRule copy() {
            StructRule r = new StructRule();
            r.structure = structure;
            r.radius = radius;
            r.count = count;
            return r;
        }
    }

    public final List<BiomeRule> biomes = new ArrayList<>();
    public final List<StructRule> structs = new ArrayList<>();
    public String start = "";
    public int limit = 8;

    public Criteria copy() {
        Criteria c = new Criteria();
        for (BiomeRule b : biomes) if (b.biome != null) c.biomes.add(b.copy());
        for (StructRule s : structs) if (s.structure != null) c.structs.add(s.copy());
        c.start = start;
        c.limit = limit;
        return c;
    }
}
