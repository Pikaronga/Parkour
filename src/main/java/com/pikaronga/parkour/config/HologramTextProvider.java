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
        result = applyGradients(result);
        result = applyHexColors(result);
        return ChatColor.translateAlternateColorCodes('&', result);
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
        this.configuration = YamlConfiguration.loadConfiguration(file);
    }
}

