package com.pikaronga.parkour.hologram;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.List;

public class Hologram {

    private final Location baseLocation;
    private final List<ArmorStand> armorStands = new ArrayList<>();

    public Hologram(Location baseLocation) {
        this.baseLocation = baseLocation.clone();
    }

    public void spawn(List<String> lines) {
        despawn();
        World world = baseLocation.getWorld();
        if (world == null) {
            return;
        }
        try {
            baseLocation.getChunk().load();
        } catch (Throwable ignored) {}
        Location current = baseLocation.clone();
        for (String line : lines) {
            ArmorStand stand = (ArmorStand) world.spawnEntity(current, EntityType.ARMOR_STAND);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvisible(true);
            stand.setCustomNameVisible(true);
            try {
                Component name = LegacyComponentSerializer.legacySection().deserialize(line);
                stand.customName(name);
            } catch (Throwable t) {
                // Fallback for older APIs
                try { stand.setCustomName(line); } catch (Throwable ignored) {}
            }
            stand.setBasePlate(false);
            stand.setSmall(true);
            stand.setPersistent(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            try { stand.addScoreboardTag("parkour_holo"); } catch (Throwable ignored) {}
            armorStands.add(stand);
            current = current.clone().add(0, -0.3, 0);
        }
    }

    public void update(List<String> lines) {
        if (armorStands.isEmpty()) {
            spawn(lines);
            return;
        }
        for (int i = 0; i < armorStands.size(); i++) {
            ArmorStand stand = armorStands.get(i);
            if (i < lines.size()) {
                try {
                    Component name = LegacyComponentSerializer.legacySection().deserialize(lines.get(i));
                    stand.customName(name);
                } catch (Throwable t) {
                    try { stand.setCustomName(lines.get(i)); } catch (Throwable ignored) {}
                }
            } else {
                try { stand.customName(Component.space()); } catch (Throwable t) { try { stand.setCustomName(" "); } catch (Throwable ignored) {} }
            }
        }
        if (lines.size() > armorStands.size()) {
            Location current = armorStands.get(armorStands.size() - 1).getLocation().clone().add(0, -0.3, 0);
            List<String> extra = lines.subList(armorStands.size(), lines.size());
            for (String line : extra) {
                World world = baseLocation.getWorld();
                if (world == null) { break; }
                ArmorStand stand = (ArmorStand) world.spawnEntity(current, EntityType.ARMOR_STAND);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setInvisible(true);
                stand.setCustomNameVisible(true);
                try {
                    Component name = LegacyComponentSerializer.legacySection().deserialize(line);
                    stand.customName(name);
                } catch (Throwable t) {
                    try { stand.setCustomName(line); } catch (Throwable ignored) {}
                }
                stand.setBasePlate(false);
                stand.setSmall(true);
                stand.setPersistent(true);
                stand.setInvulnerable(true);
                stand.setSilent(true);
                try { stand.addScoreboardTag("parkour_holo"); } catch (Throwable ignored) {}
                armorStands.add(stand);
                current = current.clone().add(0, -0.3, 0);
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
}
