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
        if (!player.hasPermission("parkour.admin")) {
            player.sendMessage(messageManager.getMessage("no-permission", "&cYou do not have permission to use this command."));
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(messageManager.getMessage("usage", "&cUsage: /parkour <setstart|setend|setspawn|setcheckpoint|setholotop10|setholobest|setmaxfall> <name> [value]"));
            return true;
        }
        String sub = args[0].toLowerCase();
        String name = args[1];
        ParkourCourse course = parkourManager.getOrCreate(name);
        boolean success = true;
        switch (sub) {
            case "setstart" -> handleSetStart(player, course);
            case "setend" -> handleSetEnd(player, course);
            case "setspawn" -> handleSetSpawn(player, course);
            case "setcheckpoint" -> handleSetCheckpoint(player, course);
            case "setholotop10" -> handleSetTopHolo(player, course);
            case "setholobest" -> handleSetBestHolo(player, course);
            case "setmaxfall" -> handleSetMaxFall(player, course, args);
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
            return Arrays.asList("setstart", "setend", "setspawn", "setcheckpoint", "setholotop10", "setholobest", "setmaxfall");
        }
        if (args.length == 2) {
            return new ArrayList<>(parkourManager.getCourseNames());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setmaxfall")) {
            ParkourCourse course = parkourManager.getCourse(args[1]);
            double suggestion = course != null ? course.getMaxFallDistance() : ParkourCourse.DEFAULT_MAX_FALL_DISTANCE;
            return Collections.singletonList(Double.toString(suggestion));
        }
        return Collections.emptyList();
    }
}
