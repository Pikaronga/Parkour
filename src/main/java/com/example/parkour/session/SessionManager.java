package com.example.parkour.session;

import com.example.parkour.ParkourPlugin;
import com.example.parkour.course.ParkourCourse;
import com.example.parkour.util.TimeUtil;
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

    public SessionManager(ParkourPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "parkour-action");
    }

    public ParkourSession startSession(Player player, ParkourCourse course) {
        ParkourSession existing = sessions.remove(player.getUniqueId());
        if (existing != null) {
            endSession(existing, false);
        }
        Location startTeleport = course.getStartTeleport();
        if (startTeleport == null && course.getStartPlate() != null) {
            startTeleport = course.getStartPlate().clone().add(0.5, 0, 0.5);
        }
        ParkourSession session = new ParkourSession(player, course, startTeleport);
        session.captureInventory();
        session.clearEffects();
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        giveParkourItems(player);
        if (startTeleport != null) {
            player.teleport(startTeleport);
            session.setLastCheckpoint(startTeleport);
        }
        sessions.put(player.getUniqueId(), session);
        player.sendMessage("§aStarted parkour §f" + course.getName() + "§a! Good luck.");
        return session;
    }

    private void giveParkourItems(Player player) {
        player.getInventory().clear();
        player.getInventory().setHeldItemSlot(0);
        player.getInventory().setItem(0, createItem(Material.SLIME_BALL, "§aRestart Parkour", "§7Return to start and reset timer", "restart"));
        player.getInventory().setItem(1, createItem(Material.ENDER_PEARL, "§bLast Checkpoint", "§7Teleport back to your latest checkpoint", "checkpoint"));
        player.getInventory().setItem(8, createItem(Material.RED_BED, "§cLeave Parkour", "§7Exit parkour mode", "leave"));
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
            endSession(session, completed);
        }
    }

    private void endSession(ParkourSession session, boolean completed) {
        session.restoreInventory();
        if (completed) {
            long duration = System.currentTimeMillis() - session.getStartTime();
            session.getPlayer().sendMessage("§aParkour completed in §e" + TimeUtil.formatDuration(duration) + "§a!");
        } else {
            session.getPlayer().sendMessage("§cYou have left the parkour.");
        }
    }

    public long completeSession(Player player) {
        ParkourSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            return -1L;
        }
        session.restoreInventory();
        long duration = System.currentTimeMillis() - session.getStartTime();
        session.getCourse().addTime(player.getUniqueId(), duration);
        if (session.getCourse().getFinishTeleport() != null) {
            player.teleport(session.getCourse().getFinishTeleport());
        }
        player.sendMessage("§aParkour completed in §e" + TimeUtil.formatDuration(duration) + "§a!");
        plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses());
        plugin.getHologramManager().updateHolograms(session.getCourse());
        return duration;
    }

    public ParkourSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public void resetToStart(Player player) {
        ParkourSession session = getSession(player);
        if (session == null) {
            player.sendMessage("§cYou are not in a parkour.");
            return;
        }
        Location start = session.getCourse().getStartTeleport();
        if (start == null) {
            player.sendMessage("§cStart location is not configured.");
            return;
        }
        player.teleport(start);
        session.setLastCheckpoint(start);
        session.resetTimer();
        player.sendMessage("§aRestarted the parkour and reset your timer.");
    }

    public void teleportToCheckpoint(Player player) {
        ParkourSession session = getSession(player);
        if (session == null) {
            player.sendMessage("§cYou are not in a parkour.");
            return;
        }
        Location checkpoint = session.getLastCheckpoint();
        if (checkpoint == null) {
            player.sendMessage("§cNo checkpoint available.");
            return;
        }
        player.teleport(checkpoint);
        player.sendMessage("§aTeleported to your latest checkpoint.");
    }

    public void endAllSessions() {
        for (ParkourSession session : sessions.values()) {
            session.restoreInventory();
        }
        sessions.clear();
    }
}
