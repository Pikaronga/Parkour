package com.pikaronga.parkour.command;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.config.MessageManager;
import com.pikaronga.parkour.course.Checkpoint;
import com.pikaronga.parkour.course.ParkourCourse;
import com.pikaronga.parkour.hologram.HologramManager;
import com.pikaronga.parkour.util.ParkourManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ParkourCommand implements CommandExecutor, TabCompleter {

    private final ParkourPlugin plugin;
    private final ParkourManager parkourManager;
    private final HologramManager hologramManager;
    private final MessageManager messageManager;

    public ParkourCommand(ParkourPlugin plugin, ParkourManager parkourManager, HologramManager hologramManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.parkourManager = parkourManager;
        this.hologramManager = hologramManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.getMessage("only-players", "Only players can use this command."));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(messageManager.getMessage("usage", "&cUsage: /parkour <...>"));
            return true;
        }
        String sub = args[0].toLowerCase();
        boolean success = true;
        switch (sub) {
            // Admin subcommands
            case "setstart", "setend", "setspawn", "setcheckpoint", "setholotop10", "setholobest", "setmaxfall", "deletetime", "deletecourse", "renamecourse", "holocleanup", "listdbcourses" -> {
                if (!player.hasPermission("parkour.admin")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (sub.equals("listdbcourses")) {
                    plugin.getStorage().listCourseNamesAsync(names -> {
                        int count = names != null ? names.size() : 0;
                        String joined = (names == null || names.isEmpty()) ? "<none>" : String.join(", ", names);
                        player.sendMessage(messageManager.getMessage(
                                "db-course-list",
                                "&aDB courses (&f{count}&a): &f{list}",
                                java.util.Map.of("count", Integer.toString(count), "list", joined)));
                    });
                    return true;
                }
                if (sub.equals("holocleanup")) {
                    if (!player.hasPermission("parkour.admin.holocleanup")) {
                        player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                        return true;
                    }
                    if (args.length < 2) {
                        player.sendMessage(messageManager.getMessage("usage", "&cUsage: /parkour holocleanup <course|all>"));
                        return true;
                    }
                    String target = args[1];
                    int removed = 0;
                    if ("all".equalsIgnoreCase(target)) {
                        removed = plugin.getHologramManager().cleanupAllHologramEntities();
                        player.sendMessage(messageManager.getMessage("holo-cleaned-all", "&aCleaned up &f{count}&a hologram entities.", java.util.Map.of("count", Integer.toString(removed))));
                        return true;
                    }
                    ParkourCourse c = parkourManager.getCourse(target);
                    if (c != null) {
                        plugin.getHologramManager().removeCourseHolograms(c);
                        removed = plugin.getHologramManager().cleanupHologramsByCourseName(c.getName());
                        player.sendMessage(messageManager.getMessage("holo-cleaned-course", "&aCleaned holograms for &f{course}&a (&f{count}&a removed).", java.util.Map.of("course", c.getName(), "count", Integer.toString(removed))));
                    } else {
                        removed = plugin.getHologramManager().cleanupHologramsByCourseName(target);
                        player.sendMessage(messageManager.getMessage("holo-cleaned-old", "&aCleaned stray holograms for old course name &f{course}&a (&f{count}&a removed).", java.util.Map.of("course", target, "count", Integer.toString(removed))));
                    }
                    return true;
                }
                if (sub.equals("renamecourse")) {
                    if (args.length < 3) {
                        player.sendMessage(messageManager.getMessage("admin-rename-usage", "&cUsage: /parkour renamecourse <old> <new>"));
                        return true;
                    }
                    String oldName = args[1];
                    String newName = args[2];
                    ParkourCourse src = parkourManager.getCourse(oldName);
                    if (src == null) {
                        player.sendMessage(messageManager.getMessage("rate-not-found", "&cParkour not found or not published."));
                        return true;
                    }
                    if (parkourManager.getCourse(newName) != null) {
                        player.sendMessage(messageManager.getMessage("rename-exists", "&cA parkour with that name already exists."));
                        return true;
                    }
                    ParkourCourse dst = com.pikaronga.parkour.util.RenameUtil.cloneWithName(src, newName);
                    // Swap in memory and holograms
                    plugin.getHologramManager().removeCourseHolograms(src);
                    parkourManager.removeCourse(src.getName());
                    parkourManager.getCourses().put(dst.getName().toLowerCase(), dst);
                    plugin.getHologramManager().createHolograms(dst);
                    // Persist new row and times, then migrate counters and delete old row
                    try { plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses()); } catch (Throwable ignored) {}
                    try { plugin.getStorage().saveTimesAsync(dst); } catch (Throwable ignored) {}
                    plugin.getStorage().migrateCourseRunCountersAsync(oldName, newName, ok -> {
                        plugin.getStorage().deleteCourseByNameAsync(oldName, delOk -> {
                            player.sendMessage(messageManager.getMessage("admin-rename-success", "&aRenamed course &f{old}&a to &f{new}&a.", java.util.Map.of("old", oldName, "new", newName)));
                            plugin.getLogger().info("[Parkour] Admin " + player.getName() + " renamed course '" + oldName + "' -> '" + newName + "'.");
                        });
                    });
                    return true;
                }
                if (sub.equals("deletecourse")) {
                    if (args.length < 2) {
                        player.sendMessage(messageManager.getMessage("usage", "&cUsage: /parkour deletecourse <name>"));
                        return true;
                    }
                    String delName = args[1];
                    ParkourCourse existing = parkourManager.getCourse(delName);
                    if (existing == null) {
                        player.sendMessage(messageManager.getMessage("rate-not-found", "&cParkour not found or not published."));
                        return true;
                    }
                    // Remove holograms and from memory first
                    plugin.getHologramManager().removeCourseHolograms(existing);
                    parkourManager.removeCourse(existing.getName());
                    plugin.getStorage().deleteCourseByNameAsync(existing.getName(), ok -> {
                        if (ok) {
                            player.sendMessage("§aDeleted course §f" + delName + "§a and its data.");
                            plugin.getLogger().info("[Parkour] Admin " + player.getName() + " deleted course " + delName + ".");
                        } else {
                            player.sendMessage("§cFailed to delete course.");
                        }
                    });
                    return true;
                }
                if (sub.equals("deletetime")) {
                    if (!player.hasPermission("parkour.admin.deletetime")) {
                        player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                        return true;
                    }
                    if (args.length < 3) {
                        player.sendMessage(messageManager.getMessage("usage", "&cUsage: /parkour deletetime <player> <course>"));
                        return true;
                    }
                    String who = args[1];
                    String courseName = args[2];
                    ParkourCourse target = parkourManager.getCourse(courseName);
                    if (target == null) {
                        player.sendMessage(messageManager.getMessage("rate-not-found", "&cParkour not found or not published."));
                        return true;
                    }
                    java.util.UUID uid;
                    org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(who);
                    if (off != null && off.getUniqueId() != null) {
                        uid = off.getUniqueId();
                    } else {
                        try { uid = java.util.UUID.fromString(who); } catch (IllegalArgumentException ex) { uid = null; }
                    }
                    if (uid == null) {
                        player.sendMessage(messageManager.getMessage("invalid-player", "&cUnknown player."));
                        return true;
                    }
                    final ParkourCourse tCourse = target;
                    final java.util.UUID tUid = uid;
                    final org.bukkit.OfflinePlayer tOff = off;
                    final org.bukkit.entity.Player senderPlayer = player;
                    plugin.getStorage().deleteBestTimeAsync(tCourse.getName(), tUid, ok -> {
                        if (ok) {
                            // Remove from in-memory cache too
                            tCourse.getTimes().remove(tUid);
                            senderPlayer.sendMessage("§aDeleted best time for §f" + (tOff != null && tOff.getName()!=null? tOff.getName(): tUid) + "§a on §f" + tCourse.getName() + "§a.");
                            plugin.getLogger().info("[Parkour] Admin " + senderPlayer.getName() + " deleted best time for " + (tOff != null && tOff.getName()!=null? tOff.getName(): tUid) + " on " + tCourse.getName() + ".");
                            try { plugin.getHologramManager().updateHolograms(tCourse); } catch (Throwable ignored) {}
                        } else {
                            senderPlayer.sendMessage("§cFailed to delete best time (not found or error)." );
                        }
                    });
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("usage", "&cUsage: /parkour <setstart|setend|setspawn|setcheckpoint|setholotop10|setholobest|setmaxfall> <name> [value]"));
                    return true;
                }
                String name = args[1];
                ParkourCourse course = parkourManager.getOrCreate(name);
                switch (sub) {
                    case "setstart" -> handleSetStart(player, course);
                    case "setend" -> handleSetEnd(player, course);
                    case "setspawn" -> handleSetSpawn(player, course);
                    case "setcheckpoint" -> handleSetCheckpoint(player, course);
                    case "setholotop10" -> handleSetTopHolo(player, course);
                    case "setholobest" -> handleSetBestHolo(player, course);
                    case "setmaxfall" -> handleSetMaxFall(player, course, args);
                }
            }
            case "setcreatorholo" -> {
                if (!player.hasPermission("parkour.admin")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("setcreatorholo-usage", "&cUsage: /parkour setcreatorholo <name>"));
                    return true;
                }
                ParkourCourse course = parkourManager.getOrCreate(args[1]);
                plugin.getHologramManager().setCreatorHologram(course, player.getLocation().clone().add(0, 2, 0));
                player.sendMessage(messageManager.getMessage("set-creator-holo", "&aSet creators hologram for parkour &f{course}&a.", Map.of("course", course.getName())));
            }
            case "create" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.create")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (!plugin.getPlayerParkourManager().canCreateNow(player.getUniqueId())) {
                    int sec = plugin.getPlayerParkourManager().secondsUntilCreate(player.getUniqueId());
                    player.sendMessage(messageManager.getMessage("create-cooldown", "&cYou can create a new parkour in &f{seconds}&cs.", java.util.Map.of("seconds", Integer.toString(sec))));
                    return true;
                }
                String name;
                if (args.length < 2) {
                    // Auto-generate from player's name; ensure uniqueness with numeric suffix
                    String base = player.getName();
                    name = base;
                    int suffix = 1;
                    while (plugin.getParkourManager().getCourse(name) != null) {
                        name = base + suffix;
                        suffix++;
                        if (suffix > 9999) break;
                    }
                } else {
                    name = args[1];
                }
                long owned = plugin.getParkourManager().getCourses().values().stream().filter(c -> c.getCreators().contains(player.getUniqueId())).count();
                if (owned >= plugin.getConfigManager().getMaxPerPlayer()) {
                    player.sendMessage(messageManager.getMessage("create-limit", "&cYou have reached your parkour limit."));
                    return true;
                }
                if (args.length >= 2 && plugin.getParkourManager().getCourse(name) != null) {
                    player.sendMessage(messageManager.getMessage("create-exists", "&cA parkour with that name already exists."));
                    return true;
                }
                ParkourCourse created = plugin.getPlayerParkourManager().createPlayerCourse(player, name);
                if (created == null) {
                    player.sendMessage(messageManager.getMessage("create-failed", "&cFailed to create parkour."));
                    return true;
                }
                plugin.getPlayerParkourManager().markCreatedNow(player.getUniqueId());
                plugin.getPlayerParkourManager().setLastEditingCourse(player.getUniqueId(), created);
                plugin.getPlayerParkourManager().teleportToPlot(player, created);
                // Ensure creator is in Creative when entering their new plot
                try { player.setGameMode(org.bukkit.GameMode.CREATIVE); } catch (Throwable ignored) {}
                player.sendMessage(messageManager.getMessage("create-success", "&aCreated parkour &f{course}&a. You are now in your plot.", Map.of("course", created.getName())));
            }
            case "rename" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.edit")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(messageManager.getMessage("rename-usage", "&cUsage: /parkour rename <current> <new>"));
                    return true;
                }
                String current = args[1];
                String fresh = args[2];
                ParkourCourse src = parkourManager.getCourse(current);
                if (src == null) {
                    player.sendMessage(messageManager.getMessage("rate-not-found", "&cParkour not found or not published."));
                    return true;
                }
                if (!src.getCreators().contains(player.getUniqueId())) {
                    player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
                    return true;
                }
                if (src.isPublished()) {
                    player.sendMessage(messageManager.getMessage("publish-locked", "&cPublished parkours cannot be renamed or edited."));
                    return true;
                }
                if (parkourManager.getCourse(fresh) != null) {
                    player.sendMessage(messageManager.getMessage("rename-exists", "&cA parkour with that name already exists."));
                    return true;
                }
                ParkourCourse dst = com.pikaronga.parkour.util.RenameUtil.cloneWithName(src, fresh);
                plugin.getHologramManager().removeCourseHolograms(src);
                parkourManager.removeCourse(src.getName());
                parkourManager.getCourses().put(dst.getName().toLowerCase(), dst);
                plugin.getHologramManager().createHolograms(dst);
                // Persist new row and times, then migrate counters and delete old row
                try { plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses()); } catch (Throwable ignored) {}
                try { plugin.getStorage().saveTimesAsync(dst); } catch (Throwable ignored) {}
                try {
                    final org.bukkit.entity.Player senderPlayer = player;
                    plugin.getStorage().migrateCourseRunCountersAsync(current, fresh, ok -> {
                        plugin.getStorage().deleteCourseByNameAsync(current, delOk -> {
                            if (delOk != null && delOk) {
                                senderPlayer.sendMessage(messageManager.getMessage("rename-cleanup-done", "&aFinalized rename in database. Old name cleaned."));
                                plugin.getLogger().info("[Parkour] Finalized player rename cleanup: '" + current + "' -> '" + fresh + "'.");
                            } else {
                                senderPlayer.sendMessage(messageManager.getMessage("rename-cleanup-failed", "&cRename saved, but failed to cleanup old entry. Check logs."));
                                plugin.getLogger().warning("[Parkour] Failed to delete old course after rename: '" + current + "'");
                            }
                        });
                    });
                } catch (Throwable ignored) {}
                plugin.getPlayerParkourManager().setLastEditingCourse(player.getUniqueId(), dst);
                player.sendMessage(messageManager.getMessage("rename-success", "&aRenamed &f{old}&a to &f{new}", java.util.Map.of("old", current, "new", fresh)));
            }
            case "edit" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.edit")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("edit-usage", "&cUsage: /parkour edit <name>"));
                    return true;
                }
                ParkourCourse target = parkourManager.getCourse(args[1]);
                if (target == null) {
                    player.sendMessage(messageManager.getMessage("rate-not-found", "&cParkour not found or not published."));
                    return true;
                }
                if (!target.getCreators().contains(player.getUniqueId())) {
                    player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
                    return true;
                }
                if (target.isPublished()) {
                    player.sendMessage(messageManager.getMessage("publish-locked", "&cThis parkour is already published and can no longer be edited."));
                    return true;
                }
                plugin.getPlayerParkourManager().setLastEditingCourse(player.getUniqueId(), target);
                plugin.getPlayerParkourManager().teleportToPlot(player, target);
                player.sendMessage(messageManager.getMessage("edit-teleport", "&aTeleported to parkour &f{course}&a.", java.util.Map.of("course", target.getName())));
            }
            case "setup" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.edit")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("setup-usage", "&cUsage: /parkour setup <name>"));
                    return true;
                }
                ParkourCourse target = parkourManager.getCourse(args[1]);
                if (target == null) {
                    player.sendMessage(messageManager.getMessage("rate-not-found", "&cParkour not found or not published."));
                    return true;
                }
                if (!target.getCreators().contains(player.getUniqueId())) {
                    player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
                    return true;
                }
                if (target.isPublished()) {
                    player.sendMessage(messageManager.getMessage("publish-locked", "&cThis parkour is already published and can no longer be edited."));
                    return true;
                }
                com.pikaronga.parkour.gui.SetupGUI.open(player, plugin, target);
            }
            case "psetstart", "psetend", "psetspawn", "psetcheckpoint", "psettop", "psetholotop10", "psetbest", "psetholobest", "psetcreatorholo" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.edit")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("pedit-usage", "&cUsage: /parkour " + sub + " <name>"));
                    return true;
                }
                ParkourCourse editable = parkourManager.getCourse(args[1]);
                if (editable == null || !editable.getCreators().contains(player.getUniqueId())) {
                    player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
                    return true;
                }
                if (editable.isPublished()) {
                    player.sendMessage(messageManager.getMessage("publish-locked", "&cPublished parkours can no longer be edited."));
                    return true;
                }
                switch (sub) {
                    case "psetstart" -> handleSetStart(player, editable);
                    case "psetend" -> handleSetEnd(player, editable);
                    case "psetspawn" -> handleSetSpawn(player, editable);
                    case "psetcheckpoint" -> handleSetCheckpoint(player, editable);
                    case "psettop", "psetholotop10" -> handleSetTopHolo(player, editable);
                    case "psetbest", "psetholobest" -> handleSetBestHolo(player, editable);
                    case "psetcreatorholo" -> {
                        // Restrict placement to player's own parkour world and plot
                        if (!plugin.getPlayerParkourManager().isOwner(player, editable)) {
                            player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
                            return true;
                        }
                        org.bukkit.World plots = plugin.getPlayerParkourManager().getWorld();
                        org.bukkit.Location here = player.getLocation();
                        if (plots == null || here.getWorld() == null || !plots.getUID().equals(here.getWorld().getUID())) {
                            player.sendMessage(messageManager.getMessage("holo-placement-denied", "&cYou can only place holograms inside your own parkour world."));
                            return true;
                        }
                        com.pikaronga.parkour.player.PlotRegion r = editable.getPlotRegion();
                        if (r != null && !r.contains(here)) {
                            player.sendMessage(messageManager.getMessage("holo-placement-denied", "&cYou can only place holograms inside your own parkour world."));
                            return true;
                        }
                        org.bukkit.Location loc = here.clone().add(0, 2, 0);
                        plugin.getHologramManager().setCreatorHologram(editable, loc);
                        player.sendMessage(messageManager.getMessage("set-creator-holo", "&aSet creators hologram for parkour &f{course}&a.", java.util.Map.of("course", editable.getName())));
                    }
                }
            }
            case "publish" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.publish")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("publish-usage", "&cUsage: /parkour publish <name> [new-name]"));
                    return true;
                }
                ParkourCourse c = parkourManager.getCourse(args[1]);
                if (c == null || !c.getCreators().contains(player.getUniqueId())) {
                    player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
                    return true;
                }
                if (c.isPublished()) {
                    player.sendMessage(messageManager.getMessage("publish-locked", "&cThis parkour is already published."));
                    return true;
                }
                if (args.length >= 3) {
                    String newName = args[2];
                    if (parkourManager.getCourse(newName) != null) {
                        player.sendMessage(messageManager.getMessage("rename-exists", "&cA parkour with that name already exists."));
                        return true;
                    }
                    String oldName = c.getName();
                    ParkourCourse dst = com.pikaronga.parkour.util.RenameUtil.cloneWithName(c, newName);
                    plugin.getHologramManager().removeCourseHolograms(c);
                    parkourManager.removeCourse(oldName);
                    parkourManager.getCourses().put(dst.getName().toLowerCase(), dst);
                    plugin.getHologramManager().createHolograms(dst);
                    // Persist new course row and data, migrate counters, then cleanup old row in DB
                    try { plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses()); } catch (Throwable ignored) {}
                    try { plugin.getStorage().saveTimesAsync(dst); } catch (Throwable ignored) {}
                    try {
                        final org.bukkit.entity.Player senderPlayer = player;
                        plugin.getStorage().migrateCourseRunCountersAsync(oldName, newName, ok -> {
                            plugin.getStorage().deleteCourseByNameAsync(oldName, delOk -> {
                                if (delOk != null && delOk) {
                                    senderPlayer.sendMessage(messageManager.getMessage("rename-cleanup-done", "&aFinalized rename in database. Old name cleaned."));
                                    plugin.getLogger().info("[Parkour] Finalized publish-rename cleanup: '" + oldName + "' -> '" + newName + "'.");
                                } else {
                                    senderPlayer.sendMessage(messageManager.getMessage("rename-cleanup-failed", "&cRename saved, but failed to cleanup old entry. Check logs."));
                                    plugin.getLogger().warning("[Parkour] Failed to delete old course after publish rename: '" + oldName + "'");
                                }
                            });
                        });
                    } catch (Throwable ignored) {}
                    c = dst;
                }
                if (c.getStartPlate() == null || c.getFinishPlate() == null) {
                    player.sendMessage(messageManager.getMessage("publish-missing-points", "&cSet start and end plates before publishing."));
                    return true;
                }
                if (c.getTopHologramLocation() == null) {
                    player.sendMessage(messageManager.getMessage("publish-missing-topholo", "&cSet a Top Times hologram before publishing."));
                    return true;
                }
                try {
                    // Require some minimal building
                    java.lang.reflect.Method m = c.getClass().getMethod("getPlacedBlocks");
                    Object val = m.invoke(c);
                    if (val instanceof Integer blocks && blocks < 20) {
                        player.sendMessage(messageManager.getMessage("publish-min-blocks", "&cYou need to place at least 20 blocks in your plot before publishing."));
                        return true;
                    }
                } catch (Exception ignored) { }
                c.setPublished(true);
                plugin.getHologramManager().createHolograms(c);
                try { plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses()); } catch (Throwable ignored) {}
                plugin.getPlayerParkourManager().handleCoursePublished(c);
                player.sendMessage(messageManager.getMessage("publish-success", "&aPublished parkour &f{course}&a.", Map.of("course", c.getName())));
            }
            case "browse" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.browse")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                java.util.List<ParkourCourse> published = new java.util.ArrayList<>(parkourManager.getCourses().values());
                published.removeIf(pc -> !pc.isPublished());
                com.pikaronga.parkour.gui.PublishedParkoursGUI.open(player, plugin, published, "rate", 1);
            }
            case "rategui" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.rate")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("rategui-usage", "&cUsage: /parkour rategui <name>"));
                    return true;
                }
                ParkourCourse rated = parkourManager.getCourse(args[1]);
                
                if (rated == null || !rated.isPublished()) {
                    player.sendMessage(messageManager.getMessage("rate-not-found", "&cParkour not found or not published."));
                    return true;
                }
                if (args.length == 2) {
                    if (rated.getCreators().contains(player.getUniqueId())) {
                        player.sendMessage(messageManager.getMessage("rate-own", "&cYou cannot rate your own parkour."));
                        return true;
                    }
                    com.pikaronga.parkour.gui.RatingGUI.open(player, plugin, rated);
                    return true;
                }
                if (rated.getCreators().contains(player.getUniqueId())) {
                    player.sendMessage(messageManager.getMessage("rate-own", "&cYou cannot rate your own parkour."));
                    return true;
                }
                com.pikaronga.parkour.gui.RatingGUI.open(player, plugin, rated);
            }
            case "rate" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.rate")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("rate-usage", "&cUsage: /parkour rate <name> <looks 1-5> <diff 1-5>"));
                    return true;
                }
                ParkourCourse rated = parkourManager.getCourse(args[1]);
                
                if (rated == null || !rated.isPublished()) {
                    player.sendMessage(messageManager.getMessage("rate-not-found", "&cParkour not found or not published."));
                    return true;
                }
                if (args.length == 2) {
                    if (rated.getCreators().contains(player.getUniqueId())) {
                        player.sendMessage(messageManager.getMessage("rate-own", "&cYou cannot rate your own parkour."));
                        return true;
                    }
                    com.pikaronga.parkour.gui.RatingGUI.open(player, plugin, rated);
                    return true;
                }
                if (args.length < 4) { player.sendMessage(messageManager.getMessage("rate-usage", "&cUsage: /parkour rate <name> <looks 1-5> <diff 1-5>")); return true; }
                try {
                    int looks = Integer.parseInt(args[2]);
                    int diff = Integer.parseInt(args[3]);
                    if (rated.getCreators().contains(player.getUniqueId())) {
                        // Allow creators to rate difficulty only
                        rated.setDifficultyRating(player.getUniqueId(), diff);
                    } else {
                        rated.setLookRating(player.getUniqueId(), looks);
                        rated.setDifficultyRating(player.getUniqueId(), diff);
                    }
                    try { plugin.getStorage().saveCourses(parkourManager.getCourses()); } catch (Throwable ignored) {}
                    player.sendMessage(messageManager.getMessage("rate-success", "&aYour rating has been recorded."));
                } catch (NumberFormatException ex) {
                    player.sendMessage(messageManager.getMessage("rate-usage", "&cUsage: /parkour rate <name> <looks 1-5> <diff 1-5>"));
                }
            }
            case "test" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.edit")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("test-usage", "&cUsage: /parkour test <name>"));
                    return true;
                }
                ParkourCourse tc = parkourManager.getCourse(args[1]);
                if (tc == null) {
                    player.sendMessage(messageManager.getMessage("rate-not-found", "&cParkour not found or not published."));
                    return true;
                }
                if (!tc.getCreators().contains(player.getUniqueId())) {
                    player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
                    return true;
                }
                if (plugin.getPlayerParkourManager().isTesting(player.getUniqueId(), tc)) {
                    plugin.getPlayerParkourManager().stopTesting(player.getUniqueId());
                    player.sendMessage(messageManager.getMessage("test-disabled", "&eTest mode disabled for &f{course}&e.", java.util.Map.of("course", tc.getName())));
                } else {
                    plugin.getPlayerParkourManager().startTesting(player.getUniqueId(), tc);
                    player.sendMessage(messageManager.getMessage("test-enabled", "&aTest mode enabled for &f{course}&a. Step on start to try it.", java.util.Map.of("course", tc.getName())));
                }
            }
            case "myplots" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.myplots")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                java.util.List<ParkourCourse> all = new java.util.ArrayList<>(parkourManager.getCourses().values());
                com.pikaronga.parkour.gui.MyPlotsGUI.open(player, plugin, all, 1);
            }
            case "admin" -> {
                if (!player.hasPermission("parkour.admin")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                java.util.List<ParkourCourse> all = new java.util.ArrayList<>(parkourManager.getCourses().values());
                player.openInventory(com.pikaronga.parkour.gui.AdminParkoursGUI.build(all));
            }
            case "reload" -> {
                if (!player.hasPermission("parkour.admin")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                plugin.getMessageManager().reload();
                plugin.getHologramTextProvider().reload();
                plugin.getGuiConfig().reload();
                plugin.getConfigManager().reload();
                player.sendMessage(messageManager.getMessage("reloaded", "&aReloaded configuration and messages."));
            }
            case "setstaffdiff" -> {
                if (!player.hasPermission("parkour.admin")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (args.length < 3) {
                    player.sendMessage(messageManager.getMessage("setstaffdiff-usage", "&cUsage: /parkour setstaffdiff <name> <1-5>"));
                    return true;
                }
                ParkourCourse cc = parkourManager.getCourse(args[1]);
                if (cc == null) {
                    player.sendMessage(messageManager.getMessage("rate-not-found", "&cParkour not found or not published."));
                    return true;
                }
                try {
                    int v = Integer.parseInt(args[2]);
                    cc.setStaffDifficulty(v);
                    player.sendMessage(messageManager.getMessage("setstaffdiff-success", "&aSet staff difficulty for &f{course}&a to &f{value}&a.", java.util.Map.of("course", cc.getName(), "value", Integer.toString(v))));
                } catch (NumberFormatException ex) {
                    player.sendMessage(messageManager.getMessage("setstaffdiff-usage", "&cUsage: /parkour setstaffdiff <name> <1-5>"));
                }
            }
            case "rewardbest" -> {
                if (!player.hasPermission("parkour.admin")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (!plugin.getConfigManager().rewardsEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cRewards are disabled."));
                    return true;
                }
                java.util.List<ParkourCourse> published = new java.util.ArrayList<>(parkourManager.getCourses().values());
                published.removeIf(pc -> !pc.isPublished());
                published.sort((a,b) -> Double.compare(b.getAverageLookRating(), a.getAverageLookRating()));
                int winners = Math.min(plugin.getConfigManager().rewardsWinners(), published.size());
                String cmd = plugin.getConfigManager().rewardsCommand();
                int given = 0;
                for (int i = 0; i < published.size() && given < winners; i++) {
                    ParkourCourse pc = published.get(i);
                    if (pc.getCreators().isEmpty()) continue;
                    java.util.UUID uuid = pc.getCreators().get(0);
                    org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                    String run = cmd.replace("%player%", op.getName() != null ? op.getName() : uuid.toString());
                    org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), run);
                    given++;
                }
                player.sendMessage(messageManager.getMessage("rewards-given", "&aIssued rewards to top &f{count}&a parkours.", java.util.Map.of("count", Integer.toString(given))));
            }
            default -> {
                player.sendMessage(messageManager.getMessage("unknown-subcommand", "&cUnknown subcommand."));
                success = false;
            }
        }
        if (success) {
            plugin.getStorage().saveCourses(parkourManager.getCourses());
        }
        return true;
    }

    private boolean ensureCourseEditable(Player player, ParkourCourse course) {
        if (course == null) return false;
        if (course.isPublished()) {
            player.sendMessage(messageManager.getMessage("publish-locked", "&cPublished parkours can no longer be edited."));
            return false;
        }
        return true;
    }

    private void handleSetStart(Player player, ParkourCourse course) {
        // Restrict to owner's plot region
        if (!plugin.getPlayerParkourManager().isOwner(player, course)) {
            player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
            return;
        }
        if (!ensureCourseEditable(player, course)) return;
        if (!ensureCourseEditable(player, course)) return;
        if (!ensureCourseEditable(player, course)) return;
        if (!ensureCourseEditable(player, course)) return;
        org.bukkit.World plots = plugin.getPlayerParkourManager().getWorld();
        Block block = getTargetPressurePlate(player);
        if (block == null) {
            player.sendMessage(messageManager.getMessage("look-at-plate-start", "&cLook at a pressure plate to set the start."));
            return;
        }
        com.pikaronga.parkour.player.PlotRegion r = course.getPlotRegion();
        if (plots == null || block.getWorld() == null || !plots.getUID().equals(block.getWorld().getUID()) || r == null || !r.contains(block.getLocation())) {
            player.sendMessage(messageManager.getMessage("setup-placement-denied", "&cYou can only configure a parkour from within your own plot."));
            return;
        }
        if (player.getWorld() == null || !plots.getUID().equals(player.getWorld().getUID()) || !r.contains(player.getLocation())) {
            player.sendMessage(messageManager.getMessage("setup-placement-denied", "&cYou can only configure a parkour from within your own plot."));
            return;
        }
        course.setStartPlate(block.getLocation());
        course.setStartTeleport(player.getLocation());
        player.sendMessage(messageManager.getMessage("set-start-plate", "&aSet start plate for parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetEnd(Player player, ParkourCourse course) {
        // Restrict to owner's plot region
        if (!plugin.getPlayerParkourManager().isOwner(player, course)) {
            player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
            return;
        }
        org.bukkit.World plots = plugin.getPlayerParkourManager().getWorld();
        Block block = getTargetPressurePlate(player);
        if (block == null) {
            player.sendMessage(messageManager.getMessage("look-at-plate-end", "&cLook at a pressure plate to set the end."));
            return;
        }
        com.pikaronga.parkour.player.PlotRegion r = course.getPlotRegion();
        if (plots == null || block.getWorld() == null || !plots.getUID().equals(block.getWorld().getUID()) || r == null || !r.contains(block.getLocation())) {
            player.sendMessage(messageManager.getMessage("setup-placement-denied", "&cYou can only configure a parkour from within your own plot."));
            return;
        }
        course.setFinishPlate(block.getLocation());
        player.sendMessage(messageManager.getMessage("set-end-plate", "&aSet end plate for parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetSpawn(Player player, ParkourCourse course) {
        // Restrict to owner's plot region
        if (!plugin.getPlayerParkourManager().isOwner(player, course)) {
            player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
            return;
        }
        org.bukkit.World plots = plugin.getPlayerParkourManager().getWorld();
        com.pikaronga.parkour.player.PlotRegion r = course.getPlotRegion();
        if (plots == null || player.getWorld() == null || !plots.getUID().equals(player.getWorld().getUID()) || r == null || !r.contains(player.getLocation())) {
            player.sendMessage(messageManager.getMessage("setup-placement-denied", "&cYou can only configure a parkour from within your own plot."));
            return;
        }
        course.setFinishTeleport(player.getLocation());
        player.sendMessage(messageManager.getMessage("set-completion-spawn", "&aSet completion spawn for parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetCheckpoint(Player player, ParkourCourse course) {
        // Restrict to owner's plot region
        if (!plugin.getPlayerParkourManager().isOwner(player, course)) {
            player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
            return;
        }
        if (!ensureCourseEditable(player, course)) return;
        org.bukkit.World plots = plugin.getPlayerParkourManager().getWorld();
        Block block = getTargetPressurePlate(player);
        if (block == null) {
            player.sendMessage(messageManager.getMessage("look-at-plate-checkpoint", "&cLook at a pressure plate to add a checkpoint."));
            return;
        }
        com.pikaronga.parkour.player.PlotRegion r = course.getPlotRegion();
        if (plots == null || block.getWorld() == null || !plots.getUID().equals(block.getWorld().getUID()) || r == null || !r.contains(block.getLocation())) {
            player.sendMessage(messageManager.getMessage("setup-placement-denied", "&cYou can only configure a parkour from within your own plot."));
            return;
        }
        if (player.getWorld() == null || !plots.getUID().equals(player.getWorld().getUID()) || !r.contains(player.getLocation())) {
            player.sendMessage(messageManager.getMessage("setup-placement-denied", "&cYou can only configure a parkour from within your own plot."));
            return;
        }
        course.addCheckpoint(new Checkpoint(block.getLocation(), player.getLocation()));
        player.sendMessage(messageManager.getMessage("checkpoint-added", "&aAdded checkpoint to parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetTopHolo(Player player, ParkourCourse course) {
        // Restrict placement to player's own parkour world and plot
        if (!plugin.getPlayerParkourManager().isOwner(player, course)) {
            player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
            return;
        }
        org.bukkit.World plots = plugin.getPlayerParkourManager().getWorld();
        Location here = player.getLocation();
        if (plots == null || here.getWorld() == null || !plots.getUID().equals(here.getWorld().getUID())) {
            player.sendMessage(messageManager.getMessage("holo-placement-denied", "&cYou can only place holograms inside your own parkour world."));
            return;
        }
        com.pikaronga.parkour.player.PlotRegion r = course.getPlotRegion();
        if (r != null && !r.contains(here)) {
            player.sendMessage(messageManager.getMessage("holo-placement-denied", "&cYou can only place holograms inside your own parkour world."));
            return;
        }
        Location location = here.clone().add(0, 2, 0);
        hologramManager.setTopHologram(course, location);
        player.sendMessage(messageManager.getMessage("set-top-holo", "&aSet top times hologram for parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetBestHolo(Player player, ParkourCourse course) {
        // Restrict placement to player's own parkour world and plot
        if (!plugin.getPlayerParkourManager().isOwner(player, course)) {
            player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
            return;
        }
        if (!ensureCourseEditable(player, course)) return;
        org.bukkit.World plots = plugin.getPlayerParkourManager().getWorld();
        Location here = player.getLocation();
        if (plots == null || here.getWorld() == null || !plots.getUID().equals(here.getWorld().getUID())) {
            player.sendMessage(messageManager.getMessage("holo-placement-denied", "&cYou can only place holograms inside your own parkour world."));
            return;
        }
        com.pikaronga.parkour.player.PlotRegion r = course.getPlotRegion();
        if (r != null && !r.contains(here)) {
            player.sendMessage(messageManager.getMessage("holo-placement-denied", "&cYou can only place holograms inside your own parkour world."));
            return;
        }
        Location location = here.clone().add(0, 2, 0);
        hologramManager.setBestHologram(course, location);
        player.sendMessage(messageManager.getMessage("set-best-holo", "&aSet personal best hologram for parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetMaxFall(Player player, ParkourCourse course, String[] args) {
        if (!plugin.getPlayerParkourManager().isOwner(player, course)) {
            player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
            return;
        }
        if (!ensureCourseEditable(player, course)) return;
        if (args.length < 3) {
            player.sendMessage(messageManager.getMessage("setmaxfall-usage", "&cUsage: /parkour setmaxfall <name> <distance>"));
            return;
        }
        double distance;
        try {
            distance = Double.parseDouble(args[2]);
        } catch (NumberFormatException exception) {
            player.sendMessage(messageManager.getMessage("invalid-max-fall", "&cPlease provide a valid non-negative number for max fall distance."));
            return;
        }
        if (distance < 0) {
            player.sendMessage(messageManager.getMessage("invalid-max-fall", "&cPlease provide a valid non-negative number for max fall distance."));
            return;
        }
        course.setMaxFallDistance(distance);
        player.sendMessage(messageManager.getMessage("set-max-fall", "&aSet max fall distance for parkour &f{course}&a to &f{distance}&a.", Map.of("course", course.getName(), "distance", Double.toString(distance))));
    }

    private Block getTargetPressurePlate(Player player) {
        Block target = player.getTargetBlockExact(5);
        if (target == null || !isPressurePlate(target.getType())) {
            target = player.getLocation().getBlock();
            if (!isPressurePlate(target.getType())) {
                return null;
            }
        }
        return target;
    }

    private boolean isPressurePlate(Material material) {
        return material != null && material.name().endsWith("_PRESSURE_PLATE");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("create", "edit", "setup", "myplots", "publish", "browse", "rate", "test",
                    "psetstart", "psetend", "psetspawn", "psetcheckpoint", "psettop", "psetholotop10", "psetbest", "psetholobest", "psetcreatorholo",
                    "admin", "reload", "rewardbest",
                    "setstart", "setend", "setspawn", "setcheckpoint", "setholotop10", "setholobest", "setmaxfall", "setcreatorholo", "setstaffdiff", "deletetime", "deletecourse", "renamecourse", "holocleanup");
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("browse")) {
                return Arrays.asList("rate", "difficulty", "name");
            }
            if (args[0].equalsIgnoreCase("renamecourse") || args[0].equalsIgnoreCase("deletecourse") || args[0].equalsIgnoreCase("holocleanup")) {
                return new java.util.ArrayList<>(parkourManager.getCourseNames());
            }
            if (args[0].equalsIgnoreCase("deletetime")) {
                // Suggest online player names
                java.util.List<String> names = new java.util.ArrayList<>();
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) names.add(p.getName());
                // Also suggest 'all' for holocleanup
                return names;
            }
            if (args[0].equalsIgnoreCase("holocleanup")) {
                java.util.List<String> list = new java.util.ArrayList<>(parkourManager.getCourseNames());
                list.add("all");
                return list;
            }
            if (sender instanceof Player p) {
                java.util.UUID uid = p.getUniqueId();
                String sub = args[0].toLowerCase();
                java.util.List<String> owned = new java.util.ArrayList<>();
                for (ParkourCourse c : parkourManager.getCourses().values()) {
                    if (c.getCreators().contains(uid)) owned.add(c.getName());
                }
                // Player-only subcommands that should only suggest own courses
                java.util.Set<String> ownOnly = new java.util.HashSet<>(java.util.Arrays.asList(
                        "edit", "setup", "publish",
                        "psetstart", "psetend", "psetspawn", "psetcheckpoint", "psettop", "psetholotop10", "psetbest", "psetholobest", "psetcreatorholo",
                        "test", "rategui"
                ));
                if (ownOnly.contains(sub)) {
                    return owned;
                }
                if (sub.equals("rate")) {
                    java.util.List<String> published = new java.util.ArrayList<>();
                    for (ParkourCourse c : parkourManager.getCourses().values()) {
                        if (c.isPublished()) published.add(c.getName());
                    }
                    return published;
                }
            }
            return new ArrayList<>(parkourManager.getCourseNames());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("browse")) {
            return Arrays.asList("1", "2", "3");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("deletetime")) {
            return new java.util.ArrayList<>(parkourManager.getCourseNames());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("renamecourse")) {
            return java.util.Collections.emptyList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setmaxfall")) {
            ParkourCourse course = parkourManager.getCourse(args[1]);
            double suggestion = course != null ? course.getMaxFallDistance() : ParkourCourse.DEFAULT_MAX_FALL_DISTANCE;
            return Collections.singletonList(Double.toString(suggestion));
        }
        return Collections.emptyList();
    }
}
