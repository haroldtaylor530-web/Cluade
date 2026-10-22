package com.harold.corpsedecay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.harold.corpsedecay.corpse.CorpseEntity;
import com.harold.corpsedecay.corpse.DeathSnapshot;
import com.harold.corpsedecay.corpse.StuckArrows;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Swaps dying mobs for corpses. The mob is snapshotted when it dies, gear it would have dropped
 * is moved onto the body instead, and at the end of that server tick the mob is removed (before
 * vanilla's "poof") and the corpse takes its place. XP and normal loot still drop as usual.
 */
public final class DeathHandler {
    private static final Map<LivingEntity, DeathSnapshot> PENDING = new IdentityHashMap<>();
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final boolean VERBOSE = Boolean.getBoolean("corpsedecay.selftest");

    private DeathHandler() {
    }

    public static boolean eligible(LivingEntity mob) {
        if (!Config.ENABLED.get() || !(mob instanceof Mob) || mob instanceof Player) return false;
        if (mob.getType().is(CorpseKind.NO_CORPSE)) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        return id == null || !Config.EXCLUDED.get().contains(id.toString());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        if (e.isCanceled()) return;
        LivingEntity mob = e.getEntity();
        if (mob.level().isClientSide || !eligible(mob)) return;
        if (Config.ONLY_PLAYER_KILLS.get() && !(e.getSource().getEntity() instanceof Player)) return;
        PENDING.put(mob, DeathSnapshot.of(mob, e.getSource()));
        if (VERBOSE) LOG.info("corpsedecay: {} died at {}", mob.getType(), mob.position());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onDrops(LivingDropsEvent e) {
        DeathSnapshot s = PENDING.get(e.getEntity());
        if (s == null) return;
        e.getDrops().removeIf(item -> claim(s, item));
        if (Config.LOOT_ALL_EQUIPMENT.get()) s.lootEverything();
    }

    private static boolean claim(DeathSnapshot s, ItemEntity item) {
        return s.claimDrop(item.getItem(), Config.LOOT_ALL_EQUIPMENT.get());
    }

    /** Arrows that actually hurt a mob are remembered so they stay in the body. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent e) {
        if (e.isCanceled() || e.getAmount() <= 0) return;
        LivingEntity mob = e.getEntity();
        if (mob.level().isClientSide || mob instanceof Player) return;
        Entity direct = e.getSource().getDirectEntity();
        if (direct instanceof AbstractArrow arrow && !(direct instanceof ThrownTrident) && arrow.getPierceLevel() <= 0) {
            StuckArrows.onArrowHit(mob, arrow);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        List<Map.Entry<LivingEntity, DeathSnapshot>> todo = new ArrayList<>(PENDING.entrySet());
        PENDING.clear();
        for (Map.Entry<LivingEntity, DeathSnapshot> entry : todo) {
            LivingEntity mob = entry.getKey();
            if (!(mob.level() instanceof ServerLevel level)) continue;
            // a totem or another mod may have saved it after all
            if (!mob.isDeadOrDying() && !mob.isRemoved()) continue;
            spawnCorpse(level, entry.getValue());
            if (!mob.isRemoved()) mob.remove(Entity.RemovalReason.KILLED);
        }
    }

    public static CorpseEntity spawnCorpse(ServerLevel level, DeathSnapshot s) {
        CorpseEntity corpse = ModRegistry.CORPSE.get().create(level);
        if (corpse == null) return null;
        corpse.setup(s);
        boolean added = level.addFreshEntity(corpse);
        if (VERBOSE) LOG.info("corpsedecay: corpse of {} at {} added={}", s.type, corpse.position(), added);
        enforceLimit(level);
        return corpse;
    }

    /** Oldest corpses crumble first once a dimension has too many. */
    private static void enforceLimit(ServerLevel level) {
        int max = Config.MAX_CORPSES.get();
        List<CorpseEntity> all = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof CorpseEntity c && !c.isRemoved() && c.kind() != CorpseKind.REMAINS) all.add(c);
        }
        if (all.size() <= max) return;
        all.sort(Comparator.comparingLong(CorpseEntity::deathTime));
        for (int i = 0; i < all.size() - max; i++) all.get(i).crumble(false);
    }
}
