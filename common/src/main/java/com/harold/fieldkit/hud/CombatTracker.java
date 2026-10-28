package com.harold.fieldkit.hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/** Clicks per second, who you're fighting, and a session fight log: damage dealt and taken, kills and deaths. */
public final class CombatTracker {
    public record Event(long time, String text) {
    }

    private static final Deque<Long> leftClicks = new ArrayDeque<>(), rightClicks = new ArrayDeque<>();
    private static boolean leftDown, rightDown;

    /** The last thing you hit or looked at, and when. */
    public static LivingEntity target;
    public static long targetSeen;
    private static LivingEntity hitTarget;
    private static float hitHealthBefore;
    private static long hitTime;
    private static float myLastHealth = -1;
    private static boolean wasDead;

    public static float dealt, taken;
    public static int kills, playerKills, deaths, hits;
    public static float biggestHit;
    public static final List<Event> LOG = new ArrayList<>();

    private CombatTracker() {
    }

    public static void resetSession() {
        dealt = taken = biggestHit = 0;
        kills = playerKills = deaths = hits = 0;
        LOG.clear();
        target = hitTarget = null;
        myLastHealth = -1;
    }

    /** Called every frame: counts mouse presses (fast clicks would be missed at 20 ticks a second). */
    public static void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        long w = mc.getWindow().getWindow();
        boolean l = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        long t = Util.getMillis();
        if (l && !leftDown) leftClicks.addLast(t);
        if (r && !rightDown) rightClicks.addLast(t);
        leftDown = l;
        rightDown = r;
        while (!leftClicks.isEmpty() && t - leftClicks.peekFirst() > 1000) leftClicks.removeFirst();
        while (!rightClicks.isEmpty() && t - rightClicks.peekFirst() > 1000) rightClicks.removeFirst();
    }

    public static int cpsLeft() {
        return leftClicks.size();
    }

    public static int cpsRight() {
        return rightClicks.size();
    }

    public static void onAttack(Entity e) {
        if (!(e instanceof LivingEntity le)) return;
        hitTarget = le;
        hitHealthBefore = le.getHealth();
        hitTime = Util.getMillis();
        target = le;
        targetSeen = hitTime;
        hits++;
    }

    public static void tick(Minecraft mc) {
        LocalPlayer me = mc.player;
        long t = Util.getMillis();
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
            target = le;
            targetSeen = t;
        }
        // damage dealt: the target's health drop in the moments after your hit
        if (hitTarget != null) {
            float now = hitTarget.getHealth();
            if (now < hitHealthBefore) {
                float d = hitHealthBefore - now;
                dealt += d;
                biggestHit = Math.max(biggestHit, d);
                hitHealthBefore = now;
            }
            if (hitTarget.isDeadOrDying() || hitTarget.isRemoved() && now <= 0) {
                kills++;
                if (hitTarget instanceof Player) playerKills++;
                log(t, "Killed " + hitTarget.getName().getString());
                hitTarget = null;
            } else if (t - hitTime > 1500) {
                hitTarget = null;
            }
        }
        float h = me.getHealth();
        if (myLastHealth >= 0 && h < myLastHealth) {
            float d = myLastHealth - h;
            taken += d;
            if (d >= 4) log(t, String.format("Took %.1f damage%s", d, me.getLastHurtByMob() != null
                    ? " from " + me.getLastHurtByMob().getName().getString() : ""));
        }
        myLastHealth = h;
        boolean dead = me.isDeadOrDying();
        if (dead && !wasDead) {
            deaths++;
            log(t, "Died" + (me.getLastHurtByMob() != null ? " to " + me.getLastHurtByMob().getName().getString() : ""));
        }
        wasDead = dead;
        if (target != null && (target.isRemoved() || t - targetSeen > 5000)) target = null;
    }

    private static void log(long t, String text) {
        LOG.add(0, new Event(t, text));
        if (LOG.size() > 100) LOG.remove(LOG.size() - 1);
    }
}
