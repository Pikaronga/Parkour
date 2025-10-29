package com.pikaronga.parkour.gui;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class ConfirmDeleteGUI {
    public static void open(org.bukkit.entity.Player player, ParkourPlugin plugin, ParkourCourse course) {
        String title = plugin.getGuiConfig().title("confirm-delete", "&cDelete &f{course}&c?", Map.of("course", course.getName()));
        int rows = plugin.getGuiConfig().rows("confirm-delete", 3);
        // Resolve target slots with sensible fallbacks so GUI isn't empty if config keys are missing.
        // Support both keys: 'yes'/'no' and 'true'/'false' to avoid YAML 1.1 boolean pitfalls.
        int yesSlot = plugin.getGuiConfig().slot("confirm-delete", "yes", -1);
        if (yesSlot < 0) yesSlot = plugin.getGuiConfig().slot("confirm-delete", "true", 12);
        int noSlot = plugin.getGuiConfig().slot("confirm-delete", "no", -1);
        if (noSlot < 0) noSlot = plugin.getGuiConfig().slot("confirm-delete", "false", 16);
        int maxSlot = Math.max(yesSlot, noSlot);
        if (maxSlot > 0) {
            int neededRows = ((maxSlot + 1) + 8) / 9;
            rows = Math.max(rows, neededRows);
        }
        rows = Math.max(1, Math.min(6, rows));
        Inventory inv = Bukkit.createInventory(new PluginGuiHolder("confirm-delete"), rows * 9, title);
        setVariant(plugin, inv, "confirm-delete", "yes", "true", yesSlot, org.bukkit.Material.RED_WOOL, "yes");
        setVariant(plugin, inv, "confirm-delete", "no", "false", noSlot, org.bukkit.Material.LIME_WOOL, "no");
        player.openInventory(inv);
    }

    private static void set(ParkourPlugin plugin, Inventory inv, String base, String key) {
        // Backward-compatible helper if called elsewhere; allow slot 0
        int slot = plugin.getGuiConfig().slot(base, key, 0);
        if (slot < 0) return;
        org.bukkit.Material mat = plugin.getGuiConfig().material(base, key, org.bukkit.Material.RED_WOOL);
        String name = plugin.getGuiConfig().name(base, key, key, Map.of());
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); it.setItemMeta(meta);}        
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, it);
        } else if (slot >= inv.getSize()) {
            plugin.getLogger().warning("GUI '" + base + "." + key + "' slot out of range: " + slot);
        }
    }

    private static void set(ParkourPlugin plugin, Inventory inv, String base, String key, int slot) {
        if (slot < 0) return;
        org.bukkit.Material mat = plugin.getGuiConfig().material(base, key, org.bukkit.Material.RED_WOOL);
        String name = plugin.getGuiConfig().name(base, key, key, Map.of());
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.setDisplayName(name); it.setItemMeta(meta);}        
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, it);
        } else if (slot >= inv.getSize()) {
            plugin.getLogger().warning("GUI '" + base + "." + key + "' slot out of range: " + slot);
        }
    }

    // Prefer primary key. If it appears missing (empty name), try alternate key (e.g., true/false).
    private static void setVariant(ParkourPlugin plugin, Inventory inv, String base, String primaryKey, String altKey, int slot,
                                   org.bukkit.Material defMat, String defName) {
        if (slot < 0 || slot >= inv.getSize()) {
            // still allow placing if within computed rows gets expanded
        }
        String namePrimary = plugin.getGuiConfig().name(base, primaryKey, "", Map.of());
        String keyUsed = primaryKey;
        String finalName = namePrimary;
        if (finalName == null || finalName.isBlank()) {
            String nameAlt = plugin.getGuiConfig().name(base, altKey, "", Map.of());
            if (nameAlt != null && !nameAlt.isBlank()) {
                keyUsed = altKey;
                finalName = nameAlt;
            } else {
                keyUsed = primaryKey;
                finalName = defName;
            }
        }
        org.bukkit.Material mat = plugin.getGuiConfig().material(base, keyUsed, defMat);
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) { meta.setDisplayName(finalName); it.setItemMeta(meta);}        
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, it);
        } else if (slot >= inv.getSize()) {
            plugin.getLogger().warning("GUI '" + base + "." + keyUsed + "' slot out of range: " + slot);
        }
    }
}
