package com.example.parkour.course;

import com.example.parkour.util.TimeUtil;
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

public class ParkourCourse {

    private final String name;
    private Location startPlate;
    private Location startTeleport;
    private Location finishPlate;
    private Location finishTeleport;
    private final List<Checkpoint> checkpoints = new ArrayList<>();
    private Location topHologramLocation;
    private Location bestHologramLocation;
    private final Map<UUID, List<Long>> times = new HashMap<>();

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

    public void addTime(UUID playerId, long time) {
        times.computeIfAbsent(playerId, key -> new ArrayList<>()).add(time);
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

    public List<String> createTopLines() {
        List<Map.Entry<UUID, Long>> top = getTopTimes(10);
        List<String> lines = new ArrayList<>();
        lines.add("§aTop Parkour Times");
        if (top.isEmpty()) {
            lines.add("§7No completions yet.");
            return lines;
        }
        for (int i = 0; i < top.size(); i++) {
            Map.Entry<UUID, Long> entry = top.get(i);
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null || name.isBlank()) {
                name = entry.getKey().toString().substring(0, 8);
            }
            lines.add("§b" + (i + 1) + ". §f" + name + " §7- §e" + TimeUtil.formatDuration(entry.getValue()));
        }
        return lines;
    }
}
