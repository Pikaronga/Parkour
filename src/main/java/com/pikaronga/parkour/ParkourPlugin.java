package com.pikaronga.parkour;

import com.pikaronga.parkour.command.ParkourCommand;
import com.pikaronga.parkour.config.HologramTextProvider;
import com.pikaronga.parkour.config.ConfigManager;
import com.pikaronga.parkour.config.GuiConfig;
import com.pikaronga.parkour.config.MessageManager;
import com.pikaronga.parkour.hologram.HologramManager;
import com.pikaronga.parkour.listener.ParkourListener;
import com.pikaronga.parkour.gui.GUIListener;
import com.pikaronga.parkour.session.SessionManager;
import com.pikaronga.parkour.storage.SqliteParkourStorage;
import com.pikaronga.parkour.storage.DatabaseManager;
import com.pikaronga.parkour.cache.ParkourCacheManager;
import com.pikaronga.parkour.util.ParkourManager;
import com.pikaronga.parkour.player.PlayerParkourManager;
import com.pikaronga.parkour.player.BuildProtectionListener;
import com.pikaronga.parkour.player.PlotWorldListener;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class ParkourPlugin extends JavaPlugin {

    private SqliteParkourStorage storage;
    private ParkourManager parkourManager;
    private SessionManager sessionManager;
    private HologramManager hologramManager;
    private MessageManager messageManager;
    private HologramTextProvider hologramTextProvider;
    private ConfigManager configManager;
    private GuiConfig guiConfig;
    private PlayerParkourManager playerParkourManager;
    private ParkourCacheManager cacheManager;
    private NamespacedKey hologramKey;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        DatabaseManager db = new DatabaseManager(this);
        this.storage = new SqliteParkourStorage(this, db);
        try { this.storage.cleanupInvalidNumericColumns(); } catch (Throwable ignored) {}
        this.parkourManager = new ParkourManager(java.util.Collections.emptyList());
        this.cacheManager = new ParkourCacheManager(this, storage);
        this.messageManager = new MessageManager(this);
        this.guiConfig = new GuiConfig(this);
        this.hologramTextProvider = new HologramTextProvider(this);
        this.sessionManager = new SessionManager(this, messageManager);
        this.hologramKey = new NamespacedKey(this, "parkour_holo_id");
        this.hologramManager = new HologramManager(this, parkourManager, hologramTextProvider, hologramKey);

        PlayerParkourManager.ensureWorld(this, configManager);

        // Safety sweep: purge stray hologram armor stands with tag
        try {
            Bukkit.getWorlds().forEach(w -> w.getEntitiesByClass(ArmorStand.class).forEach(as -> {
                try {
                    boolean legacyTag = as.getScoreboardTags().contains("parkour_holo");
                    boolean keyed = hologramKey != null && as.getPersistentDataContainer().has(hologramKey, PersistentDataType.STRING);
                    if (legacyTag || keyed) {
                        as.remove();
                    }
                } catch (Throwable ignored) {}
            }));
        } catch (Throwable ignored) {}

        ParkourCommand command = new ParkourCommand(this, parkourManager, hologramManager, messageManager);
        if (getCommand("parkour") != null) {
            getCommand("parkour").setExecutor(command);
            getCommand("parkour").setTabCompleter(command);
        }

        Bukkit.getPluginManager().registerEvents(hologramManager, this);
        Bukkit.getPluginManager().registerEvents(new GUIListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.pikaronga.parkour.gui.GuiProtectionListener(), this);
        Bukkit.getPluginManager().registerEvents(new com.pikaronga.parkour.gui.SetupInputListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.pikaronga.parkour.gui.RatingGUIListener(this), this);
        // Disable collisions globally via team
        if (configManager.disableCollisions()) {
            com.pikaronga.parkour.player.CollisionManager collisionManager = new com.pikaronga.parkour.player.CollisionManager();
            Bukkit.getPluginManager().registerEvents(collisionManager, this);
            collisionManager.applyToOnlinePlayers();
        }

        if (configManager.playerParkoursEnabled()) {
            this.playerParkourManager = new PlayerParkourManager(this, configManager, parkourManager);
            Bukkit.getPluginManager().registerEvents(new BuildProtectionListener(playerParkourManager), this);
            Bukkit.getPluginManager().registerEvents(new PlotWorldListener(playerParkourManager), this);
            Bukkit.getPluginManager().registerEvents(new ParkourListener(parkourManager, sessionManager, messageManager, playerParkourManager), this);
        } else {
            // Register without player-parkour testing support
            Bukkit.getPluginManager().registerEvents(new ParkourListener(parkourManager, sessionManager, messageManager, null), this);
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            getLogger().info("Loading parkour data after world bootstrap...");
            java.util.List<com.pikaronga.parkour.course.ParkourCourse> courses = storage.loadCourses();
            parkourManager.replaceCourses(courses);
            if (playerParkourManager != null) {
                playerParkourManager.refreshPlotUsage();
                if (configManager.outlineEnabled() && !configManager.isVoidWorld()) {
                    playerParkourManager.redrawAllOutlines();
                }
            }
            // Schedule hologram spawning later to ensure worlds/chunks are fully registered and ticking
            // (player world creation may schedule its own chunk loads at 40L)
            Bukkit.getScheduler().runTaskLater(this, () -> hologramManager.spawnConfiguredHolograms(), 80L);
            getLogger().info("Loaded " + parkourManager.getCourses().size() + " parkour course(s).");
            // Log counts from SQLite
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                long times = 0L, stats = 0L, runCounters = 0L;
                try (java.sql.Connection conn = new DatabaseManager(this).getConnection();
                     java.sql.Statement st = conn.createStatement()) {
                    try (java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM times")) { if (rs.next()) times = rs.getLong(1); }
                    try (java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM parkour_stats")) { if (rs.next()) stats = rs.getLong(1); }
                    try (java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM parkour_runs")) { if (rs.next()) runCounters = rs.getLong(1); }
                } catch (Exception ignored) {}
                getLogger().info("[Parkour] Loaded " + times + " times, " + runCounters + " run counters, and " + stats + " player stats from SQLite.");
            });
        }, 20L);

        try { storage.startStatsTasks(); } catch (Throwable ignored) {}

        // Register PlaceholderAPI placeholders (if PlaceholderAPI is present)
        try {
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new com.pikaronga.parkour.placeholder.ParkourPlaceholders(this).register();
                getLogger().info("Registered PlaceholderAPI expansion 'parkour'.");
            }
        } catch (Throwable t) {
            getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) {
            hologramManager.despawnAll();
        }
        if (sessionManager != null) {
            sessionManager.endAllSessions();
        }
        // Ensure plot inventories/gamemode restored for any players still inside plots
        try {
            if (playerParkourManager != null) {
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    playerParkourManager.handleExitPlot(p);
                }
            }
        } catch (Throwable ignored) {}
        if (parkourManager != null && storage != null) {
            storage.saveCourses(parkourManager.getCourses());
            try { storage.shutdown(); } catch (Throwable ignored) {}
        }
    }

    public SqliteParkourStorage getStorage() {
        return storage;
    }

    public ParkourManager getParkourManager() {
        return parkourManager;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public HologramTextProvider getHologramTextProvider() {
        return hologramTextProvider;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PlayerParkourManager getPlayerParkourManager() {
        return playerParkourManager;
    }

    public GuiConfig getGuiConfig() { return guiConfig; }

    public ParkourCacheManager getCacheManager() { return cacheManager; }
}
