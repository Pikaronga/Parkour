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
        if (input == null) return "";
        String result = input;
        // Apply simple placeholders first
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        // Handle gradient blocks of the form: "&#RRGGBB/ text /&#RRGGBB"
        result = applyGradients(result);
        // Handle standalone hex codes: "&#RRGGBB"
        result = applyHexColors(result);
        // Finally, handle standard & color codes
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
        // Pattern: &#RRGGBB/ text /&#RRGGBB
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

