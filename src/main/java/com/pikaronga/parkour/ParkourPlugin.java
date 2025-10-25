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
        this.parkourManager = new ParkourManager(storage.loadCourses());
        this.cacheManager = new ParkourCacheManager(this, storage);
        this.messageManager = new MessageManager(this);
        this.guiConfig = new GuiConfig(this);
        this.hologramTextProvider = new HologramTextProvider(this);
        this.sessionManager = new SessionManager(this, messageManager);
        this.hologramKey = new NamespacedKey(this, "parkour_holo_id");
        this.hologramManager = new HologramManager(this, parkourManager, hologramTextProvider, hologramKey);

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

        if (configManager.playerParkoursEnabled()) {
            this.playerParkourManager = new PlayerParkourManager(this, configManager, parkourManager);
            Bukkit.getPluginManager().registerEvents(new BuildProtectionListener(playerParkourManager), this);
            Bukkit.getPluginManager().registerEvents(new PlotWorldListener(playerParkourManager), this);
            Bukkit.getPluginManager().registerEvents(new ParkourListener(parkourManager, sessionManager, messageManager, playerParkourManager), this);
            // Draw outlines for existing plots if enabled
            playerParkourManager.redrawAllOutlines();
        } else {
            // Register without player-parkour testing support
            Bukkit.getPluginManager().registerEvents(new ParkourListener(parkourManager, sessionManager, messageManager, null), this);
        }

        Bukkit.getScheduler().runTaskLater(this, () -> hologramManager.spawnConfiguredHolograms(), 20L);
        getLogger().info("Loaded " + parkourManager.getCourses().size() + " parkour course(s).");
    }

    @Override
    public void onDisable() {
        if (hologramManager != null) {
            hologramManager.despawnAll();
        }
        if (sessionManager != null) {
            sessionManager.endAllSessions();
        }
        if (parkourManager != null && storage != null) {
            storage.saveCourses(parkourManager.getCourses());
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
