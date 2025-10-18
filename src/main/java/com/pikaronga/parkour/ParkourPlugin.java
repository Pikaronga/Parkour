package com.pikaronga.parkour;

import com.pikaronga.parkour.command.ParkourCommand;
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

    @Override
    public void onEnable() {
        this.storage = new ParkourStorage(this);
        this.parkourManager = new ParkourManager(storage.loadCourses());
        this.sessionManager = new SessionManager(this);
        this.hologramManager = new HologramManager(this, parkourManager);

        ParkourCommand command = new ParkourCommand(this, parkourManager, hologramManager);
        if (getCommand("parkour") != null) {
            getCommand("parkour").setExecutor(command);
            getCommand("parkour").setTabCompleter(command);
        }

        Bukkit.getPluginManager().registerEvents(new ParkourListener(parkourManager, sessionManager), this);
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
}
