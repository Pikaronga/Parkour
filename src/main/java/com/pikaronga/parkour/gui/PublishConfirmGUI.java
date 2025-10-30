package com.pikaronga.parkour.gui;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public final class PublishConfirmGUI {

    private PublishConfirmGUI() {}

    public static void open(Player player, ParkourPlugin plugin, ParkourCourse course) {
        if (player == null || course == null) return;
        String title = plugin.getGuiConfig().title("publish-confirm", "&6Publish {course}?", Map.of("course", course.getName()));
        int rows = plugin.getGuiConfig().rows("publish-confirm", 3);
        int confirmSlot = plugin.getGuiConfig().slot("publish-confirm", "confirm", 11);
        int cancelSlot = plugin.getGuiConfig().slot("publish-confirm", "cancel", 15);
        int infoSlot = plugin.getGuiConfig().slot("publish-confirm", "info", 13);
        int maxSlot = Math.max(confirmSlot, Math.max(cancelSlot, infoSlot));
        if (maxSlot >= rows * 9) {
            int neededRows = ((maxSlot + 1) + 8) / 9;
            rows = Math.max(rows, neededRows);
        }
        rows = Math.max(1, Math.min(6, rows));
        Inventory inv = Bukkit.createInventory(new PluginGuiHolder("publish-confirm:" + course.getName().toLowerCase()), rows * 9, title);

        Map<String, String> placeholders = Map.of("course", course.getName());
        setItem(plugin, inv, "publish-confirm", "confirm", confirmSlot, Material.EMERALD_BLOCK, "&aPublish", List.of("&7This will lock further edits."), placeholders);
        setItem(plugin, inv, "publish-confirm", "cancel", cancelSlot, Material.BARRIER, "&cBack", List.of("&7Return to setup without publishing."), placeholders);
        setItem(plugin, inv, "publish-confirm", "info", infoSlot, Material.BOOK, "&eBefore You Publish", List.of("&7Publishing locks all editing.", "&7Test thoroughly before confirming."), placeholders);
        player.openInventory(inv);
    }

    private static void setItem(ParkourPlugin plugin,
                                Inventory inv,
                                String base,
                                String key,
                                int slot,
                                Material defMat,
                                String defName,
                                List<String> defLore,
                                Map<String, String> placeholders) {
        if (slot < 0 || slot >= inv.getSize()) return;
        Material material = plugin.getGuiConfig().material(base, key, defMat);
        String name = plugin.getGuiConfig().name(base, key, defName, placeholders);
        List<String> lore = plugin.getGuiConfig().lore(base, key, defLore, placeholders);
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            stack.setItemMeta(meta);
        }
        inv.setItem(slot, stack);
    }
}
