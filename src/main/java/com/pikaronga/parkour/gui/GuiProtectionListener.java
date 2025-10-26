package com.pikaronga.parkour.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Hard guard to prevent taking/moving items from any plugin GUI.
 * Uses holder-based detection so it remains reliable under title changes.
 */
public class GuiProtectionListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        org.bukkit.inventory.Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof PluginGuiHolder) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
            try {
                org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("Parkour");
                if (plugin instanceof com.pikaronga.parkour.ParkourPlugin p && p.getConfigManager().debugEnabled()) {
                    com.pikaronga.parkour.gui.PluginGuiHolder h = (PluginGuiHolder) top.getHolder();
                    p.getLogger().info("GuiProtection: cancelled click for holder=" + h.getId() + ", slot=" + event.getRawSlot());
                }
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        org.bukkit.inventory.Inventory top = event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof PluginGuiHolder) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
            try {
                org.bukkit.plugin.Plugin plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("Parkour");
                if (plugin instanceof com.pikaronga.parkour.ParkourPlugin p && p.getConfigManager().debugEnabled()) {
                    com.pikaronga.parkour.gui.PluginGuiHolder h = (PluginGuiHolder) top.getHolder();
                    p.getLogger().info("GuiProtection: cancelled drag for holder=" + h.getId());
                }
            } catch (Throwable ignored) {}
        }
    }
}
