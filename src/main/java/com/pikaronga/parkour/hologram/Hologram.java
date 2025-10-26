package com.pikaronga.parkour.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.WorldCreator;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Hologram {

    private static final String HOLOGRAM_TAG = "parkour_holo";
    private static final double LINE_SPACING = 0.3D;

    private final Location baseLocation;
    private final List<ArmorStand> armorStands = new ArrayList<>();
    private final NamespacedKey dataKey;
    private final String identifier;
    private final org.bukkit.plugin.Plugin plugin;

    public Hologram(Location baseLocation, NamespacedKey dataKey, String identifier, org.bukkit.plugin.Plugin plugin) {
        this.baseLocation = baseLocation.clone();
        this.dataKey = dataKey;
        this.identifier = identifier;
        this.plugin = plugin;
    }

    

    public void spawn(List<String> lines) {
        despawn();
        // Keep the hologram chunk loaded while we spawn entities
        try { forceLoadChunk(true); } catch (Throwable ignored) {}
        // Ensure world and chunk are loaded for the base location
        if (!ensureWorldAndChunkLoaded(baseLocation)) {
            Bukkit.getLogger().warning("Hologram.spawn: base location world/chunk not ready for " + baseLocation);
            return;
        }

        World world = baseLocation.getWorld();
        Location current = baseLocation.clone();
        for (int index = 0; index < lines.size(); index++) {
            ArmorStand stand = spawnLine(world, current, lines.get(index));
            if (stand == null) {
                continue;
            }
            markStand(stand, index);
            armorStands.add(stand);
            current = current.clone().add(0, -LINE_SPACING, 0);
        }
        
    }

    public void update(List<String> lines) {
        if (armorStands.isEmpty()) {
            spawn(lines);
            return;
        }
        int index = 0;
        for (; index < armorStands.size(); index++) {
            ArmorStand stand = armorStands.get(index);
            if (index < lines.size()) {
                applyName(stand, lines.get(index));
            } else {
                applyEmptyName(stand);
            }
            markStand(stand, index);
        }
        if (lines.size() > armorStands.size()) {
            Location current = armorStands.get(armorStands.size() - 1).getLocation().clone().add(0, -LINE_SPACING, 0);
            List<String> extra = lines.subList(armorStands.size(), lines.size());
            World world = baseLocation.getWorld();
            if (world == null) {
                return;
            }
            for (String line : extra) {
                ArmorStand stand = spawnLine(world, current, line);
                if (stand == null) {
                    continue;
                }
                markStand(stand, index++);
                armorStands.add(stand);
                current = current.clone().add(0, -LINE_SPACING, 0);
            }
        }
    }

    public void despawn() {
        // Release any force-loading of the hologram chunk
        try { forceLoadChunk(false); } catch (Throwable ignored) {}
        for (ArmorStand stand : armorStands) {
            stand.remove();
        }
        armorStands.clear();
    }

    public Location getBaseLocation() {
        return baseLocation;
    }

    public List<UUID> getArmorStandEntityIds() {
        List<UUID> ids = new ArrayList<>(armorStands.size());
        for (ArmorStand stand : armorStands) {
            ids.add(stand.getUniqueId());
        }
        return ids;
    }

    private ArmorStand spawnLine(World world, Location location, String line) {
        if (world == null) return null;
        try {
            try { if (!location.getChunk().isLoaded()) location.getChunk().load(); } catch (Throwable ignored) {}

            ArmorStand stand = world.spawn(location, ArmorStand.class, a -> {
                a.setGravity(false);
                a.setSmall(true);
                a.setBasePlate(false);
                a.setInvulnerable(true);
                a.setPersistent(true);
                a.setSilent(true);
                try { a.setRemoveWhenFarAway(false); } catch (Throwable ignored) {}

                // Apply name first, then make invisible so client properly shows the nameplate
                try { a.customName(LegacyComponentSerializer.legacySection().deserialize(line)); } catch (Throwable t) { try { a.setCustomName(line); } catch (Throwable ignored) {} }
                try { a.setCustomNameVisible(true); } catch (Throwable ignored) {}
                try { a.setInvisible(true); } catch (Throwable ignored) {}
            });

            if (stand == null) return null;
            return stand;
        } catch (Throwable t) {
            try {
                ArmorStand stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
                stand.setGravity(false);
                stand.setSmall(true);
                stand.setBasePlate(false);
                stand.setInvulnerable(true);
                stand.setPersistent(true);
                stand.setSilent(true);
                try { stand.setRemoveWhenFarAway(false); } catch (Throwable ignored) {}
                try { stand.customName(LegacyComponentSerializer.legacySection().deserialize(line)); } catch (Throwable t2) { try { stand.setCustomName(line); } catch (Throwable ignored) {} }
                try { stand.setCustomNameVisible(true); } catch (Throwable ignored) {}
                try { stand.setInvisible(true); } catch (Throwable ignored) {}
                return stand;
            } catch (Throwable ex) {
                return null;
            }
        }
    }

    private void forceLoadChunk(boolean force) {
        try {
            int cx = baseLocation.getBlockX() >> 4;
            int cz = baseLocation.getBlockZ() >> 4;
            World w = baseLocation.getWorld();
            if (w == null) return;
            try {
                if (force) {
                    w.addPluginChunkTicket(cx, cz, plugin);
                } else {
                    w.removePluginChunkTicket(cx, cz, plugin);
                }
                return;
            } catch (Throwable ignored) { }
            try { w.setChunkForceLoaded(cx, cz, force); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private boolean ensureWorldAndChunkLoaded(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            // attempt to load fallback world by name if available
            // we don't know which world name to try here; try 'player_parkours' as a likely candidate
            String fallback = "player_parkours";
            World found = Bukkit.getWorld(fallback);
            if (found == null) {
                Bukkit.getLogger().info("Hologram.ensureWorldAndChunkLoaded: world '" + fallback + "' not loaded; attempting to create it.");
                try {
                    found = new WorldCreator(fallback).createWorld();
                } catch (Throwable t) {
                    Bukkit.getLogger().warning("Hologram.ensureWorldAndChunkLoaded: Failed to create world '" + fallback + "': " + t.getMessage());
                }
            }
            if (found == null) {
                Bukkit.getLogger().warning("Hologram.ensureWorldAndChunkLoaded: world is null for location " + loc);
                return false;
            }
            loc.setWorld(found);
            world = found;
        }

        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            Bukkit.getLogger().info("Hologram.ensureWorldAndChunkLoaded: loading chunk [" + chunkX + "," + chunkZ + "] in world '" + world.getName() + "'.");
            try {
                world.getChunkAt(chunkX, chunkZ).load(true);
            } catch (Throwable t) {
                Bukkit.getLogger().warning("Hologram.ensureWorldAndChunkLoaded: failed to load chunk: " + t.getMessage());
                return false;
            }
        }
        return true;
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "unknown";
        }
        return location.getWorld().getName() + String.format("[%.2f, %.2f, %.2f]", location.getX(), location.getY(), location.getZ());
    }

    // No ProtocolLib present at compile time; we use a hide/show-with-delay per-player workaround instead.

    private void markStand(ArmorStand stand, int index) {
        try {
            stand.addScoreboardTag(HOLOGRAM_TAG);
        } catch (Throwable ignored) {
        }
        if (dataKey != null && identifier != null) {
            try {
                stand.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, identifier + ":" + index);
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyName(ArmorStand stand, String text) {
        try {
            Component name = LegacyComponentSerializer.legacySection().deserialize(text);
            stand.customName(name);
        } catch (Throwable t) {
            try {
                // Fallback to a plain text Component if parsing failed
                stand.customName(Component.text(text));
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyEmptyName(ArmorStand stand) {
        try {
            stand.customName(Component.space());
        } catch (Throwable t) {
            try {
                stand.customName(Component.space());
            } catch (Throwable ignored) {
            }
        }
    }
}
