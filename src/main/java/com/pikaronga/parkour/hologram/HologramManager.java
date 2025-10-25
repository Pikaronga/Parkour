package com.pikaronga.parkour.hologram;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.config.HologramTextProvider;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class HologramManager implements Listener {

    private final ParkourPlugin plugin;
    private final com.pikaronga.parkour.util.ParkourManager parkourManager;
    private final HologramTextProvider textProvider;
    private final NamespacedKey hologramKey;
    private final Map<String, Hologram> topHolograms = new HashMap<>();
    private final Map<String, PersonalBestHologram> bestHolograms = new HashMap<>();
    private final Map<String, CreatorHologram> creatorHolograms = new HashMap<>();
    private BukkitTask task;

    public HologramManager(ParkourPlugin plugin,
                           com.pikaronga.parkour.util.ParkourManager parkourManager,
                           HologramTextProvider textProvider,
                           NamespacedKey hologramKey) {
        this.plugin = plugin;
        this.parkourManager = parkourManager;
        this.textProvider = textProvider;
        this.hologramKey = hologramKey;
    }

    public void spawnConfiguredHolograms() {
        plugin.getLogger().info("Spawning configured holograms for loaded courses.");
        for (ParkourCourse course : parkourManager.getCourses().values()) {
            createHolograms(course);
        }
        startUpdater();
    }

    public void createHolograms(ParkourCourse course) {
        refreshCourseHolograms(course, true);
    }

    public void updateHolograms(ParkourCourse course) {
        refreshCourseHolograms(course, false);
    }

    public void setTopHologram(ParkourCourse course, Location location) {
        course.setTopHologramLocation(location != null ? location.clone() : null);
        log(Level.INFO, "Configured top hologram location for course " + course.getName() + ".");
        createHolograms(course);
    }

    public void setBestHologram(ParkourCourse course, Location location) {
        course.setBestHologramLocation(location != null ? location.clone() : null);
        log(Level.INFO, "Configured personal best hologram location for course " + course.getName() + ".");
        createHolograms(course);
    }

    public void setCreatorHologram(ParkourCourse course, Location location) {
        course.setCreatorHologramLocation(location != null ? location.clone() : null);
        log(Level.INFO, "Configured creator hologram location for course " + course.getName() + ".");
        createHolograms(course);
    }

    private void refreshCourseHolograms(ParkourCourse course, boolean forceRespawn) {
        runSync(() -> {
            String key = course.getName().toLowerCase(Locale.ROOT);
            handleTopHologram(course, key, forceRespawn);
            handleBestHologram(course, key, forceRespawn);
            handleCreatorHologram(course, key, forceRespawn);
        });
    }

    private void handleTopHologram(ParkourCourse course, String key, boolean forceRespawn) {
        Location location = course.getTopHologramLocation();
        Hologram top = topHolograms.get(key);
        if (location != null) {
            int limit = course.getCreators().isEmpty() ? 10 : plugin.getConfigManager().getTopHologramSize();
            java.util.List<String> lines = course.createTopLinesWithLimit(textProvider, limit);
            boolean respawn = forceRespawn || top == null || !isSameLocation(top.getBaseLocation(), location);
            if (respawn) {
                if (top != null) {
                    log(Level.INFO, String.format(
                            "Despawning stale top hologram for course %s at %s with stands %s.",
                            course.getName(),
                            formatLocation(top.getBaseLocation()),
                            formatEntityIds(top.getEntityIds())));
                    top.despawn();
                }
                Hologram hologram = new Hologram(location, hologramKey, identifierFor(course.getName(), "top"));
                hologram.spawn(lines);
                topHolograms.put(key, hologram);
                log(Level.INFO, String.format(
                        "Spawned top hologram for course %s at %s with stands %s.",
                        course.getName(),
                        formatLocation(location),
                        formatEntityIds(hologram.getEntityIds())));
            } else {
                top.update(lines);
                log(Level.FINE, String.format(
                        "Updated top hologram for course %s at %s with stands %s.",
                        course.getName(),
                        formatLocation(top.getBaseLocation()),
                        formatEntityIds(top.getEntityIds())));
            }
        } else if (top != null) {
            log(Level.INFO, String.format(
                    "Removed top hologram for course %s at %s with stands %s.",
                    course.getName(),
                    formatLocation(top.getBaseLocation()),
                    formatEntityIds(top.getEntityIds())));
            top.despawn();
            topHolograms.remove(key);
        }
    }

    private void handleBestHologram(ParkourCourse course, String key, boolean forceRespawn) {
        Location location = course.getBestHologramLocation();
        PersonalBestHologram best = bestHolograms.get(key);
        if (location != null) {
            boolean respawn = forceRespawn || best == null || !isSameLocation(best.getBaseLocation(), location);
            if (respawn) {
                if (best != null) {
                    log(Level.INFO, String.format(
                            "Despawning stale personal best hologram for course %s at %s with stands %s.",
                            course.getName(),
                            formatLocation(best.getBaseLocation()),
                            formatEntityIds(best.getTrackedEntityIds())));
                    best.destroy();
                }
                PersonalBestHologram hologram = new PersonalBestHologram(plugin, course, location, textProvider, hologramKey);
                bestHolograms.put(key, hologram);
                log(Level.INFO, String.format(
                        "Spawned personal best hologram for course %s at %s with stands %s.",
                        course.getName(),
                        formatLocation(location),
                        formatEntityIds(hologram.getTrackedEntityIds())));
            } else {
                Bukkit.getOnlinePlayers().forEach(best::updateFor);
                log(Level.FINE, String.format(
                        "Updated personal best hologram for course %s at %s with stands %s.",
                        course.getName(),
                        formatLocation(best.getBaseLocation()),
                        formatEntityIds(best.getTrackedEntityIds())));
            }
        } else if (best != null) {
            log(Level.INFO, String.format(
                    "Removed personal best hologram for course %s at %s with stands %s.",
                    course.getName(),
                    formatLocation(best.getBaseLocation()),
                    formatEntityIds(best.getTrackedEntityIds())));
            best.destroy();
            bestHolograms.remove(key);
        }
    }

    private void handleCreatorHologram(ParkourCourse course, String key, boolean forceRespawn) {
        Location location = course.getCreatorHologramLocation();
        CreatorHologram creator = creatorHolograms.get(key);
        if (location != null) {
            Location existing = creator != null ? creator.getBaseLocation() : null;
            boolean respawn = forceRespawn || creator == null || !isSameLocation(existing, location);
            if (respawn) {
                if (creator != null) {
                    log(Level.INFO, String.format(
                            "Despawning stale creator hologram for course %s at %s with stands %s.",
                            course.getName(),
                            formatLocation(existing),
                            formatEntityIds(creator.getEntityIds())));
                    creator.destroy();
                }
                CreatorHologram hologram = new CreatorHologram(course, location, textProvider, hologramKey);
                creatorHolograms.put(key, hologram);
                log(Level.INFO, String.format(
                        "Spawned creator hologram for course %s at %s with stands %s.",
                        course.getName(),
                        formatLocation(location),
                        formatEntityIds(hologram.getEntityIds())));
            } else {
                creator.update();
                log(Level.FINE, String.format(
                        "Updated creator hologram for course %s at %s with stands %s.",
                        course.getName(),
                        formatLocation(creator.getBaseLocation()),
                        formatEntityIds(creator.getEntityIds())));
            }
        } else if (creator != null) {
            log(Level.INFO, String.format(
                    "Removed creator hologram for course %s at %s with stands %s.",
                    course.getName(),
                    formatLocation(creator.getBaseLocation()),
                    formatEntityIds(creator.getEntityIds())));
            creator.destroy();
            creatorHolograms.remove(key);
        }
    }

    private void startUpdater() {
        runSync(() -> {
            if (task != null) {
                task.cancel();
            }
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Entry<String, PersonalBestHologram> entry : bestHolograms.entrySet()) {
                    ParkourCourse course = parkourManager.getCourse(entry.getKey());
                    if (course == null) {
                        continue;
                    }
                    PersonalBestHologram hologram = entry.getValue();
                    Bukkit.getOnlinePlayers().forEach(hologram::updateFor);
                }
            }, 20L, 40L);
            log(Level.FINE, "Started personal best hologram updater task.");
        });
    }

    public void despawnAll() {
        runSync(() -> {
            if (task != null) {
                task.cancel();
                task = null;
            }
            topHolograms.values().forEach(Hologram::despawn);
            bestHolograms.values().forEach(PersonalBestHologram::destroy);
            creatorHolograms.values().forEach(CreatorHologram::destroy);
            topHolograms.clear();
            bestHolograms.clear();
            creatorHolograms.clear();
            log(Level.INFO, "Despawned all holograms.");
        });
    }

    public void removeCourseHolograms(ParkourCourse course) {
        runSync(() -> {
            String key = course.getName().toLowerCase(Locale.ROOT);
            Hologram top = topHolograms.remove(key);
            if (top != null) {
                log(Level.INFO, String.format(
                        "Removed top hologram for removed course %s at %s with stands %s.",
                        course.getName(),
                        formatLocation(top.getBaseLocation()),
                        formatEntityIds(top.getEntityIds())));
                top.despawn();
            }
            PersonalBestHologram best = bestHolograms.remove(key);
            if (best != null) {
                log(Level.INFO, String.format(
                        "Removed personal best hologram for removed course %s at %s with stands %s.",
                        course.getName(),
                        formatLocation(best.getBaseLocation()),
                        formatEntityIds(best.getTrackedEntityIds())));
                best.destroy();
            }
            CreatorHologram creator = creatorHolograms.remove(key);
            if (creator != null) {
                log(Level.INFO, String.format(
                        "Removed creator hologram for removed course %s at %s with stands %s.",
                        course.getName(),
                        formatLocation(creator.getBaseLocation()),
                        formatEntityIds(creator.getEntityIds())));
                creator.destroy();
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        runSync(() -> bestHolograms.values().forEach(hologram -> hologram.prepareForPlayer(player)));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        bestHolograms.values().forEach(hologram -> hologram.removePlayer(player.getUniqueId()));
    }

    private void runSync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    private boolean isSameLocation(Location a, Location b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.getWorld() == null || b.getWorld() == null) {
            return false;
        }
        if (!a.getWorld().getUID().equals(b.getWorld().getUID())) {
            return false;
        }
        return a.distanceSquared(b) < 0.0001D;
    }

    private String formatLocation(Location location) {
        if (location == null) {
            return "unknown";
        }
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "null";
        return String.format("%s:(%.2f, %.2f, %.2f)", worldName, location.getX(), location.getY(), location.getZ());
    }

    private String formatEntityIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }
        return ids.stream().map(UUID::toString).collect(Collectors.joining(", ", "[", "]"));
    }

    private String identifierFor(String courseName, String type) {
        return type + ":" + courseName.toLowerCase(Locale.ROOT);
    }

    private void log(Level level, String message) {
        plugin.getLogger().log(level, message);
    }
}
