package com.harold.fieldkit.map;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/** Map colours: the familiar seed-map palette for vanilla biomes, and a good guess from the name for modded ones. */
public final class BiomeColors {
    private static final Map<String, Integer> VANILLA = new HashMap<>();
    private static final Map<ResourceKey<Biome>, Integer> CACHE = new HashMap<>();

    static {
        v(0x000070, "ocean"); v(0x000090, "deep_ocean"); v(0x0000AC, "warm_ocean"); v(0x000090, "lukewarm_ocean");
        v(0x000050, "deep_lukewarm_ocean"); v(0x202070, "cold_ocean"); v(0x202038, "deep_cold_ocean");
        v(0x7070D6, "frozen_ocean"); v(0x404090, "deep_frozen_ocean"); v(0x0000FF, "river"); v(0xA0A0FF, "frozen_river");
        v(0x8DB360, "plains"); v(0xB5DB88, "sunflower_plains"); v(0xFFFFFF, "snowy_plains"); v(0xB4DCDC, "ice_spikes");
        v(0xFA9418, "desert"); v(0x07F9B2, "swamp"); v(0x2CCC8E, "mangrove_swamp"); v(0x056621, "forest");
        v(0x2D8E49, "flower_forest"); v(0x307444, "birch_forest"); v(0x589C6C, "old_growth_birch_forest");
        v(0x40511A, "dark_forest"); v(0x0B6659, "taiga"); v(0x596651, "old_growth_pine_taiga");
        v(0x818E79, "old_growth_spruce_taiga"); v(0x31554A, "snowy_taiga"); v(0x537B09, "jungle");
        v(0x628B17, "sparse_jungle"); v(0x768E14, "bamboo_jungle"); v(0xBDB25F, "savanna"); v(0xA79D64, "savanna_plateau");
        v(0xE5DA87, "windswept_savanna"); v(0x606060, "windswept_hills"); v(0x789878, "windswept_gravelly_hills");
        v(0x507050, "windswept_forest"); v(0xD94515, "badlands"); v(0xB09765, "wooded_badlands");
        v(0xFF6D3D, "eroded_badlands"); v(0xFADE55, "beach"); v(0xFAF0C0, "snowy_beach"); v(0xA2A284, "stony_shore");
        v(0xFF00FF, "mushroom_fields"); v(0x60A445, "meadow"); v(0x47875A, "grove"); v(0xC4C4C4, "snowy_slopes");
        v(0xDCDCC8, "frozen_peaks"); v(0xB0B3CE, "jagged_peaks"); v(0x7B8F74, "stony_peaks"); v(0xFFB7D9, "cherry_grove");
        v(0x6F9F6B, "pale_garden"); v(0x4E3012, "dripstone_caves"); v(0x283C00, "lush_caves"); v(0x031F29, "deep_dark");
        v(0x572526, "nether_wastes"); v(0x4D3A2E, "soul_sand_valley"); v(0x981A11, "crimson_forest");
        v(0x49907B, "warped_forest"); v(0x645F63, "basalt_deltas"); v(0x8080FF, "the_end"); v(0x4B4BAB, "small_end_islands");
        v(0xC9C959, "end_midlands"); v(0xB5B536, "end_highlands"); v(0x7070CC, "end_barrens"); v(0x000000, "the_void");
    }

    private static void v(int c, String id) {
        VANILLA.put(id, 0xFF000000 | c);
    }

    private BiomeColors() {
    }

    public static int of(Holder<Biome> biome) {
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key == null) return 0xFF404040;
        Integer c = CACHE.get(key);
        if (c == null) {
            c = compute(key, biome.value());
            CACHE.put(key, c);
        }
        return c;
    }

    private static int compute(ResourceKey<Biome> key, Biome b) {
        String ns = key.location().getNamespace(), path = key.location().getPath();
        if (ns.equals("minecraft") && VANILLA.containsKey(path)) return VANILLA.get(path);
        String p = path.toLowerCase();
        int base;
        if (has(p, "deep_ocean", "abyss", "trench")) base = 0x000060;
        else if (has(p, "ocean", "sea", "reef")) base = 0x000090;
        else if (has(p, "river", "lake", "pond", "creek", "stream", "lagoon", "spring")) base = 0x2848F0;
        else if (has(p, "frozen", "ice", "glacier", "snow", "tundra", "alpine", "polar", "arctic", "frost", "winter")) base = 0xE0E8F0;
        else if (has(p, "badlands", "mesa", "canyon", "red_rock", "painted")) base = 0xD06A30;
        else if (has(p, "desert", "dune", "sand", "arid", "oasis", "outback")) base = 0xE8B048;
        else if (has(p, "beach", "shore", "coast")) base = 0xF0DE7A;
        else if (has(p, "volcan", "ash", "lava", "basalt", "scorch", "burnt", "wasteland")) base = 0x5A4A46;
        else if (has(p, "peak", "mountain", "cliff", "crag", "summit", "highland", "rock", "stone", "gravel", "rugged")) base = 0x8A8A88;
        else if (has(p, "swamp", "marsh", "bog", "bayou", "mangrove", "fen", "wetland", "mire")) base = 0x2E8C6A;
        else if (has(p, "jungle", "rainforest", "tropic", "bamboo")) base = 0x4E9A12;
        else if (has(p, "cherry", "blossom", "sakura", "lavender", "flower", "bloom", "orchard", "rose")) base = 0xE890B8;
        else if (has(p, "dark_forest", "black_forest", "ominous", "dead", "spooky", "haunt", "shadow")) base = 0x3A4A22;
        else if (has(p, "taiga", "boreal", "conifer", "pine", "spruce", "fir", "redwood")) base = 0x2F6B55;
        else if (has(p, "forest", "wood", "grove", "thicket", "copse", "glade")) base = 0x2C7A32;
        else if (has(p, "savanna", "shrub", "steppe", "prairie", "scrub", "bush", "outback")) base = 0xB8AE5E;
        else if (has(p, "mushroom", "fung")) base = 0xD040D0;
        else if (has(p, "cave", "cavern", "underground", "deep", "hollow")) base = 0x2A2620;
        else if (has(p, "plain", "meadow", "field", "pasture", "grass", "valley", "moor", "heath", "hill")) base = 0x86B060;
        else if (ns.contains("nether") || has(p, "nether", "crimson", "warped", "soul")) base = 0x6A2A26;
        else if (has(p, "end", "void")) base = 0x8080D0;
        else {
            int f = b.getFoliageColor();
            base = f & 0xFFFFFF;
        }
        // small per-biome variation so neighbouring modded biomes can be told apart
        int h = key.location().hashCode();
        int r = clamp(((base >> 16) & 255) + ((h & 15) - 8)), g = clamp(((base >> 8) & 255) + (((h >> 4) & 15) - 8)),
                bl = clamp((base & 255) + (((h >> 8) & 15) - 8));
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static boolean has(String p, String... words) {
        for (String w : words) if (p.contains(w)) return true;
        return false;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Scales an ARGB colour's brightness. */
    public static int shade(int argb, float k) {
        int r = clamp((int) (((argb >> 16) & 255) * k)), g = clamp((int) (((argb >> 8) & 255) * k)), b = clamp((int) ((argb & 255) * k));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** ARGB to the ABGR NativeImage wants. */
    public static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    public static String pretty(ResourceKey<?> key) {
        String p = key.location().getPath();
        int slash = p.lastIndexOf('/');
        if (slash >= 0) p = p.substring(slash + 1);
        StringBuilder sb = new StringBuilder();
        for (String w : p.split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
