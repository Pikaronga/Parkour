package com.pikaronga.parkour.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Map;

public class GuiConfig {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public GuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "gui.yml");
        if (!file.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public String title(String path, String def, Map<String, String> ph) {
        return color(apply(config.getString("gui." + path + ".title", def), ph));
    }

    // Returns the colored, static prefix of a GUI title (text before first placeholder like {page}).
    public String titlePrefix(String path, String def) {
        String raw = config.getString("gui." + path + ".title", def);
        if (raw == null) raw = def;
        int idx = raw.indexOf('{');
        String base = idx >= 0 ? raw.substring(0, idx) : raw;
        return color(base);
    }

    public Material material(String path, String key, Material def) {
        String name = config.getString("gui." + path + "." + key + ".material");
        if (name == null) return def;
        try { return Material.valueOf(name.toUpperCase()); } catch (IllegalArgumentException e) { return def; }
    }

    public String name(String path, String key, String def, Map<String, String> ph) {
        String raw = config.getString("gui." + path + "." + key + ".name", def);
        return color(apply(raw, ph));
    }

    public List<String> lore(String path, String key, List<String> def, Map<String, String> ph) {
        List<String> list = config.getStringList("gui." + path + "." + key + ".lore");
        if (list == null || list.isEmpty()) list = def;
        return list.stream().map(s -> color(apply(s, ph))).toList();
    }

    public Material itemMaterial(String path, Material def) {
        String name = config.getString("gui." + path + ".item.material");
        if (name == null) return def;
        try { return Material.valueOf(name.toUpperCase()); } catch (IllegalArgumentException e) { return def; }
    }

    public String itemName(String path, String def, Map<String, String> ph) {
        return color(apply(config.getString("gui." + path + ".item.name", def), ph));
    }

    public List<String> itemLore(String path, List<String> def, Map<String, String> ph) {
        List<String> list = config.getStringList("gui." + path + ".item.lore");
        if (list == null || list.isEmpty()) list = def;
        return list.stream().map(s -> color(apply(s, ph))).toList();
    }

    public int slot(String path, String key, int def) {
        return config.getInt("gui." + path + "." + key + ".slot", def);
    }

    public int rows(String path, int defRows) {
        int rows = config.getInt("gui." + path + ".rows", defRows);
        if (rows <= 0) rows = defRows;
        // Bukkit inventories support 9-54 slots, clamp to 1-6 rows
        return Math.max(1, Math.min(6, rows));
    }

    private String apply(String s, Map<String, String> ph) {
        if (s == null) return "";
        String out = s;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }
}
