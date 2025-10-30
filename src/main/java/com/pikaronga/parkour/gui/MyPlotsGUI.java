package com.pikaronga.parkour.gui;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MyPlotsGUI {
    public static final String TITLE_PREFIX = ChatColor.DARK_GREEN + "My Parkours";

    public static void open(Player player, ParkourPlugin plugin, List<ParkourCourse> allCourses, int page) {
        UUID me = player.getUniqueId();
        List<ParkourCourse> mine = new ArrayList<>();
        for (ParkourCourse c : allCourses) {
            if (c.getCreators().contains(me)) mine.add(c);
        }
        Inventory inv = build(plugin, mine, page);
        player.openInventory(inv);
    }

    public static Inventory build(ParkourPlugin plugin, List<ParkourCourse> courses, int page) {
        int rows = plugin.getGuiConfig().rows("my-plots", 6);
        rows = Math.max(2, rows); // reserve last row for controls
        int perPage = (rows - 1) * 9;
        int pages = Math.max(1, (int) Math.ceil(courses.size() / (double) perPage));
        int current = Math.max(1, Math.min(page, pages));
        String title = plugin.getGuiConfig().title("my-plots", "&2My Parkours | page:{page}/{pages}", java.util.Map.of("page", Integer.toString(current), "pages", Integer.toString(pages)));
        Inventory inv = Bukkit.createInventory(new PluginGuiHolder("my-plots"), rows * 9, title);

        int start = (current - 1) * perPage;
        for (int i = 0; i < perPage && start + i < courses.size(); i++) {
            ParkourCourse c = courses.get(start + i);
            ItemStack item = new ItemStack(plugin.getGuiConfig().itemMaterial("my-plots", Material.GRASS_BLOCK));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String published = c.isPublished() ? ChatColor.GREEN + "Published (Locked)" : ChatColor.RED + "Unpublished";
                int minX = c.getPlotRegion() != null ? c.getPlotRegion().minX() : 0;
                int minZ = c.getPlotRegion() != null ? c.getPlotRegion().minZ() : 0;
                int size = c.getPlotRegion() != null ? c.getPlotRegion().size() : 0;
                meta.setDisplayName(plugin.getGuiConfig().itemName("my-plots", "&a{course}", java.util.Map.of("course", c.getName())));
                List<String> lore = plugin.getGuiConfig().itemLore("my-plots", java.util.List.of("&7Status: {published}", "&7Region: &f({minX},{minZ}) size {size}"), java.util.Map.of(
                        "course", c.getName(),
                        "published", published,
                        "minX", Integer.toString(minX),
                        "minZ", Integer.toString(minZ),
                        "size", Integer.toString(size)
                ));
                meta.setLore(lore);
                try {
                    org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.set(new org.bukkit.NamespacedKey(plugin, "course-name"), org.bukkit.persistence.PersistentDataType.STRING, c.getName());
                } catch (Throwable ignored) {}
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        // Controls on last row (allow slot overrides)
        int base = (rows - 1) * 9;
        int prevSlot = plugin.getGuiConfig().slot("my-plots", "controls.previous", base + 0);
        int browseSlot = plugin.getGuiConfig().slot("my-plots", "controls.browse", base + 4);
        int createSlot = plugin.getGuiConfig().slot("my-plots", "controls.create", base + 6);
        int nextSlot = plugin.getGuiConfig().slot("my-plots", "controls.next", base + 8);
        if (prevSlot >= 0 && prevSlot < rows * 9) inv.setItem(prevSlot, named(plugin.getGuiConfig().material("my-plots", "controls.previous", Material.PAPER), plugin.getGuiConfig().name("my-plots", "controls.previous", "&ePrevious", java.util.Map.of()))); else if (prevSlot >= rows * 9) plugin.getLogger().warning("GUI 'my-plots.controls.previous' slot out of range: " + prevSlot);
        if (browseSlot >= 0 && browseSlot < rows * 9) inv.setItem(browseSlot, named(plugin.getGuiConfig().material("my-plots", "controls.browse", Material.COMPASS), plugin.getGuiConfig().name("my-plots", "controls.browse", "&bBrowse Published", java.util.Map.of()))); else if (browseSlot >= rows * 9) plugin.getLogger().warning("GUI 'my-plots.controls.browse' slot out of range: " + browseSlot);
        if (createSlot > 0 && createSlot < rows * 9) inv.setItem(createSlot, named(plugin.getGuiConfig().material("my-plots", "controls.create", Material.EMERALD_BLOCK), plugin.getGuiConfig().name("my-plots", "controls.create", "&aCreate New", java.util.Map.of()))); else if (createSlot >= rows * 9) plugin.getLogger().warning("GUI 'my-plots.controls.create' slot out of range: " + createSlot);
        if (nextSlot >= 0 && nextSlot < rows * 9) inv.setItem(nextSlot, named(plugin.getGuiConfig().material("my-plots", "controls.next", Material.PAPER), plugin.getGuiConfig().name("my-plots", "controls.next", "&eNext", java.util.Map.of()))); else if (nextSlot >= rows * 9) plugin.getLogger().warning("GUI 'my-plots.controls.next' slot out of range: " + nextSlot);
        return inv;
    }

    private static ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            it.setItemMeta(meta);
        }
        return it;
    }
}
