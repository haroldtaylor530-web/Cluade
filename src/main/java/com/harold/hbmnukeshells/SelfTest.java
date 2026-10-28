package com.harold.hbmnukeshells;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.hbm.config.BombConfig;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * -Dhbmnukeshells.selftest=true on a real server: fires every installed nuclear shell (calling its own detonate, so
 * the mixins are what turn it into an HBM bomb), sets off every HBM bomb directly, then checks the craters.
 */
public final class SelfTest {
    private static final Logger LOG = LogUtils.getLogger();
    private static int ticks;
    private static final List<Site> sites = new ArrayList<>();
    private static int failures;

    private record Site(String what, BlockPos ground, boolean big) {
    }

    private SelfTest() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        ticks++;
        try {
            if (ticks == 60) fire(level);
            if (ticks == 66) checkBlasts(level);
            if (ticks == 700) checkCraters(level, server);
        } catch (Throwable t) {
            LOG.error("HBMTEST crashed", t);
            failures++;
            if (ticks < 700) ticks = 699;
        }
    }

    private static BlockPos pad(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = 1; dy <= 3; dy++)
                    level.setBlockAndUpdate(new BlockPos(x + dx, y - dy, z + dz), Blocks.STONE.defaultBlockState());
        return new BlockPos(x, y - 1, z);
    }

    private static void fire(ServerLevel level) throws Exception {
        LOG.info("HBMTEST HBM radii: boy {} gadget {} man {} mike {} tsar {} n2 {}; clouds {}", BombConfig.boyRadius,
                BombConfig.gadgetRadius, BombConfig.manRadius, BombConfig.mikeRadius, BombConfig.tsarRadius,
                BombConfig.n2Radius, BombConfig.enableNukeClouds);
        // small blasts so the test is quick; the wiring is the same at full size
        BombConfig.boyRadius = BombConfig.gadgetRadius = BombConfig.manRadius = 14;
        BombConfig.mikeRadius = BombConfig.tsarRadius = BombConfig.n2Radius = 16;
        int x = -400;
        for (String id : ShellConfig.SHELLS.keySet()) {
            x += 100;
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (!ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
                LOG.info("HBMTEST SKIP {} (its mod isn't installed)", id);
                continue;
            }
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
            Entity shell = type.create(level);
            BlockPos g = pad(level, x, 0);
            Vec3 at = new Vec3(x + 0.5, g.getY() + 1.0, 0.5);
            shell.setPos(at);
            if (id.contains("fusion")) shell.getPersistentData().putDouble("Time", 300);
            Method m = find(shell.getClass());
            if (m == null) {
                LOG.error("HBMTEST FAIL {} has no detonate method", id);
                failures++;
                continue;
            }
            m.setAccessible(true);
            m.invoke(shell, (Position) at);
            Bomb bomb = ShellConfig.bombFor(id);
            if (NukeShells.FIRED.getOrDefault(id, 0) > 0) {
                LOG.info("HBMTEST FIRED {} -> {}", id, bomb);
                sites.add(new Site(id, g, bomb.radius() > 0));
            } else {
                LOG.error("HBMTEST FAIL {} did not go off as an HBM bomb (mixin not applied?)", id);
                failures++;
            }
        }
        for (Bomb bomb : Bomb.values()) {
            if (bomb == Bomb.NATIVE) continue;
            x += 100;
            BlockPos g = pad(level, x, 0);
            bomb.detonate(level, x + 0.5, g.getY() + 1.0, 0.5, null);
            sites.add(new Site("bomb " + bomb, g, bomb.radius() > 0));
        }
    }

    private static Method find(Class<?> c) {
        for (; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod("detonate", Position.class);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static void checkBlasts(ServerLevel level) {
        for (Site s : sites) {
            if (!s.big()) continue;
            int n = level.getEntitiesOfClass(EntityNukeExplosionMK5.class, new AABB(s.ground()).inflate(4)).size();
            if (n > 0) LOG.info("HBMTEST BLAST {}: HBM nuke explosion running", s.what());
            else {
                LOG.error("HBMTEST FAIL {}: no HBM nuke explosion entity", s.what());
                failures++;
            }
        }
    }

    private static void checkCraters(ServerLevel level, MinecraftServer server) {
        for (Site s : sites) {
            int gone = 0;
            for (int dx = -3; dx <= 3; dx++)
                for (int dz = -3; dz <= 3; dz++)
                    if (!level.getBlockState(s.ground().offset(dx, 0, dz)).is(Blocks.STONE)) gone++;
            if (gone >= 25) LOG.info("HBMTEST CRATER {}: {}/49 pad blocks blown away", s.what(), gone);
            else {
                LOG.error("HBMTEST FAIL {}: only {}/49 pad blocks gone", s.what(), gone);
                failures++;
            }
        }
        LOG.info(failures == 0 ? "HBMTEST PASS ({} sites)" : "HBMTEST FAILED ({} sites, {} failures)", sites.size(), failures);
        server.halt(false);
    }
}
