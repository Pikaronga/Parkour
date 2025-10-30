package com.pikaronga.parkour.player;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.config.ConfigManager;
import com.pikaronga.parkour.course.ParkourCourse;
import com.pikaronga.parkour.util.ParkourManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;

import java.util.*;

public class PlayerParkourManager {
    private final ParkourPlugin plugin;
    private final ConfigManager config;
    private final ParkourManager parkourManager;

    // Simple grid allocator: index -> (gridX, gridZ)
    private final Set<Long> usedChunks = new HashSet<>();
    private final Map<UUID, GameMode> lastGamemode = new HashMap<>();
    private final Map<UUID, org.bukkit.inventory.ItemStack[]> savedContents = new HashMap<>();
    private final Map<UUID, org.bukkit.inventory.ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, org.bukkit.inventory.ItemStack[]> savedExtra = new HashMap<>();
    private final Map<UUID, org.bukkit.inventory.ItemStack> savedOffhand = new HashMap<>();
    private final Map<UUID, Long> lastCreateTime = new HashMap<>();
    private final Map<UUID, String> lastEditingCourse = new HashMap<>();
    private final Map<UUID, String> testingCourses = new HashMap<>();

    public PlayerParkourManager(ParkourPlugin plugin, ConfigManager config, ParkourManager parkourManager) {
        this.plugin = plugin;
        this.config = config;
        this.parkourManager = parkourManager;
        ensureWorld(plugin, config);
        // Pre-mark used plots from existing courses
        refreshPlotUsage();
    }

    public World getWorld() {
        return Bukkit.getWorld(config.getPlayerWorldName());
    }

    public ParkourPlugin getPlugin() {
        return plugin;
    }

    public boolean canCreateNow(UUID playerId) {
        int cd = config.getCreateCooldownSeconds();
        if (cd <= 0) return true;
        Long last = lastCreateTime.get(playerId);
        if (last == null) return true;
        long now = System.currentTimeMillis();
        return now - last >= cd * 1000L;
    }

    public int secondsUntilCreate(UUID playerId) {
        int cd = config.getCreateCooldownSeconds();
        if (cd <= 0) return 0;
        Long last = lastCreateTime.get(playerId);
        if (last == null) return 0;
        long now = System.currentTimeMillis();
        long rem = cd * 1000L - (now - last);
        return (int) Math.max(0L, (rem + 999) / 1000); // ceil
    }

    public void markCreatedNow(UUID playerId) {
        lastCreateTime.put(playerId, System.currentTimeMillis());
    }

    public void setLastEditingCourse(UUID playerId, ParkourCourse course) {
        if (course != null) lastEditingCourse.put(playerId, course.getName().toLowerCase());
    }

    public ParkourCourse getLastEditingCourse(UUID playerId) {
        String name = lastEditingCourse.get(playerId);
        return name == null ? null : parkourManager.getCourse(name);
    }

    public ParkourCourse findAnyOwnedCourse(UUID playerId) {
        for (ParkourCourse c : parkourManager.getCourses().values()) {
            if (c.getCreators().contains(playerId)) return c;
        }
        return null;
    }

    public void teleportToPlot(Player player, ParkourCourse course) {
        if (course == null || course.getPlotRegion() == null) return;
        Location tp = course.getStartTeleport();
        if (tp == null) {
            int y = config.isVoidWorld() ? config.getPlatformY() : getWorld().getHighestBlockYAt(course.getPlotRegion().minX() + course.getPlotRegion().size() / 2, course.getPlotRegion().minZ() + course.getPlotRegion().size() / 2) + 1;
            tp = new Location(getWorld(), course.getPlotRegion().minX() + course.getPlotRegion().size() / 2.0, y + 1.0, course.getPlotRegion().minZ() + course.getPlotRegion().size() / 2.0);
        }
        // Avoid landing directly on the start plate to prevent auto-start
        Location safe = tp.clone();
        try {
            org.bukkit.block.Block destBlock = safe.getBlock();
            boolean onPlate = destBlock.getType() != null && destBlock.getType().name().endsWith("_PRESSURE_PLATE");
            boolean equalsStartPlate = course.getStartPlate() != null && com.pikaronga.parkour.util.LocationUtil.isSameBlock(course.getStartPlate(), safe);
            if (onPlate || equalsStartPlate) {
                safe = safe.clone().add(1.0, 1.0, 0.0);
            }
        } catch (Throwable ignored) {}
        player.teleport(safe);
        handleEnterPlot(player);
    }

