package com.harold.fieldkit.map;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * What you've seen this session: for each chunk that has been loaded around you, its biomes (4x4) and ground heights.
 * The map uses it on servers, where the world's seed and generator aren't available.
 */
public final class ExploredMap {
    private static final Map<Long, int[]> CHUNKS = new HashMap<>();
    private static ClientLevel level;
    private static int scanTick;

    private ExploredMap() {
    }

    public static void reset() {
        CHUNKS.clear();
        level = null;
    }

    public static void tick(Minecraft mc) {
        if (mc.level != level) {
            CHUNKS.clear();
            level = mc.level;
        }
        if (++scanTick % 10 != 0 || mc.player == null) return;
        int r = Math.min(mc.options.renderDistance().get(), 16);
        ChunkPos at = mc.player.chunkPosition();
        int done = 0;
        for (int d = 0; d <= r && done < 48; d++) {
            for (int cx = at.x - d; cx <= at.x + d && done < 48; cx++) {
                for (int cz = at.z - d; cz <= at.z + d && done < 48; cz++) {
                    if (Math.max(Math.abs(cx - at.x), Math.abs(cz - at.z)) != d) continue;
                    long key = ChunkPos.asLong(cx, cz);
                    if (CHUNKS.containsKey(key) || !level.hasChunk(cx, cz)) continue;
                    CHUNKS.put(key, sample(level, cx, cz));
                    done++;
                }
            }
        }
        if (CHUNKS.size() > 60000) CHUNKS.clear();
    }

    /** [0..15] biome colours of the 4x4 quarts, [16..31] surface heights. */
    private static int[] sample(ClientLevel level, int cx, int cz) {
        int[] out = new int[32];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
                int x = cx * 16 + qx * 4 + 2, z = cz * 16 + qz * 4 + 2;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                out[qz * 4 + qx] = BiomeColors.of(level.getBiome(p.set(x, Math.max(y - 1, level.getMinBuildHeight()), z)));
                out[16 + qz * 4 + qx] = y;
            }
        }
        return out;
    }

    /** Colour and height at a block from what's been seen, or null if never loaded. */
    public static int[] at(int x, int z) {
        int[] c = CHUNKS.get(ChunkPos.asLong(x >> 4, z >> 4));
        if (c == null) return null;
        int i = ((z & 15) >> 2) * 4 + ((x & 15) >> 2);
        return new int[] { c[i], c[16 + i] };
    }

    public static int size() {
        return CHUNKS.size();
    }
}
