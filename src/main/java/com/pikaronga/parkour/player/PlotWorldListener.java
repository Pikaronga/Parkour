package com.pikaronga.parkour.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.entity.Player;

public class PlotWorldListener implements Listener {
    private final PlayerParkourManager ppm;

    public PlotWorldListener(PlayerParkourManager ppm) {
        this.ppm = ppm;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!event.getLocation().getWorld().equals(ppm.getWorld())) return;
        // Optionally block natural-like spawns to keep plots tidy
        if (ppm.getPlugin().getConfigManager().blockNaturalSpawns()) {
            switch (event.getSpawnReason()) {
                case NATURAL, CHUNK_GEN, JOCKEY, MOUNT, BREEDING, REINFORCEMENTS, PATROL, VILLAGE_INVASION, DISPENSE_EGG, INFECTION, ENDER_PEARL, SILVERFISH_BLOCK, FROZEN, SHEARED, BEEHIVE -> {
                    event.setCancelled(true);
                }
                default -> { /* allow other spawn reasons (e.g. PLUGIN, CUSTOM) */ }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!player.getWorld().equals(ppm.getWorld())) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) return;
        // Sessions already handled elsewhere; here we cover builders in plots
        event.setCancelled(true);
        java.util.Optional<com.pikaronga.parkour.course.ParkourCourse> in = ppm.getCourseByLocation(player.getLocation());
        com.pikaronga.parkour.course.ParkourCourse target = in.orElseGet(() -> {
            com.pikaronga.parkour.course.ParkourCourse last = ppm.getLastEditingCourse(player.getUniqueId());
            if (last != null) return last;
            return ppm.findAnyOwnedCourse(player.getUniqueId());
        });
        if (target != null) {
            ppm.teleportToPlot(player, target);
        }
    }
}
