package com.pikaronga.parkour.gui;

import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AdminParkoursGUI {
    public static final String TITLE = ChatColor.DARK_GREEN + "All Player Parkours";

    public static Inventory build(List<ParkourCourse> courses) {
        int size = ((Math.min(courses.size(), 54) + 8) / 9) * 9;
        if (size <= 0) size = 9;
        Inventory inv = Bukkit.createInventory(null, size, TITLE);
        int i = 0;
        for (ParkourCourse c : courses) {
            ItemStack item = new ItemStack(c.isPublished() ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName((c.isPublished() ? ChatColor.GREEN : ChatColor.RED) + c.getName());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Published: " + (c.isPublished() ? ChatColor.GREEN + "Yes" : ChatColor.RED + "No"));
                if (!c.getCreators().isEmpty()) {
                    StringBuilder creators = new StringBuilder();
                    for (int idx = 0; idx < c.getCreators().size(); idx++) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(c.getCreators().get(idx));
                        creators.append(op.getName() != null ? op.getName() : c.getCreators().get(idx).toString().substring(0,8));
                        if (idx < c.getCreators().size() - 1) creators.append(", ");
                    }
                    lore.add(ChatColor.GRAY + "By: " + ChatColor.AQUA + creators);
                }
                if (c.getPlotRegion() != null) {
                    lore.add(ChatColor.GRAY + String.format("Region: %s (%d,%d) size %d",
                            c.getPlotRegion().world(), c.getPlotRegion().minX(), c.getPlotRegion().minZ(), c.getPlotRegion().size()));
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(i++, item);
            if (i >= size) break;
        }
        return inv;
    }
}

