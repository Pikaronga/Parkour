package com.pikaronga.parkour.hologram;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.config.HologramTextProvider;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;

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
            if (!isLocationReady(location, course, "top")) {
                return;
            }
            int limit = course.getCreators().isEmpty() ? 10 : plugin.getConfigManager().getTopHologramSize();
            java.util.List<String> lines = course.createTopLinesWithLimit(textProvider, limit);
            boolean respawn = forceRespawn || top == null || !isSameLocation(top.getBaseLocation(), location);
            if (respawn) {
                if (top != null) {
                    top.despawn();
                    log(Level.INFO, "Despawning stale top hologram for course " + course.getName() + ".");
                }
                Hologram hologram = new Hologram(location, hologramKey, identifierFor(course.getName(), "top"), plugin);
                hologram.spawn(lines);
                topHolograms.put(key, hologram);
                logHologramSpawn("top", course, location, hologram.getArmorStandEntityIds());
                log(Level.INFO, "Spawned top hologram for course " + course.getName() + ".");
            } else {
                top.update(lines);
                log(Level.FINE, "Updated top hologram for course " + course.getName() + ".");
            }
        } else if (top != null) {
            top.despawn();
            topHolograms.remove(key);
            log(Level.INFO, "Removed top hologram for course " + course.getName() + ".");
        }
    }

    private void handleBestHologram(ParkourCourse course, String key, boolean forceRespawn) {
        Location location = course.getBestHologramLocation();
        PersonalBestHologram best = bestHolograms.get(key);
        if (location != null) {
            if (!isLocationReady(location, course, "personal best")) {
                return;
            }
            boolean respawn = forceRespawn || best == null || !isSameLocation(best.getBaseLocation(), location);
            if (respawn) {
                if (best != null) {
                    best.destroy();
                    log(Level.INFO, "Despawning stale personal best hologram for course " + course.getName() + ".");
                }
                PersonalBestHologram hologram = new PersonalBestHologram(plugin, course, location, textProvider, hologramKey);
                bestHolograms.put(key, hologram);
                hologram.getHeaderEntityId().ifPresent(id -> log(Level.INFO, "Spawned personal best hologram for course " + course.getName() + " at " + formatLocation(location) + " (header=" + id + ")"));
                log(Level.INFO, "Spawned personal best hologram for course " + course.getName() + ".");
            } else {
                Bukkit.getOnlinePlayers().forEach(best::updateFor);
                log(Level.FINE, "Updated personal best hologram for course " + course.getName() + ".");
            }
        } else if (best != null) {
            best.destroy();
            bestHolograms.remove(key);
            log(Level.INFO, "Removed personal best hologram for course " + course.getName() + ".");
        }
    }

    private void handleCreatorHologram(ParkourCourse course, String key, boolean forceRespawn) {
        Location location = course.getCreatorHologramLocation();
        CreatorHologram creator = creatorHolograms.get(key);
        if (location != null) {
            if (!isLocationReady(location, course, "creator")) {
                return;
            }
            Location existing = creator != null ? creator.getBaseLocation() : null;
            boolean respawn = forceRespawn || creator == null || !isSameLocation(existing, location);
            if (respawn) {
                if (creator != null) {
                    creator.destroy();
                    log(Level.INFO, "Despawning stale creator hologram for course " + course.getName() + ".");
                }
                CreatorHologram hologram = new CreatorHologram(course, location, textProvider, hologramKey, plugin);
                creatorHolograms.put(key, hologram);
                logHologramSpawn("creator", course, location, hologram.getEntityIds());
                log(Level.INFO, "Spawned creator hologram for course " + course.getName() + ".");
            } else {
                creator.update();
                log(Level.FINE, "Updated creator hologram for course " + course.getName() + ".");
            }
        } else if (creator != null) {
            creator.destroy();
            creatorHolograms.remove(key);
            log(Level.INFO, "Removed creator hologram for course " + course.getName() + ".");
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
                top.despawn();
                log(Level.INFO, "Removed top hologram for removed course " + course.getName() + ".");
            }
            PersonalBestHologram best = bestHolograms.remove(key);
            if (best != null) {
                best.destroy();
                log(Level.INFO, "Removed personal best hologram for removed course " + course.getName() + ".");
            }
            CreatorHologram creator = creatorHolograms.remove(key);
            if (creator != null) {
                creator.destroy();
                log(Level.INFO, "Removed creator hologram for removed course " + course.getName() + ".");
            }
            // Deep-clean any stray ArmorStands with our hologram key still present for this course
            try { purgeWorldEntitiesForCourse(key); } catch (Throwable t) { log(Level.FINE, "Purge for course '" + key + "' failed: " + t.getMessage()); }
            // Also purge by known locations using scoreboard tag to catch legacy entities without PDC keys
            try { purgeNearbyTagged(course.getTopHologramLocation()); } catch (Throwable ignored) {}
            try { purgeNearbyTagged(course.getBestHologramLocation()); } catch (Throwable ignored) {}
            try { purgeNearbyTagged(course.getCreatorHologramLocation()); } catch (Throwable ignored) {}
        });
    }

    private int purgeWorldEntitiesForCourse(String courseKey) {
        if (hologramKey == null || courseKey == null || courseKey.isBlank()) return 0;
        int removed = 0;
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            try {
                for (org.bukkit.entity.ArmorStand as : w.getEntitiesByClass(org.bukkit.entity.ArmorStand.class)) {
                    String id = null;
                    try { id = as.getPersistentDataContainer().get(hologramKey, org.bukkit.persistence.PersistentDataType.STRING); } catch (Throwable ignored) {}
                    if (id == null || id.isBlank()) continue;
                    // Expect formats: top:course:idx, creator:course[:idx], best:course:header or best:course:uuid
                    String lowered = id.toLowerCase(Locale.ROOT);
                    String[] parts = lowered.split(":");
                    if (parts.length >= 2 && courseKey.equals(parts[1])) {
                        as.remove();
                        removed++;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return removed;
    }

    private int purgeNearbyTagged(Location base) {
        if (base == null || base.getWorld() == null) return 0;
        org.bukkit.World w = base.getWorld();
        double hr = 1.6; // horizontal radius
        double vr = 8.0; // vertical range to cover stacked lines
        int removed = 0;
        try {
            java.util.Collection<org.bukkit.entity.Entity> near = w.getNearbyEntities(base, hr, vr, hr, e -> e instanceof org.bukkit.entity.ArmorStand);
            for (org.bukkit.entity.Entity e : near) {
                try {
                    org.bukkit.entity.ArmorStand as = (org.bukkit.entity.ArmorStand) e;
                    // Remove only our tagged holograms
                    if (as.getScoreboardTags() != null && as.getScoreboardTags().contains("parkour_holo")) {
                        as.remove();
                        removed++;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return removed;
    }

    // Public utilities for admin cleanup
    public int cleanupHologramsByCourseName(String courseName) {
        if (courseName == null || courseName.isBlank()) return 0;
        String key = courseName.toLowerCase(Locale.ROOT);
        int removed = 0;
        // If we still have the course loaded, use full path which also despawns tracked
        ParkourCourse maybe = parkourManager.getCourse(key);
        if (maybe != null) {
            removeCourseHolograms(maybe);
        }
        removed += purgeWorldEntitiesForCourse(key);
        return removed;
    }

    public int cleanupAllHologramEntities() {
        int removed = 0;
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            try {
                for (org.bukkit.entity.ArmorStand as : w.getEntitiesByClass(org.bukkit.entity.ArmorStand.class)) {
                    boolean ours = false;
                    // PDC-based identification
                    try {
                        String id = hologramKey != null ? as.getPersistentDataContainer().get(hologramKey, org.bukkit.persistence.PersistentDataType.STRING) : null;
                        if (id != null && !id.isBlank()) ours = true;
                    } catch (Throwable ignored) {}
                    // Legacy scoreboard tag
                    try {
                        if (!ours && as.getScoreboardTags() != null && as.getScoreboardTags().contains("parkour_holo")) ours = true;
                    } catch (Throwable ignored) {}
                    if (ours) {
                        as.remove();
                        removed++;
                    }
                }
            } catch (Throwable ignored) {}
        }
        // Also clear any tracked maps; entities are gone anyway
        topHolograms.clear();
        bestHolograms.clear();
        creatorHolograms.clear();
        return removed;
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

    private String identifierFor(String courseName, String type) {
        return type + ":" + courseName.toLowerCase(Locale.ROOT);
    }

    private void log(Level level, String message) {
        boolean debug = false;
        try { debug = plugin.getConfigManager().debugEnabled(); } catch (Throwable ignored) {}
        // Always log warnings and above; gate info/fine by debug flag
        if (level.intValue() >= Level.WARNING.intValue() || debug) {
            plugin.getLogger().log(level, message);
        }
    }

    private boolean isLocationReady(Location location, ParkourCourse course, String type) {
        if (location.getWorld() == null) {
            // Attempt to resolve player parkour world as a fallback
            String playerWorld = plugin.getConfigManager().getPlayerWorldName();
            if (playerWorld != null) {
                org.bukkit.World w = Bukkit.getWorld(playerWorld);
                if (w == null) {
                    // Try to create the world (same logic as PlayerParkourManager.ensureWorld)
                    try {
                        WorldCreator wc = new WorldCreator(playerWorld);
                        Bukkit.createWorld(wc);
                        w = Bukkit.getWorld(playerWorld);
                        log(Level.INFO, "Created fallback player world '" + playerWorld + "' while preparing holograms.");
                    } catch (Throwable t) {
                        log(Level.WARNING, "Failed to create fallback player world '" + playerWorld + "': " + t.getMessage());
                    }
                }
                if (w != null) {
                    location.setWorld(w);
                }
            }
        }
        if (location.getWorld() == null) {
            log(Level.WARNING, "Cannot spawn " + type + " hologram for course '" + course.getName() + "': world is not loaded.");
            return false;
        }

        // Ensure chunk is loaded
        try {
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            org.bukkit.World w = location.getWorld();
            if (!w.isChunkLoaded(chunkX, chunkZ)) {
                w.getChunkAt(chunkX, chunkZ).load(true);
                log(Level.FINE, "Loaded chunk [" + chunkX + "," + chunkZ + "] for hologram at " + formatLocation(location));
            }
        } catch (Throwable ignored) {}

        return true;
    }

    private void logHologramSpawn(String type, ParkourCourse course, Location location, List<UUID> entityIds) {
        if (entityIds == null || entityIds.isEmpty()) {
            log(Level.INFO, "Spawned " + type + " hologram for course " + course.getName() + " at " + formatLocation(location) + " (no entities spawned)");
            return;
        }
        log(Level.INFO, "Spawned " + type + " hologram for course " + course.getName() + " at " + formatLocation(location) + " (entities=" + entityIds + ")");
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName() + String.format("[%.2f, %.2f, %.2f]", location.getX(), location.getY(), location.getZ());
    }
}
