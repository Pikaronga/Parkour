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
        int maxSlot = 0;
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("confirm-delete", "yes", 0));
        maxSlot = Math.max(maxSlot, plugin.getGuiConfig().slot("confirm-delete", "no", 0));
        if (maxSlot > 0) {
            int neededRows = ((maxSlot + 1) + 8) / 9;
            rows = Math.max(rows, neededRows);
        }
        rows = Math.max(1, Math.min(6, rows));
        Inventory inv = Bukkit.createInventory(new PluginGuiHolder("confirm-delete"), rows * 9, title);
        set(plugin, inv, "confirm-delete", "yes");
        set(plugin, inv, "confirm-delete", "no");
        player.openInventory(inv);
    }

    private static void set(ParkourPlugin plugin, Inventory inv, String base, String key) {
        int slot = plugin.getGuiConfig().slot(base, key, 0);
        if (slot <= 0) return;
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
}
