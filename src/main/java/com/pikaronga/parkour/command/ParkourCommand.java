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
            case "setstart", "setend", "setspawn", "setcheckpoint", "setholotop10", "setholobest", "setmaxfall" -> {
                if (!player.hasPermission("parkour.admin")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
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
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("create-usage", "&cUsage: /parkour create <name>"));
                    return true;
                }
                String name = args[1];
                long owned = plugin.getParkourManager().getCourses().values().stream().filter(c -> c.getCreators().contains(player.getUniqueId())).count();
                if (owned >= plugin.getConfigManager().getMaxPerPlayer()) {
                    player.sendMessage(messageManager.getMessage("create-limit", "&cYou have reached your parkour limit."));
                    return true;
                }
                if (plugin.getParkourManager().getCourse(name) != null) {
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
                player.sendMessage(messageManager.getMessage("create-success", "&aCreated parkour &f{course}&a. You are now in your plot.", Map.of("course", created.getName())));
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
                switch (sub) {
                    case "psetstart" -> handleSetStart(player, editable);
                    case "psetend" -> handleSetEnd(player, editable);
                    case "psetspawn" -> handleSetSpawn(player, editable);
                    case "psetcheckpoint" -> handleSetCheckpoint(player, editable);
                    case "psettop", "psetholotop10" -> handleSetTopHolo(player, editable);
                    case "psetbest", "psetholobest" -> handleSetBestHolo(player, editable);
                    case "psetcreatorholo" -> {
                        org.bukkit.Location loc = player.getLocation().clone().add(0, 2, 0);
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
                    player.sendMessage(messageManager.getMessage("publish-usage", "&cUsage: /parkour publish <name>"));
                    return true;
                }
                ParkourCourse c = parkourManager.getCourse(args[1]);
                if (c == null || !c.getCreators().contains(player.getUniqueId())) {
                    player.sendMessage(messageManager.getMessage("publish-not-owner", "&cYou do not own this parkour."));
                    return true;
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
            case "rate" -> {
                if (!plugin.getConfigManager().playerParkoursEnabled()) {
                    player.sendMessage(messageManager.getMessage("feature-disabled", "&cPlayer parkours are disabled."));
                    return true;
                }
                if (!player.hasPermission("parkour.player.rate")) {
                    player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
                    return true;
                }
                if (args.length < 4) {
                    player.sendMessage(messageManager.getMessage("rate-usage", "&cUsage: /parkour rate <name> <looks 1-5> <diff 1-5>"));
                    return true;
                }
                ParkourCourse rated = parkourManager.getCourse(args[1]);
                if (rated == null || !rated.isPublished()) {
                    player.sendMessage(messageManager.getMessage("rate-not-found", "&cParkour not found or not published."));
                    return true;
                }
                if (rated.getCreators().contains(player.getUniqueId())) {
                    player.sendMessage(messageManager.getMessage("rate-own", "&cYou cannot rate your own parkour."));
                    return true;
                }
                try {
                    int looks = Integer.parseInt(args[2]);
                    int diff = Integer.parseInt(args[3]);
                    rated.setLookRating(player.getUniqueId(), looks);
                    rated.setDifficultyRating(player.getUniqueId(), diff);
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

    private void handleSetStart(Player player, ParkourCourse course) {
        Block block = getTargetPressurePlate(player);
        if (block == null) {
            player.sendMessage(messageManager.getMessage("look-at-plate-start", "&cLook at a pressure plate to set the start."));
            return;
        }
        course.setStartPlate(block.getLocation());
        course.setStartTeleport(player.getLocation());
        player.sendMessage(messageManager.getMessage("set-start-plate", "&aSet start plate for parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetEnd(Player player, ParkourCourse course) {
        Block block = getTargetPressurePlate(player);
        if (block == null) {
            player.sendMessage(messageManager.getMessage("look-at-plate-end", "&cLook at a pressure plate to set the end."));
            return;
        }
        course.setFinishPlate(block.getLocation());
        player.sendMessage(messageManager.getMessage("set-end-plate", "&aSet end plate for parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetSpawn(Player player, ParkourCourse course) {
        course.setFinishTeleport(player.getLocation());
        player.sendMessage(messageManager.getMessage("set-completion-spawn", "&aSet completion spawn for parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetCheckpoint(Player player, ParkourCourse course) {
        Block block = getTargetPressurePlate(player);
        if (block == null) {
            player.sendMessage(messageManager.getMessage("look-at-plate-checkpoint", "&cLook at a pressure plate to add a checkpoint."));
            return;
        }
        course.addCheckpoint(new Checkpoint(block.getLocation(), player.getLocation()));
        player.sendMessage(messageManager.getMessage("checkpoint-added", "&aAdded checkpoint to parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetTopHolo(Player player, ParkourCourse course) {
        Location location = player.getLocation().clone().add(0, 2, 0);
        hologramManager.setTopHologram(course, location);
        player.sendMessage(messageManager.getMessage("set-top-holo", "&aSet top times hologram for parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetBestHolo(Player player, ParkourCourse course) {
        Location location = player.getLocation().clone().add(0, 2, 0);
        hologramManager.setBestHologram(course, location);
        player.sendMessage(messageManager.getMessage("set-best-holo", "&aSet personal best hologram for parkour &f{course}&a.", Map.of("course", course.getName())));
    }

    private void handleSetMaxFall(Player player, ParkourCourse course, String[] args) {
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
                    "setstart", "setend", "setspawn", "setcheckpoint", "setholotop10", "setholobest", "setmaxfall", "setcreatorholo", "setstaffdiff");
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("browse")) {
                return Arrays.asList("rate", "difficulty", "name");
            }
            return new ArrayList<>(parkourManager.getCourseNames());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("browse")) {
            return Arrays.asList("1", "2", "3");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setmaxfall")) {
            ParkourCourse course = parkourManager.getCourse(args[1]);
            double suggestion = course != null ? course.getMaxFallDistance() : ParkourCourse.DEFAULT_MAX_FALL_DISTANCE;
            return Collections.singletonList(Double.toString(suggestion));
        }
        return Collections.emptyList();
    }
}
