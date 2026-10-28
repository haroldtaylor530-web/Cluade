package com.harold.fieldkit.map;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The game's own world generation, for any seed: biomes and where structures go. Only in singleplayer, where the
 * client also runs the world's server and so has its generator settings (including modded biomes and structures).
 */
public final class Gen {
    public record Found(Holder<Structure> structure, ChunkPos chunk) {
        public int x() {
            return chunk.getMiddleBlockX();
        }

        public int z() {
            return chunk.getMiddleBlockZ();
        }
    }

    /** Underground or tiny things that would bury the map in markers (still searchable in the seed finder). */
    private static final java.util.Set<String> HIDDEN = java.util.Set.of("mineshafts", "buried_treasures", "nether_fossils");

    private static Gen current;
    private static Object currentFor;

    public final long seed;
    public final RandomState state;
    public final BiomeSource source;
    public final ChunkGeneratorStructureState structures;
    public final List<Holder<StructureSet>> sets;
    private final NoiseGeneratorSettings settings;
    private final HolderGetter<NormalNoise.NoiseParameters> noise;
    private final HolderLookup<StructureSet> setLookup;

    private Gen(long seed, RandomState state, BiomeSource source, ChunkGeneratorStructureState structures,
            NoiseGeneratorSettings settings, HolderGetter<NormalNoise.NoiseParameters> noise, HolderLookup<StructureSet> setLookup) {
        this.seed = seed;
        this.state = state;
        this.source = source;
        this.structures = structures;
        this.sets = new ArrayList<>(structures.possibleStructureSets());
        this.settings = settings;
        this.noise = noise;
        this.setLookup = setLookup;
    }

    /** The current singleplayer overworld's generator, or null on a server / with a non-noise world. */
    public static synchronized Gen current() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            current = null;
            currentFor = null;
            return null;
        }
        ServerLevel level = server.overworld();
        if (level == null) return null;
        if (current != null && currentFor == level) return current;
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator ng)) return null;
        current = new Gen(level.getSeed(), level.getChunkSource().randomState(), generator.getBiomeSource(),
                level.getChunkSource().getGeneratorState(), ng.generatorSettings().value(),
                server.registryAccess().lookupOrThrow(Registries.NOISE), server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET));
        currentFor = level;
        return current;
    }

    /** The same world settings with another seed. */
    public Gen withSeed(long other) {
        if (other == seed) return this;
        RandomState rs = RandomState.create(settings, noise, other);
        ChunkGeneratorStructureState st = ChunkGeneratorStructureState.createForNormal(rs, other, source, setLookup);
        return new Gen(other, rs, source, st, settings, noise, setLookup);
    }

    /** The surface-level biome at a block position. */
    public Holder<Biome> biome(int x, int z) {
        int qx = QuartPos.fromBlock(x), qz = QuartPos.fromBlock(z);
        Holder<Biome> b = source.getNoiseBiome(qx, QuartPos.fromBlock(64), qz, state.sampler());
        // hills and mountains rise above y 64, where the "biome" there is a cave biome: look higher up
        if (underground(b)) b = source.getNoiseBiome(qx, QuartPos.fromBlock(160), qz, state.sampler());
        if (underground(b)) b = source.getNoiseBiome(qx, QuartPos.fromBlock(256), qz, state.sampler());
        return b;
    }

    private static final java.util.Map<Holder<Biome>, Boolean> UNDERGROUND = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean underground(Holder<Biome> b) {
        return UNDERGROUND.computeIfAbsent(b, h -> h.unwrapKey().map(k -> {
            String p = k.location().getPath();
            return p.contains("cave") || p.contains("cavern") || p.equals("deep_dark") || p.contains("underground")
                    || p.contains("subterranean") || p.contains("grotto");
        }).orElse(false));
    }

    /** Structures whose chunk lies in [cx0..cx1] x [cz0..cz1] (chunk coordinates). */
    public List<Found> structures(int cx0, int cz0, int cx1, int cz1, ResourceKey<Structure> only, int max) {
        List<Found> out = new ArrayList<>();
        for (Holder<StructureSet> set : sets) {
            StructureSet s = set.value();
            if (only == null && set.unwrapKey().map(k -> HIDDEN.contains(k.location().getPath())).orElse(false)) continue;
            if (only != null && s.structures().stream().noneMatch(e -> e.structure().is(only))) continue;
            StructurePlacement p = s.placement();
            if (p instanceof RandomSpreadStructurePlacement rs) {
                int sp = rs.spacing();
                for (int rx = Math.floorDiv(cx0, sp); rx <= Math.floorDiv(cx1, sp); rx++) {
                    for (int rz = Math.floorDiv(cz0, sp); rz <= Math.floorDiv(cz1, sp); rz++) {
                        ChunkPos c = rs.getPotentialStructureChunk(seed, rx * sp, rz * sp);
                        if (c.x < cx0 || c.x > cx1 || c.z < cz0 || c.z > cz1) continue;
                        if (!p.isStructureChunk(structures, c.x, c.z)) continue;
                        Holder<Structure> st = pick(s, c, only);
                        if (st != null) {
                            out.add(new Found(st, c));
                            if (out.size() >= max) return out;
                        }
                    }
                }
            } else if (p instanceof ConcentricRingsStructurePlacement cr) {
                structures.ensureStructuresGenerated();
                List<ChunkPos> ring = structures.getRingPositionsFor(cr);
                if (ring == null) continue;
                for (ChunkPos c : ring) {
                    if (c.x < cx0 || c.x > cx1 || c.z < cz0 || c.z > cz1) continue;
                    Holder<Structure> st = pick(s, c, only);
                    if (st != null) out.add(new Found(st, c));
                }
            }
        }
        return out;
    }

    /** The structure of a set that can generate in that chunk's biome (the game tries them in turn, too). */
    private Holder<Structure> pick(StructureSet s, ChunkPos c, ResourceKey<Structure> only) {
        Holder<Biome> b = biome(c.getMiddleBlockX(), c.getMiddleBlockZ());
        for (StructureSet.StructureSelectionEntry e : s.structures()) {
            if (only != null && !e.structure().is(only)) continue;
            if (e.structure().value().biomes().contains(b)) return e.structure();
        }
        return null;
    }
}