    public void startTesting(UUID playerId, ParkourCourse course) {
        testingCourses.put(playerId, course.getName().toLowerCase());
    }

    public void stopTesting(UUID playerId) {
        testingCourses.remove(playerId);
    }

    public boolean isTesting(UUID playerId, ParkourCourse course) {
        String name = testingCourses.get(playerId);
        return name != null && course != null && course.getName().equalsIgnoreCase(name);
    }

    public static void ensureWorld(ParkourPlugin plugin, ConfigManager config) {
        String worldName = config.getPlayerWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            return;
        }

        // Pre-create world folder and ensure any JSON files we rely on are valid (non-empty)
        java.io.File container = Bukkit.getWorldContainer();
        java.io.File worldFolder = new java.io.File(container, worldName);
        if (!worldFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            worldFolder.mkdirs();
        }
        // Some server paths expect a datapacks config JSON; if it's empty or just "{}" it causes MapLike parsing errors
        // Best-effort: if datapacks.json exists but is empty or equals "{}" then delete it so Paper won't try to parse it.
        try {
            java.io.File dpFolder = new java.io.File(worldFolder, "datapacks");
            if (!dpFolder.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dpFolder.mkdirs();
            }
            java.io.File dpJson = new java.io.File(worldFolder, "datapacks.json");
            if (dpJson.exists()) {
                try {
                    String content = java.nio.file.Files.readString(dpJson.toPath());
                    if (content == null || content.trim().isEmpty() || content.trim().equals("{}")) {
                        // remove the problematic file so Paper won't parse an empty/invalid MapLike
                        try {
                            if (dpJson.delete()) {
                                plugin.getLogger().info("Removed empty or invalid datapacks.json from world folder '" + worldName + "' to avoid MapLike parsing errors.");
                            } else {
                                plugin.getLogger().warning("Failed to delete empty datapacks.json in world folder '" + worldName + "'.");
                            }
                        } catch (Throwable ignored) {
                            try { dpJson.delete(); } catch (Throwable ignored2) {}
                        }
                    }
                } catch (Throwable t) {
                    // if we can't read it, try to delete it to be safe
                    try {
                        if (dpJson.delete()) {
                            plugin.getLogger().info("Deleted unreadable datapacks.json from world folder '" + worldName + "' to avoid MapLike parsing errors.");
                        } else {
                            plugin.getLogger().warning("Failed to delete unreadable datapacks.json in world folder '" + worldName + "'.");
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }

        WorldCreator wc = new WorldCreator(worldName);
        // set explicit environment and type to avoid Paper reading invalid datapack JSON
        wc.environment(org.bukkit.World.Environment.NORMAL);
        wc.type(org.bukkit.WorldType.FLAT);
        if (config.isVoidWorld()) {
            // Prefer an installed VoidGen plugin if present (it may provide a better, server-integrated void generator).
            boolean voidgenPresent = false;
            try {
                org.bukkit.plugin.Plugin p1 = Bukkit.getPluginManager().getPlugin("VoidGen");
                org.bukkit.plugin.Plugin p2 = Bukkit.getPluginManager().getPlugin("Voidgen");
                org.bukkit.plugin.Plugin p3 = Bukkit.getPluginManager().getPlugin("voidgen");
                if (p1 != null || p2 != null || p3 != null) voidgenPresent = true;
            } catch (Throwable ignored) {}

            if (voidgenPresent) {
                // Let the server/plugin-provided generator handle world generation for maximum compatibility.
                plugin.getLogger().info("VoidGen detected; delegating void generation to installed plugin for world '" + worldName + "'.");
            } else {
                // Fall back to our simple built-in VoidChunkGenerator
                wc.generator(new VoidChunkGenerator());
            }
        }

        org.bukkit.World created = null;
        try {
            created = Bukkit.createWorld(wc);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to create player parkour world '" + worldName + "': " + t.getMessage());
        }

        if (created != null) {
            try { created.setKeepSpawnInMemory(true); } catch (Throwable ignored) {}
            try { created.setAutoSave(true); } catch (Throwable ignored) {}
            // load spawn chunk and schedule a delayed verification/tick to ensure entity trackers are active
            try { created.getChunkAt(0, 0).load(true); } catch (Throwable ignored) {}
            final org.bukkit.World wref = created;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    wref.getChunkAt(0, 0).load(true);
                    wref.setKeepSpawnInMemory(true);
                    wref.setAutoSave(true);
                    plugin.getLogger().info("Ensured player parkour world '" + worldName + "' is fully loaded and ticking.");
                } catch (Throwable t) {
                    plugin.getLogger().warning("Failed to finalize loading of player parkour world '" + worldName + "': " + t.getMessage());
                }
            }, 40L);
            plugin.getLogger().info("Ensured player parkour world '" + worldName + "' is loaded.");
        } else {
            plugin.getLogger().warning("Player parkour world '" + worldName + "' could not be created.");
        }
    }

