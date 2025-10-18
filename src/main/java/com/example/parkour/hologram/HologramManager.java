package com.example.parkour.hologram;

import com.example.parkour.ParkourPlugin;
import com.example.parkour.course.ParkourCourse;
import com.example.parkour.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HologramManager {

    private final ParkourPlugin plugin;
    private final com.example.parkour.util.ParkourManager parkourManager;
    private final Map<String, Hologram> topHolograms = new HashMap<>();
    private final Map<String, Hologram> bestHolograms = new HashMap<>();
    private BukkitTask task;

    public HologramManager(ParkourPlugin plugin, com.example.parkour.util.ParkourManager parkourManager) {
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
                Hologram hologram = new Hologram(course.getBestHologramLocation());
                hologram.spawn(List.of("§eYour current best time:", "§7Step closer to update"));
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
        Hologram hologram = new Hologram(location);
        hologram.spawn(List.of("§eYour current best time:", "§7Step closer to update"));
        Hologram existing = bestHolograms.put(course.getName().toLowerCase(), hologram);
        if (existing != null) {
            existing.despawn();
        }
        course.setBestHologramLocation(location);
    }

    private void startUpdater() {
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, Hologram> entry : bestHolograms.entrySet()) {
                ParkourCourse course = parkourManager.getCourse(entry.getKey());
                if (course == null) {
                    continue;
                }
                Hologram hologram = entry.getValue();
                Location location = hologram.getBaseLocation();
                Player nearest = findNearestPlayer(location, 6);
                if (nearest == null) {
                    hologram.update(List.of("§eYour current best time:", "§7Step closer to update"));
                    continue;
                }
                long best = course.getBestTime(nearest.getUniqueId());
                if (best <= 0) {
                    hologram.update(List.of("§eYour current best time:", "§7No time recorded."));
                } else {
                    hologram.update(List.of("§eYour current best time:", "§f" + nearest.getName() + " §7- §a" + TimeUtil.formatDuration(best)));
                }
            }
        }, 20L, 40L);
    }

    private Player findNearestPlayer(Location location, double range) {
        Player closest = null;
        double bestDistance = range * range;
        for (Player player : location.getWorld().getPlayers()) {
            double distance = player.getLocation().distanceSquared(location);
            if (distance <= bestDistance) {
                bestDistance = distance;
                closest = player;
            }
        }
        return closest;
    }

    public void despawnAll() {
        if (task != null) {
            task.cancel();
        }
        topHolograms.values().forEach(Hologram::despawn);
        bestHolograms.values().forEach(Hologram::despawn);
        topHolograms.clear();
        bestHolograms.clear();
    }
}
