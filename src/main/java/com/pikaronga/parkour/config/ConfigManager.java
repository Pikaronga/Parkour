package com.pikaronga.parkour.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

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
}
