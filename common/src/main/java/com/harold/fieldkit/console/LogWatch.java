package com.harold.fieldkit.console;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.harold.fieldkit.Config;
import com.harold.fieldkit.hud.Notifications;

import net.minecraft.Util;

/**
 * Reads logs/latest.log as the game writes it (so it sees everything since launch, Forge and Fabric alike), keeps the
 * last few thousand lines for the Console tab, and spots trouble: errors by mod, mixin failures, missing textures and
 * models, a lagging server, running out of memory.
 */
public final class LogWatch {
    public enum Level {
        INFO, WARN, ERROR, CHAT, NOTE
    }

    public record Line(String time, Level level, String source, String text) {
    }

    /** A recognised problem, with a plain-language hint. */
    public record Finding(String title, String hint, int count) {
    }

    private static final int KEEP = 4000;
    // [28Oct2026 12:34:56.789] [Render thread/WARN] [mixin/]: text   (Forge)   or   [12:34:56] [Render thread/WARN] (Minecraft) text (Fabric)
    private static final Pattern LINE = Pattern.compile(
            "^\\[(?:\\d{1,2}[A-Za-z]{3}\\d{4} )?(\\d\\d:\\d\\d:\\d\\d)(?:\\.\\d+)?\\] \\[([^\\]]*?)/(INFO|WARN|ERROR|FATAL|DEBUG|TRACE)\\]\\s*(?:\\[([^\\]]*)\\]|\\(([^)]*)\\))?:?\\s?(.*)$");
    private static final List<Line> LINES = new ArrayList<>();
    private static final Map<String, int[]> BY_SOURCE = new LinkedHashMap<>();
    private static final Map<String, int[]> FINDINGS = new LinkedHashMap<>();
    private static final Map<String, String> HINTS = new LinkedHashMap<>();
    private static Path file;
    private static long offset;
    private static String firstTime;
    private static int errors, warns, newErrors;
    private static String lastErrorText;
    private static long lastPoll, lastErrorNote, lastLagNote, lastMemNote;
    private static Thread reader;

    private LogWatch() {
    }

