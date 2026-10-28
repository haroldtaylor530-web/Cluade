package com.harold.fieldkit.hud;

import java.util.ArrayList;
import java.util.List;

import com.harold.fieldkit.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Draws the HUD pieces: minimap and info line (top left), warnings (top), combat panel (left), target (crosshair). */
public final class Hud {
    private Hud() {
    }

    public static void tick(Minecraft mc) {
        if (Config.INSTANCE.hud) Minimap.tick(mc);
    }

    public static void render(GuiGraphics g, Minecraft mc, float partial) {
        CombatTracker.frame();
        Config c = Config.INSTANCE;
        int y = Minimap.render(g, mc, partial, 6, 6);
        if (c.coordsLine) y = infoLines(g, mc, 6, y);
        if (c.combatHud) combatPanel(g, mc, 6, y + 2);
        if (c.targetInfo) target(g, mc);
        Warnings.render(g, mc, partial);
    }

    private static int infoLines(GuiGraphics g, Minecraft mc, int x, int y) {
        LocalPlayer p = mc.player;
        String dir = switch (p.getDirection()) {
            case NORTH -> "N";
            case SOUTH -> "S";
            case EAST -> "E";
            case WEST -> "W";
            default -> "";
        };
        long day = mc.level.getDayTime() % 24000;
        int hours = (int) ((day / 1000 + 6) % 24), mins = (int) (day % 1000 * 60 / 1000);
        String l1 = p.getBlockX() + " " + p.getBlockY() + " " + p.getBlockZ() + "  " + dir;
        String l2 = Minimap.biomeName + "  " + String.format("%02d:%02d", hours, mins);
        int w = Math.max(mc.font.width(l1), mc.font.width(l2)) + 6;
        g.fill(x - 2, y - 1, x + w, y + 20, 0x90101014);
        g.drawString(mc.font, l1, x + 1, y + 1, 0xFFFFFFFF, true);
        g.drawString(mc.font, l2, x + 1, y + 11, 0xFFB8D47A, true);
        return y + 22;
    }

    private static int durColor(ItemStack s) {
        float f = 1F - (float) s.getDamageValue() / Math.max(1, s.getMaxDamage());
        return f > 0.5F ? 0xFF7CDC5C : f > 0.2F ? 0xFFE8C040 : 0xFFFF5040;
    }