    public void refreshPlotUsage() {
        usedChunks.clear();
        for (ParkourCourse c : parkourManager.getCourses().values()) {
            if (c.getPlotRegion() != null) {
                usedChunks.add(key(c.getPlotRegion().minX(), c.getPlotRegion().minZ()));
            }
        }
    }

    private static class VoidChunkGenerator extends org.bukkit.generator.ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, java.util.Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world); // empty
        }
    }

    public Optional<ParkourCourse> getCourseByLocation(Location location) {
        if (location == null) return Optional.empty();
        for (ParkourCourse c : parkourManager.getCourses().values()) {
            PlotRegion r = c.getPlotRegion();
            if (r != null && r.contains(location)) return Optional.of(c);
        }
        return Optional.empty();
    }

    public boolean isOwner(Player player, ParkourCourse course) {
        return player != null && isOwner(player.getUniqueId(), course);
    }

    public boolean isOwner(UUID uuid, ParkourCourse course) {
        return uuid != null && course != null && course.getCreators().contains(uuid);
    }

    public boolean canEdit(Player player, ParkourCourse course) {
        return player != null && canEdit(player.getUniqueId(), course);
    }

    public boolean canEdit(UUID uuid, ParkourCourse course) {
        return uuid != null && course != null && course.getCreators().contains(uuid) && !course.isPublished();
    }

    public ParkourCourse createPlayerCourse(Player player, String name) {
        ParkourCourse existing = parkourManager.getCourse(name);
        if (existing != null) return null;
        long next = findNextFreePlotKey();
        int size = config.getPlotSize();
        int gap = config.getPlotGap();
        int gridX = (int)(next >> 32);
        int gridZ = (int)(next & 0xffffffffL);
        int minX = gridX * (size + gap);
        int minZ = gridZ * (size + gap);
        PlotRegion region = new PlotRegion(config.getPlayerWorldName(), minX, minZ, size);
        ParkourCourse course = parkourManager.getOrCreate(name);
        course.addCreator(player.getUniqueId());
        course.setPlotRegion(region);
        int py = config.isVoidWorld() ? config.getPlatformY() : getWorld().getHighestBlockYAt(minX + size / 2, minZ + size / 2) + 1;
        Location center = new Location(getWorld(), minX + size / 2.0, py + 1.0, minZ + size / 2.0);
        if (config.isVoidWorld()) {
            buildPlatform(center.clone().subtract(0, 1, 0), config.getPlatformSize(), config.getPlatformBlock());
        }
        course.setStartTeleport(center.clone());
        course.setFinishTeleport(center.clone());
        usedChunks.add(key(minX, minZ));
        plugin.getStorage().saveCourses(plugin.getParkourManager().getCourses());
        if (plugin.getConfigManager().outlineEnabled() && !plugin.getConfigManager().isVoidWorld()) {
            drawPlotOutline(region);
        }
        return course;
    }

    private long findNextFreePlotKey() {
        // spiral-ish scan over small range
        for (int r = 0; r < 10000; r++) {
            for (int x = -r; x <= r; x++) {
                int z = r;
                if (!usedChunks.contains(key(xBase(x), zBase(z)))) return keyIndex(x, z);
                z = -r;
                if (!usedChunks.contains(key(xBase(x), zBase(z)))) return keyIndex(x, z);
            }
            for (int z = -r + 1; z <= r - 1; z++) {
                int x = r;
                if (!usedChunks.contains(key(xBase(x), zBase(z)))) return keyIndex(x, z);
                x = -r;
                if (!usedChunks.contains(key(xBase(x), zBase(z)))) return keyIndex(x, z);
            }
        }
        return keyIndex(0, 0);
    }

    private int xBase(int gridX) {
        return gridX * (config.getPlotSize() + config.getPlotGap());
    }
    private int zBase(int gridZ) {
        return gridZ * (config.getPlotSize() + config.getPlotGap());
    }
    private long keyIndex(int gridX, int gridZ) {
        return ((long)gridX << 32) | (gridZ & 0xffffffffL);
    }
    private long key(int minX, int minZ) {
        return ((long)minX << 32) | (minZ & 0xffffffffL);
    }

    public void freePlotForCourse(com.pikaronga.parkour.course.ParkourCourse course) {
        if (course.getPlotRegion() != null) {
            usedChunks.remove(key(course.getPlotRegion().minX(), course.getPlotRegion().minZ()));
        }
    }

    /**
     * Reset/remove all player-placed blocks inside the plot region of a course and free the plot.
     * - In void worlds: clears a vertical slice around the platform Y and rebuilds the platform.
     * - In normal worlds: clears blocks above natural terrain top surface and redraws outline if enabled.
     */
    public void resetPlotForCourse(com.pikaronga.parkour.course.ParkourCourse course) {
        if (course == null) return;
        PlotRegion region = course.getPlotRegion();
        World w = getWorld();
        if (region == null || w == null) { freePlotForCourse(course); return; }

        int minX = region.minX();
        int minZ = region.minZ();
        int maxX = region.maxX();
        int maxZ = region.maxZ();

        boolean voidWorld = config.isVoidWorld();
        int platformY = config.getPlatformY();
        int maxY = Math.max(1, w.getMaxHeight() - 1);

        // Clear blocks inside the region
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (voidWorld) {
                    int clearFrom = Math.max(0, platformY - 1); // keep platform one below
                    int clearTo = Math.min(maxY, platformY + 128); // generous slice above platform
                    for (int y = clearFrom; y <= clearTo; y++) {
                        org.bukkit.block.Block b = w.getBlockAt(x, y, z);
                        // Rebuild platform below after clear loop
                        b.setType(org.bukkit.Material.AIR, false);
                    }
                } else {
                    int groundY = w.getHighestBlockYAt(x, z);
                    int clearFrom = Math.min(maxY, groundY + 1);
                    for (int y = clearFrom; y <= Math.min(maxY, groundY + 128); y++) {
                        org.bukkit.block.Block b = w.getBlockAt(x, y, z);
                        b.setType(org.bukkit.Material.AIR, false);
                    }
                }
            }
        }

        // Rebuild base features
        if (voidWorld) {
            // Rebuild the platform at platformY - 1
            Location centerBelow = new Location(w,
                    minX + region.size() / 2.0,
                    platformY,
                    minZ + region.size() / 2.0);
            buildPlatform(centerBelow, config.getPlatformSize(), config.getPlatformBlock());
        } else if (config.outlineEnabled()) {
            drawPlotOutline(region);
        }

        // Finally mark plot as free for new allocations
        freePlotForCourse(course);
    }

    /**
     * Reset the plot in small batches over multiple ticks to reduce lag.
     * Processes up to {@code columnsPerTick} (x,z) columns per tick.
     * Calls {@code after} on the main thread when done.
     */
    public void resetPlotForCourseBatched(com.pikaronga.parkour.course.ParkourCourse course, int columnsPerTick, Runnable after) {
        if (course == null) { if (after != null) org.bukkit.Bukkit.getScheduler().runTask(plugin, after); return; }
        PlotRegion region = course.getPlotRegion();
        World w = getWorld();
        if (region == null || w == null) { freePlotForCourse(course); if (after != null) org.bukkit.Bukkit.getScheduler().runTask(plugin, after); return; }

        final int minX = region.minX();
        final int minZ = region.minZ();
        final int maxX = region.maxX();
        final int maxZ = region.maxZ();
        final boolean voidWorld = config.isVoidWorld();
        final int platformY = config.getPlatformY();
        final int maxY = Math.max(1, w.getMaxHeight() - 1);
        final int cps = Math.max(1, columnsPerTick);

        // Flatten iteration over (x,z) columns
        final int totalX = (maxX - minX + 1);
        final int totalZ = (maxZ - minZ + 1);
        final int totalColumns = totalX * totalZ;
        final int[] idx = new int[]{0};

        final org.bukkit.scheduler.BukkitTask[] taskRef = new org.bukkit.scheduler.BukkitTask[1];
        taskRef[0] = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int processed = 0;
            while (processed < cps && idx[0] < totalColumns) {
                int k = idx[0]++;
                int lx = k / totalZ; // 0..totalX-1
                int lz = k % totalZ; // 0..totalZ-1
                int x = minX + lx;
                int z = minZ + lz;

                if (voidWorld) {
                    int clearFrom = Math.max(0, platformY - 1);
                    int clearTo = Math.min(maxY, platformY + 128);
                    for (int y = clearFrom; y <= clearTo; y++) {
                        org.bukkit.block.Block b = w.getBlockAt(x, y, z);
                        b.setType(org.bukkit.Material.AIR, false);
                    }
                } else {
                    int groundY = w.getHighestBlockYAt(x, z);
                    int clearFrom = Math.min(maxY, groundY + 1);
                    for (int y = clearFrom; y <= Math.min(maxY, groundY + 128); y++) {
                        org.bukkit.block.Block b = w.getBlockAt(x, y, z);
                        b.setType(org.bukkit.Material.AIR, false);
                    }
                }
                processed++;
            }

            if (idx[0] >= totalColumns) {
                // Done clearing: rebuild base, free plot, finish
                if (voidWorld) {
                    Location centerBelow = new Location(w,
                            minX + region.size() / 2.0,
                            platformY,
                            minZ + region.size() / 2.0);
                    buildPlatform(centerBelow, config.getPlatformSize(), config.getPlatformBlock());
                } else if (config.outlineEnabled()) {
                    drawPlotOutline(region);
                }
                freePlotForCourse(course);
                try { if (taskRef[0] != null) taskRef[0].cancel(); } catch (Throwable ignored) {}
                if (after != null) org.bukkit.Bukkit.getScheduler().runTask(plugin, after);
            }
        }, 1L, 1L);
    }

    public void handleEnterPlot(Player player) {
        if (player == null) return;
        ParkourCourse course = getCourseByLocation(player.getLocation()).orElse(null);
        if (config.useWorldBorder() && course != null && course.getPlotRegion() != null) {
            applyPlayerBorder(player, course.getPlotRegion());
        }
        if (!canEdit(player, course)) {
            UUID id = player.getUniqueId();
            GameMode prev = lastGamemode.remove(id);
            if (prev != null && player.getGameMode() != prev) {
                try { player.setGameMode(prev); } catch (Throwable ignored) {}
            }
            return;
        }
        UUID id = player.getUniqueId();
        if (!savedContents.containsKey(id)) {
            try {
                org.bukkit.inventory.PlayerInventory inv = player.getInventory();
                savedContents.put(id, inv.getContents());
                savedArmor.put(id, inv.getArmorContents());
                savedExtra.put(id, inv.getExtraContents());
                savedOffhand.put(id, inv.getItemInOffHand());
            } catch (Throwable ignored) {}
        }
        lastGamemode.putIfAbsent(id, player.getGameMode());
        forceCreativeMode(player, course);
    }

    public void handleExitPlot(Player player) {
        GameMode prev = lastGamemode.remove(player.getUniqueId());
        if (prev != null) {
            player.setGameMode(prev);
        }
        // Restore/clean inventory so creative items don't leak outside plots
        try {
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            org.bukkit.inventory.ItemStack[] contents = savedContents.remove(player.getUniqueId());
            org.bukkit.inventory.ItemStack[] armor = savedArmor.remove(player.getUniqueId());
            org.bukkit.inventory.ItemStack[] extra = savedExtra.remove(player.getUniqueId());
            org.bukkit.inventory.ItemStack off = savedOffhand.remove(player.getUniqueId());
            if (contents != null) inv.setContents(contents);
            if (armor != null) inv.setArmorContents(armor);
            if (extra != null) inv.setExtraContents(extra);
            if (off != null) inv.setItemInOffHand(off);
            if (contents == null && armor == null && extra == null && off == null) {
                inv.clear();
            }
        } catch (Throwable ignored) {}
        if (config.useWorldBorder()) {
            player.setWorldBorder(null);
        }
    }

    public void handleCoursePublished(ParkourCourse course) {
        if (course == null) return;
        // Stop tracking testing state for this course
        testingCourses.entrySet().removeIf(e -> course.getName().equalsIgnoreCase(e.getValue()));
        PlotRegion region = course.getPlotRegion();
        World world = getWorld();
        if (world == null || region == null) return;
        for (Player online : world.getPlayers()) {
            if (!isOwner(online, course)) continue;
            if (!region.contains(online.getLocation())) continue;
            handleExitPlot(online);
            try { online.setGameMode(GameMode.ADVENTURE); } catch (Throwable ignored) {}
            try {
                online.sendMessage(plugin.getMessageManager().getMessage(
                        "publish-locked",
                        "&cThis parkour is now published. Editing has been disabled."
                ));
            } catch (Throwable ignored) {}
        }
    }

    public void redrawAllOutlines() {
        if (!config.outlineEnabled() || config.isVoidWorld()) return;
        for (ParkourCourse c : parkourManager.getCourses().values()) {
            if (c.getPlotRegion() != null) drawPlotOutline(c.getPlotRegion());
        }
    }

    private void drawPlotOutline(PlotRegion r) {
        World w = getWorld();
        if (w == null) return;
        org.bukkit.Material border = config.outlineBorderMaterial();
        org.bukkit.Material corner = config.outlineCornerMaterial();
        int minX = r.minX();
        int minZ = r.minZ();
        int maxX = r.maxX();
        int maxZ = r.maxZ();
        // Edges along ground top surface
        for (int x = minX; x <= maxX; x++) {
            placeTopBlock(w, x, minZ, border);
            placeTopBlock(w, x, maxZ, border);
        }
        for (int z = minZ; z <= maxZ; z++) {
            placeTopBlock(w, minX, z, border);
            placeTopBlock(w, maxX, z, border);
        }
        // Corners
        placeTopBlock(w, minX, minZ, corner);
        placeTopBlock(w, minX, maxZ, corner);
        placeTopBlock(w, maxX, minZ, corner);
        placeTopBlock(w, maxX, maxZ, corner);
    }

    private void placeTopBlock(World w, int x, int z, org.bukkit.Material type) {
        int y = w.getHighestBlockYAt(x, z);
        org.bukkit.block.Block b = w.getBlockAt(x, y, z);
        if (b.getType() != type) {
            b.setType(type, false);
        }
    }

    private void buildPlatform(Location centerBelow, int size, org.bukkit.Material material) {
        World w = centerBelow.getWorld();
        if (w == null) return;
        int half = size / 2;
        int y = centerBelow.getBlockY();
        int cx = centerBelow.getBlockX();
        int cz = centerBelow.getBlockZ();
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                org.bukkit.block.Block b = w.getBlockAt(x, y, z);
                b.setType(material, false);
            }
        }
    }

    private void applyPlayerBorder(Player player, PlotRegion r) {
        org.bukkit.WorldBorder wb = org.bukkit.Bukkit.createWorldBorder();
        double centerX = r.minX() + (r.size() / 2.0);
        double centerZ = r.minZ() + (r.size() / 2.0);
        wb.setCenter(new Location(getWorld(), centerX, config.getPlatformY(), centerZ));
        wb.setSize(Math.max(1.0, r.size()));
        wb.setDamageBuffer(0);
        wb.setWarningDistance(1);
        player.setWorldBorder(wb);
    }

    private void forceCreativeMode(Player player, ParkourCourse expectedCourse) {
        applyCreative(player, expectedCourse);
        scheduleCreativeReapply(player, expectedCourse, 1L);
        scheduleCreativeReapply(player, expectedCourse, 20L);
    }

    private void scheduleCreativeReapply(Player player, ParkourCourse expectedCourse, long delayTicks) {
        try {
            Bukkit.getScheduler().runTaskLater(plugin, () -> applyCreative(player, expectedCourse), delayTicks);
        } catch (Throwable ignored) {}
    }

    private void applyCreative(Player player, ParkourCourse expectedCourse) {
        if (player == null || !player.isOnline()) return;
        ParkourCourse current = getCourseByLocation(player.getLocation()).orElse(null);
        if (current == null) return;
        if (expectedCourse != null && !current.getName().equalsIgnoreCase(expectedCourse.getName())) return;
        if (!canEdit(player, current)) return;
        try { player.setGameMode(GameMode.CREATIVE); } catch (Throwable ignored) {}
    }
}
