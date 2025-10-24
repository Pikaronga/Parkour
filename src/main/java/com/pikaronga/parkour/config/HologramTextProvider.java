package com.pikaronga.parkour.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class HologramTextProvider {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration configuration;

    public HologramTextProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "holos.yml");
        if (!file.exists()) {
            plugin.saveResource("holos.yml", false);
        }
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    public String formatTopHeader(String courseName) {
        return getString("top.header", "&aTop Parkour Times", Map.of("course", courseName));
    }

    public String formatTopEmpty(String courseName) {
        return getString("top.empty", "&7No completions yet.", Map.of("course", courseName));
    }

    public String formatTopEntry(int position, String playerName, String time, String courseName) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("position", String.valueOf(position));
        placeholders.put("name", playerName);
        placeholders.put("time", time);
        placeholders.put("course", courseName);
        return getString("top.entry", "&b{position}. &f{name} &7- &e{time}", placeholders);
    }

    public String formatBestHeader(String courseName) {
        return getString("personal-best.header", "&eYour current best time:", Map.of("course", courseName));
    }

    public String formatBestEmpty(String courseName) {
        return getString("personal-best.empty", "&7No time recorded.", Map.of("course", courseName));
    }

    public String formatBestEntry(String playerName, String time, String courseName) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", playerName);
        placeholders.put("time", time);
        placeholders.put("course", courseName);
        return getString("personal-best.entry", "&f{name} &7- &a{time}", placeholders);
    }

    public String formatCreatorsHeader(String courseName) {
        return getString("creators.header", "&aCreated by:", Map.of("course", courseName));
    }

    public String formatCreatorEntry(String name, String courseName) {
        return getString("creators.entry", "&f" + name, Map.of("course", courseName, "name", name));
    }

    private String getString(String path, String def, Map<String, String> placeholders) {
        String value = configuration.getString(path);
        if (value == null) {
            value = def;
        }
        String result = value;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    public void reload() {
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }
}

