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
        int perPage = 45;
        int pages = Math.max(1, (int) Math.ceil(courses.size() / (double) perPage));
        int current = Math.max(1, Math.min(page, pages));
        String title = plugin.getGuiConfig().title("my-plots", "&2My Parkours | page:{page}/{pages}", java.util.Map.of("page", Integer.toString(current), "pages", Integer.toString(pages)));
        Inventory inv = Bukkit.createInventory(null, 54, title);

        int start = (current - 1) * perPage;
        for (int i = 0; i < perPage && start + i < courses.size(); i++) {
            ParkourCourse c = courses.get(start + i);
            ItemStack item = new ItemStack(plugin.getGuiConfig().itemMaterial("my-plots", Material.GRASS_BLOCK));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String published = c.isPublished() ? ChatColor.GREEN + "Published" : ChatColor.RED + "Unpublished";
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
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        // Controls
        inv.setItem(45, named(plugin.getGuiConfig().material("my-plots", "controls.previous", Material.PAPER), plugin.getGuiConfig().name("my-plots", "controls.previous", "&ePrevious", java.util.Map.of())));
        inv.setItem(49, named(plugin.getGuiConfig().material("my-plots", "controls.browse", Material.COMPASS), plugin.getGuiConfig().name("my-plots", "controls.browse", "&bBrowse Published", java.util.Map.of())));
        inv.setItem(53, named(plugin.getGuiConfig().material("my-plots", "controls.next", Material.PAPER), plugin.getGuiConfig().name("my-plots", "controls.next", "&eNext", java.util.Map.of())));
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
