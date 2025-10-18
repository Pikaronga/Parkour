package com.pikaronga.parkour.session;

import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public class ParkourSession {

    private final Player player;
    private final ParkourCourse course;
    private long startTime;
    private Location lastCheckpoint;
    private ItemStack[] contents;
    private ItemStack[] armor;
    private ItemStack offHand;
    private GameMode originalGameMode;
    private boolean originalAllowFlight;

    public ParkourSession(Player player, ParkourCourse course, Location startLocation) {
        this.player = player;
        this.course = course;
        this.lastCheckpoint = startLocation != null ? startLocation.clone() : null;
        this.startTime = System.currentTimeMillis();
    }

    public Player getPlayer() {
        return player;
    }

    public ParkourCourse getCourse() {
        return course;
    }

    public long getStartTime() {
        return startTime;
    }

    public void resetTimer() {
        this.startTime = System.currentTimeMillis();
    }

    public Location getLastCheckpoint() {
        return lastCheckpoint;
    }

    public void setLastCheckpoint(Location lastCheckpoint) {
        this.lastCheckpoint = lastCheckpoint != null ? lastCheckpoint.clone() : null;
    }

    public void captureInventory() {
        this.contents = player.getInventory().getContents();
        this.armor = player.getInventory().getArmorContents();
        this.offHand = player.getInventory().getItemInOffHand();
        this.originalGameMode = player.getGameMode();
        this.originalAllowFlight = player.getAllowFlight();
    }

    public void restoreInventory() {
        if (contents != null) {
            player.getInventory().setContents(contents);
        }
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }
        if (offHand != null) {
            player.getInventory().setItemInOffHand(offHand);
        }
        if (originalGameMode != null) {
            player.setGameMode(originalGameMode);
        }
        player.setAllowFlight(originalAllowFlight);
    }

    public void clearEffects() {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setFireTicks(0);
        player.setFoodLevel(20);
        player.setSaturation(5f);
        player.setFallDistance(0f);
        player.setHealth(player.getMaxHealth());
    }
}
