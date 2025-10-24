package com.pikaronga.parkour.session;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.config.MessageManager;
import com.pikaronga.parkour.course.ParkourCourse;
import com.pikaronga.parkour.util.TimeUtil;
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
        Location startTeleport = course.getStartTeleport();
        if (startTeleport == null && course.getStartPlate() != null) {
            startTeleport = course.getStartPlate().clone().add(0.5, 0, 0.5);
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
                    player.teleport(startTeleport);
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
                player.teleport(startTeleport);
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
        Location completionSpawn = session.getCourse().getFinishTeleport();
        if (teleportToFinish && completionSpawn != null) {
            session.getPlayer().teleport(completionSpawn);
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
            player.sendMessage(messageManager.getMessage("test-completed", "&eTest completed in &f{time}&e.", java.util.Map.of("time", com.pikaronga.parkour.util.TimeUtil.formatDuration(durationNanos))));
        } else {
            session.getCourse().addTime(player.getUniqueId(), durationNanos);
            try { plugin.getCacheManager().markDirty(session.getCourse()); } catch (Throwable ignored) {}
        }
        if (session.getCourse().getFinishTeleport() != null) {
            player.teleport(session.getCourse().getFinishTeleport());
        }
        if (!session.isTestMode()) {
            player.sendMessage(messageManager.getMessage("completed-parkour", "&aParkour completed in &e{time}&a!", java.util.Map.of("time", TimeUtil.formatDuration(durationNanos))));
            plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses());
            plugin.getHologramManager().updateHolograms(session.getCourse());
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
        Location start = session.getCourse().getStartTeleport();
        if (start == null) {
            player.sendMessage(messageManager.getMessage("start-not-configured", "&cStart location is not configured."));
            return;
        }
        session.markIgnoreNextStartPlate();
        player.teleport(start);
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
        Location checkpoint = session.getLastCheckpoint();
        if (checkpoint == null) {
            player.sendMessage(messageManager.getMessage("no-checkpoint", "&cNo checkpoint available."));
            return;
        }
        player.teleport(checkpoint);
        player.sendMessage(messageManager.getMessage("teleported-checkpoint", "&aTeleported to your latest checkpoint."));
    }

    public void endAllSessions() {
        for (ParkourSession session : sessions.values()) {
            session.restoreInventory();
        }
        sessions.clear();
    }
}
