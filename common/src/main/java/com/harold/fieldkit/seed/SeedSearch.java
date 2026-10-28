package com.harold.fieldkit.seed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import com.harold.fieldkit.Fieldkit;
import com.harold.fieldkit.map.BiomeColors;
import com.harold.fieldkit.map.Gen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Tries seed after seed on background threads, using the game's own world generation with this world's settings
 * (so modded biomes and structures count), and keeps the ones that match.
 */
public final class SeedSearch {
    public record Result(long seed, List<Gen.Found> found, int[] preview, int previewSize, int span) {
    }

    public static final int PREVIEW = 64;
    private final Gen base;
    private final Criteria c;
    private final List<List<RandomSpreadStructurePlacement>> spread = new ArrayList<>();
    public final AtomicLong checked = new AtomicLong();
    public final List<Result> results = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean running;
    public final long started = System.currentTimeMillis();
    public long stoppedAt;
    private final int threads;
    private int alive;
    public volatile String error;

    public SeedSearch(Gen base, Criteria criteria) {
        this.base = base;
        this.c = criteria.copy();
        for (Criteria.StructRule r : c.structs) {
            List<RandomSpreadStructurePlacement> list = new ArrayList<>();
            boolean other = false;
            for (Holder<StructureSet> set : base.sets) {
                if (set.value().structures().stream().noneMatch(e -> e.structure().is(r.structure))) continue;
                if (set.value().placement() instanceof RandomSpreadStructurePlacement rs) list.add(rs);
                else other = true;
            }
            spread.add(other ? null : list);
        }
        this.threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    }

    public boolean running() {
        return running;
    }

    public void start() {
        running = true;
        long start;
        Long parsed = parse(c.start);
        start = parsed != null ? parsed : new Random().nextLong();
        boolean random = parsed == null;
        alive = threads;
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            Thread th = new Thread(() -> loop(tid, start, random), "Fieldkit seed search " + t);
            th.setDaemon(true);
            th.setPriority(Thread.MIN_PRIORITY);
            th.start();
        }
    }

    public void stop() {
        running = false;
        if (stoppedAt == 0) stoppedAt = System.currentTimeMillis();
    }

    /** Seeds can be numbers or text (text seeds hash like the game does). */
    public static Long parse(String s) {
        s = s == null ? "" : s.trim();
        if (s.isEmpty()) return null;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return (long) s.hashCode();
        }
    }

    private void loop(int tid, long start, boolean random) {
        Random rnd = new Random(start + tid * 0x9E3779B97F4A7C15L);
        try {
            for (long i = tid; running; i += threads) {
                long seed = random ? rnd.nextLong() : start + i;
                Result r = test(seed);
                checked.incrementAndGet();
                if (r != null) {
                    synchronized (results) {
                        if (results.size() < c.limit) results.add(r);
                        if (results.size() >= c.limit) stop();
                    }
                }
            }
        } catch (Throwable t) {
            error = t.toString();
            Fieldkit.LOG.error("Seed search failed", t);
            stop();
        } finally {
            synchronized (this) {
                if (--alive == 0 && stoppedAt == 0) stoppedAt = System.currentTimeMillis();
            }
        }
    }

    private Result test(long seed) {
        // cheap first: are there even enough candidate spots for each structure? (needs only the seed)
        for (int i = 0; i < c.structs.size(); i++) {
            List<RandomSpreadStructurePlacement> list = spread.get(i);
            if (list == null) continue;
            Criteria.StructRule r = c.structs.get(i);
            int n = 0;
            int cr = (r.radius >> 4) + 1;
            for (RandomSpreadStructurePlacement rs : list) {
                int sp = rs.spacing();
                for (int rx = Math.floorDiv(-cr, sp); rx <= Math.floorDiv(cr, sp); rx++)
                    for (int rz = Math.floorDiv(-cr, sp); rz <= Math.floorDiv(cr, sp); rz++) {
                        ChunkPos p = rs.getPotentialStructureChunk(seed, rx * sp, rz * sp);
                        if (within(p.getMiddleBlockX(), p.getMiddleBlockZ(), r.radius)) n++;
                    }
            }
            if (n < r.count) return null;
        }
        Gen g = base.withSeed(seed);
        List<Gen.Found> found = new ArrayList<>();
        for (Criteria.StructRule r : c.structs) {
            int cr = (r.radius >> 4) + 1;
            int n = 0;
            for (Gen.Found f : g.structures(-cr, -cr, cr, cr, r.structure, 1000)) {
                if (within(f.x(), f.z(), r.radius)) {
                    n++;
                    found.add(f);
                }
            }
            if (n < r.count) return null;
        }
        for (Criteria.BiomeRule r : c.biomes) {
            if (has(g, r) != r.must) return null;
        }
        int span = 512;
        for (Criteria.BiomeRule r : c.biomes) span = Math.max(span, r.radius * 2 + 64);
        for (Criteria.StructRule r : c.structs) span = Math.max(span, r.radius * 2 + 64);
        span = Math.min(span, 8192);
        int[] px = new int[PREVIEW * PREVIEW];
        for (int y = 0; y < PREVIEW; y++)
            for (int x = 0; x < PREVIEW; x++)
                px[y * PREVIEW + x] = BiomeColors.of(g.biome((int) ((x + 0.5 - PREVIEW / 2.0) * span / PREVIEW),
                        (int) ((y + 0.5 - PREVIEW / 2.0) * span / PREVIEW)));
        return new Result(seed, found, px, PREVIEW, span);
    }

    private static boolean within(int x, int z, int r) {
        return (long) x * x + (long) z * z <= (long) r * r;
    }

    private static boolean has(Gen g, Criteria.BiomeRule r) {
        int step = Math.max(8, Math.min(48, r.radius / 6));
        for (int d = 0; d <= r.radius; d += step) {
            // rings outward from the origin, so "must have" stops early
            for (int x = -d; x <= d; x += step) {
                for (int z = -d; z <= d; z += step) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != d && d != 0) continue;
                    if (!within(x, z, r.radius)) continue;
                    if (g.biome(x, z).is(r.biome)) return true;
                }
            }
        }
        return false;
    }

    public double rate() {
        long end = stoppedAt > 0 ? stoppedAt : System.currentTimeMillis();
        return checked.get() * 1000.0 / Math.max(1, end - started);
    }
}
