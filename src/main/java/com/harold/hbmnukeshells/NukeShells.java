package com.harold.hbmnukeshells;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.Position;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.registries.ForgeRegistries;

/** Called by the shell mixins when a nuclear shell goes off. */
public final class NukeShells {
    private static final Logger LOG = LogUtils.getLogger();
    /** Shell id → how many times it has gone off as an HBM bomb (read by the self test). */
    public static final Map<String, Integer> FIRED = new ConcurrentHashMap<>();

    private NukeShells() {
    }

    /**
     * Sets off the HBM bomb this shell maps to. Returns false if the shell's own explosion should happen instead
     * (client side, set to NATIVE, or something went wrong).
     */
    public static boolean detonate(Entity shell, Position pos) {
        if (!(shell.level() instanceof ServerLevel level)) return false;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(shell.getType());
        if (id == null) return false;
        Bomb bomb = ShellConfig.bombFor(id.toString());
        if (bomb == null || bomb == Bomb.NATIVE) return false;
        try {
            UUID owner = null;
            if (shell instanceof Projectile p && p.getOwner() != null) owner = p.getOwner().getUUID();
            bomb.detonate(level, pos.x(), pos.y(), pos.z(), owner);
            FIRED.merge(id.toString(), 1, Integer::sum);
            LOG.info("{} went off as HBM's {} at {}, {}, {}", id, bomb.title, Math.round(pos.x()), Math.round(pos.y()),
                    Math.round(pos.z()));
            return true;
        } catch (Throwable t) {
            LOG.error("Couldn't set off {} as HBM's {}; using the shell's own explosion", id, bomb.title, t);
            return false;
        }
    }

    /** Warium's own rule for a fusion warhead going nuclear: armed (Time over 260 ticks) or the easyNukes game rule. */
    @SuppressWarnings("unchecked")
    public static boolean wariumFusionArmed(LevelAccessor world, Entity shell) {
        if (shell.getPersistentData().getDouble("Time") > 260) return true;
        try {
            Class<?> rules = Class.forName("net.mcreator.crustychunks.init.CrustyChunksModGameRules");
            Object key = rules.getField("EASYNUKES").get(null);
            return world.getLevelData().getGameRules().getBoolean((GameRules.Key<GameRules.BooleanValue>) key);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** Calls a Warium hit procedure taking (world, entity). */
    public static void callHit(String className, LevelAccessor world, Entity entity) {
        try {
            Class.forName(className).getMethod("execute", LevelAccessor.class, Entity.class).invoke(null, world, entity);
        } catch (ReflectiveOperationException e) {
            LOG.error("Couldn't call {}", className, e);
        }
    }

    /** Calls a Warium explosion procedure the shell would have used (fallback when the shell stays NATIVE). */
    public static void callProcedure(String className, LevelAccessor world, double x, double y, double z) {
        try {
            Class<?> c = Class.forName(className);
            Method m = c.getMethod("execute", LevelAccessor.class, double.class, double.class, double.class);
            m.invoke(null, world, x, y, z);
        } catch (ReflectiveOperationException e) {
            LOG.error("Couldn't call {}", className, e);
        }
    }
}
