package com.pikaronga.parkour.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class MessageManager {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration configuration;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }

    public String getMessage(String key, String def) {
        return getMessage(key, def, Collections.emptyMap());
    }

    public String getMessage(String key, String def, Map<String, String> placeholders) {
        String value = configuration.getString("messages." + key);
        if (value == null) {
            value = def;
        }
        return applyPlaceholdersAndColor(value, placeholders);
    }

    public String getItemName(String key, String def) {
        return getItemValue(key, "name", def);
    }

    public String getItemLore(String key, String def) {
        return getItemValue(key, "lore", def);
    }

    private String getItemValue(String key, String path, String def) {
        String value = configuration.getString("items." + key + "." + path);
        if (value == null) {
            value = def;
        }
        return applyPlaceholdersAndColor(value, Collections.emptyMap());
    }

    private String applyPlaceholdersAndColor(String input, Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    public void reload() {
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }
}

