package com.pikaronga.parkour.gui;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class SetupGUI {
    public static final String TITLE_PREFIX = ChatColor.DARK_AQUA + "Setup: ";

    public static void open(Player player, ParkourPlugin plugin, ParkourCourse course) {
        Inventory inv = build(plugin, course);
        player.openInventory(inv);
    }

    public static Inventory build(ParkourPlugin plugin, ParkourCourse course) {
        String title = plugin.getGuiConfig().title("setup", "&3Setup: {course}", Map.of("course", course.getName()));
        // Determine rows: use configured rows but ensure it fits the highest configured button slot
        int rows = plugin.getGuiConfig().rows("setup", 3);
        int maxSlot = 0;
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-start", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-start-done", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-end", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-end-done", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-spawn", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-spawn-done", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.add-checkpoint", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-top", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-top-done", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-best", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-best-done", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-creator", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.set-creator-done", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.teleport", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.publish", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.status", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.delete", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("setup", "buttons.close", 0));
        if (maxSlot > 0) {
            int neededRows = ((maxSlot + 1) + 8) / 9; // ceil to rows
            rows = Math.max(rows, neededRows);
        }
        rows = Math.max(1, Math.min(6, rows));
        // Include course name in holder id to bind actions to the target course reliably
        Inventory inv = Bukkit.createInventory(new PluginGuiHolder("setup:" + course.getName().toLowerCase()), rows * 9, title);

        // Steps with completed/incomplete variants (use -done variant if configured)
        setStep(plugin, inv, "setup", "buttons.set-start", "buttons.set-start-done", course.getStartPlate() != null, "&cRequired: not set");
        setStep(plugin, inv, "setup", "buttons.set-end", "buttons.set-end-done", course.getFinishPlate() != null, "&cRequired: not set");
        setStep(plugin, inv, "setup", "buttons.set-spawn", "buttons.set-spawn-done", course.getFinishTeleport() != null, null);
        set(plugin, inv, "setup", "buttons.add-checkpoint");
        setStep(plugin, inv, "setup", "buttons.set-top", "buttons.set-top-done", course.getTopHologramLocation() != null, "&cRequired: not set");
        setStep(plugin, inv, "setup", "buttons.set-best", "buttons.set-best-done", course.getBestHologramLocation() != null, null);
        setStep(plugin, inv, "setup", "buttons.set-creator", "buttons.set-creator-done", course.getCreatorHologramLocation() != null, null);
        set(plugin, inv, "setup", "buttons.rename");
        setMaxFallAdjust(plugin, inv, course);
        set(plugin, inv, "setup", "buttons.teleport");
        if (course.isPublished()) {
            set(plugin, inv, "setup", "buttons.publish-locked");
        } else {
            set(plugin, inv, "setup", "buttons.publish");
        }
        set(plugin, inv, "setup", "buttons.toggle-test");
        // Ready status
        boolean ready = course.getStartPlate() != null && course.getFinishPlate() != null && course.getTopHologramLocation() != null;
        setStatus(plugin, inv, ready, course.isPublished());
        set(plugin, inv, "setup", "buttons.delete");
        set(plugin, inv, "setup", "buttons.close");
        return inv;
    }

    private static void setStep(ParkourPlugin plugin, Inventory inv, String base, String key, String doneKey, boolean done, String missingLore) {
        String useKey = done ? doneKey : key;
        int slot = plugin.getGuiConfig().slot(base, useKey, plugin.getGuiConfig().slot(base, key, 0));
        if (slot <= 0) return;
        Material mat = plugin.getGuiConfig().material(base, useKey, plugin.getGuiConfig().material(base, key, Material.STONE_BUTTON));
        String name = plugin.getGuiConfig().name(base, useKey, plugin.getGuiConfig().name(base, key, "&f" + key, Map.of()), Map.of());
        List<String> lore = plugin.getGuiConfig().lore(base, useKey, plugin.getGuiConfig().lore(base, key, List.of(), Map.of()), Map.of());
        if (!done && missingLore != null && !missingLore.isBlank()) {
            java.util.ArrayList<String> nl = new java.util.ArrayList<>(lore);
            nl.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', missingLore));
            lore = nl;
        }
        // If done variant not configured, add a simple green indicator
        if (done && useKey.equals(key)) {
            name = org.bukkit.ChatColor.GREEN + org.bukkit.ChatColor.stripColor(name);
        }
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, it);
        } else if (slot >= inv.getSize()) {
            plugin.getLogger().warning("GUI '" + base + "." + key + "' slot out of range: " + slot);
        }
    }

    private static void set(ParkourPlugin plugin, Inventory inv, String base, String key) {
        int slot = plugin.getGuiConfig().slot(base, key, 0);
        if (slot <= 0) return;
        Material mat = plugin.getGuiConfig().material(base, key, Material.STONE_BUTTON);
        String name = plugin.getGuiConfig().name(base, key, "&f" + key, Map.of());
        List<String> lore = plugin.getGuiConfig().lore(base, key, List.of(), Map.of());
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, it);
        } else if (slot >= inv.getSize()) {
            plugin.getLogger().warning("GUI '" + base + "." + key + "' slot out of range: " + slot);
        }
    }

    private static void setRequired(ParkourPlugin plugin, Inventory inv, String base, String key, boolean missing, String requiredLore) {
        int slot = plugin.getGuiConfig().slot(base, key, 0);
        if (slot <= 0) return;
        Material mat = plugin.getGuiConfig().material(base, key, Material.STONE_BUTTON);
        String name = plugin.getGuiConfig().name(base, key, "&f" + key, Map.of());
        List<String> lore = plugin.getGuiConfig().lore(base, key, List.of(), Map.of());
        if (missing) {
            name = ChatColor.RED + ChatColor.stripColor(name);
            java.util.ArrayList<String> nl = new java.util.ArrayList<>(lore);
            nl.add(ChatColor.translateAlternateColorCodes('&', requiredLore));
            lore = nl;
        }
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (!lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, it);
        } else if (slot >= inv.getSize()) {
            plugin.getLogger().warning("GUI 'setup.buttons.status' slot out of range: " + slot);
        }
    }

    private static void setStatus(ParkourPlugin plugin, Inventory inv, boolean ready, boolean published) {
        int slot = plugin.getGuiConfig().slot("setup", "buttons.status", 4);
        org.bukkit.Material mat;
        String name;
        if (published) {
            mat = org.bukkit.Material.EMERALD_BLOCK;
            name = "&aPublished: Locked";
        } else {
            mat = ready ? org.bukkit.Material.LIME_CONCRETE : org.bukkit.Material.RED_CONCRETE;
            name = ready ? "&aReady to publish: Yes" : "&cReady to publish: No";
        }
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
            it.setItemMeta(meta);
        }
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, it);
        } else if (slot >= inv.getSize()) {
            plugin.getLogger().warning("GUI 'setup.buttons.set-maxfall' slot out of range: " + slot);
        }
    }

    private static void setMaxFallAdjust(ParkourPlugin plugin, Inventory inv, ParkourCourse course) {
        int slot = plugin.getGuiConfig().slot("setup", "buttons.set-maxfall", 20);
        if (slot <= 0) return;
        org.bukkit.Material mat = plugin.getGuiConfig().material("setup", "buttons.set-maxfall", org.bukkit.Material.FEATHER);
        java.util.Map<String, String> ph = java.util.Map.of("distance", String.format("%.1f", course.getMaxFallDistance()));
        String name = plugin.getGuiConfig().name("setup", "buttons.set-maxfall", "&bMax Fall: &f{distance}", ph);
        java.util.List<String> lore = plugin.getGuiConfig().lore("setup", "buttons.set-maxfall", java.util.List.of("&7Click to adjust."), ph);
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            it.setItemMeta(meta);
        }
        inv.setItem(slot, it);
    }
}