    private static void combatPanel(GuiGraphics g, Minecraft mc, int x, int y) {
        LocalPlayer p = mc.player;
        Font font = mc.font;
        List<String> lines = new ArrayList<>();
        // armour and held items with durability
        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };
        int ix = x;
        int rowY = y;
        boolean any = false;
        for (EquipmentSlot s : slots) {
            ItemStack st = p.getItemBySlot(s);
            if (st.isEmpty()) continue;
            any = true;
            g.fill(ix - 1, rowY - 1, ix + 17, rowY + 25, 0x90101014);
            g.renderItem(st, ix, rowY);
            if (st.isDamageableItem()) {
                int pct = Math.round(100F * (st.getMaxDamage() - st.getDamageValue()) / st.getMaxDamage());
                String t = pct + "%";
                g.pose().pushPose();
                g.pose().translate(ix + 8 - font.width(t) * 0.35F, rowY + 18, 200);
                g.pose().scale(0.7F, 0.7F, 1F);
                g.drawString(font, t, 0, 0, durColor(st), true);
                g.pose().popPose();
            } else if (st.getCount() > 1) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.drawString(font, "×" + st.getCount(), ix + 1, rowY + 17, 0xFFFFFFFF, true);
                g.pose().popPose();
            }
            ix += 19;
        }
        if (any) rowY += 28;
        int totems = count(p, Items.TOTEM_OF_UNDYING);
        ItemStack held = p.getMainHandItem();
        String extra = "";
        if (totems > 0) extra += "Totems " + totems + "  ";
        if (held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem) {
            extra += "Arrows " + (count(p, Items.ARROW) + count(p, Items.SPECTRAL_ARROW) + count(p, Items.TIPPED_ARROW)) + "  ";
        }
        if (!extra.isEmpty()) lines.add(extra.trim());
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(p.getUUID()) : null;
        lines.add("CPS " + CombatTracker.cpsLeft() + " | " + CombatTracker.cpsRight() + "   FPS " + mc.getFps()
                + (info != null && !mc.hasSingleplayerServer() ? "   Ping " + info.getLatency() + " ms" : ""));
        lines.add(String.format("HP %.1f/%.0f", p.getHealth() + p.getAbsorptionAmount(), p.getMaxHealth())
                + "   Armour " + p.getArmorValue() + "   Food " + p.getFoodData().getFoodLevel());
        if (Config.INSTANCE.effectTimers) {
            for (MobEffectInstance e : p.getActiveEffects()) {
                String name = Component.translatable(e.getDescriptionId()).getString()
                        + (e.getAmplifier() > 0 ? " " + roman(e.getAmplifier() + 1) : "");
                String time = e.isInfiniteDuration() ? "∞" : formatTicks(e.getDuration());
                lines.add("§b" + name + " §f" + time);
            }
        }
        if (Config.INSTANCE.fightLog && (CombatTracker.hits > 0 || CombatTracker.taken > 0 || CombatTracker.deaths > 0)) {
            lines.add(String.format("§eK %d%s  D %d  dealt %.0f  taken %.0f", CombatTracker.kills,
                    CombatTracker.playerKills > 0 ? " (" + CombatTracker.playerKills + "p)" : "", CombatTracker.deaths,
                    CombatTracker.dealt, CombatTracker.taken));
        }
        // stay above the chat area
        int room = (g.guiHeight() - 64 - rowY) / 10;
        if (room < lines.size()) lines = lines.subList(0, Math.max(0, room));
        if (lines.isEmpty()) return;
        int w = 0;
        for (String l : lines) w = Math.max(w, font.width(l));
        g.fill(x - 2, rowY - 2, x + w + 4, rowY + lines.size() * 10, 0x90101014);
        for (String l : lines) {
            g.drawString(font, l, x + 1, rowY, 0xFFE8E4DA, true);
            rowY += 10;
        }
    }

    private static void target(GuiGraphics g, Minecraft mc) {
        LivingEntity t = CombatTracker.target;
        if (t == null || !t.isAlive()) return;
        long age = net.minecraft.Util.getMillis() - CombatTracker.targetSeen;
        float a = age < 3500 ? 1F : Math.max(0, 1F - (age - 3500) / 1500F);
        if (a <= 0.05F) return;
        Font font = mc.font;
        int cx = g.guiWidth() / 2, y = g.guiHeight() / 2 + 14;
        float hp = t.getHealth(), max = Math.max(1, t.getMaxHealth()), abs = t.getAbsorptionAmount();
        String name = t.getName().getString();
        String stats = String.format("%.1f/%.0f", hp + abs, max) + (t.getArmorValue() > 0 ? "  ⛨" + t.getArmorValue() : "")
                + "  " + Math.round(t.distanceTo(mc.player)) + "m";
        int w = Math.max(90, Math.max(font.width(name), font.width(stats)) + 8);
        int x = cx - w / 2;
        g.fill(x, y, x + w, y + 30, Notifications.alpha(0xB0101014, a));
        g.drawString(font, name, x + 4, y + 3, Notifications.alpha(t instanceof Player ? 0xFFFFE8B0 : 0xFFFFFFFF, a), false);
        int bw = w - 8;
        g.fill(x + 4, y + 14, x + 4 + bw, y + 18, Notifications.alpha(0xFF3A1010, a));
        int filled = (int) (bw * Mth.clamp(hp / max, 0, 1));
        int col = hp / max > 0.5F ? 0xFF50D050 : hp / max > 0.25F ? 0xFFE8C040 : 0xFFFF4040;
        g.fill(x + 4, y + 14, x + 4 + filled, y + 18, Notifications.alpha(col, a));
        if (abs > 0) g.fill(x + 4, y + 13, x + 4 + (int) (bw * Mth.clamp(abs / max, 0, 1)), y + 14, Notifications.alpha(0xFFFFD040, a));
        g.drawString(font, stats, x + 4, y + 20, Notifications.alpha(0xFFC8C4BA, a), false);
    }

    private static int count(Player p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    static String formatTicks(int ticks) {
        int s = ticks / 20;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    static String roman(int n) {
        String[] r = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
        return n < r.length ? r[n] : String.valueOf(n);
    }
}
