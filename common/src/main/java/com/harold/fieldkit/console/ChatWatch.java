package com.harold.fieldkit.console;

import java.util.Locale;

import com.harold.fieldkit.Config;
import com.harold.fieldkit.hud.Notifications;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Chat: mentions of your name or your keywords, and server messages (joins, leaves, deaths, advancements). */
public final class ChatWatch {
    private ChatWatch() {
    }

    public static void onMessage(Component message, boolean fromPlayer) {
        String text = message.getString();
        LogWatch.recordChat(text);
        Minecraft mc = Minecraft.getInstance();
        Config c = Config.INSTANCE;
        if (message.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            if (c.serverMessages) {
                if (key.equals("multiplayer.player.joined") || key.startsWith("multiplayer.player.joined.")) {
                    Notifications.add(Notifications.Kind.INFO, text, 4000, false);
                    return;
                }
                if (key.equals("multiplayer.player.left")) {
                    Notifications.add(Notifications.Kind.INFO, text, 4000, false);
                    return;
                }
                if (key.startsWith("death.")) {
                    Notifications.add(Notifications.Kind.WARN, text, 6000, false);
                    return;
                }
                if (key.startsWith("chat.type.advancement.")) {
                    Notifications.add(Notifications.Kind.INFO, text, 5000, false);
                    return;
                }
                if (key.equals("chat.type.announcement") || key.startsWith("commands.")) {
                    Notifications.add(Notifications.Kind.INFO, text, 5000, false);
                    return;
                }
            }
        }
        if (!c.chatMentions || mc.player == null) return;
        String me = mc.player.getGameProfile().getName();
        String lower = text.toLowerCase(Locale.ROOT);
        // ignore your own messages ("<Name> ..." or "[Name] ...")
        if (fromPlayer && (text.startsWith("<" + me + ">") || text.startsWith("[" + me + "]"))) return;
        boolean hit = me.length() > 2 && lower.contains(me.toLowerCase(Locale.ROOT));
        if (!hit) {
            for (String k : c.chatKeywords) {
                if (k != null && !k.isBlank() && lower.contains(k.toLowerCase(Locale.ROOT))) {
                    hit = true;
                    break;
                }
            }
        }
        if (hit) Notifications.chat(text.length() > 140 ? text.substring(0, 140) + "…" : text);
    }
}
