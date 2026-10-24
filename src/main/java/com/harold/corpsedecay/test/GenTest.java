package com.harold.corpsedecay.test;

import java.util.List;

import org.slf4j.Logger;

import com.harold.corpsedecay.corpse.CorpseEntity;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * CI only: a server on ordinary world generation. Finds real shipwrecks, generates them the way the
 * game does and counts the crew that turned up aboard.
 */
public class GenTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static int tick;
    private static boolean done;

    private static final List<BlockPos> SHIPS = new java.util.ArrayList<>();

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || done) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        tick++;
        ServerLevel level = server.overworld();
        try {
            if (tick == 40) {
                int[][] origins = {{0, 0}, {3000, 0}, {0, 3000}, {-3000, 0}, {0, -3000}, {3000, 3000}, {-3000, -3000}, {3000, -3000}};
                for (int[] o : origins) {
                    var found = level.findNearestMapStructure(StructureTags.SHIPWRECK, new BlockPos(o[0], 64, o[1]), 60, false);
                    if (found == null) continue;
                    SHIPS.add(found);
                    int cx = found.getX() >> 4, cz = found.getZ() >> 4;
                    for (int x = cx - 3; x <= cx + 3; x++) for (int z = cz - 3; z <= cz + 3; z++) level.setChunkForced(x, z, true);
                }
                return;
            }
            if (tick < 900) return;
        } catch (Throwable t) {
            LOG.error("GENTEST crashed", t);
        }
        done = true;
        int ships = SHIPS.size(), crewed = 0, bodies = 0, dressed = 0;
        for (BlockPos found : SHIPS) {
            List<CorpseEntity> crew = level.getEntitiesOfClass(CorpseEntity.class,
                    new AABB(found.getX() - 48, level.getMinBuildHeight(), found.getZ() - 48, found.getX() + 48, level.getMaxBuildHeight(), found.getZ() + 48));
            LOG.info("GENTEST shipwreck near {} has {} bodies", found, crew.size());
            if (!crew.isEmpty()) crewed++;
            bodies += crew.size();
            for (CorpseEntity c : crew) {
                if (!c.equipment(EquipmentSlot.CHEST).isEmpty() || !c.equipment(EquipmentSlot.MAINHAND).isEmpty()) dressed++;
            }
        }
        int late = com.harold.corpsedecay.world.LateCrews.crewed;
        LOG.info("GENTEST {} shipwrecks, {} with a crew, {} bodies, {} dressed, {} late crews", ships, crewed, bodies, dressed, late);
        LOG.info(ships >= 4 && crewed * 4 >= ships * 3 && dressed >= 1 && late >= 2 ? "GENTEST PASS" : "GENTEST FAILED");
        server.halt(false);
    }
}
