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
import java.util.Comparator;
import java.util.List;

public class PublishedParkoursGUI {
    public static final String TITLE_PREFIX = ChatColor.DARK_AQUA + "Player Parkours";

    public static void open(Player player, ParkourPlugin plugin, List<ParkourCourse> allCourses, String sort, int page) {
        List<ParkourCourse> courses = new ArrayList<>(allCourses);
        courses.removeIf(pc -> !pc.isPublished());
        sort = normalizeSort(sort);
        sortCourses(courses, sort);
        Inventory inv = build(plugin, courses, sort, page);
        player.openInventory(inv);
    }

    private static String normalizeSort(String s) {
        if (s == null) return "rate";
        return switch (s.toLowerCase()) {
            case "rate", "looks" -> "rate";
            case "difficulty", "diff" -> "difficulty";
            case "name" -> "name";
            default -> "rate";
        };
    }

    private static void sortCourses(List<ParkourCourse> courses, String sort) {
        switch (sort) {
            case "rate" -> courses.sort(Comparator.comparingDouble(ParkourCourse::getAverageLookRating).reversed().thenComparing(ParkourCourse::getName, String.CASE_INSENSITIVE_ORDER));
            case "difficulty" -> courses.sort(Comparator.comparingDouble(PublishedParkoursGUI::effectiveDifficulty).thenComparing(ParkourCourse::getName, String.CASE_INSENSITIVE_ORDER));
            case "name" -> courses.sort(Comparator.comparing(ParkourCourse::getName, String.CASE_INSENSITIVE_ORDER));
        }
    }

    private static double effectiveDifficulty(ParkourCourse c) {
        if (c.getStaffDifficulty() != null) return c.getStaffDifficulty();
        double avg = c.getAverageDifficultyRating();
        return avg <= 0 ? 3.0 : avg;
    }

    public static Inventory build(ParkourPlugin plugin, List<ParkourCourse> courses, String sort, int page) {
        int rows = plugin.getGuiConfig().rows("published", 6);
        // Reserve last row for controls; ensure at least 2 rows total
        rows = Math.max(2, rows);
        int perPage = (rows - 1) * 9;
        int pages = Math.max(1, (int) Math.ceil(courses.size() / (double) perPage));
        int current = Math.max(1, Math.min(page, pages));
        String title = plugin.getGuiConfig().title("published", "&3Player Parkours | sort:{sort} | page:{page}/{pages}", java.util.Map.of("sort", sort, "page", Integer.toString(current), "pages", Integer.toString(pages)));
        Inventory inv = Bukkit.createInventory(new PluginGuiHolder("published"), rows * 9, title);

        int start = (current - 1) * perPage;
        for (int i = 0; i < perPage && start + i < courses.size(); i++) {
            ParkourCourse c = courses.get(start + i);
            ItemStack item = new ItemStack(plugin.getGuiConfig().itemMaterial("published", Material.LIGHT_WEIGHTED_PRESSURE_PLATE));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String creators = "";
                if (!c.getCreators().isEmpty()) {
                    StringBuilder cs = new StringBuilder();
                    for (int idx = 0; idx < c.getCreators().size(); idx++) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(c.getCreators().get(idx));
                        cs.append(op.getName() != null ? op.getName() : c.getCreators().get(idx).toString().substring(0, 8));
                        if (idx < c.getCreators().size() - 1) cs.append(", ");
                    }
                    creators = cs.toString();
                }
                String staff = c.getStaffDifficulty() != null ? Integer.toString(c.getStaffDifficulty()) : "-";
                meta.setDisplayName(plugin.getGuiConfig().itemName("published", "&6{course}", java.util.Map.of("course", c.getName())));
                List<String> lore = plugin.getGuiConfig().itemLore("published", java.util.List.of("&7Looks: &e{looks}", "&7Difficulty: &e{difficulty}", "&7Staff Diff: &c{staffDiff}", "&7By: &b{creators}"), java.util.Map.of(
                        "course", c.getName(),
                        "looks", String.format("%.1f", c.getAverageLookRating()),
                        "difficulty", String.format("%.1f", effectiveDifficulty(c)),
                        "staffDiff", staff,
                        "creators", creators
                ));
                meta.setLore(lore);
                try {
                    // Tag the item with the course name for reliable click handling
                    org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    pdc.set(new org.bukkit.NamespacedKey(plugin, "course-name"), org.bukkit.persistence.PersistentDataType.STRING, c.getName());
                } catch (Throwable ignored) {}
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        // Controls (last row by default; allow slot overrides)
        int base = (rows - 1) * 9;
        int prevSlot = plugin.getGuiConfig().slot("published", "controls.previous", base + 0);
        int myPlotsSlot = plugin.getGuiConfig().slot("published", "controls.my-plots", base + 2);
        int sortSlot = plugin.getGuiConfig().slot("published", "controls.sort", base + 4);
        int nextSlot = plugin.getGuiConfig().slot("published", "controls.next", base + 8);
        if (prevSlot >= 0 && prevSlot < rows * 9) inv.setItem(prevSlot, named(plugin.getGuiConfig().material("published", "controls.previous", Material.PAPER), plugin.getGuiConfig().name("published", "controls.previous", "&ePrevious", java.util.Map.of()))); else if (prevSlot >= rows * 9) plugin.getLogger().warning("GUI 'published.controls.previous' slot out of range: " + prevSlot);
        if (myPlotsSlot >= 0 && myPlotsSlot < rows * 9) inv.setItem(myPlotsSlot, named(plugin.getGuiConfig().material("published", "controls.my-plots", Material.BOOK), plugin.getGuiConfig().name("published", "controls.my-plots", "&aMy Parkours", java.util.Map.of()))); else if (myPlotsSlot >= rows * 9) plugin.getLogger().warning("GUI 'published.controls.my-plots' slot out of range: " + myPlotsSlot);
        if (sortSlot >= 0 && sortSlot < rows * 9) inv.setItem(sortSlot, named(plugin.getGuiConfig().material("published", "controls.sort", Material.COMPARATOR), plugin.getGuiConfig().name("published", "controls.sort", "&bSort: {sort}", java.util.Map.of("sort", sort)))); else if (sortSlot >= rows * 9) plugin.getLogger().warning("GUI 'published.controls.sort' slot out of range: " + sortSlot);
        if (nextSlot >= 0 && nextSlot < rows * 9) inv.setItem(nextSlot, named(plugin.getGuiConfig().material("published", "controls.next", Material.PAPER), plugin.getGuiConfig().name("published", "controls.next", "&eNext", java.util.Map.of()))); else if (nextSlot >= rows * 9) plugin.getLogger().warning("GUI 'published.controls.next' slot out of range: " + nextSlot);
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
