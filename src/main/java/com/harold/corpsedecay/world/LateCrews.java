package com.harold.corpsedecay.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;

import com.harold.corpsedecay.Config;
import com.harold.corpsedecay.CorpseDecay;
import com.mojang.logging.LogUtils;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Crews for shipwrecks that other mods build their own way, perhaps after the world generation
 * step where vanilla wrecks get theirs. Any structure in the {@code corpsedecay:late_crews} tag
 * (empty unless a compat add-on fills it) gets its crew once its chunks are loaded and the ship is
 * standing there: each wreck once, remembered in the world's save, so wrecks generated before
 * the add-on was installed get a crew too the next time they're visited.
 */
public final class LateCrews {
    private static final Logger LOG = LogUtils.getLogger();
    public static final TagKey<Structure> LATE_CREWS = TagKey.create(Registries.STRUCTURE, new ResourceLocation(CorpseDecay.MODID, "late_crews"));
    /** wait this long after the wreck's chunk loads before looking, and between tries */
    private static final int WAIT = 60, RETRY = 100, TRIES = 8;

    private LateCrews() {
    }

    private static final class Pending {
        final ServerLevel level;
        final StructureStart start;
        final String key;
        long due;
        int tries;

        Pending(ServerLevel level, StructureStart start, String key, long due) {
            this.level = level;
            this.start = start;
            this.key = key;
            this.due = due;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Set<String> QUEUED = new HashSet<>();

    /** How many crews this has put aboard since the server started (for the tests). */
    public static int crewed;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !Config.REMAINS.get()) return;
        if (!(e.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)) return;
        if (chunk.getAllStarts().isEmpty()) return;
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (var entry : chunk.getAllStarts().entrySet()) {
            Structure s = entry.getKey();
            StructureStart start = entry.getValue();
            if (start == null || !start.isValid() || !reg.wrapAsHolder(s).is(LATE_CREWS)) continue;
            String key = key(level, reg.getKey(s), start.getChunkPos());
            if (QUEUED.contains(key) || Done.of(level).done.contains(key)) continue;
            QUEUED.add(key);
            PENDING.add(new Pending(level, start, key, level.getGameTime() + WAIT));
        }
    }

    private static String key(ServerLevel level, ResourceLocation id, ChunkPos pos) {
        return level.dimension().location() + "|" + id + "|" + pos.x + "," + pos.z;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            ServerLevel level = p.level;
            if (level.getGameTime() < p.due) continue;
            BoundingBox box = p.start.getBoundingBox();
            ChunkPos sp = p.start.getChunkPos();
            if (level.getChunkSource().getChunkNow(sp.x, sp.z) == null) {
                // gone again before we got to it: it'll come back round when it next loads
                it.remove();
                QUEUED.remove(p.key);
                continue;
            }
            if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                p.due = level.getGameTime() + RETRY;
                continue;
            }
            int placed = crew(level, p.start);
            p.tries++;
            if (placed > 0 || p.tries >= TRIES) {
                Done.of(level).add(p.key);
                it.remove();
                QUEUED.remove(p.key);
                if (placed > 0) crewed++;
                LOG.info("corpsedecay: {} crew aboard the wreck at {} ({})", placed, sp, p.key);
            } else {
                p.due = level.getGameTime() + RETRY;
            }
        }
    }

    /** Puts a crew aboard this wreck, wherever there's deck to lie on. */
    public static int crew(ServerLevel level, StructureStart start) {
        List<BoundingBox> boxes = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) boxes.add(piece.getBoundingBox());
        if (boxes.isEmpty()) boxes.add(start.getBoundingBox());
        long seed = level.getSeed() ^ (start.getChunkPos().toLong() * 0x9E3779B97F4A7C15L) ^ 0x1A7EL;
        return PirateFeature.crewIn(level, boxes, null, RandomSource.create(seed));
    }

    /** Which wrecks in this world already have their crew. */
    static final class Done extends SavedData {
        final Set<String> done = new HashSet<>();

        static Done of(ServerLevel level) {
            ServerLevel overworld = level.getServer().overworld();
            return overworld.getDataStorage().computeIfAbsent(Done::load, Done::new, "corpsedecay_late_crews");
        }

        static Done load(CompoundTag tag) {
            Done d = new Done();
            ListTag list = tag.getList("Done", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) d.done.add(list.getString(i));
            return d;
        }

        void add(String key) {
            if (done.add(key)) setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (String s : done) list.add(StringTag.valueOf(s));
            tag.put("Done", list);
            return tag;
        }
    }
}
