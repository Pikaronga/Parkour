package com.pikaronga.parkour.hologram;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.config.HologramTextProvider;
import com.pikaronga.parkour.course.ParkourCourse;
import com.pikaronga.parkour.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PersonalBestHologram {

    private final ParkourPlugin plugin;
    private final ParkourCourse course;
    private final Location baseLocation;
    private ArmorStand headerStand;
    private final Map<UUID, ArmorStand> personalLines = new HashMap<>();
    private final HologramTextProvider textProvider;

    public PersonalBestHologram(ParkourPlugin plugin, ParkourCourse course, Location baseLocation, HologramTextProvider textProvider) {
        this.plugin = plugin;
        this.course = course;
        this.baseLocation = baseLocation.clone();
        this.textProvider = textProvider;
        spawnHeader();
        Bukkit.getOnlinePlayers().forEach(this::prepareForPlayer);
    }

    private void spawnHeader() {
        World world = baseLocation.getWorld();
        if (world == null) {
            return;
        }
        try { baseLocation.getChunk().load(); } catch (Throwable ignored) {}
        headerStand = (ArmorStand) world.spawnEntity(baseLocation, EntityType.ARMOR_STAND);
        configureStand(headerStand, textProvider.formatBestHeader(course.getName()));
    }

    private ArmorStand spawnPersonalLine() {
        World world = baseLocation.getWorld();
        if (world == null) {
            return null;
        }
        try { baseLocation.getChunk().load(); } catch (Throwable ignored) {}
        ArmorStand stand = (ArmorStand) world.spawnEntity(baseLocation.clone().add(0, -0.3, 0), EntityType.ARMOR_STAND);
        configureStand(stand, textProvider.formatBestEmpty(course.getName()));
        Bukkit.getOnlinePlayers().forEach(player -> player.hideEntity(plugin, stand));
        return stand;
    }

    private void configureStand(ArmorStand stand, String name) {
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setInvisible(true);
        stand.setCustomNameVisible(true);
        try {
            Component comp = LegacyComponentSerializer.legacySection().deserialize(name);
            stand.customName(comp);
        } catch (Throwable t) {
            try { stand.setCustomName(name); } catch (Throwable ignored) {}
        }
        stand.setBasePlate(false);
        stand.setSmall(true);
        stand.setPersistent(true);
        stand.setInvulnerable(true);
        stand.setSilent(true);
        try { stand.addScoreboardTag("parkour_holo"); } catch (Throwable ignored) {}
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
}

