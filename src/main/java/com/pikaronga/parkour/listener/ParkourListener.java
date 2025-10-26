package com.pikaronga.parkour.listener;

import com.pikaronga.parkour.config.MessageManager;
import com.pikaronga.parkour.session.ParkourSession;
import com.pikaronga.parkour.player.PlayerParkourManager;
import com.pikaronga.parkour.session.SessionManager;
import com.pikaronga.parkour.util.ParkourManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ParkourListener implements Listener {

    private final ParkourManager parkourManager;
    private final SessionManager sessionManager;
    private final MessageManager messageManager;
    private final Set<UUID> recentPhysicalPlates = new HashSet<>();
    private final PlayerParkourManager ppm;

    public ParkourListener(
                           ParkourManager parkourManager,
                           SessionManager sessionManager,
                           MessageManager messageManager,
                           PlayerParkourManager ppm) {
        this.parkourManager = parkourManager;
        this.sessionManager = sessionManager;
        this.messageManager = messageManager;
        this.ppm = ppm;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != null && event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null) {
            Material type = event.getClickedBlock().getType();
            if (!isPressurePlate(type)) {
                return;
            }
            recentPhysicalPlates.add(event.getPlayer().getUniqueId());
            handlePressurePlate(event.getPlayer(), event.getClickedBlock().getLocation());
        } else if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item == null) {
                return;
            }
            String action = getAction(item);
            if (action == null) {
                return;
            }
            event.setCancelled(true);
            switch (action) {
                case "restart" -> sessionManager.resetToStart(event.getPlayer());
                case "checkpoint" -> sessionManager.teleportToCheckpoint(event.getPlayer());
                case "leave" -> {
                    // End session and send the player to server spawn instead of course finish
                    sessionManager.endSession(event.getPlayer(), false, false);
                    teleportToServerSpawn(event.getPlayer());
                }
            }
        }
    }

    private void teleportToServerSpawn(Player player) {
        // Try using a common /spawn command if a plugin provides it; fall back to default spawn location
        try {
            boolean dispatched = player.performCommand("spawn");
            if (dispatched) return;
        } catch (Throwable ignored) {}
        try {
            org.bukkit.Location loc = player.getServer().getWorlds().isEmpty() ? null : player.getServer().getWorlds().get(0).getSpawnLocation();
            if (loc != null) {
                player.teleport(loc);
            }
        } catch (Throwable ignored) {}
    }

    private void handlePressurePlate(Player player, Location blockLocation) {
        org.bukkit.block.Block block = blockLocation.getBlock();
        parkourManager.findCourseByStart(block).ifPresent(course -> {
            boolean allowed = course.isPublished() || (ppm != null && ppm.isOwner(player, course) && ppm.isTesting(player.getUniqueId(), course));
            if (!allowed) {
                return;
            }
            if (course.getStartTeleport() == null) {
                course.setStartTeleport(blockLocation.clone().add(0.5, 0, 0.5));
            }
            ParkourSession session = sessionManager.getSession(player);
            if (session != null && session.getCourse().equals(course) && session.consumeIgnoreNextStartPlate()) {
                return;
            }
            if (ppm != null && ppm.isTesting(player.getUniqueId(), course)) {
                sessionManager.startSessionTest(player, course, false);
            } else {
                sessionManager.startSession(player, course, false);
            }
        });

        Optional<ParkourManager.CheckpointMatch> match = parkourManager.findCheckpoint(block);
        if (match.isPresent()) {
            boolean allowed = match.get().course().isPublished() || (ppm != null && ppm.isOwner(player, match.get().course()) && ppm.isTesting(player.getUniqueId(), match.get().course()));
            if (!allowed) {
                return;
            }
            ParkourSession session = sessionManager.getSession(player);
            if (session != null && session.getCourse().equals(match.get().course())) {
                session.setLastCheckpoint(match.get().checkpoint().respawnLocation());
                player.sendMessage(messageManager.getMessage("checkpoint-reached", "&aCheckpoint reached!"));
            }
        }

        parkourManager.findCourseByFinish(block).ifPresent(course -> {
            boolean allowed = course.isPublished() || (ppm != null && ppm.isOwner(player, course) && ppm.isTesting(player.getUniqueId(), course));
            if (!allowed) {
                return;
            }
            ParkourSession session = sessionManager.getSession(player);
            if (session == null || !session.getCourse().equals(course)) {
                return;
            }
            sessionManager.completeSession(player);
        });
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        boolean skipPlateHandling = recentPhysicalPlates.remove(player.getUniqueId());
        if (!skipPlateHandling) {
            Location from = event.getFrom();
            if (from.getBlockX() != to.getBlockX()
                    || from.getBlockY() != to.getBlockY()
                    || from.getBlockZ() != to.getBlockZ()) {
                org.bukkit.block.Block toBlock = to.getBlock();
                if (isPressurePlate(toBlock.getType())) {
                    handlePressurePlate(player, toBlock.getLocation());
                }
            }
        }
        ParkourSession session = sessionManager.getSession(player);
        if (session == null) {
            return;
        }
        Location checkpoint = session.getLastCheckpoint();
        if (checkpoint == null) {
            return;
        }
        double maxFallDistance = session.getCourse().getMaxFallDistance();
        if (to.getY() < checkpoint.getY() - maxFallDistance) {
            player.teleport(checkpoint);
            player.sendMessage(messageManager.getMessage("fell-teleport", "&cYou fell! Teleporting to your checkpoint."));
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ParkourSession session = sessionManager.getSession(player);
        if (session == null) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            Location checkpoint = session.getLastCheckpoint();
            if (checkpoint != null) {
                player.teleport(checkpoint);
                player.sendMessage(messageManager.getMessage("fell-teleport", "&cYou fell! Teleporting to your checkpoint."));
            }
        } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            // Prevent fall damage while in a parkour session
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        recentPhysicalPlates.remove(event.getPlayer().getUniqueId());
        sessionManager.endSession(event.getPlayer(), false);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        ParkourSession session = sessionManager.getSession(event.getPlayer());
        if (session != null && session.getLastCheckpoint() != null) {
            event.setRespawnLocation(session.getLastCheckpoint());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ParkourSession session = sessionManager.getSession(player);
        if (session == null) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        if (current != null && getAction(current) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        ParkourSession session = sessionManager.getSession(event.getPlayer());
        if (session == null) {
            return;
        }
        if (getAction(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        ParkourSession session = sessionManager.getSession(player);
        if (session == null) {
            return;
        }
        player.sendMessage(messageManager.getMessage("command-blocked", "&cYou cannot use commands while in a parkour."));
        event.setCancelled(true);
    }

    private boolean isPressurePlate(Material material) {
        return material.name().endsWith("_PRESSURE_PLATE");
    }

    private String getAction(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return null;
        }
        PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();
        NamespacedKey key = sessionManager.getActionKey();
        if (!container.has(key, PersistentDataType.STRING)) {
            return null;
        }
        return container.get(key, PersistentDataType.STRING);
    }
}
