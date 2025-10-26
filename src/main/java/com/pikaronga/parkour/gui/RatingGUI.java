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

public class RatingGUI {
    public static final String TITLE_PREFIX = ChatColor.GREEN + "Rate: ";

    public static void open(Player player, ParkourPlugin plugin, ParkourCourse course) {
        Inventory inv = build(plugin, course);
        player.openInventory(inv);
    }

    public static Inventory build(ParkourPlugin plugin, ParkourCourse course) {
        String title = plugin.getGuiConfig().title("rating", "&aRate: {course}", Map.of("course", course.getName()));
        int rows = plugin.getGuiConfig().rows("rating", 3);
        rows = Math.max(1, Math.min(6, rows));
        Inventory inv = Bukkit.createInventory(new PluginGuiHolder("rating"), rows * 9, title);

        for (int i = 1; i <= 5; i++) {
            set(plugin, inv, "rating", "looks." + i, i, course);
            set(plugin, inv, "rating", "difficulty." + i, i, course);
        }
        return inv;
    }

    private static void set(ParkourPlugin plugin, Inventory inv, String base, String key, int value, ParkourCourse course) {
        int defSlot;
        if (key.startsWith("looks.")) {
            int i = value; // 1..5
            defSlot = 9 + i; // 10..14
        } else {
            int i = value; // 1..5
            defSlot = 9 + 4 + i; // 14..18 -> 13..? but base index, results in 14..18; OK
        }
        int slot = plugin.getGuiConfig().slot(base, key, defSlot);
        if (slot <= 0) return;
        Material mat = plugin.getGuiConfig().material(base, key, key.startsWith("looks.") ? Material.LIME_DYE : Material.RED_DYE);
        String name = plugin.getGuiConfig().name(base, key, (key.startsWith("looks.") ? "&eLooks: {value}" : "&cDifficulty: {value}"), Map.of("value", Integer.toString(value), "course", course.getName()));
        List<String> lore = plugin.getGuiConfig().lore(base, key, List.of(), Map.of("value", Integer.toString(value), "course", course.getName()));
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
}
