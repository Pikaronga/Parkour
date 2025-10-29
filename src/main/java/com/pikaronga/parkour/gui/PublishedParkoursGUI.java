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
        SortSpec spec = normalizeSort(sort);
        sortCourses(courses, spec);
        Inventory inv = build(plugin, courses, spec, page);
        player.openInventory(inv);
    }

    private record SortSpec(String key, boolean ascending) {}

    private static SortSpec normalizeSort(String s) {
        if (s == null || s.isBlank()) return new SortSpec("looks", false); // default: best -> worst
        String raw = s.toLowerCase();
        String key = raw;
        boolean asc = false;
        if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            key = parts[0];
            asc = parts[1].startsWith("asc");
        }
        key = switch (key) {
            case "rate", "looks" -> "looks";
            case "difficulty", "diff" -> "difficulty";
            case "name" -> "name";
            case "created", "date", "newest" -> "created";
            default -> "looks";
        };
        return new SortSpec(key, asc);
    }

    private static void sortCourses(List<ParkourCourse> courses, SortSpec spec) {
        Comparator<ParkourCourse> cmp;
        switch (spec.key) {
            case "looks" -> cmp = Comparator.comparingDouble(ParkourCourse::getAverageLookRating)
                    .thenComparing(ParkourCourse::getName, String.CASE_INSENSITIVE_ORDER);
            case "difficulty" -> cmp = Comparator.comparingDouble(PublishedParkoursGUI::playerDifficulty)
                    .thenComparing(ParkourCourse::getName, String.CASE_INSENSITIVE_ORDER);
            case "name" -> cmp = Comparator.comparing(ParkourCourse::getName, String.CASE_INSENSITIVE_ORDER);
            case "created" -> cmp = Comparator.comparingInt(ParkourCourse::getCreatedOrder)
                    .thenComparing(ParkourCourse::getName, String.CASE_INSENSITIVE_ORDER);
            default -> cmp = Comparator.comparingDouble(ParkourCourse::getAverageLookRating)
                    .thenComparing(ParkourCourse::getName, String.CASE_INSENSITIVE_ORDER);
        }
        if (!spec.ascending) {
            // Descending for looks (best->worst), difficulty (hard->easy), name (Z->A), created (newest->oldest)
            cmp = cmp.reversed();
        }
        courses.sort(cmp);
    }

    private static double playerDifficulty(ParkourCourse c) {
        double avg = c.getAverageDifficultyRating();
        return Math.max(0.0, avg);
    }

    private static String formatDifficulty(ParkourCourse c) {
        double avg = c.getAverageDifficultyRating();
        return (avg <= 0.0) ? "N/A" : String.format("%.1f/5", avg);
    }

    public static Inventory build(ParkourPlugin plugin, List<ParkourCourse> courses, SortSpec spec, int page) {
        int rows = plugin.getGuiConfig().rows("published", 6);
        // Reserve last row for controls; ensure at least 2 rows total
        rows = Math.max(2, rows);
        int perPage = (rows - 1) * 9;
        int pages = Math.max(1, (int) Math.ceil(courses.size() / (double) perPage));
        int current = Math.max(1, Math.min(page, pages));
        String sortLabel = spec.key + ":" + (spec.ascending ? "asc" : "desc");
        String title = plugin.getGuiConfig().title("published", "&3Player Parkours | sort:{sort} | page:{page}/{pages}", java.util.Map.of("sort", sortLabel, "page", Integer.toString(current), "pages", Integer.toString(pages)));
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
                String staffStr = c.getStaffDifficulty() != null ? (Integer.toString(c.getStaffDifficulty()) + "/5") : "N/A";
                double looksAvg = c.getAverageLookRating();
                String looksStr = looksAvg <= 0.0 ? "N/A" : String.format("%.1f/5", looksAvg);
                meta.setDisplayName(plugin.getGuiConfig().itemName("published", "&6{course}", java.util.Map.of("course", c.getName())));
                String difficultyStr = formatDifficulty(c);
                List<String> lore = plugin.getGuiConfig().itemLore("published", java.util.List.of("&7Looks: &e{looks}", "&7Difficulty: &e{difficulty}", "&7By: &b{creators}"), java.util.Map.of(
                        "course", c.getName(),
                        "looks", looksStr,
                        "difficulty", difficultyStr,
                        "creators", creators,
                        "staffDiff", staffStr
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
        if (sortSlot >= 0 && sortSlot < rows * 9) inv.setItem(sortSlot, named(plugin.getGuiConfig().material("published", "controls.sort", Material.COMPARATOR), plugin.getGuiConfig().name("published", "controls.sort", "&bSort: {sort}", java.util.Map.of("sort", sortLabel)))); else if (sortSlot >= rows * 9) plugin.getLogger().warning("GUI 'published.controls.sort' slot out of range: " + sortSlot);
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



