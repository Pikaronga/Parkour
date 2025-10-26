package com.pikaronga.parkour.session;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.config.MessageManager;
import com.pikaronga.parkour.course.ParkourCourse;
import com.pikaronga.parkour.util.TimeUtil;
import org.bukkit.Bukkit;
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
        Location startTeleport = resolveStartTeleport(course);
        if (startTeleport == null) {
            player.sendMessage(messageManager.getMessage("start-not-configured", "&cStart location is not configured."));
            plugin.getLogger().severe("Cannot start parkour '" + course.getName() + "': start location is not configured.");
            return null;
        }
        if (!isLocationReady(startTeleport)) {
            notifyWorldMissing(player, course, "start parkour session");
            return null;
        }
        // Ensure any previous per-player worldborder is cleared when entering a parkour
        try { player.setWorldBorder(null); } catch (Throwable ignored) {}
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
                    teleportSync(player, startTeleport);
                }
                existing.setLastCheckpoint(startTeleport.clone());
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
                teleportSync(player, startTeleport);
            }
            session.setLastCheckpoint(startTeleport.clone());
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

    // Overload allowing callers to choose whether to teleport to course finish
    public void endSession(Player player, boolean completed, boolean teleportToFinish) {
        ParkourSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            try { plugin.getCacheManager().stopUsing(session.getCourse()); } catch (Throwable ignored) {}
            endSession(session, completed, teleportToFinish);
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
            sendRatePrompt(session.getPlayer(), session.getCourse(), TimeUtil.formatDuration(durationNanos));
        } else {
            session.getPlayer().sendMessage(messageManager.getMessage("left-parkour", "&cYou have left the parkour."));
        }
        Location completionSpawn = session.getCourse().getFinishTeleport();
        if (teleportToFinish && completionSpawn != null) {
            if (isLocationReady(completionSpawn)) {
                teleportSync(session.getPlayer(), completionSpawn);
            } else {
                notifyWorldMissing(session.getPlayer(), session.getCourse(), "teleport to parkour finish");
            }
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
            try { plugin.getCacheManager().recordTime(session.getCourse(), player.getUniqueId(), durationNanos); } catch (Throwable ignored) {}
        }
        if (session.getCourse().getFinishTeleport() != null) {
            Location finish = session.getCourse().getFinishTeleport();
            if (isLocationReady(finish)) {
                teleportSync(player, finish);
            } else {
                notifyWorldMissing(player, session.getCourse(), "teleport to parkour finish");
            }
        }
        if (!session.isTestMode()) {
            player.sendMessage(messageManager.getMessage("completed-parkour", "&aParkour completed in &e{time}&a!", java.util.Map.of("time", TimeUtil.formatDuration(durationNanos))));
            try { plugin.getHologramManager().updateHolograms(session.getCourse()); } catch (Throwable ignored) {}
            sendRatePrompt(player, session.getCourse(), TimeUtil.formatDuration(durationNanos));
            // Increment run counters (in-memory immediately; persist asynchronously)
            try {
                session.getCourse().incrementRunCount(player.getUniqueId());
            } catch (Throwable ignored) {}
            try {
                plugin.getStorage().recordRunAsync(session.getCourse(), player.getUniqueId(), durationNanos);
            } catch (Throwable ignored) {}
        }
        try { plugin.getCacheManager().stopUsing(session.getCourse()); } catch (Throwable ignored) {}
        return durationNanos;
    }

    private void sendRatePrompt(Player player, ParkourCourse course, String timeStr) {
        try {
            if (course == null || !course.isPublished()) return;
            if (!player.hasPermission("parkour.player.rate")) return;
            if (course.getCreators().contains(player.getUniqueId())) return;
            // Do not prompt if the player has already rated this course (looks or difficulty)
            try {
                java.util.UUID uid = player.getUniqueId();
                boolean rated = (course.getLookRatings() != null && course.getLookRatings().containsKey(uid))
                        || (course.getDifficultyRatings() != null && course.getDifficultyRatings().containsKey(uid));
                if (rated) return;
            } catch (Throwable ignored) {}
            String prefix = messageManager.getMessage("completed-congrats", "&aCongrats on completing &f{course}&a in &e{time}&a! &7Enjoyed the parkour? ", java.util.Map.of("course", course.getName(), "time", timeStr));
            String clickable = messageManager.getMessage("completed-congrats-rate", "&a[Rate it here]", java.util.Map.of());
            net.md_5.bungee.api.chat.TextComponent pre = new net.md_5.bungee.api.chat.TextComponent(org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix));
            net.md_5.bungee.api.chat.TextComponent btn = new net.md_5.bungee.api.chat.TextComponent(org.bukkit.ChatColor.translateAlternateColorCodes('&', clickable));
            btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/parkour rategui " + course.getName()));
            btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.ComponentBuilder(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&7Click to open rating")) .create()));
            player.spigot().sendMessage(pre, btn);
        } catch (Throwable ignored) {}
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
        Location start = resolveStartTeleport(session.getCourse());
        if (start == null) {
            player.sendMessage(messageManager.getMessage("start-not-configured", "&cStart location is not configured."));
            return;
        }
        if (!isLocationReady(start)) {
            notifyWorldMissing(player, session.getCourse(), "return to parkour start");
            return;
        }
        session.markIgnoreNextStartPlate();
        teleportSync(player, start);
        session.setLastCheckpoint(start.clone());
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
        if (!isLocationReady(checkpoint)) {
            notifyWorldMissing(player, session.getCourse(), "teleport to checkpoint");
            return;
        }
        teleportSync(player, checkpoint);
        player.sendMessage(messageManager.getMessage("teleported-checkpoint", "&aTeleported to your latest checkpoint."));
    }

    public void endAllSessions() {
        for (ParkourSession session : sessions.values()) {
            session.restoreInventory();
        }
        sessions.clear();
    }

    private Location resolveStartTeleport(ParkourCourse course) {
        Location start = course.getStartTeleport();
        if (start != null) {
            return start.clone();
        }
        Location plate = course.getStartPlate();
        if (plate != null) {
            return plate.clone().add(0.5, 0, 0.5);
        }
        return null;
    }

    private boolean isLocationReady(Location location) {
        return location != null && location.getWorld() != null;
    }

    private void notifyWorldMissing(Player player, ParkourCourse course, String action) {
        player.sendMessage(messageManager.getMessage("world-not-loaded", "&cThat parkour's world is not currently loaded."));
        String courseName = course != null ? course.getName() : "unknown";
        plugin.getLogger().severe("Cannot " + action + " for parkour '" + courseName + "': target world is not loaded.");
    }

    private void teleportSync(Player player, Location destination) {
        Location target = destination.clone();
        Bukkit.getScheduler().runTask(plugin, () -> player.teleport(target));
    }
}
