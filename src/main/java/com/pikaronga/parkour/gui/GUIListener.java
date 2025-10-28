package com.pikaronga.parkour.gui;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
        org.bukkit.inventory.Inventory topInv0 = event.getView().getTopInventory();
        boolean oursHolder0 = topInv0 != null && topInv0.getHolder() instanceof PluginGuiHolder;
        boolean isPublishedGui = title.startsWith(PublishedParkoursGUI.TITLE_PREFIX);
        boolean isMyPlotsGui = title.startsWith(MyPlotsGUI.TITLE_PREFIX);
        boolean isSetupGui = title.startsWith(SetupGUI.TITLE_PREFIX);
        boolean isConfirmDelete = ChatColor.stripColor(title).toLowerCase().startsWith(ChatColor.stripColor(plugin.getGuiConfig().title("confirm-delete", "&cDelete", java.util.Map.of())).toLowerCase().replace("Â§", ""));
        if (oursHolder0) { event.setCancelled(true); if (plugin.getConfigManager().debugEnabled()) plugin.getLogger().info("GUI detected via holder; cancelling to protect."); }
        if (!oursHolder0 && !isPublishedGui && !isMyPlotsGui && !isSetupGui && !isConfirmDelete && !title.equals(AdminParkoursGUI.TITLE)) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getItemMeta() == null || clicked.getItemMeta().getDisplayName() == null) return;
        // If this is one of our plugin GUIs, map the holder id to the GUI flags
        if (oursHolder0) {
            PluginGuiHolder holder = (PluginGuiHolder) topInv0.getHolder();
            String id = holder.getId();
            isPublishedGui = "published".equals(id);
            isMyPlotsGui = "my-plots".equals(id);
            // Accept either plain "setup" or the per-course variant "setup:<course>"
            isSetupGui = "setup".equals(id) || (id != null && id.startsWith("setup:"));
            isConfirmDelete = "confirm-delete".equals(id);
        }
        if (isPublishedGui) {
            // Prefer configured slots for controls if present
            try {
                int rowsCfg = plugin.getGuiConfig().rows("published", 6);
                int baseCfg = (rowsCfg - 1) * 9;
                int prevSlotCfg = plugin.getGuiConfig().slot("published", "controls.previous", baseCfg + 0);
                int myPlotsSlotCfg = plugin.getGuiConfig().slot("published", "controls.my-plots", baseCfg + 2);
                int sortSlotCfg = plugin.getGuiConfig().slot("published", "controls.sort", baseCfg + 4);
                int nextSlotCfg = plugin.getGuiConfig().slot("published", "controls.next", baseCfg + 8);
                int raw = event.getRawSlot();
                if (raw == prevSlotCfg) {
                    GuiState state = GuiState.fromTitle(title);
                    int newPage = Math.max(1, state.page - 1);
                    PublishedParkoursGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), state.sort, newPage);
                    return;
                }
                if (raw == myPlotsSlotCfg) {
                    MyPlotsGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), 1);
                    return;
                }
                if (raw == nextSlotCfg) {
                    GuiState state = GuiState.fromTitle(title);
                    PublishedParkoursGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), state.sort, state.page + 1);
                    return;
                }
                if (raw == sortSlotCfg) {
                    GuiState state = GuiState.fromTitle(title);
                    String nextSort = nextSort(state.sort);
                    PublishedParkoursGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), nextSort, 1);
                    return;
                }
            } catch (Throwable ignored) {}
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
                String nextSort = nextSort(state.sort);
                PublishedParkoursGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), nextSort, 1);
                return;
            }
            String courseTag = null;
            try {
                courseTag = clicked.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "course-name"), org.bukkit.persistence.PersistentDataType.STRING);
            } catch (Throwable ignored) {}
            ParkourCourse course = courseTag != null ? plugin.getParkourManager().getCourse(courseTag) : plugin.getParkourManager().getCourse(ChatColor.stripColor(clicked.getItemMeta().getDisplayName()));
            if (course == null) return;
            player.closeInventory();
            // Teleport viewers to Finish Spawn (or fallback) instead of starting a session
            org.bukkit.Location dest = course.getFinishTeleport();
            if (dest == null) {
                // Fallback: world spawn if same world, otherwise start teleport or center of plot
                org.bukkit.Location alt = course.getStartTeleport();
                if (alt != null && alt.getWorld() != null) {
                    dest = alt.clone();
                } else if (alt != null) {
                    dest = alt.clone();
                } else if (course.getPlotRegion() != null && plugin.getPlayerParkourManager().getWorld() != null) {
                    com.pikaronga.parkour.player.PlotRegion r = course.getPlotRegion();
                    int y = plugin.getConfigManager().isVoidWorld() ? plugin.getConfigManager().getPlatformY() : plugin.getPlayerParkourManager().getWorld().getHighestBlockYAt(r.minX() + r.size() / 2, r.minZ() + r.size() / 2) + 1;
                    dest = new org.bukkit.Location(plugin.getPlayerParkourManager().getWorld(), r.minX() + r.size() / 2.0, y + 1.0, r.minZ() + r.size() / 2.0);
                }
            }
            if (dest != null) {
                try {
                    // Ensure chunk is loaded and teleport on main thread; clear any per-player border
                    org.bukkit.World w = dest.getWorld();
                    if (w != null) {
                        int cx = dest.getBlockX() >> 4;
                        int cz = dest.getBlockZ() >> 4;
                        if (!w.isChunkLoaded(cx, cz)) {
                            w.getChunkAt(cx, cz).load(true);
                        }
                    }
                } catch (Throwable ignored) {}
                try { player.setWorldBorder(null); } catch (Throwable ignored) {}
                try { player.setGameMode(org.bukkit.GameMode.ADVENTURE); } catch (Throwable ignored) {}
                org.bukkit.Location tp = dest.clone();
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> player.teleport(tp));
            }
        } else if (isMyPlotsGui) {
            // Prefer configured slots for controls if present
            try {
                int rowsCfg = plugin.getGuiConfig().rows("my-plots", 6);
                int baseCfg = (rowsCfg - 1) * 9;
                int prevSlotCfg = plugin.getGuiConfig().slot("my-plots", "controls.previous", baseCfg + 0);
                int browseSlotCfg = plugin.getGuiConfig().slot("my-plots", "controls.browse", baseCfg + 4);
                int createSlotCfg = plugin.getGuiConfig().slot("my-plots", "controls.create", baseCfg + 6);
                int nextSlotCfg = plugin.getGuiConfig().slot("my-plots", "controls.next", baseCfg + 8);
                int raw = event.getRawSlot();
                if (raw == prevSlotCfg) {
                    int page = MyPlotsState.fromTitle(title).page;
                    MyPlotsGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), Math.max(1, page - 1));
                    return;
                }
                if (raw == nextSlotCfg) {
                    int page = MyPlotsState.fromTitle(title).page;
                    MyPlotsGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), page + 1);
                    return;
                }
                if (raw == browseSlotCfg) {
                    PublishedParkoursGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), "looks:desc", 1);
                    return;
                }
                if (raw == createSlotCfg) {
                    player.performCommand("parkour create");
                    player.closeInventory();
                    return;
                }
            } catch (Throwable ignored) {}
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
                PublishedParkoursGUI.open(player, plugin, new java.util.ArrayList<>(plugin.getParkourManager().getCourses().values()), "looks:desc", 1);
                return;
            }
            if (clicked.getType() == Material.EMERALD_BLOCK && ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).equalsIgnoreCase("Create New")) {
                com.pikaronga.parkour.gui.SetupInputListener.requestCreate(plugin, player);
                player.closeInventory();
                return;
            }
            // Teleport to selected parkour (prefer tag over name)
            String taggedCourse = null;
            try { taggedCourse = clicked.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "course-name"), org.bukkit.persistence.PersistentDataType.STRING); } catch (Throwable ignored) {}
            String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            com.pikaronga.parkour.course.ParkourCourse course = plugin.getParkourManager().getCourse(taggedCourse != null ? taggedCourse : name);
            if (course == null) return;
            plugin.getPlayerParkourManager().setLastEditingCourse(player.getUniqueId(), course);
            plugin.getPlayerParkourManager().teleportToPlot(player, course);
            player.closeInventory();
            player.sendMessage(plugin.getMessageManager().getMessage("edit-teleport", "&aTeleported to parkour &f{course}&a.", java.util.Map.of("course", course.getName())));
        } else if (isSetupGui) {
            String courseName = null;
            if (oursHolder0) {
                try {
                    PluginGuiHolder holder = (PluginGuiHolder) event.getView().getTopInventory().getHolder();
                    if (holder != null) {
                        String id = holder.getId();
                        if (id != null && id.startsWith("setup:")) {
                            courseName = id.substring("setup:".length());
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (courseName == null) {
                // Fallback to title parsing if holder data is unavailable
                courseName = ChatColor.stripColor(title).replace("Setup: ", "").trim().toLowerCase();
            }
            com.pikaronga.parkour.course.ParkourCourse course = plugin.getParkourManager().getCourse(courseName);
            if (course == null) return;
            // Enforce ownership or admin permission for setup actions
            try {
                com.pikaronga.parkour.player.PlayerParkourManager ppm = plugin.getPlayerParkourManager();
                boolean owner = ppm != null && ppm.isOwner(player, course);
                if (!owner && !player.hasPermission("parkour.admin")) {
                    player.sendMessage(plugin.getMessageManager().getMessage("no-permission", "&cYou can only setup your own parkour."));
                    player.closeInventory();
                    return;
                }
            } catch (Throwable ignored) {}
            int raw = event.getRawSlot();
            // Resolve slots for all configured buttons (accept both base and -done variants)
            int ss = plugin.getGuiConfig().slot("setup", "buttons.set-start", -1);
            int ssd = plugin.getGuiConfig().slot("setup", "buttons.set-start-done", ss);
            int se = plugin.getGuiConfig().slot("setup", "buttons.set-end", -1);
            int sed = plugin.getGuiConfig().slot("setup", "buttons.set-end-done", se);
            int sp = plugin.getGuiConfig().slot("setup", "buttons.set-spawn", -1);
            int spd = plugin.getGuiConfig().slot("setup", "buttons.set-spawn-done", sp);
            int cp = plugin.getGuiConfig().slot("setup", "buttons.add-checkpoint", -1);
            int top = plugin.getGuiConfig().slot("setup", "buttons.set-top", -1);
            int topd = plugin.getGuiConfig().slot("setup", "buttons.set-top-done", top);
            int best = plugin.getGuiConfig().slot("setup", "buttons.set-best", -1);
            int bestd = plugin.getGuiConfig().slot("setup", "buttons.set-best-done", best);
            int creator = plugin.getGuiConfig().slot("setup", "buttons.set-creator", -1);
            int creatord = plugin.getGuiConfig().slot("setup", "buttons.set-creator-done", creator);
            int rename = plugin.getGuiConfig().slot("setup", "buttons.rename", -1);
            int maxfall = plugin.getGuiConfig().slot("setup", "buttons.set-maxfall", -1);
            int tp = plugin.getGuiConfig().slot("setup", "buttons.teleport", -1);
            int publish = plugin.getGuiConfig().slot("setup", "buttons.publish", -1);
            int delete = plugin.getGuiConfig().slot("setup", "buttons.delete", -1);
            int close = plugin.getGuiConfig().slot("setup", "buttons.close", -1);

            boolean refresh = false;
            if (raw == ss || raw == ssd) { player.performCommand("parkour psetstart " + course.getName()); refresh = true; }
            else if (raw == se || raw == sed) { player.performCommand("parkour psetend " + course.getName()); refresh = true; }
            else if (raw == sp || raw == spd) { player.performCommand("parkour psetspawn " + course.getName()); refresh = true; }
            else if (raw == cp) { player.performCommand("parkour psetcheckpoint " + course.getName()); refresh = true; }
            else if (raw == top || raw == topd) { player.performCommand("parkour psettop " + course.getName()); refresh = true; }
            else if (raw == best || raw == bestd) { player.performCommand("parkour psetbest " + course.getName()); refresh = true; }
            else if (raw == creator || raw == creatord) { player.performCommand("parkour psetcreatorholo " + course.getName()); refresh = true; }
            else if (raw == rename) { com.pikaronga.parkour.gui.SetupInputListener.requestRename(plugin, player, course); player.closeInventory(); return; }
            else if (raw == maxfall) { com.pikaronga.parkour.gui.SetupInputListener.requestMaxFall(plugin, player, course); player.closeInventory(); return; }
            else if (raw == tp) { plugin.getPlayerParkourManager().setLastEditingCourse(player.getUniqueId(), course); plugin.getPlayerParkourManager().teleportToPlot(player, course); return; }
            else if (raw == publish) { player.performCommand("parkour publish " + course.getName()); }
            else if (raw == delete) { com.pikaronga.parkour.gui.ConfirmDeleteGUI.open(player, plugin, course); }
            else if (raw == close) { player.closeInventory(); return; }

            if (refresh) {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> com.pikaronga.parkour.gui.SetupGUI.open(player, plugin, course));
            }
        } else if (isConfirmDelete) {
            String raw = ChatColor.stripColor(title);
            String courseName = raw.replace("Delete", "").replace("?", "").trim();
            com.pikaronga.parkour.course.ParkourCourse course = plugin.getParkourManager().getCourse(courseName);
            if (course == null) { player.closeInventory(); return; }
            int yesSlot = plugin.getGuiConfig().slot("confirm-delete", "yes", 11);
            int noSlot = plugin.getGuiConfig().slot("confirm-delete", "no", 15);
            int rawSlot = event.getRawSlot();
            if (rawSlot == yesSlot) {
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
            } else if (rawSlot == noSlot) {
                player.closeInventory();
                player.sendMessage(plugin.getMessageManager().getMessage("delete-cancel", "&eCancelled deletion."));
            }
        } else {
            // Admin view: no special actions yet
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = event.getView().getTitle();
        String tStripped = ChatColor.stripColor(title).replace("Ã‚Â§", "");
        String pubPrefix = ChatColor.stripColor(plugin.getGuiConfig().titlePrefix("published", "&3Player Parkours")).replace("Ã‚Â§", "");
        String plotsPrefix = ChatColor.stripColor(plugin.getGuiConfig().titlePrefix("my-plots", "&2My Parkours")).replace("Ã‚Â§", "");
        String setupPrefix = ChatColor.stripColor(plugin.getGuiConfig().titlePrefix("setup", "&3Setup: ")).replace("Ã‚Â§", "");
        String delPrefix = ChatColor.stripColor(plugin.getGuiConfig().titlePrefix("confirm-delete", "&cDelete ")).replace("Ã‚Â§", "");
        boolean isPublishedGui = tStripped.toLowerCase().startsWith(pubPrefix.toLowerCase());
        boolean isMyPlotsGui = tStripped.toLowerCase().startsWith(plotsPrefix.toLowerCase());
        boolean isSetupGui = tStripped.toLowerCase().startsWith(setupPrefix.toLowerCase());
        boolean isConfirmDelete = tStripped.toLowerCase().startsWith(delPrefix.toLowerCase());
        if (!isPublishedGui && !isMyPlotsGui && !isSetupGui && !isConfirmDelete && !title.equals(AdminParkoursGUI.TITLE)) return;
        event.setCancelled(true);
    }

    private static class GuiState {
        final String sort;
        final int page;
        private GuiState(String sort, int page) { this.sort = sort; this.page = page; }
        static GuiState fromTitle(String title) {
            // Title format: Player Parkours | sort:<sort> | page:X/Y
            String lower = ChatColor.stripColor(title).toLowerCase();
            String sort = "looks:desc";
            int page = 1;
            int sIdx = lower.indexOf("sort:");
            if (sIdx < 0) {
                sIdx = lower.indexOf("sort by:");
            }
            if (sIdx >= 0) {
                int end = lower.indexOf("|", sIdx);
                int valueStart = lower.startsWith("sort by:", sIdx) ? (sIdx + 8) : (sIdx + 5);
                String part = end > sIdx ? lower.substring(valueStart, end).trim() : lower.substring(valueStart).trim();
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

    private static String nextSort(String current) {
        String[] cycle = new String[]{
                "looks:desc", "looks:asc",
                "difficulty:asc", "difficulty:desc",
                "name:asc", "name:desc",
                "created:desc", "created:asc"
        };
        String cur = (current == null || current.isBlank()) ? cycle[0] : current.toLowerCase();
        int idx = 0;
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i].equals(cur) || cycle[i].startsWith(cur + ":") || cur.startsWith(cycle[i] + ":")) { idx = i; break; }
        }
        return cycle[(idx + 1) % cycle.length];
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


