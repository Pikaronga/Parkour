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

    private String color(String s) {
        String in = (s == null) ? "" : s;
        // Support gradients and hex like in MessageManager
        in = applyGradients(in);
        in = applyHexColors(in);
        return ChatColor.translateAlternateColorCodes('&', in);
    }

    private String applyHexColors(String input) {
        StringBuilder out = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(input);
        int last = 0;
        while (m.find()) {
            out.append(input, last, m.start());
            String hex = m.group(1);
            out.append(rgbToSection(hex));
            last = m.end();
        }
        out.append(input.substring(last));
        return out.toString();
    }

    private String applyGradients(String input) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})/([^/]+)/&#([A-Fa-f0-9]{6})");
        java.util.regex.Matcher m = p.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String startHex = m.group(1);
            String text = m.group(2);
            String endHex = m.group(3);
            String grad = gradient(text, startHex, endHex);
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(grad));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String gradient(String text, String startHex, String endHex) {
        if (text == null || text.isEmpty()) return "";
        int n = text.length();
        int[] s = parseHex(startHex);
        int[] e = parseHex(endHex);
        if (s == null || e == null) return rgbToSection(startHex) + text;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) {
            double t = (n == 1) ? 0.0 : (i / (double) (n - 1));
            int r = (int) Math.round(s[0] + (e[0] - s[0]) * t);
            int g = (int) Math.round(s[1] + (e[1] - s[1]) * t);
            int b = (int) Math.round(s[2] + (e[2] - s[2]) * t);
            String hex = String.format("%02X%02X%02X", clamp(r), clamp(g), clamp(b));
            out.append(rgbToSection(hex)).append(text.charAt(i));
        }
        return out.toString();
    }

    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private int[] parseHex(String hex) {
        if (hex == null) return null;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) return null;
        try {
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            return new int[]{r, g, b};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String rgbToSection(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) return "";
        StringBuilder sb = new StringBuilder("§x");
        for (char c : h.toCharArray()) {
            sb.append('§').append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "gui.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }
}
