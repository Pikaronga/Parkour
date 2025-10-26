package com.pikaronga.parkour.gui;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class RatingGUIListener implements Listener {
    private final ParkourPlugin plugin;

    public RatingGUIListener(ParkourPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        String title = event.getView().getTitle();
        String base = ChatColor.stripColor(plugin.getGuiConfig().titlePrefix("rating", "&aRate: "));
        String stripped = ChatColor.stripColor(title);
        if (!stripped.toLowerCase().startsWith(base.toLowerCase())) return;
        event.setCancelled(true);
        String courseName = stripped.replace(base, "").replace(":", "").trim();
        ParkourCourse course = plugin.getParkourManager().getCourse(courseName);
        if (course == null || !course.isPublished()) { player.closeInventory(); return; }
        if (course.getCreators().contains(player.getUniqueId())) { player.closeInventory(); return; }
        int clickedSlot = event.getRawSlot();
        for (int i = 1; i <= 5; i++) {
            int slotL = plugin.getGuiConfig().slot("rating", "looks." + i, 9 + i);
            if (clickedSlot == slotL) {
                course.setLookRating(player.getUniqueId(), i);
                try { plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses()); } catch (Throwable ignored) {}
                player.sendMessage(plugin.getMessageManager().getMessage("rating-thanks", "&aThanks for rating &f{course}&a!", java.util.Map.of("course", course.getName())));
                player.closeInventory();
                return;
            }
        }
        for (int i = 1; i <= 5; i++) {
            int slotD = plugin.getGuiConfig().slot("rating", "difficulty." + i, 9 + 4 + i);
            if (clickedSlot == slotD) {
                course.setDifficultyRating(player.getUniqueId(), i);
                try { plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses()); } catch (Throwable ignored) {}
                player.sendMessage(plugin.getMessageManager().getMessage("rating-thanks", "&aThanks for rating &f{course}&a!", java.util.Map.of("course", course.getName())));
                player.closeInventory();
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player)) return;
        String title = event.getView().getTitle();
        String base = ChatColor.stripColor(plugin.getGuiConfig().title("rating", "&aRate:", java.util.Map.of()));
        String stripped = ChatColor.stripColor(title);
        if (!stripped.toLowerCase().startsWith(base.toLowerCase())) return;
        event.setCancelled(true);
    }
}
