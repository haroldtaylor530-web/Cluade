package com.harold.corpsedecay.test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Test fixture: a walled basin of sea, its surface at {@code top}, to float a ship in. */
public final class TestSea {
    private TestSea() {
    }

    public static void basin(ServerLevel level, int cx, int top, int cz, int rx, int rz, int depth) {
        for (int x = cx - rx; x <= cx + rx; x += 16) for (int z = cz - rz; z <= cz + rz; z += 16) level.getChunk(x >> 4, z >> 4);
        level.getChunk((cx + rx) >> 4, (cz + rz) >> 4);
        for (int x = cx - rx; x <= cx + rx; x++) {
            for (int z = cz - rz; z <= cz + rz; z++) {
                boolean wall = x == cx - rx || x == cx + rx || z == cz - rz || z == cz + rz;
                level.setBlock(new BlockPos(x, top - depth, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = top - depth + 1; y <= top; y++) {
                    level.setBlock(new BlockPos(x, y, z), (wall ? Blocks.PACKED_ICE : Blocks.WATER).defaultBlockState(), 2);
                }
                for (int y = top + 1; y <= top + 30; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }
}
