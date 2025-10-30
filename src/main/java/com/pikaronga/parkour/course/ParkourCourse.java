package com.pikaronga.parkour.course;

import com.pikaronga.parkour.config.HologramTextProvider;
import com.pikaronga.parkour.util.LocationUtil;
import com.pikaronga.parkour.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

public class ParkourCourse {

    public static final double DEFAULT_MAX_FALL_DISTANCE = 10.0;

    private final String name;
    private Location startPlate;
    private Location startTeleport;
    private Location finishPlate;
    private Location finishTeleport;
    private final List<Checkpoint> checkpoints = new ArrayList<>();
    private Location topHologramLocation;
    private Location bestHologramLocation;
    private final Map<UUID, List<Long>> times = new HashMap<>();
    private double maxFallDistance = DEFAULT_MAX_FALL_DISTANCE;

    // Player-parkour specific (optional)
    private final List<UUID> creators = new ArrayList<>();
    private boolean published = false;
    private com.pikaronga.parkour.player.PlotRegion plotRegion;
    private Location creatorHologramLocation;
    private final Map<UUID, Integer> lookRatings = new HashMap<>();
    private final Map<UUID, Integer> difficultyRatings = new HashMap<>();
    private Integer staffDifficulty; // 1-5 optional
    private int placedBlocks = 0;
    // Creation order hint (DB row id or timestamp). Used for sorting in GUIs.
    private int createdOrder = 0;

    // Run counters
    private final Map<UUID, Integer> playerRunCounts = new ConcurrentHashMap<>();
    private int totalRunCount = 0;

