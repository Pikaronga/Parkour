package com.pikaronga.parkour.storage;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.Checkpoint;
import com.pikaronga.parkour.course.ParkourCourse;
import com.pikaronga.parkour.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ParkourStorage {

    private final ParkourPlugin plugin;
    private final File file;

    public ParkourStorage(ParkourPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "parkours.yml");
    }

    public List<ParkourCourse> loadCourses() {
        if (!file.exists()) {
            return new ArrayList<>();
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection parkours = config.getConfigurationSection("parkours");
        List<ParkourCourse> courses = new ArrayList<>();
        if (parkours == null) {
            return courses;
        }
        for (String key : parkours.getKeys(false)) {
            ConfigurationSection section = parkours.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            ParkourCourse course = new ParkourCourse(key);
            course.setStartPlate(LocationUtil.deserialize(section.getConfigurationSection("startPlate")));
            course.setStartTeleport(LocationUtil.deserialize(section.getConfigurationSection("startTeleport")));
            course.setFinishPlate(LocationUtil.deserialize(section.getConfigurationSection("finishPlate")));
            course.setFinishTeleport(LocationUtil.deserialize(section.getConfigurationSection("finishTeleport")));
            ConfigurationSection checkpointsSection = section.getConfigurationSection("checkpoints");
            if (checkpointsSection != null) {
                for (String cpKey : checkpointsSection.getKeys(false)) {
                    ConfigurationSection cpSection = checkpointsSection.getConfigurationSection(cpKey);
                    if (cpSection == null) {
                        continue;
                    }
                    Location plate = LocationUtil.deserialize(cpSection.getConfigurationSection("plate"));
                    Location respawn = LocationUtil.deserialize(cpSection.getConfigurationSection("respawn"));
                    if (plate != null && respawn != null) {
                        course.addCheckpoint(new Checkpoint(plate, respawn));
                    }
                }
            }
            course.setTopHologramLocation(LocationUtil.deserialize(section.getConfigurationSection("topHologram")));
            course.setBestHologramLocation(LocationUtil.deserialize(section.getConfigurationSection("bestHologram")));
            ConfigurationSection timesSection = section.getConfigurationSection("times");
            if (timesSection != null) {
                for (String uuidKey : timesSection.getKeys(false)) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(uuidKey);
                    } catch (IllegalArgumentException ignored) {
                        continue;
                    }
                    List<Long> entries = new ArrayList<>();
                    ConfigurationSection playerSection = timesSection.getConfigurationSection(uuidKey);
                    if (playerSection == null) {
                        continue;
                    }
                    for (String index : playerSection.getKeys(false)) {
                        long time = playerSection.getLong(index);
                        entries.add(time);
                    }
                    entries.sort(Long::compareTo);
                    if (entries.size() > 3) {
                        entries = new ArrayList<>(entries.subList(0, 3));
                    }
                    course.getTimes().put(uuid, entries);
                }
            }
            courses.add(course);
        }
        return courses;
    }

    public void saveCourses(Map<String, ParkourCourse> courses) {
        FileConfiguration config = new YamlConfiguration();
        ConfigurationSection parkours = config.createSection("parkours");
        for (ParkourCourse course : courses.values()) {
            ConfigurationSection section = parkours.createSection(course.getName());
            if (course.getStartPlate() != null) {
                LocationUtil.serialize(section.createSection("startPlate"), course.getStartPlate());
            }
            if (course.getStartTeleport() != null) {
                LocationUtil.serialize(section.createSection("startTeleport"), course.getStartTeleport());
            }
            if (course.getFinishPlate() != null) {
                LocationUtil.serialize(section.createSection("finishPlate"), course.getFinishPlate());
            }
            if (course.getFinishTeleport() != null) {
                LocationUtil.serialize(section.createSection("finishTeleport"), course.getFinishTeleport());
            }
            if (!course.getCheckpoints().isEmpty()) {
                ConfigurationSection checkpointsSection = section.createSection("checkpoints");
                int i = 0;
                for (Checkpoint checkpoint : course.getCheckpoints()) {
                    ConfigurationSection cpSection = checkpointsSection.createSection(String.valueOf(i++));
                    LocationUtil.serialize(cpSection.createSection("plate"), checkpoint.plateLocation());
                    LocationUtil.serialize(cpSection.createSection("respawn"), checkpoint.respawnLocation());
                }
            }
            if (course.getTopHologramLocation() != null) {
                LocationUtil.serialize(section.createSection("topHologram"), course.getTopHologramLocation());
            }
            if (course.getBestHologramLocation() != null) {
                LocationUtil.serialize(section.createSection("bestHologram"), course.getBestHologramLocation());
            }
            if (!course.getTimes().isEmpty()) {
                ConfigurationSection timesSection = section.createSection("times");
                for (Map.Entry<UUID, List<Long>> entry : course.getTimes().entrySet()) {
                    ConfigurationSection playerSection = timesSection.createSection(entry.getKey().toString());
                    List<Long> sorted = new ArrayList<>(entry.getValue());
                    sorted.sort(Long::compareTo);
                    int index = 0;
                    for (Long time : sorted) {
                        playerSection.set(String.valueOf(index++), time);
                    }
                }
            }
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save parkours.yml: " + e.getMessage());
        }
    }
}
