package com.pikaronga.parkour;

import com.pikaronga.parkour.command.ParkourCommand;
import com.pikaronga.parkour.config.HologramTextProvider;
import com.pikaronga.parkour.config.MessageManager;
import com.pikaronga.parkour.hologram.HologramManager;
import com.pikaronga.parkour.listener.ParkourListener;
import com.pikaronga.parkour.session.SessionManager;
import com.pikaronga.parkour.storage.ParkourStorage;
import com.pikaronga.parkour.util.ParkourManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class ParkourPlugin extends JavaPlugin {

    private ParkourStorage storage;
    private ParkourManager parkourManager;
    private SessionManager sessionManager;
    private HologramManager hologramManager;
    private MessageManager messageManager;
    private HologramTextProvider hologramTextProvider;

    @Override
    public void onEnable() {
        this.storage = new ParkourStorage(this);
        this.parkourManager = new ParkourManager(storage.loadCourses());
        this.messageManager = new MessageManager(this);
        this.hologramTextProvider = new HologramTextProvider(this);
        this.sessionManager = new SessionManager(this, messageManager);
        this.hologramManager = new HologramManager(this, parkourManager, hologramTextProvider);

        ParkourCommand command = new ParkourCommand(this, parkourManager, hologramManager, messageManager);
        if (getCommand("parkour") != null) {
            getCommand("parkour").setExecutor(command);
            getCommand("parkour").setTabCompleter(command);
        }

        Bukkit.getPluginManager().registerEvents(new ParkourListener(parkourManager, sessionManager, messageManager), this);
        Bukkit.getPluginManager().registerEvents(hologramManager, this);

        hologramManager.spawnConfiguredHolograms();
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

    public ParkourStorage getStorage() {
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
}
