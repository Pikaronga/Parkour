package com.pikaronga.parkour.gui;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class GUIListener implements Listener {
    private final ParkourPlugin plugin;

    public GUIListener(ParkourPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        boolean isPublishedGui = title.startsWith(PublishedParkoursGUI.TITLE_PREFIX);
        boolean isMyPlotsGui = title.startsWith(MyPlotsGUI.TITLE_PREFIX);
        boolean isSetupGui = title.startsWith(SetupGUI.TITLE_PREFIX);
        boolean isConfirmDelete = ChatColor.stripColor(title).toLowerCase().startsWith(ChatColor.stripColor(plugin.getGuiConfig().title("confirm-delete", "&cDelete", java.util.Map.of())).toLowerCase().replace("§", ""));
        if (!isPublishedGui && !isMyPlotsGui && !isSetupGui && !isConfirmDelete && !title.equals(AdminParkoursGUI.TITLE)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null || clicked.getItemMeta().getDisplayName() == null) return;
        if (isPublishedGui) {
            // Controls
            if (clicked.getType() == Material.PAPER && ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).equalsIgnoreCase("Previous")) {
                GuiState state = GuiState.fromTitle(title);
                int newPage = Math.max(1, state.page - 1);
                PublishedParkoursGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), state.sort, newPage);
                return;
            }
            if (clicked.getType() == Material.BOOK && ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).equalsIgnoreCase("My Parkours")) {
                MyPlotsGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), 1);
                return;
            }
            if (clicked.getType() == Material.PAPER && ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).equalsIgnoreCase("Next")) {
                GuiState state = GuiState.fromTitle(title);
                PublishedParkoursGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), state.sort, state.page + 1);
                return;
            }
            if (clicked.getType() == Material.COMPARATOR) {
                GuiState state = GuiState.fromTitle(title);
                String nextSort = switch (state.sort) { case "rate" -> "difficulty"; case "difficulty" -> "name"; default -> "rate"; };
                PublishedParkoursGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), nextSort, 1);
                return;
            }
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            ParkourCourse course = plugin.getParkourManager().getCourse(name);
            if (course == null || course.getStartTeleport() == null) return;
            player.closeInventory();
            plugin.getSessionManager().startSession(player, course);
        } else if (isMyPlotsGui) {
            // Controls
            if (clicked.getType() == Material.PAPER && ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).equalsIgnoreCase("Previous")) {
                int page = MyPlotsState.fromTitle(title).page;
                MyPlotsGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), Math.max(1, page - 1));
                return;
            }
            if (clicked.getType() == Material.PAPER && ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).equalsIgnoreCase("Next")) {
                int page = MyPlotsState.fromTitle(title).page;
                MyPlotsGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), page + 1);
                return;
            }
            if (clicked.getType() == Material.COMPASS && ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).equalsIgnoreCase("Browse Published")) {
                PublishedParkoursGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), "rate", 1);
                return;
            }
            // Teleport to selected parkour
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            com.pikaronga.parkour.course.ParkourCourse course = plugin.getParkourManager().getCourse(name);
            if (course == null) return;
            plugin.getPlayerParkourManager().setLastEditingCourse(player.getUniqueId(), course);
            plugin.getPlayerParkourManager().teleportToPlot(player, course);
            player.closeInventory();
            player.sendMessage(plugin.getMessageManager().getMessage("edit-teleport", "&aTeleported to parkour &f{course}&a.", java.util.Map.of("course", course.getName())));
        } else if (isSetupGui) {
            String courseName = ChatColor.stripColor(title).replace("Setup: ", "").trim();
            com.pikaronga.parkour.course.ParkourCourse course = plugin.getParkourManager().getCourse(courseName);
            if (course == null) return;
            Material type = clicked.getType();
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            if (type == Material.BARRIER) {
                player.closeInventory();
                return;
            }
            if (type == Material.COMPASS) {
                plugin.getPlayerParkourManager().setLastEditingCourse(player.getUniqueId(), course);
                plugin.getPlayerParkourManager().teleportToPlot(player, course);
                return;
            }
            // Delegate to existing commands to reuse logic
            if (type == Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
                player.performCommand("parkour psetstart " + course.getName());
            } else if (type == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
                player.performCommand("parkour psetend " + course.getName());
            } else if (type == Material.RED_BED) {
                player.performCommand("parkour psetspawn " + course.getName());
            } else if (type == Material.STONE_PRESSURE_PLATE) {
                player.performCommand("parkour psetcheckpoint " + course.getName());
            } else if (type == Material.BOOK) {
                player.performCommand("parkour psettop " + course.getName());
            } else if (type == Material.NETHER_STAR) {
                player.performCommand("parkour psetbest " + course.getName());
            } else if (type == Material.NAME_TAG) {
                player.performCommand("parkour psetcreatorholo " + course.getName());
            } else if (type == Material.LEVER || name.equalsIgnoreCase("Publish")) {
                player.performCommand("parkour publish " + course.getName());
            } else if (type == Material.CLOCK || name.toLowerCase().contains("test")) {
                player.performCommand("parkour test " + course.getName());
            } else if (type == Material.LAVA_BUCKET || name.toLowerCase().contains("delete")) {
                com.pikaronga.parkour.gui.ConfirmDeleteGUI.open(player, plugin, course);
            }
        } else if (isConfirmDelete) {
            String raw = ChatColor.stripColor(title);
            String courseName = raw.replace("Delete", "").replace("?", "").trim();
            com.pikaronga.parkour.course.ParkourCourse course = plugin.getParkourManager().getCourse(courseName);
            if (course == null) { player.closeInventory(); return; }
            String dn = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).toLowerCase();
            if (dn.contains("confirm")) {
                // owner or admin
                boolean owner = course.getCreators().contains(player.getUniqueId());
                if (!owner && !player.hasPermission("parkour.admin")) {
                    player.sendMessage(plugin.getMessageManager().getMessage("no-permission", "&cYou do not have permission to use this command."));
                    player.closeInventory();
                    return;
                }
                plugin.getHologramManager().removeCourseHolograms(course);
                plugin.getPlayerParkourManager().freePlotForCourse(course);
                plugin.getParkourManager().removeCourse(course.getName());
                plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses());
                player.closeInventory();
                player.sendMessage(plugin.getMessageManager().getMessage("delete-success", "&aDeleted parkour &f{course}&a.", java.util.Map.of("course", course.getName())));
            } else {
                player.closeInventory();
                player.sendMessage(plugin.getMessageManager().getMessage("delete-cancel", "&eCancelled deletion."));
            }
        } else {
            // Admin view: no special actions yet
        }
    }

    private static class GuiState {
        final String sort;
        final int page;
        private GuiState(String sort, int page) { this.sort = sort; this.page = page; }
        static GuiState fromTitle(String title) {
            // Title format: Player Parkours | sort:<sort> | page:X/Y
            String lower = ChatColor.stripColor(title).toLowerCase();
            String sort = "rate";
            int page = 1;
            int sIdx = lower.indexOf("sort:");
            if (sIdx >= 0) {
                int end = lower.indexOf("|", sIdx);
                String part = end > sIdx ? lower.substring(sIdx + 5, end).trim() : lower.substring(sIdx + 5).trim();
                sort = part;
            }
            int pIdx = lower.indexOf("page:");
            if (pIdx >= 0) {
                String part = lower.substring(pIdx + 5).trim();
                int slash = part.indexOf("/");
                if (slash > 0) part = part.substring(0, slash);
                try { page = Integer.parseInt(part); } catch (NumberFormatException ignored) {}
            }
            return new GuiState(sort, page);
        }
    }

    private static class MyPlotsState {
        final int page;
        private MyPlotsState(int page) { this.page = page; }
        static MyPlotsState fromTitle(String title) {
            String lower = ChatColor.stripColor(title).toLowerCase();
            int page = 1;
            int pIdx = lower.indexOf("page:");
            if (pIdx >= 0) {
                String part = lower.substring(pIdx + 5).trim();
                int slash = part.indexOf("/");
                if (slash > 0) part = part.substring(0, slash);
                try { page = Integer.parseInt(part); } catch (NumberFormatException ignored) {}
            }
            return new MyPlotsState(page);
        }
    }
}
