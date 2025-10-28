package com.pikaronga.parkour.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConfigMigrator {
    private final JavaPlugin plugin;

    public ConfigMigrator(JavaPlugin plugin) { this.plugin = plugin; }

    public void migrateAll() {
        migrate("config.yml");
        migrate("gui.yml");
        migrate("messages.yml");
        migrate("holos.yml");
    }

    public void migrate(String fileName) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dataFolder.mkdirs();
        }
        File target = new File(dataFolder, fileName);

        int bundledVersion = readBundledVersion(fileName);
        if (bundledVersion <= 0) return; // nothing to compare

        int currentVersion = readFileVersion(target);
        if (!target.exists()) {
            // Not present yet; let normal saveResource flow create it
            return;
        }
        if (currentVersion >= bundledVersion) {
            return; // up to date
        }

        // Backup old file
        File backupsDir = new File(dataFolder, "backups");
        if (!backupsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            backupsDir.mkdirs();
        }
        String base = baseName(fileName);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        // Example: Config_version_config.yml-20251028-120301 or Config_v1_config.yml-...
        File backup = new File(backupsDir, capitalized(base) + "_version_" + fileName.replace('/', '_') + "-" + stamp);
        try {
            Files.copy(target.toPath(), backup.toPath());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to backup " + fileName + ": " + e.getMessage());
        }

        // Merge defaults: add missing keys only, preserve existing values
        try (InputStream in = plugin.getResource(fileName)) {
            if (in == null) return;
            java.io.InputStreamReader reader = new java.io.InputStreamReader(in);
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            FileConfiguration current = YamlConfiguration.loadConfiguration(target);

            mergeMissing(current, defaults);
            current.set("config-version", bundledVersion);
            current.save(target);
            plugin.getLogger().info("Merged defaults into " + fileName + " (" + currentVersion + " -> " + bundledVersion + ") — backup: " + backup.getName());
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to merge defaults for " + fileName + ": " + t.getMessage());
        }
    }

    private int readBundledVersion(String resource) {
        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) return -1;
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in));
            return cfg.getInt("config-version", 1); // default 1 for bundled
        } catch (Exception e) {
            return -1;
        }
    }

    private int readFileVersion(File file) {
        try {
            if (!file.exists()) return 0;
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            return cfg.getInt("config-version", 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private String baseName(String name) {
        int slash = name.lastIndexOf('/') + 1;
        String n = slash > 0 ? name.substring(slash) : name;
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    private String capitalized(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void mergeMissing(FileConfiguration target, FileConfiguration defaults) {
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) {
                if (!target.isConfigurationSection(path) && !target.contains(path)) {
                    target.createSection(path);
                }
                continue;
            }
            if (!target.contains(path)) {
                target.set(path, defaults.get(path));
            }
        }
    }
}
