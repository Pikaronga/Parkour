package com.pikaronga.parkour.hologram;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public class HologramManager implements Listener {

    private final ParkourPlugin plugin;
    private final com.pikaronga.parkour.util.ParkourManager parkourManager;
    private final Map<String, Hologram> topHolograms = new HashMap<>();
    private final Map<String, PersonalBestHologram> bestHolograms = new HashMap<>();
    private BukkitTask task;

    public HologramManager(ParkourPlugin plugin, com.pikaronga.parkour.util.ParkourManager parkourManager) {
        this.plugin = plugin;
        this.parkourManager = parkourManager;
    }

    public void spawnConfiguredHolograms() {
        for (ParkourCourse course : parkourManager.getCourses().values()) {
            if (course.getTopHologramLocation() != null) {
                Hologram hologram = new Hologram(course.getTopHologramLocation());
                hologram.spawn(course.createTopLines());
                topHolograms.put(course.getName().toLowerCase(), hologram);
            }
            if (course.getBestHologramLocation() != null) {
                PersonalBestHologram hologram = new PersonalBestHologram(plugin, course, course.getBestHologramLocation());
                bestHolograms.put(course.getName().toLowerCase(), hologram);
            }
        }
        startUpdater();
    }

    public void updateHolograms(ParkourCourse course) {
        Hologram top = topHolograms.get(course.getName().toLowerCase());
        if (top != null) {
            top.update(course.createTopLines());
        }
        PersonalBestHologram best = bestHolograms.get(course.getName().toLowerCase());
        if (best != null) {
            Bukkit.getOnlinePlayers().forEach(best::updateFor);
        }
    }

    public void setTopHologram(ParkourCourse course, Location location) {
        Hologram hologram = new Hologram(location);
        hologram.spawn(course.createTopLines());
        Hologram existing = topHolograms.put(course.getName().toLowerCase(), hologram);
        if (existing != null) {
            existing.despawn();
        }
        course.setTopHologramLocation(location);
    }

    public void setBestHologram(ParkourCourse course, Location location) {
        PersonalBestHologram hologram = new PersonalBestHologram(plugin, course, location);
        PersonalBestHologram existing = bestHolograms.put(course.getName().toLowerCase(), hologram);
        if (existing != null) {
            existing.destroy();
        }
        course.setBestHologramLocation(location);
    }

    private void startUpdater() {
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, PersonalBestHologram> entry : bestHolograms.entrySet()) {
                ParkourCourse course = parkourManager.getCourse(entry.getKey());
                if (course == null) {
                    continue;
                }
                PersonalBestHologram hologram = entry.getValue();
                Bukkit.getOnlinePlayers().forEach(hologram::updateFor);
            }
        }, 20L, 40L);
    }

    public void despawnAll() {
        if (task != null) {
            task.cancel();
        }
        topHolograms.values().forEach(Hologram::despawn);
        bestHolograms.values().forEach(PersonalBestHologram::destroy);
        topHolograms.clear();
        bestHolograms.clear();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        bestHolograms.values().forEach(hologram -> hologram.prepareForPlayer(player));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        bestHolograms.values().forEach(hologram -> hologram.removePlayer(player.getUniqueId()));
    }
}