    public ParkourCourse(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Location getStartPlate() {
        return startPlate;
    }

    public void setStartPlate(Location startPlate) {
        this.startPlate = startPlate;
    }

    public Location getStartTeleport() {
        return startTeleport;
    }

    public void setStartTeleport(Location startTeleport) {
        this.startTeleport = startTeleport;
    }

    public Location getFinishPlate() {
        return finishPlate;
    }

    public void setFinishPlate(Location finishPlate) {
        this.finishPlate = finishPlate;
    }

    public Location getFinishTeleport() {
        return finishTeleport;
    }

    public void setFinishTeleport(Location finishTeleport) {
        this.finishTeleport = finishTeleport;
    }

    public List<Checkpoint> getCheckpoints() {
        return checkpoints;
    }

    public void clearCheckpoints() {
        checkpoints.clear();
    }

    public void addCheckpoint(Checkpoint checkpoint) {
        if (checkpoint == null || checkpoint.plateLocation() == null) {
            return;
        }
        for (int i = 0; i < checkpoints.size(); i++) {
            Checkpoint existing = checkpoints.get(i);
            if (existing != null && LocationUtil.isSameBlock(existing.plateLocation(), checkpoint.plateLocation())) {
                checkpoints.set(i, checkpoint);
                return;
            }
        }
        checkpoints.add(checkpoint);
    }

    public Location getTopHologramLocation() {
        return topHologramLocation;
    }

    public void setTopHologramLocation(Location topHologramLocation) {
        this.topHologramLocation = topHologramLocation;
    }

    public Location getBestHologramLocation() {
        return bestHologramLocation;
    }

    public void setBestHologramLocation(Location bestHologramLocation) {
        this.bestHologramLocation = bestHologramLocation;
    }

    public Map<UUID, List<Long>> getTimes() {
        return times;
    }

    public double getMaxFallDistance() {
        return maxFallDistance;
    }

    public void setMaxFallDistance(double maxFallDistance) {
        if (Double.isNaN(maxFallDistance) || Double.isInfinite(maxFallDistance) || maxFallDistance < 0) {
            return;
        }
        this.maxFallDistance = maxFallDistance;
    }

    public void addTime(UUID playerId, long time) {
        List<Long> playerTimes = times.computeIfAbsent(playerId, key -> new ArrayList<>());
        playerTimes.add(time);
        playerTimes.sort(Long::compareTo);
        if (playerTimes.size() > 3) {
            playerTimes.subList(3, playerTimes.size()).clear();
        }
    }

    public long getBestTime(UUID playerId) {
        return times.getOrDefault(playerId, Collections.emptyList()).stream()
                .min(Long::compareTo)
                .orElse(-1L);
    }

    public List<Map.Entry<UUID, Long>> getTopTimes(int limit) {
        return times.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().stream().min(Long::compareTo).orElse(Long.MAX_VALUE)))
                .sorted(Comparator.comparingLong(Map.Entry::getValue))
                .limit(limit)
                .collect(Collectors.toList());
    }

    public List<String> createTopLines(HologramTextProvider textProvider) {
        return createTopLinesWithLimit(textProvider, 10);
    }

    public List<String> createTopLinesWithLimit(HologramTextProvider textProvider, int limit) {
        List<Map.Entry<UUID, Long>> top = getTopTimes(limit);
        List<String> lines = new ArrayList<>();
        lines.add(textProvider.formatTopHeader(getName()));
        if (top.isEmpty()) {
            lines.add(textProvider.formatTopEmpty(getName()));
            return lines;
        }
        for (int i = 0; i < top.size(); i++) {
            Map.Entry<UUID, Long> entry = top.get(i);
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null || name.isBlank()) {
                name = entry.getKey().toString().substring(0, 8);
            }
            lines.add(textProvider.formatTopEntry(i + 1, name, TimeUtil.formatDuration(entry.getValue()), getName()));
        }
        return lines;
    }

    // --- Player-parkour additions ---
    public List<UUID> getCreators() { return creators; }
    public void addCreator(UUID uuid) { if (!creators.contains(uuid)) creators.add(uuid); }
    public void removeCreator(UUID uuid) { creators.remove(uuid); }
    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
    public com.pikaronga.parkour.player.PlotRegion getPlotRegion() { return plotRegion; }
    public void setPlotRegion(com.pikaronga.parkour.player.PlotRegion plotRegion) { this.plotRegion = plotRegion; }
    public Location getCreatorHologramLocation() { return creatorHologramLocation; }
    public void setCreatorHologramLocation(Location location) { this.creatorHologramLocation = location; }

    public void setLookRating(UUID player, int value) {
        int v = Math.max(1, Math.min(5, value));
        lookRatings.put(player, v);
    }
    public void setDifficultyRating(UUID player, int value) {
        int v = Math.max(1, Math.min(5, value));
        difficultyRatings.put(player, v);
    }
    public Map<UUID, Integer> getLookRatings() { return lookRatings; }
    public Map<UUID, Integer> getDifficultyRatings() { return difficultyRatings; }
    public double getAverageLookRating() {
        if (lookRatings.isEmpty()) return 0.0;
        return lookRatings.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }
    public double getAverageDifficultyRating() {
        if (difficultyRatings.isEmpty()) return 0.0;
        return difficultyRatings.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }
    public Integer getStaffDifficulty() { return staffDifficulty; }
    public void setStaffDifficulty(Integer staffDifficulty) {
        if (staffDifficulty == null) { this.staffDifficulty = null; return; }
        int v = Math.max(1, Math.min(5, staffDifficulty));
        this.staffDifficulty = v;
    }

    public int getPlacedBlocks() { return placedBlocks; }
    public void setPlacedBlocks(int value) { this.placedBlocks = Math.max(0, value); }
    public void incrementPlacedBlocks() { this.placedBlocks++; }
    public void decrementPlacedBlocks() { if (this.placedBlocks > 0) this.placedBlocks--; }

    public int getCreatedOrder() { return createdOrder; }
    public void setCreatedOrder(int createdOrder) { this.createdOrder = Math.max(0, createdOrder); }

    // ---- Run counts (persistent via storage) ----
    public Map<UUID, Integer> getPlayerRunCounts() { return playerRunCounts; }
    public int getPlayerRunCount(UUID player) { return playerRunCounts.getOrDefault(player, 0); }
    public int getTotalRunCount() { return Math.max(0, totalRunCount); }
    public void setTotalRunCount(int total) { this.totalRunCount = Math.max(0, total); }
    public void setPlayerRunCount(UUID player, int runs) { if (player != null) playerRunCounts.put(player, Math.max(0, runs)); }
    /**
     * Increment in-memory counters. Persistent write must be queued by storage layer separately.
     */
    public void incrementRunCount(UUID player) {
        if (player != null) {
            playerRunCounts.merge(player, 1, Integer::sum);
        }
        totalRunCount++;
    }
}
