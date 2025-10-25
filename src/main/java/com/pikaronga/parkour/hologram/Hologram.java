package com.pikaronga.parkour.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
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

    public Hologram(Location baseLocation, NamespacedKey dataKey, String identifier) {
        this.baseLocation = baseLocation.clone();
        this.dataKey = dataKey;
        this.identifier = identifier;
    }

    

    public void spawn(List<String> lines) {
        despawn();
        World world = baseLocation.getWorld();
        if (world == null) {
            return;
        }
        try {
            baseLocation.getChunk().load();
        } catch (Throwable ignored) {
        }
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
        try {
            // Ensure chunk is loaded before spawning
            try {
                if (!location.getChunk().isLoaded()) location.getChunk().load();
            } catch (Throwable ignored) {}

            ArmorStand stand = world.spawn(location, ArmorStand.class, a -> {
                // body invisible, name visible
                a.setInvisible(true);
                // Do NOT set marker=true here; some server builds hide nameplates for marker armor stands.
                a.setSmall(true);
                a.setGravity(false);
                a.setPersistent(true);
                a.setBasePlate(false);
                a.setInvulnerable(true);
                a.setSilent(true);
                // We'll explicitly set name visible after applying the Component name to ensure clients render it.
            });

            if (stand == null) return null;

            // Apply the name using existing helper (handles Adventure and legacy fallback)
            applyName(stand, line);
            // Ensure the custom name is visible (set after applying the name to force client update)
            try { stand.setCustomNameVisible(true); } catch (Throwable ignored) {}

            // Debug logging: world, coords, name visibility, invisibility, custom name contents
            try {
                String worldName = (world != null && world.getName() != null) ? world.getName() : "unknown";
                String coords = String.format("[%.2f, %.2f, %.2f]", location.getX(), location.getY(), location.getZ());
                boolean nameVisible = stand.isCustomNameVisible();
                boolean invisible = stand.isInvisible();
                String cname = "null";
                try {
                    if (stand.customName() != null) {
                        cname = LegacyComponentSerializer.legacySection().serialize(stand.customName());
                    }
                } catch (Throwable ignored) {}
                Bukkit.getLogger().info("[Parkour] Spawned hologram '" + identifier + "' at " + worldName + coords + " (nameVisible=" + nameVisible + ", invisible=" + invisible + ", name=" + cname + ")");
            } catch (Throwable ignored) {}

            // No ProtocolLib resend here; rely on Bukkit/Paper entity metadata propagation.

            return stand;
        } catch (Throwable t) {
            try {
                // fallback: spawning via entity type
                ArmorStand stand = (ArmorStand) world.spawnEntity(location, EntityType.ARMOR_STAND);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setInvisible(true);
                stand.setCustomNameVisible(true);
                applyName(stand, line);
                stand.setBasePlate(false);
                stand.setSmall(true);
                stand.setPersistent(true);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                return stand;
            } catch (Throwable ex) {
                return null;
            }
        }
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
