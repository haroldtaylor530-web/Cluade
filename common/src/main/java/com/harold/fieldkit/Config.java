package com.harold.fieldkit;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** config/fieldkit.json */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path file;
    public static Config INSTANCE = new Config();

    // HUD
    public boolean hud = true;
    public boolean minimap = true;
    public int minimapSize = 100;
    public int minimapZoom = 1;
    public boolean minimapShading = true;
    public boolean minimapEntities = true;
    public boolean coordsLine = true;

    // warnings
    public boolean warnMobs = true;
    public int mobRadius = 16;
    public boolean warnPlayers = true;
    public int playerRadius = 48;
    public boolean warnSound = true;

    // combat
    public boolean combatHud = true;
    public boolean targetInfo = true;
    public boolean fightLog = true;
    public boolean effectTimers = true;

    // console / notifications
    public boolean notifyErrors = true;
    public boolean notifyLag = true;
    public boolean notifyMemory = true;
    public boolean notifyFps = true;
    public boolean chatMentions = true;
    public List<String> chatKeywords = new ArrayList<>();
    public boolean serverMessages = true;
    public boolean loadInfo = true;

    public static void load(Path configDir) {
        file = configDir.resolve("fieldkit.json");
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file)) {
                    Config c = GSON.fromJson(r, Config.class);
                    if (c != null) INSTANCE = c;
                }
            }
        } catch (Exception e) {
            Fieldkit.LOG.warn("Couldn't read {}, using defaults", file, e);
        }
        if (INSTANCE.chatKeywords == null) INSTANCE.chatKeywords = new ArrayList<>();
        save();
    }

    public static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(file)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (Exception e) {
            Fieldkit.LOG.warn("Couldn't save {}", file, e);
        }
    }
}
