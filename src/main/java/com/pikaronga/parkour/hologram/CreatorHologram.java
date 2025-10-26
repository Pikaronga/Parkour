package com.pikaronga.parkour.hologram;

import com.pikaronga.parkour.config.HologramTextProvider;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CreatorHologram {
    private final ParkourCourse course;
    private final Hologram hologram;
    private final HologramTextProvider textProvider;
    private final org.bukkit.plugin.Plugin plugin;

    public CreatorHologram(ParkourCourse course, Location location, HologramTextProvider textProvider, NamespacedKey hologramKey, org.bukkit.plugin.Plugin plugin) {
        this.course = course;
        this.textProvider = textProvider;
        this.plugin = plugin;
        this.hologram = new Hologram(location, hologramKey, identifier(course), plugin);
        List<String> lines = new ArrayList<>();
        lines.add(textProvider.formatCreatorsHeader(course.getName()));
        if (course.getCreators().isEmpty()) {
            lines.add(textProvider.formatCreatorEntry("Unknown", course.getName()));
        } else {
            for (UUID id : course.getCreators()) {
                String name = Bukkit.getOfflinePlayer(id).getName();
                if (name == null || name.isBlank()) {
                    name = id.toString().substring(0, 8);
                }
                lines.add(textProvider.formatCreatorEntry(name, course.getName()));
            }
        }
        this.hologram.spawn(lines);
    }

    public void update() {
        List<String> lines = new ArrayList<>();
        lines.add(textProvider.formatCreatorsHeader(course.getName()));
        if (course.getCreators().isEmpty()) {
            lines.add(textProvider.formatCreatorEntry("Unknown", course.getName()));
        } else {
            for (UUID id : course.getCreators()) {
                String name = Bukkit.getOfflinePlayer(id).getName();
                if (name == null || name.isBlank()) {
                    name = id.toString().substring(0, 8);
                }
                lines.add(textProvider.formatCreatorEntry(name, course.getName()));
            }
        }
        hologram.update(lines);
    }

    public void destroy() {
        hologram.despawn();
    }

    private String identifier(ParkourCourse course) {
        return "creator:" + course.getName().toLowerCase(Locale.ROOT);
    }

    public Location getBaseLocation() {
        return hologram.getBaseLocation();
    }

    public List<UUID> getEntityIds() {
        return hologram.getArmorStandEntityIds();
    }
}
