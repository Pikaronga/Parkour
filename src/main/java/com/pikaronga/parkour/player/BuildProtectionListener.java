package com.pikaronga.parkour.player;

import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Optional;

public class BuildProtectionListener implements Listener {
    private final PlayerParkourManager ppm;

    public BuildProtectionListener(PlayerParkourManager ppm) {
        this.ppm = ppm;
    }

    private boolean canBuild(Player player, Location location) {
        Optional<ParkourCourse> course = ppm.getCourseByLocation(location);
        return course.filter(parkourCourse -> ppm.isOwner(player, parkourCourse)).isPresent();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getWorld().equals(ppm.getWorld())) {
            Optional<ParkourCourse> in = ppm.getCourseByLocation(event.getBlockPlaced().getLocation());
            if (in.isEmpty() || !ppm.isOwner(event.getPlayer(), in.get())) {
                event.setCancelled(true);
                return;
            }
            in.get().incrementPlacedBlocks();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld().equals(ppm.getWorld())) {
            Optional<ParkourCourse> in = ppm.getCourseByLocation(event.getBlock().getLocation());
            if (in.isEmpty() || !ppm.isOwner(event.getPlayer(), in.get())) {
                event.setCancelled(true);
                return;
            }
            in.get().decrementPlacedBlocks();
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.getPlayer().getWorld().equals(ppm.getWorld())) return;
        boolean wasIn = ppm.getCourseByLocation(event.getFrom()).isPresent();
        boolean nowIn = ppm.getCourseByLocation(event.getTo()).isPresent();
        if (!wasIn && nowIn) {
            ppm.handleEnterPlot(event.getPlayer());
        } else if (wasIn && !nowIn) {
            ppm.handleExitPlot(event.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().getWorld().equals(ppm.getWorld())) return;
        java.util.Optional<com.pikaronga.parkour.course.ParkourCourse> in = ppm.getCourseByLocation(event.getPlayer().getLocation());
        in.ifPresent(course -> ppm.handleEnterPlot(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        try { event.getPlayer().setWorldBorder(null); } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (!event.getPlayer().getWorld().equals(ppm.getWorld())) return;
        boolean wasIn = ppm.getCourseByLocation(event.getFrom()).isPresent();
        boolean nowIn = ppm.getCourseByLocation(event.getTo()).isPresent();
        if (!wasIn && nowIn) {
            ppm.handleEnterPlot(event.getPlayer());
        } else if (wasIn && !nowIn) {
            ppm.handleExitPlot(event.getPlayer());
        }
    }

    // Command-level guard for WorldEdit operations
    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage();
        Player player = event.getPlayer();
        if (msg == null || msg.isBlank()) return;
        String lower = msg.toLowerCase();
        // Likely WE entrypoints
        boolean isWE = lower.startsWith("//") || lower.startsWith("/we ") || lower.equals("/we")
                || lower.startsWith("/worldedit ") || lower.equals("/worldedit")
                || lower.startsWith("/schem") || lower.startsWith("/schematic");
        if (!isWE) return;
        if (!player.getWorld().equals(ppm.getWorld())) {
            event.setCancelled(true);
            player.sendMessage(org.bukkit.ChatColor.RED + "WorldEdit is only allowed inside player-parkour plots.");
            return;
        }
        java.util.Optional<com.pikaronga.parkour.course.ParkourCourse> in = ppm.getCourseByLocation(player.getLocation());
        if (in.isEmpty() || !ppm.isOwner(player, in.get())) {
            event.setCancelled(true);
            player.sendMessage(org.bukkit.ChatColor.RED + "WorldEdit is only allowed inside your own plot.");
            return;
        }
        java.util.List<String> deny = ppm.getPlugin().getConfigManager().weDenyList();
        java.util.List<String> allow = ppm.getPlugin().getConfigManager().weAllowList();
        String[] parts = lower.trim().split("\\s+");
        String op = null;
        if (parts.length > 0) {
            if (parts[0].startsWith("//")) {
                op = parts[0].substring(2);
            } else if (parts[0].equals("/we") && parts.length > 1) {
                op = parts[1];
            } else if (parts[0].equals("/worldedit") && parts.length > 1) {
                op = parts[1];
            }
        }
        if (op != null) {
            if (!allow.isEmpty() && !allow.contains(op)) {
                event.setCancelled(true);
                player.sendMessage(org.bukkit.ChatColor.RED + "This WorldEdit operation is not allowed in plots.");
                return;
            }
            if (deny.contains(op)) {
                event.setCancelled(true);
                player.sendMessage(org.bukkit.ChatColor.RED + "This WorldEdit operation is blocked in plots.");
            }
        }
    }
}
