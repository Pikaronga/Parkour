package com.pikaronga.parkour.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        File file = new File(plugin.getDataFolder(), "config.yml");
        if (!file.exists()) {
            plugin.saveResource("config.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean playerParkoursEnabled() {
        return config.getBoolean("player-parkours.enabled", true);
    }

    public String getPlayerWorldName() {
        return config.getString("player-parkours.world", "player_parkours");
    }

    public boolean isVoidWorld() {
        return config.getBoolean("player-parkours.world-void", true);
    }

    public int getPlatformY() {
        return Math.max(5, config.getInt("player-parkours.platform.y", 64));
    }

    public int getPlatformSize() {
        return Math.max(3, config.getInt("player-parkours.platform.size", 5));
    }

    public org.bukkit.Material getPlatformBlock() {
        String name = config.getString("player-parkours.platform.block", "STONE");
        try { return org.bukkit.Material.valueOf(name.toUpperCase()); } catch (IllegalArgumentException e) { return org.bukkit.Material.STONE; }
    }

    public int getPlotSize() {
        return Math.max(16, config.getInt("player-parkours.plot-size", 64));
    }

    public int getPlotGap() {
        return Math.max(0, config.getInt("player-parkours.plot-gap", 16));
    }

    public boolean giveCreativeInPlots() {
        return config.getBoolean("player-parkours.give-creative", true);
    }

    public boolean allowWorldEdit() {
        return config.getBoolean("player-parkours.allow-worldedit", true);
    }

    public int weMaxBlocksPerOp() {
        return Math.max(100, config.getInt("player-parkours.worldedit.max-blocks-per-operation", 3000));
    }

    public java.util.List<String> weDenyList() {
        java.util.List<String> list = config.getStringList("player-parkours.worldedit.deny");
        if (list == null) list = java.util.Collections.emptyList();
        return list.stream().map(s -> s == null ? "" : s.toLowerCase()).toList();
    }

    public java.util.List<String> weAllowList() {
        java.util.List<String> list = config.getStringList("player-parkours.worldedit.allow");
        if (list == null) return java.util.Collections.emptyList();
        return list.stream().map(s -> s == null ? "" : s.toLowerCase()).toList();
    }

    public int getMaxPerPlayer() {
        return Math.max(1, config.getInt("player-parkours.max-per-player", 3));
    }

    public int getCreateCooldownSeconds() {
        return Math.max(0, config.getInt("player-parkours.create-cooldown-seconds", 0));
    }

    public int getTopHologramSize() {
        return Math.max(1, config.getInt("player-parkours.top-hologram-size", 5));
    }

    public String storageMode() {
        return config.getString("player-parkours.storage", "sqlite").toLowerCase();
    }

    public boolean useSqlite() {
        String m = storageMode();
        return m.equals("sqlite") || m.equals("sql") || m.equals("database");
    }

    public boolean outlineEnabled() {
        return config.getBoolean("player-parkours.outline.enabled", true);
    }

    public org.bukkit.Material outlineBorderMaterial() {
        String name = config.getString("player-parkours.outline.border-block", "YELLOW_CONCRETE");
        try { return org.bukkit.Material.valueOf(name.toUpperCase()); } catch (IllegalArgumentException e) { return org.bukkit.Material.YELLOW_CONCRETE; }
    }

    public org.bukkit.Material outlineCornerMaterial() {
        String name = config.getString("player-parkours.outline.corner-block", "SEA_LANTERN");
        try { return org.bukkit.Material.valueOf(name.toUpperCase()); } catch (IllegalArgumentException e) { return org.bukkit.Material.SEA_LANTERN; }
    }

    public boolean blockNaturalSpawns() {
        return config.getBoolean("player-parkours.world-rules.block-natural-spawns", true);
    }

    public boolean useWorldBorder() {
        return config.getBoolean("player-parkours.borders.use-worldborder", true);
    }

    public boolean disableCollisions() {
        // Default to true (disable collisions) if not present
        if (!config.contains("collisions.disable")) return true;
        return config.getBoolean("collisions.disable");
    }

    public boolean rewardsEnabled() {
        return config.getBoolean("player-parkours.rewards.enabled", false);
    }

    public int rewardsIntervalDays() {
        return Math.max(1, config.getInt("player-parkours.rewards.interval-days", 7));
    }

    public int rewardsWinners() {
        return Math.max(1, config.getInt("player-parkours.rewards.winners", 3));
    }

    public String rewardsCommand() {
        return config.getString("player-parkours.rewards.command", "give %player% diamond 3");
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "config.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean debugEnabled() {
        return config.getBoolean("debug", false);
    }

    public HotbarItemConfig getHotbarItem(String key,
                                          Material defaultMaterial,
                                          int defaultSlot,
                                          String defaultName,
                                          List<String> defaultLore) {
        String base = "player-parkours.items." + key;
        int slot = config.getInt(base + ".slot", defaultSlot);
        Material material = resolveMaterial(config.getString(base + ".material"), defaultMaterial);
        String name = color(config.getString(base + ".name", defaultName));
        List<String> lore = colorList(readLore(config, base + ".lore", defaultLore));
        return new HotbarItemConfig(slot, material, name, lore);
    }

    public ToggleHotbarItemConfig getToggleHotbarItem(String key,
                                                      int defaultSlot,
                                                      Material visibleMaterial,
                                                      String visibleName,
                                                      List<String> visibleLore,
                                                      Material hiddenMaterial,
                                                      String hiddenName,
                                                      List<String> hiddenLore) {
        String base = "player-parkours.items." + key;
        int slot = config.getInt(base + ".slot", defaultSlot);
        HotbarItemConfig visible = readToggleState(base + ".visible", slot, visibleMaterial, visibleName, visibleLore);
        HotbarItemConfig hidden = readToggleState(base + ".hidden", slot, hiddenMaterial, hiddenName, hiddenLore);
        return new ToggleHotbarItemConfig(slot, visible, hidden);
    }

    private HotbarItemConfig readToggleState(String path,
                                             int slot,
                                             Material defaultMaterial,
                                             String defaultName,
                                             List<String> defaultLore) {
        Material material = resolveMaterial(config.getString(path + ".material"), defaultMaterial);
        String name = color(config.getString(path + ".name", defaultName));
        List<String> lore = colorList(readLore(config, path + ".lore", defaultLore));
        return new HotbarItemConfig(slot, material, name, lore);
    }

    private Material resolveMaterial(String value, Material fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid material '" + value + "' in config; using " + fallback);
            return fallback;
        }
    }

    private List<String> readLore(FileConfiguration cfg, String path, List<String> def) {
        if (cfg.isList(path)) {
            List<String> list = cfg.getStringList(path);
            if (!list.isEmpty()) return new ArrayList<>(list);
        } else if (cfg.isString(path)) {
            String line = cfg.getString(path);
            if (line != null) return splitLore(line);
        }
        return new ArrayList<>(def == null ? Collections.emptyList() : def);
    }

    private List<String> splitLore(String input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        String[] parts = input.split("\\\\n");
        List<String> out = new ArrayList<>(parts.length);
        for (String part : parts) {
            out.add(part);
        }
        return out;
    }

    private List<String> colorList(List<String> raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(color(line));
        }
        return out;
    }

    private String color(String input) {
        if (input == null) return "";
        String result = applyGradients(input);
        result = applyHexColors(result);
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    private String applyHexColors(String input) {
        StringBuilder out = new StringBuilder();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(input);
        int last = 0;
        while (matcher.find()) {
            out.append(input, last, matcher.start());
            out.append(rgbToSection(matcher.group(1)));
            last = matcher.end();
        }
        out.append(input.substring(last));
        return out.toString();
    }

    private String applyGradients(String input) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})/([^/]+)/&#([A-Fa-f0-9]{6})");
        java.util.regex.Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String grad = gradient(matcher.group(2), matcher.group(1), matcher.group(3));
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(grad));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String gradient(String text, String startHex, String endHex) {
        if (text == null || text.isEmpty()) return "";
        int[] start = parseHex(startHex);
        int[] end = parseHex(endHex);
        if (start == null || end == null) return rgbToSection(startHex) + text;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            double t = (text.length() == 1) ? 0.0 : (i / (double) (text.length() - 1));
            int r = (int) Math.round(start[0] + (end[0] - start[0]) * t);
            int g = (int) Math.round(start[1] + (end[1] - start[1]) * t);
            int b = (int) Math.round(start[2] + (end[2] - start[2]) * t);
            String hex = String.format("%02X%02X%02X", clamp(r), clamp(g), clamp(b));
            out.append(rgbToSection(hex)).append(text.charAt(i));
        }
        return out.toString();
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private int[] parseHex(String hex) {
        if (hex == null) return null;
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() != 6) return null;
        try {
            return new int[]{
                    Integer.parseInt(h.substring(0, 2), 16),
                    Integer.parseInt(h.substring(2, 4), 16),
                    Integer.parseInt(h.substring(4, 6), 16)
            };
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

    public record HotbarItemConfig(int slot, Material material, String name, List<String> lore) {
        public boolean isEnabled() {
            return slot >= 0 && material != null && material != Material.AIR;
        }
    }

    public record ToggleHotbarItemConfig(int slot, HotbarItemConfig visibleState, HotbarItemConfig hiddenState) {
        public boolean isEnabled() {
            return slot >= 0
                    && visibleState != null && visibleState.isEnabled()
                    && hiddenState != null && hiddenState.isEnabled();
        }
    }
}