    public static void start(Path latestLog) {
        file = latestLog;
        reader = new Thread(() -> {
            while (true) {
                try {
                    poll();
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                } catch (Throwable t) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "Fieldkit log watcher");
        reader.setDaemon(true);
        reader.start();
    }

    private static synchronized void poll() throws Exception {
        if (file == null || !Files.exists(file)) return;
        long size = Files.size(file);
        if (size < offset) offset = 0; // log rolled over
        if (size == offset) return;
        byte[] buf;
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
            f.seek(offset);
            int n = (int) Math.min(size - offset, 4 << 20);
            buf = new byte[n];
            f.readFully(buf);
        }
        int end = buf.length;
        while (end > 0 && buf[end - 1] != '\n') end--; // only whole lines
        if (end == 0) return;
        offset += end;
        String chunk = new String(buf, 0, end, StandardCharsets.UTF_8);
        for (String raw : chunk.split("\r?\n")) parse(raw);
    }

    private static void parse(String raw) {
        if (raw.isEmpty()) return;
        Matcher m = LINE.matcher(raw);
        synchronized (LINES) {
            if (!m.matches()) {
                // stack trace or wrapped line: belongs to the previous entry
                if (!LINES.isEmpty()) {
                    Line prev = LINES.get(LINES.size() - 1);
                    if (prev.level == Level.ERROR || prev.level == Level.WARN) {
                        add(new Line(prev.time, prev.level, prev.source, "    " + raw.trim()));
                        analyse(Level.INFO, prev.source, raw); // known patterns only, not "other errors" again
                    }
                }
                return;
            }
            String time = m.group(1), thread = m.group(2), lvl = m.group(3);
            String source = m.group(4) != null ? m.group(4) : m.group(5) != null ? m.group(5) : thread;
            source = source.replaceAll("/$", "");
            String text = m.group(6);
            if (firstTime == null) firstTime = time;
            Level level = switch (lvl) {
                case "WARN" -> Level.WARN;
                case "ERROR", "FATAL" -> Level.ERROR;
                default -> Level.INFO;
            };
            if (text.startsWith("[CHAT]") || source.contains("ChatComponent") || source.equals("minecraft/ChatComponent")) level = Level.CHAT;
            add(new Line(time, level, shortSource(source), text));
            if (level == Level.ERROR) {
                errors++;
                newErrors++;
                lastErrorText = shortSource(source) + ": " + text;
                count(BY_SOURCE, shortSource(source), 0);
            } else if (level == Level.WARN) {
                warns++;
                count(BY_SOURCE, shortSource(source), 1);
            }
            analyse(level, source, text);
        }
    }

    private static void add(Line l) {
        LINES.add(l);
        if (LINES.size() > KEEP) LINES.subList(0, LINES.size() - KEEP).clear();
    }

    private static String shortSource(String s) {
        // net.minecraft.client.gui.components.ChatComponent -> ChatComponent
        if (s.contains(".") && !s.contains(" ")) s = s.substring(s.lastIndexOf('.') + 1);
        if (s.length() > 32) s = s.substring(s.length() - 32);
        return s;
    }

    private static void count(Map<String, int[]> map, String key, int i) {
        map.computeIfAbsent(key, k -> new int[2])[i]++;
    }

    private static final Pattern NAMESPACE = Pattern.compile("\\b([a-z0-9_.-]+):[a-z0-9_/.-]+");

    private static void finding(String title, String hint) {
        count(FINDINGS, title, 0);
        HINTS.putIfAbsent(title, hint);
    }

    private static void analyse(Level level, String source, String text) {
        String t = text;
        if (t.contains("Can't keep up! Is the server overloaded?")) {
            finding("Server falling behind", "The world's server side can't run 20 ticks a second. Lots of entities, "
                    + "big farms, chunk generation or too little RAM usually cause it.");
            Matcher m = Pattern.compile("Running (\\d+)ms").matcher(t);
            long now = Util.getMillis();
            if (Config.INSTANCE.notifyLag && now - lastLagNote > 30000) {
                lastLagNote = now;
                Notifications.warn("Server lag: " + (m.find() ? m.group(1) + " ms behind" : "can't keep up"));
            }
        } else if (t.contains("OutOfMemoryError") || t.contains("Java heap space")) {
            finding("Out of memory", "Minecraft ran out of RAM. Give it more in your launcher (6-8 GB for big modpacks).");
        } else if (t.contains("Mixin apply") || t.contains("InvalidInjectionException") || t.contains("MixinApplyError")
                || t.contains("Mixin transformation")) {
            finding("Mixin failure (mod conflict)", "A mod couldn't patch the game, usually two mods clashing or a mod "
                    + "built for a different version. The line names the mixin's mod.");
        } else if (t.contains("Missing textures") || t.contains("missing texture") || t.contains("Unable to load model")
                || t.contains("Unable to resolve texture") || t.contains("Missing model")) {
            String ns = namespace(t);
            finding("Missing textures/models" + (ns != null ? " (" + ns + ")" : ""),
                    "A mod or resource pack points at art that isn't there. Harmless but shows as purple/black blocks.");
        } else if (t.contains("Exception loading blockstate") || t.contains("Exception loading model")) {
            String ns = namespace(t);
            finding("Broken block models" + (ns != null ? " (" + ns + ")" : ""), "A block's model or blockstate file failed to load.");
        } else if (t.contains("Unknown recipe") || t.contains("Parsing error loading recipe") || t.contains("Couldn't parse data file")) {
            String ns = namespace(t);
            finding("Bad recipes/data" + (ns != null ? " (" + ns + ")" : ""), "A datapack or mod has a recipe or data file "
                    + "for an item that doesn't exist, often a compat file for a mod you don't have. Usually harmless.");
        } else if (t.contains("Skipping BlockEntity with id") || t.contains("Skipping Entity with id")) {
            finding("Things from removed mods", "The world has blocks or mobs from a mod that isn't installed any more.");
        } else if (t.contains("Unknown registry key") || t.contains("Registry entry listed as") || t.contains("missing registry")) {
            finding("Missing registry entries", "The world or a datapack refers to items or blocks that aren't registered.");
        } else if (t.contains("Unable to play unknown soundEvent") || t.contains("Missing sound for event")) {
            finding("Missing sounds", "A mod refers to a sound that doesn't exist. Harmless.");
        } else if (t.contains("Received passengers for unknown entity") || t.contains("Received packet for unknown")) {
            finding("Network desync", "The client got data for something it doesn't know about. Small hiccups are normal.");
        } else if (t.contains("Exception in thread") || t.contains("Unhandled exception") || t.contains("Reported exception")) {
            finding("Crash-level exceptions", "Something threw an error that wasn't handled. If the game feels broken, "
                    + "this is the first place to look.");
        } else if (level == Level.ERROR) {
            finding("Other errors", "Errors that don't match a known pattern; see the Console tab, filtered to errors.");
        }
    }

    private static String namespace(String t) {
        Matcher m = NAMESPACE.matcher(t);
        while (m.find()) {
            String ns = m.group(1);
            if (!ns.equals("minecraft") && !ns.contains(".") && ns.length() > 1) return ns;
        }
        return null;
    }

    /** Called every client tick: memory warnings and error notifications on the main thread's clock. */
    public static void tick() {
        long now = Util.getMillis();
        if (now - lastPoll < 1000) return;
        lastPoll = now;
        int fresh;
        String last;
        synchronized (LINES) {
            fresh = newErrors;
            newErrors = 0;
            last = lastErrorText;
        }
        if (fresh > 0 && Config.INSTANCE.notifyErrors && now - lastErrorNote > 15000) {
            lastErrorNote = now;
            Notifications.error((fresh == 1 ? "Error in the log: " : fresh + " new errors, latest: ") + trim(last, 120));
        }
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        if (Config.INSTANCE.notifyMemory && used > rt.maxMemory() * 0.92 && now - lastMemNote > 60000) {
            lastMemNote = now;
            Notifications.warn("Memory nearly full: " + (used >> 20) + " / " + (rt.maxMemory() >> 20) + " MB");
        }
    }

    private static String trim(String s, int n) {
        return s == null ? "" : s.length() > n ? s.substring(0, n) + "…" : s;
    }

    public static void recordNotification(String kind, String text) {
        synchronized (LINES) {
            add(new Line(LocalTime.now().withNano(0).toString(), Level.NOTE, "Fieldkit " + kind.toLowerCase(), text));
        }
    }

    public static void recordChat(String text) {
        synchronized (LINES) {
            add(new Line(LocalTime.now().withNano(0).toString(), Level.CHAT, "chat", text));
        }
    }

    public static List<Line> lines() {
        synchronized (LINES) {
            return new ArrayList<>(LINES);
        }
    }

    public static List<Finding> findings() {
        synchronized (LINES) {
            List<Finding> out = new ArrayList<>();
            FINDINGS.forEach((k, v) -> out.add(new Finding(k, HINTS.get(k), v[0])));
            out.sort((a, b) -> b.count() - a.count());
            return out;
        }
    }

    /** Sources with the most errors/warnings: [name, errors, warnings]. */
    public static List<Object[]> noisiest(int n) {
        synchronized (LINES) {
            List<Object[]> out = new ArrayList<>();
            BY_SOURCE.forEach((k, v) -> out.add(new Object[] { k, v[0], v[1] }));
            out.sort((a, b) -> ((int) b[1] * 10 + (int) b[2]) - ((int) a[1] * 10 + (int) a[2]));
            return out.subList(0, Math.min(n, out.size()));
        }
    }

    public static int errorCount() {
        return errors;
    }

    public static int warnCount() {
        return warns;
    }

    /** When the log began (the game's start), as epoch millis; falls back to the given time. */
    public static long firstLineMillis(long fallback) {
        try {
            if (firstTime == null) poll();
            if (firstTime == null) return fallback;
            LocalDateTime t = LocalDateTime.of(LocalDate.now(), LocalTime.parse(firstTime));
            long ms = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (ms > System.currentTimeMillis()) ms -= 86400000L;
            return ms;
        } catch (Exception e) {
            return fallback;
        }
    }
}
