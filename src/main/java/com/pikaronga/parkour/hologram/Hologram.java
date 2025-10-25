package com.pikaronga.parkour.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public List<UUID> getEntityIds() {
        return armorStands.stream()
                .map(ArmorStand::getUniqueId)
                .collect(Collectors.toUnmodifiableList());
    }

    private ArmorStand spawnLine(World world, Location location, String line) {
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
    }

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
                stand.setCustomName(text);
            } catch (Throwable ignored) {
            }
        }
    }

    private void applyEmptyName(ArmorStand stand) {
        try {
            stand.customName(Component.space());
        } catch (Throwable t) {
            try {
                stand.setCustomName(" ");
            } catch (Throwable ignored) {
            }
        }
    }
}
