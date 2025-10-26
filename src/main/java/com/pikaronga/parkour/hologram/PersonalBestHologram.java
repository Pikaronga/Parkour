package com.pikaronga.parkour.hologram;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.config.HologramTextProvider;
import com.pikaronga.parkour.course.ParkourCourse;
import com.pikaronga.parkour.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.WorldCreator;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PersonalBestHologram {

    private static final String HOLOGRAM_TAG = "parkour_holo";

    private final ParkourPlugin plugin;
    private final ParkourCourse course;
    private final Location baseLocation;
    private ArmorStand headerStand;
    private final Map<UUID, ArmorStand> personalLines = new HashMap<>();
    private final HologramTextProvider textProvider;
    private final NamespacedKey hologramKey;
    private final String headerIdentifier;

    public PersonalBestHologram(ParkourPlugin plugin, ParkourCourse course, Location baseLocation, HologramTextProvider textProvider, NamespacedKey hologramKey) {
        this.plugin = plugin;
        this.course = course;
        this.baseLocation = baseLocation.clone();
        this.textProvider = textProvider;
        this.hologramKey = hologramKey;
        this.headerIdentifier = identifierForHeader();
        // Ensure spawn runs on main thread and after world is loaded
        if (!Bukkit.isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, this::spawnHeader);
        } else {
            spawnHeader();
        }
        Bukkit.getOnlinePlayers().forEach(this::prepareForPlayer);
    }

    private void spawnHeader() {
        // Ensure this runs on main thread
        if (!Bukkit.isPrimaryThread()) {
            plugin.getServer().getScheduler().runTask(plugin, this::spawnHeader);
            return;
        }

        if (!ensureWorldAndChunkLoaded(baseLocation)) {
            // schedule a retry on the main thread next tick
            plugin.getServer().getScheduler().runTask(plugin, this::spawnHeader);
            return;
        }

        World world = baseLocation.getWorld();
        if (world == null) {
            plugin.getLogger().warning("PersonalBestHologram: world still null after ensureWorldAndChunkLoaded for " + formatLocation(baseLocation));
            return;
        }

        // Force-load the chunk containing the header so the armor stand will be ticked even when players are absent
        try { world.setChunkForceLoaded(baseLocation.getBlockX() >> 4, baseLocation.getBlockZ() >> 4, true); } catch (Throwable ignored) {}
        headerStand = world.spawn(baseLocation, ArmorStand.class, stand -> {
            configureStand(stand, textProvider.formatBestHeader(course.getName()), headerIdentifier);
        });
        if (headerStand != null) {
            try { if (plugin.getConfigManager().debugEnabled()) plugin.getLogger().info("Spawned personal best header hologram for '" + course.getName() + "' at " + formatLocation(headerStand.getLocation()) + " (entity=" + headerStand.getUniqueId() + ")"); } catch (Throwable ignored) {}
        }
    }

    private ArmorStand spawnPersonalLine() {
        // Ensure this runs on main thread
        if (!Bukkit.isPrimaryThread()) {
            final ArmorStand[] holder = new ArmorStand[1];
            plugin.getServer().getScheduler().runTask(plugin, () -> holder[0] = spawnPersonalLine());
            return holder[0];
        }

        Location loc = baseLocation.clone().add(0, -0.3, 0);
        if (!ensureWorldAndChunkLoaded(loc)) {
            // schedule a retry on the main thread next tick
            final ArmorStand[] holder = new ArmorStand[1];
            plugin.getServer().getScheduler().runTask(plugin, () -> holder[0] = spawnPersonalLine());
            return holder[0];
        }

        World world = loc.getWorld();
        if (world == null) {
            plugin.getLogger().warning("PersonalBestHologram: world null when spawning personal line at " + formatLocation(loc));
            return null;
        }

        ArmorStand stand = world.spawn(loc, ArmorStand.class, s -> {
            configureStand(s, textProvider.formatBestEmpty(course.getName()), "");
        });
        // initially hide from all players until we show it for the target player
        Bukkit.getOnlinePlayers().forEach(player -> player.hideEntity(plugin, stand));
        if (stand != null) {
            try { if (plugin.getConfigManager().debugEnabled()) plugin.getLogger().info("Spawned personal best line hologram for '" + course.getName() + "' at " + formatLocation(stand.getLocation()) + " (entity=" + stand.getUniqueId() + ")"); } catch (Throwable ignored) {}
        }
        return stand;
    }

    private void configureStand(ArmorStand stand, String name, String identifier) {
        stand.setGravity(false);
        // Use marker=false so clients render the name reliably in different server implementations
        stand.setMarker(false);
        try { stand.setRemoveWhenFarAway(false); } catch (Throwable ignored) {}
        stand.setInvisible(true);
        // apply the name first, then make it visible so metadata is applied consistently
        try {
            Component comp = LegacyComponentSerializer.legacySection().deserialize(name);
            stand.customName(comp);
        } catch (Throwable t) {
            try { stand.setCustomName(name); } catch (Throwable ignored) {}
        }
        stand.setCustomNameVisible(true);
        stand.setBasePlate(false);
        stand.setSmall(true);
        stand.setPersistent(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        applyIdentifier(stand, identifier);
    }

    public void prepareForPlayer(Player player) {
        if (headerStand != null) {
            player.showEntity(plugin, headerStand);
        }
        for (ArmorStand stand : personalLines.values()) {
            player.hideEntity(plugin, stand);
        }
        updateFor(player);
    }

    public void updateFor(Player player) {
        ArmorStand stand = personalLines.computeIfAbsent(player.getUniqueId(), uuid -> spawnPersonalLine());
        if (stand == null) {
            return;
        }
        applyIdentifier(stand, identifierForPlayer(player.getUniqueId()));
        long best = course.getBestTime(player.getUniqueId());
        if (best <= 0) {
            try {
                Component comp = LegacyComponentSerializer.legacySection().deserialize(textProvider.formatBestEmpty(course.getName()));
                stand.customName(comp);
            } catch (Throwable t) {
                try { stand.setCustomName(textProvider.formatBestEmpty(course.getName())); } catch (Throwable ignored) {}
            }
        } else {
            String line = textProvider.formatBestEntry(player.getName(), TimeUtil.formatDuration(best), course.getName());
            try {
                Component comp = LegacyComponentSerializer.legacySection().deserialize(line);
                stand.customName(comp);
            } catch (Throwable t) {
                try { stand.setCustomName(line); } catch (Throwable ignored) {}
            }
        }
        Bukkit.getOnlinePlayers().forEach(online -> {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                online.hideEntity(plugin, stand);
            }
        });
        player.showEntity(plugin, stand);
    }

    public void removePlayer(UUID uuid) {
        ArmorStand stand = personalLines.remove(uuid);
        if (stand != null) {
            stand.remove();
        }
    }

    public void destroy() {
        World w = baseLocation.getWorld();
        if (w != null) {
            try { w.setChunkForceLoaded(baseLocation.getBlockX() >> 4, baseLocation.getBlockZ() >> 4, false); } catch (Throwable ignored) {}
        }
        if (headerStand != null) {
            headerStand.remove();
            headerStand = null;
        }
        personalLines.values().forEach(ArmorStand::remove);
        personalLines.clear();
    }

    public Location getBaseLocation() {
        return baseLocation;
    }

    public java.util.Optional<UUID> getHeaderEntityId() {
        return headerStand == null ? java.util.Optional.empty() : java.util.Optional.of(headerStand.getUniqueId());
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName() + String.format("[%.2f, %.2f, %.2f]", location.getX(), location.getY(), location.getZ());
    }

    private void applyIdentifier(ArmorStand stand, String identifier) {
        try { stand.addScoreboardTag(HOLOGRAM_TAG); } catch (Throwable ignored) {}
        if (hologramKey != null && identifier != null && !identifier.isEmpty()) {
            try { stand.getPersistentDataContainer().set(hologramKey, PersistentDataType.STRING, identifier); } catch (Throwable ignored) {}
        }
    }

    private String identifierForHeader() {
        return "best:" + course.getName().toLowerCase(Locale.ROOT) + ":header";
    }

    private String identifierForPlayer(UUID uuid) {
        return "best:" + course.getName().toLowerCase(Locale.ROOT) + ":" + uuid;
    }

    /**
     * Ensure the location's world and the containing chunk are loaded.
     * If the world is null we attempt to load a known fallback world named "player_parkours".
     */
    private boolean ensureWorldAndChunkLoaded(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            // fallback target used by the plugin for player parkour plots
            String fallback = "player_parkours";
            World found = Bukkit.getWorld(fallback);
            if (found == null) {
                try { if (plugin.getConfigManager().debugEnabled()) plugin.getLogger().info("World '" + fallback + "' not loaded; attempting to create it now."); } catch (Throwable ignored) {}
                try {
                    found = new WorldCreator(fallback).createWorld();
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to create world '" + fallback + "': " + t.getMessage());
                }
            }
            if (found == null) {
                plugin.getLogger().warning("ensureWorldAndChunkLoaded: world is null and fallback could not be created for location " + formatLocation(loc));
                return false;
            }
            loc.setWorld(found);
            world = found;
        }

        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            try { if (plugin.getConfigManager().debugEnabled()) plugin.getLogger().info("Chunk [" + chunkX + "," + chunkZ + "] not loaded in world '" + world.getName() + "'; loading chunk."); } catch (Throwable ignored) {}
            try {
                world.getChunkAt(chunkX, chunkZ).load(true);
            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to load chunk at " + chunkX + "," + chunkZ + " in world '" + world.getName() + "': " + t.getMessage());
                return false;
            }
        }

        return true;
    }
}

