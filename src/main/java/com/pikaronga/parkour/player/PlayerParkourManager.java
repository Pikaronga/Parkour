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
    private final Map<UUID, Long> lastCreateTime = new HashMap<>();
    private final Map<UUID, String> lastEditingCourse = new HashMap<>();
    private final Map<UUID, String> testingCourses = new HashMap<>();

    public PlayerParkourManager(ParkourPlugin plugin, ConfigManager config, ParkourManager parkourManager) {
        this.plugin = plugin;
        this.config = config;
        this.parkourManager = parkourManager;
        ensureWorld();
        refreshPlotUsage(parkourManager.getCourses().values());
    }

    public void refreshPlotUsage(java.util.Collection<ParkourCourse> courses) {
        usedChunks.clear();
        if (courses == null) {
            return;
        }
        for (ParkourCourse course : courses) {
            if (course != null && course.getPlotRegion() != null) {
                usedChunks.add(key(course.getPlotRegion().minX(), course.getPlotRegion().minZ()));
            }
        }
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

    private void ensureWorld() {
        String worldName = config.getPlayerWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world != null) return;

        // Pre-create world folder and ensure any JSON files we rely on are valid (non-empty)
        java.io.File container = Bukkit.getWorldContainer();
        java.io.File worldFolder = new java.io.File(container, worldName);
        if (!worldFolder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            worldFolder.mkdirs();
        }
        // Some server paths expect a datapacks config JSON; ensure it's at least an empty object
        try {
            java.io.File dpFolder = new java.io.File(worldFolder, "datapacks");
            if (!dpFolder.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dpFolder.mkdirs();
            }
            java.io.File dpJson = new java.io.File(worldFolder, "datapacks.json");
            if (!dpJson.exists() || dpJson.length() == 0) {
                try (java.io.FileWriter fw = new java.io.FileWriter(dpJson)) {
                    fw.write("{}\n");
                }
            }
        } catch (Exception ignored) {
        }

        WorldCreator wc = new WorldCreator(worldName);
        if (config.isVoidWorld()) {
            // Generate a void world using a custom generator
            wc.generator(new VoidChunkGenerator());
            wc.type(org.bukkit.WorldType.FLAT); // keep flat to avoid noise features
        } else {
            // Modern flat JSON
            String flatJson = "{\"biome\":\"minecraft:plains\"," +
                    "\"layers\":[{" +
                    "\"block\":\"minecraft:bedrock\",\"height\":1},{" +
                    "\"block\":\"minecraft:dirt\",\"height\":2},{" +
                    "\"block\":\"minecraft:grass_block\",\"height\":1}]," +
                    "\"lakes\":false,\"features\":false,\"structure_overrides\":[]}";
            wc.type(org.bukkit.WorldType.FLAT);
            wc.generatorSettings(flatJson);
        }
        Bukkit.createWorld(wc);
    }

    public static class VoidChunkGenerator extends org.bukkit.generator.ChunkGenerator {
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
        return player != null && course != null && course.getCreators().contains(player.getUniqueId());
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

    public void handleEnterPlot(Player player) {
        if (!config.giveCreativeInPlots()) return;
        lastGamemode.putIfAbsent(player.getUniqueId(), player.getGameMode());
        player.setGameMode(GameMode.CREATIVE);
        if (config.useWorldBorder()) {
            com.pikaronga.parkour.course.ParkourCourse c = getCourseByLocation(player.getLocation()).orElse(null);
            if (c != null && c.getPlotRegion() != null) {
                applyPlayerBorder(player, c.getPlotRegion());
            }
        }
    }

    public void handleExitPlot(Player player) {
        GameMode prev = lastGamemode.remove(player.getUniqueId());
        if (prev != null) {
            player.setGameMode(prev);
        }
        if (config.useWorldBorder()) {
            player.setWorldBorder(null);
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
}
