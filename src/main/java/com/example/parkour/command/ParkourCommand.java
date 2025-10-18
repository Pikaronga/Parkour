package com.example.parkour.command;

import com.example.parkour.ParkourPlugin;
import com.example.parkour.course.Checkpoint;
import com.example.parkour.course.ParkourCourse;
import com.example.parkour.hologram.HologramManager;
import com.example.parkour.util.ParkourManager;
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

public class ParkourCommand implements CommandExecutor, TabCompleter {

    private final ParkourPlugin plugin;
    private final ParkourManager parkourManager;
    private final HologramManager hologramManager;

    public ParkourCommand(ParkourPlugin plugin, ParkourManager parkourManager, HologramManager hologramManager) {
        this.plugin = plugin;
        this.parkourManager = parkourManager;
        this.hologramManager = hologramManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission("parkour.admin")) {
            player.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cUsage: /parkour <setstart|setend|setspawn|setcheckpoint|setholotop10|setholobest> <name>");
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
            default -> {
                player.sendMessage("§cUnknown subcommand.");
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
            player.sendMessage("§cLook at a pressure plate to set the start.");
            return;
        }
        course.setStartPlate(block.getLocation());
        course.setStartTeleport(player.getLocation());
        player.sendMessage("§aSet start plate for parkour §f" + course.getName() + "§a.");
    }

    private void handleSetEnd(Player player, ParkourCourse course) {
        Block block = getTargetPressurePlate(player);
        if (block == null) {
            player.sendMessage("§cLook at a pressure plate to set the end.");
            return;
        }
        course.setFinishPlate(block.getLocation());
        player.sendMessage("§aSet end plate for parkour §f" + course.getName() + "§a.");
    }

    private void handleSetSpawn(Player player, ParkourCourse course) {
        course.setFinishTeleport(player.getLocation());
        player.sendMessage("§aSet completion spawn for parkour §f" + course.getName() + "§a.");
    }

    private void handleSetCheckpoint(Player player, ParkourCourse course) {
        Block block = getTargetPressurePlate(player);
        if (block == null) {
            player.sendMessage("§cLook at a pressure plate to add a checkpoint.");
            return;
        }
        course.addCheckpoint(new Checkpoint(block.getLocation(), player.getLocation()));
        player.sendMessage("§aAdded checkpoint to parkour §f" + course.getName() + "§a.");
    }

    private void handleSetTopHolo(Player player, ParkourCourse course) {
        Location location = player.getLocation().clone().add(0, 2, 0);
        hologramManager.setTopHologram(course, location);
        player.sendMessage("§aSet top times hologram for parkour §f" + course.getName() + "§a.");
    }

    private void handleSetBestHolo(Player player, ParkourCourse course) {
        Location location = player.getLocation().clone().add(0, 2, 0);
        hologramManager.setBestHologram(course, location);
        player.sendMessage("§aSet personal best hologram for parkour §f" + course.getName() + "§a.");
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
            return Arrays.asList("setstart", "setend", "setspawn", "setcheckpoint", "setholotop10", "setholobest");
        }
        if (args.length == 2) {
            return new ArrayList<>(parkourManager.getCourseNames());
        }
        return Collections.emptyList();
    }
}
