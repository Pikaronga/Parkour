package com.pikaronga.parkour.cache;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.ParkourCourse;
import com.pikaronga.parkour.storage.SqliteParkourStorage;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ParkourCacheManager {
    private static class Cached {
        final ParkourCourse course;
        final AtomicInteger active = new AtomicInteger(0);
        final AtomicBoolean dirty = new AtomicBoolean(false);
        final AtomicBoolean loading = new AtomicBoolean(false);
        volatile long lastAccess = System.currentTimeMillis();
        Cached(ParkourCourse c) { this.course = c; }
    }

    private final ParkourPlugin plugin;
    private final SqliteParkourStorage storage;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final long unloadMillis = 5 * 60 * 1000L;

    public ParkourCacheManager(ParkourPlugin plugin, SqliteParkourStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        startTasks();
    }

    private void startTasks() {
        // Periodic flush + unload check (async)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Cached c : cache.values()) {
                if (c.dirty.get()) {
                    storage.saveTimesAsync(c.course);
                    c.dirty.set(false);
                }
                if (c.active.get() <= 0 && now - c.lastAccess > unloadMillis) {
                    // unload times to free memory
                    c.course.getTimes().clear();
                    cache.remove(c.course.getName().toLowerCase());
                }
            }
        }, 20L * 60, 20L * 60); // every 60s
    }

    public void ensureTimesLoaded(ParkourCourse course) {
        String key = course.getName().toLowerCase();
        Cached c = cache.computeIfAbsent(key, k -> new Cached(course));
        c.lastAccess = System.currentTimeMillis();
        if (course.getTimes().isEmpty() && c.loading.compareAndSet(false, true)) {
            storage.loadTimesForCourseAsync(course.getName(), (crs, ok) -> {
                c.loading.set(false);
                if (ok && crs != null) {
                    try { plugin.getHologramManager().updateHolograms(crs); } catch (Throwable ignored) {}
                }
            });
        }
    }

    public void startUsing(ParkourCourse course) {
        String key = course.getName().toLowerCase();
        Cached c = cache.computeIfAbsent(key, k -> new Cached(course));
        c.active.incrementAndGet();
        c.lastAccess = System.currentTimeMillis();
        if (course.getTimes().isEmpty() && c.loading.compareAndSet(false, true)) {
            storage.loadTimesForCourseAsync(course.getName(), (crs, ok) -> {
                c.loading.set(false);
                if (ok && crs != null) {
                    try { plugin.getHologramManager().updateHolograms(crs); } catch (Throwable ignored) {}
                }
            });
        }
    }

    public void stopUsing(ParkourCourse course) {
        String key = course.getName().toLowerCase();
        Cached c = cache.get(key);
        if (c != null) {
            c.active.decrementAndGet();
            c.lastAccess = System.currentTimeMillis();
        }
    }

    public void markDirty(ParkourCourse course) {
        String key = course.getName().toLowerCase();
        Cached c = cache.computeIfAbsent(key, k -> new Cached(course));
        c.dirty.set(true);
        c.lastAccess = System.currentTimeMillis();
    }

    public void recordTime(ParkourCourse course, UUID playerId, long nanos) {
        course.addTime(playerId, nanos);
        markDirty(course);
    }
}
