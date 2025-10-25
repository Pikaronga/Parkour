package com.pikaronga.parkour.session;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.config.MessageManager;
import com.pikaronga.parkour.course.ParkourCourse;
import com.pikaronga.parkour.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SessionManager {

    private static final String WORLD_NOT_LOADED_MESSAGE = ChatColor.RED + "That parkour's world isn't currently loaded.";

    private final ParkourPlugin plugin;
    private final Map<UUID, ParkourSession> sessions = new HashMap<>();
    private final NamespacedKey actionKey;
    private final MessageManager messageManager;

    public SessionManager(ParkourPlugin plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "parkour-action");
        this.messageManager = messageManager;
    }

    public ParkourSession startSession(Player player, ParkourCourse course) {
        return startSession(player, course, true);
    }

    public ParkourSession startSession(Player player, ParkourCourse course, boolean teleportToStart) {
        try { plugin.getCacheManager().startUsing(course); } catch (Throwable ignored) {}
        ParkourSession existing = sessions.get(player.getUniqueId());
        Location startTeleport = cloneLocation(course.getStartTeleport());
        if (startTeleport == null && course.getStartPlate() != null) {
            Location startPlate = course.getStartPlate();
            if (startPlate.getWorld() != null) {
                startTeleport = startPlate.clone().add(0.5, 0, 0.5);
            } else {
                plugin.getLogger().warning(String.format(
                        "Start plate for parkour '%s' has no loaded world (location: %s).",
                        course.getName(),
                        formatLocation(startPlate)));
            }
        }
        if (startTeleport != null && !ensureWorldLoaded(startTeleport, course, "start teleport", player, true)) {
            return null;
        }
        if (existing != null && existing.getCourse().equals(course)) {
            existing.restoreInventory();
            existing.captureInventory();
            existing.clearEffects();
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setGameMode(org.bukkit.GameMode.ADVENTURE);
            giveParkourItems(player);
            if (startTeleport != null) {
                if (teleportToStart) {
                    teleportPlayer(player, startTeleport, course, "start teleport", false);
                }
                existing.setLastCheckpoint(startTeleport);
            }
            existing.resetTimer();
            player.sendMessage(messageManager.getMessage("restart-parkour", "&aRestarted the parkour and reset your timer."));
            return existing;
        }
        if (existing != null) {
            sessions.remove(player.getUniqueId());
            endSession(existing, false, teleportToStart);
        }
        ParkourSession session = new ParkourSession(player, course, startTeleport);
        session.captureInventory();
        session.clearEffects();
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        giveParkourItems(player);
        if (startTeleport != null) {
            if (teleportToStart) {
                teleportPlayer(player, startTeleport, course, "start teleport", false);
            }
            session.setLastCheckpoint(startTeleport);
        }
        sessions.put(player.getUniqueId(), session);
        player.sendMessage(messageManager.getMessage("started-parkour", "&aStarted parkour &f{course}&a! Good luck.", java.util.Map.of("course", course.getName())));
        return session;
    }

    public ParkourSession startSessionTest(Player player, ParkourCourse course, boolean teleportToStart) {
        ParkourSession s = startSession(player, course, teleportToStart);
        if (s != null) {
            s.setTestMode(true);
            player.sendMessage(messageManager.getMessage("started-parkour", "&aStarted parkour &f{course}&a! Good luck.", java.util.Map.of("course", course.getName())));
        }
        return s;
    }

    private void giveParkourItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(0);
        player.getInventory().setItem(0, createItem(
                Material.SLIME_BALL,
                messageManager.getItemName("restart", "&aRestart Parkour"),
                messageManager.getItemLore("restart", "&7Return to start and reset timer"),
                "restart"));
        player.getInventory().setItem(1, createItem(
                Material.ENDER_PEARL,
                messageManager.getItemName("checkpoint", "&bLast Checkpoint"),
                messageManager.getItemLore("checkpoint", "&7Teleport back to your latest checkpoint"),
                "checkpoint"));
        player.getInventory().setItem(8, createItem(
                Material.RED_BED,
                messageManager.getItemName("leave", "&cLeave Parkour"),
                messageManager.getItemLore("leave", "&7Exit parkour mode"),
                "leave"));
    }

    private ItemStack createItem(Material material, String name, String lore, String action) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.List.of(lore));
            meta.setUnbreakable(true);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public NamespacedKey getActionKey() {
        return actionKey;
    }

    public void endSession(Player player, boolean completed) {
        ParkourSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            try { plugin.getCacheManager().stopUsing(session.getCourse()); } catch (Throwable ignored) {}
            endSession(session, completed);
        }
    }

    private void endSession(ParkourSession session, boolean completed) {
        endSession(session, completed, true);
    }

    private void endSession(ParkourSession session, boolean completed, boolean teleportToFinish) {
        session.restoreInventory();
        if (completed) {
            long durationNanos = System.nanoTime() - session.getStartTimeNanos();
            session.getPlayer().sendMessage(messageManager.getMessage("completed-parkour", "&aParkour completed in &e{time}&a!", java.util.Map.of("time", TimeUtil.formatDuration(durationNanos))));
        } else {
            session.getPlayer().sendMessage(messageManager.getMessage("left-parkour", "&cYou have left the parkour."));
        }
        Location completionSpawn = cloneLocation(session.getCourse().getFinishTeleport());
        if (teleportToFinish && completionSpawn != null) {
            teleportPlayer(session.getPlayer(), completionSpawn, session.getCourse(), "finish teleport", true);
        }
    }

    public long completeSession(Player player) {
        ParkourSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return -1L;
        }
        session.restoreInventory();
        long durationNanos = System.nanoTime() - session.getStartTimeNanos();
        if (session.isTestMode()) {
            // Test sessions do not update leaderboards or holograms
            player.sendMessage(messageManager.getMessage("test-completed", "&eTest completed in &f{time}&e.", java.util.Map.of("time", TimeUtil.formatDuration(durationNanos))));
        } else {
            session.getCourse().addTime(player.getUniqueId(), durationNanos);
            try { plugin.getCacheManager().markDirty(session.getCourse()); } catch (Throwable ignored) {}
        }
        Location finishTeleport = cloneLocation(session.getCourse().getFinishTeleport());
        if (finishTeleport != null) {
            teleportPlayer(player, finishTeleport, session.getCourse(), "finish teleport", true);
        }
        if (!session.isTestMode()) {
            player.sendMessage(messageManager.getMessage("completed-parkour", "&aParkour completed in &e{time}&a!", java.util.Map.of("time", TimeUtil.formatDuration(durationNanos))));
            plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses());
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getHologramManager().updateHolograms(session.getCourse()));
        }
        try { plugin.getCacheManager().stopUsing(session.getCourse()); } catch (Throwable ignored) {}
        return durationNanos;
    }

    public ParkourSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void resetToStart(Player player) {
        ParkourSession session = getSession(player);
        if (session == null) {
            player.sendMessage(messageManager.getMessage("not-in-parkour", "&cYou are not in a parkour."));
            return;
        }
        Location start = cloneLocation(session.getCourse().getStartTeleport());
        if (start == null) {
            player.sendMessage(messageManager.getMessage("start-not-configured", "&cStart location is not configured."));
            return;
        }
        if (!ensureWorldLoaded(start, session.getCourse(), "start teleport", player, true)) {
            return;
        }
        session.markIgnoreNextStartPlate();
        teleportPlayer(player, start, session.getCourse(), "start teleport", false);
        session.setLastCheckpoint(start);
        session.resetTimer();
        player.sendMessage(messageManager.getMessage("restart-parkour", "&aRestarted the parkour and reset your timer."));
    }

    public void teleportToCheckpoint(Player player) {
        ParkourSession session = getSession(player);
        if (session == null) {
            player.sendMessage(messageManager.getMessage("not-in-parkour", "&cYou are not in a parkour."));
            return;
        }
        Location checkpoint = cloneLocation(session.getLastCheckpoint());
        if (checkpoint == null) {
            player.sendMessage(messageManager.getMessage("no-checkpoint", "&cNo checkpoint available."));
            return;
        }
        if (!ensureWorldLoaded(checkpoint, session.getCourse(), "checkpoint", player, true)) {
            return;
        }
        teleportPlayer(player, checkpoint, session.getCourse(), "checkpoint", false);
        player.sendMessage(messageManager.getMessage("teleported-checkpoint", "&aTeleported to your latest checkpoint."));
    }

    public void endAllSessions() {
        for (ParkourSession session : sessions.values()) {
            session.restoreInventory();
        }
        sessions.clear();
    }

    private Location cloneLocation(Location location) {
        return location != null ? location.clone() : null;
    }

    private boolean ensureWorldLoaded(Location location, ParkourCourse course, String context, Player player, boolean notifyPlayer) {
        if (location == null) {
            return false;
        }
        if (location.getWorld() != null) {
            return true;
        }
        String courseName = course != null ? course.getName() : "<unknown>";
        plugin.getLogger().severe(String.format(
                "Cannot use %s for parkour '%s': world is null (location: %s).",
                context,
                courseName,
                formatLocation(location)));
        if (notifyPlayer && player != null) {
            player.sendMessage(WORLD_NOT_LOADED_MESSAGE);
        }
        return false;
    }

    private void teleportPlayer(Player player, Location location, ParkourCourse course, String context, boolean notifyPlayer) {
        if (!ensureWorldLoaded(location, course, context, player, notifyPlayer)) {
            return;
        }
        Location target = location.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success = player.teleport(target);
            if (!success) {
                plugin.getLogger().warning(String.format(
                        "Teleport for %s in parkour '%s' (%s) failed.",
                        player.getName(),
                        course != null ? course.getName() : "<unknown>",
                        context));
            }
        });
    }

    private String formatLocation(Location location) {
        if (location == null) {
            return "unknown";
        }
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "null";
        return String.format("%s:(%.2f, %.2f, %.2f)", worldName, location.getX(), location.getY(), location.getZ());
    }
}
