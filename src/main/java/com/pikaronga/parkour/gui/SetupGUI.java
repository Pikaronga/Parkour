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
        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Required items highlighted when missing
        setRequired(plugin, inv, "setup", "buttons.set-start", course.getStartPlate() == null, "&cRequired: not set");
        setRequired(plugin, inv, "setup", "buttons.set-end", course.getFinishPlate() == null, "&cRequired: not set");
        set(plugin, inv, "setup", "buttons.set-spawn");
        set(plugin, inv, "setup", "buttons.add-checkpoint");
        setRequired(plugin, inv, "setup", "buttons.set-top", course.getTopHologramLocation() == null, "&cRequired: not set");
        set(plugin, inv, "setup", "buttons.set-best");
        set(plugin, inv, "setup", "buttons.set-creator");
        set(plugin, inv, "setup", "buttons.teleport");
        set(plugin, inv, "setup", "buttons.publish");
        // Ready status
        boolean ready = course.getStartPlate() != null && course.getFinishPlate() != null && course.getTopHologramLocation() != null;
        setStatus(plugin, inv, ready);
        set(plugin, inv, "setup", "buttons.delete");
        set(plugin, inv, "setup", "buttons.close");
        return inv;
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
        inv.setItem(slot, it);
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
        inv.setItem(slot, it);
    }

    private static void setStatus(ParkourPlugin plugin, Inventory inv, boolean ready) {
        int slot = plugin.getGuiConfig().slot("setup", "buttons.status", 4);
        org.bukkit.Material mat = ready ? org.bukkit.Material.LIME_CONCRETE : org.bukkit.Material.RED_CONCRETE;
        String name = ready ? "&aReady to publish: Yes" : "&cReady to publish: No";
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
            it.setItemMeta(meta);
        }
        inv.setItem(slot, it);
    }
}
