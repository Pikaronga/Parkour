package com.pikaronga.parkour.hologram;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

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
        Location current = baseLocation.clone();
        for (String line : lines) {
            ArmorStand stand = (ArmorStand) world.spawnEntity(current, EntityType.ARMOR_STAND);
            stand.setGravity(false);
            stand.setMarker(true);
            stand.setInvisible(true);
            stand.setCustomNameVisible(true);
            stand.setCustomName(line);
            stand.setBasePlate(false);
            stand.setSmall(true);
            stand.setPersistent(false);
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
                stand.setCustomName(lines.get(i));
            } else {
                stand.setCustomName(" ");
            }
        }
        if (lines.size() > armorStands.size()) {
            Location current = armorStands.get(armorStands.size() - 1).getLocation().clone().add(0, -0.3, 0);
            List<String> extra = lines.subList(armorStands.size(), lines.size());
            for (String line : extra) {
                ArmorStand stand = (ArmorStand) baseLocation.getWorld().spawnEntity(current, EntityType.ARMOR_STAND);
                stand.setGravity(false);
                stand.setMarker(true);
                stand.setInvisible(true);
                stand.setCustomNameVisible(true);
                stand.setCustomName(line);
                stand.setBasePlate(false);
                stand.setSmall(true);
                stand.setPersistent(false);
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
