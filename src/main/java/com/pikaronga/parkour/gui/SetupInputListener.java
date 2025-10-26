package com.pikaronga.parkour.gui;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SetupInputListener implements Listener {
    private static final Map<UUID, String> awaitingMaxFall = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> awaitingCreateName = new ConcurrentHashMap<>();
    private static final Map<UUID, String> awaitingRename = new ConcurrentHashMap<>();
    private final ParkourPlugin plugin;

    public SetupInputListener(ParkourPlugin plugin) {
        this.plugin = plugin;
    }

    public static void requestMaxFall(ParkourPlugin plugin, Player player, ParkourCourse course) {
        if (player == null || course == null) return;
        awaitingMaxFall.put(player.getUniqueId(), course.getName());
        player.sendMessage(plugin.getMessageManager().getMessage("setup-maxfall-prompt", "&eType a number in chat for Max Fall Distance, or &c'cancel'&e to abort."));
    }

    public static void requestCreate(ParkourPlugin plugin, Player player) {
        if (player == null) return;
        awaitingCreateName.put(player.getUniqueId(), Boolean.TRUE);
        player.sendMessage(plugin.getMessageManager().getMessage("create-prompt", "&eType a name in chat for your new parkour, or &c'cancel'&e to abort."));
    }

    public static void requestRename(ParkourPlugin plugin, Player player, ParkourCourse course) {
        if (player == null || course == null) return;
        awaitingRename.put(player.getUniqueId(), course.getName());
        player.sendMessage(plugin.getMessageManager().getMessage("rename-prompt", "&eType a new name in chat for this parkour, or &c'cancel'&e to abort."));
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        boolean waitingCreate = awaitingCreateName.containsKey(id);
        String courseName = awaitingMaxFall.get(id);
        String renameCourse = awaitingRename.get(id);
        if (!waitingCreate && courseName == null && renameCourse == null) return;
        event.setCancelled(true);
        String msg = event.getMessage();
        if (msg == null) msg = "";
        String lower = msg.trim().toLowerCase();
        if (lower.equals("cancel") || lower.equals("stop") || lower.equals("exit")) {
            awaitingMaxFall.remove(id);
            awaitingCreateName.remove(id);
            awaitingRename.remove(id);
            event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("setup-maxfall-cancel", "&eCancelled."));
            return;
        }
        if (waitingCreate) {
            String name = msg.trim();
            awaitingCreateName.remove(id);
            // Defer to existing command handling on main thread
            Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().performCommand("parkour create " + name));
            return;
        }
        if (renameCourse != null) {
            String newName = msg.trim();
            awaitingRename.remove(id);
            Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().performCommand("parkour rename " + renameCourse + " " + newName));
            return;
        }
        // Handle max fall
        double value;
        try { value = Double.parseDouble(msg.trim()); }
        catch (NumberFormatException ex) {
            event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("setup-maxfall-invalid", "&cPlease type a valid non-negative number."));
            return;
        }
        if (value < 0) {
            event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("setup-maxfall-invalid", "&cPlease type a valid non-negative number."));
            return;
        }
        ParkourCourse course = plugin.getParkourManager().getCourse(courseName);
        if (course == null) {
            awaitingMaxFall.remove(id);
            event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("rate-not-found", "&cParkour not found or not published."));
            return;
        }
        course.setMaxFallDistance(value);
        try { plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses()); } catch (Throwable ignored) {}
        awaitingMaxFall.remove(id);
        event.getPlayer().sendMessage(plugin.getMessageManager().getMessage("setup-maxfall-success", "&aSet max fall distance for &f{course}&a to &f{distance}", java.util.Map.of("course", course.getName(), "distance", Double.toString(value))));
        // Re-open setup GUI next tick
        Bukkit.getScheduler().runTask(plugin, () -> com.pikaronga.parkour.gui.SetupGUI.open(event.getPlayer(), plugin, course));
    }
}
