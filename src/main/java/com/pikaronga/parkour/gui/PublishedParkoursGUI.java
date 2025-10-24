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
        int perPage = 45; // 5 rows
        int pages = Math.max(1, (int) Math.ceil(courses.size() / (double) perPage));
        int current = Math.max(1, Math.min(page, pages));
        String title = plugin.getGuiConfig().title("published", "&3Player Parkours | sort:{sort} | page:{page}/{pages}", java.util.Map.of("sort", sort, "page", Integer.toString(current), "pages", Integer.toString(pages)));
        Inventory inv = Bukkit.createInventory(null, 54, title);

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
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        // Controls
        inv.setItem(45, named(plugin.getGuiConfig().material("published", "controls.previous", Material.PAPER), plugin.getGuiConfig().name("published", "controls.previous", "&ePrevious", java.util.Map.of())));
        inv.setItem(47, named(plugin.getGuiConfig().material("published", "controls.my-plots", Material.BOOK), plugin.getGuiConfig().name("published", "controls.my-plots", "&aMy Parkours", java.util.Map.of())));
        inv.setItem(49, named(plugin.getGuiConfig().material("published", "controls.sort", Material.COMPARATOR), plugin.getGuiConfig().name("published", "controls.sort", "&bSort: {sort}", java.util.Map.of("sort", sort))));
        inv.setItem(53, named(plugin.getGuiConfig().material("published", "controls.next", Material.PAPER), plugin.getGuiConfig().name("published", "controls.next", "&eNext", java.util.Map.of())));
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
