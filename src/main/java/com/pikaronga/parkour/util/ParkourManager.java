package com.pikaronga.parkour.util;

import com.pikaronga.parkour.course.Checkpoint;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.block.Block;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.LinkedHashSet;

public class ParkourManager {

    private final Map<String, ParkourCourse> courses = new HashMap<>();

    public ParkourManager(Collection<ParkourCourse> loadedCourses) {
        if (loadedCourses != null) {
            for (ParkourCourse course : loadedCourses) {
                courses.put(course.getName().toLowerCase(), course);
            }
        }
    }

    public Map<String, ParkourCourse> getCourses() {
        return courses;
    }

    public ParkourCourse getOrCreate(String name) {
        return courses.computeIfAbsent(name.toLowerCase(), key -> new ParkourCourse(name));
    }

    public ParkourCourse getCourse(String name) {
        return courses.get(name.toLowerCase());
    }

    public Optional<ParkourCourse> findCourseByStart(Block block) {
        return courses.values().stream()
                .filter(course -> course.getStartPlate() != null && LocationUtil.isSameBlock(course.getStartPlate(), block.getLocation()))
                .findFirst();
    }

    public Optional<ParkourCourse> findCourseByFinish(Block block) {
        return courses.values().stream()
                .filter(course -> course.getFinishPlate() != null && LocationUtil.isSameBlock(course.getFinishPlate(), block.getLocation()))
                .findFirst();
    }

    public Optional<CheckpointMatch> findCheckpoint(Block block) {
        for (ParkourCourse course : courses.values()) {
            for (Checkpoint checkpoint : course.getCheckpoints()) {
                if (LocationUtil.isSameBlock(checkpoint.plateLocation(), block.getLocation())) {
                    return Optional.of(new CheckpointMatch(course, checkpoint));
                }
            }
        }
        return Optional.empty();
    }

    public void removeCourse(String name) {
        courses.remove(name.toLowerCase());
    }

    public Set<String> getCourseNames() {
        return courses.values().stream().map(ParkourCourse::getName).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public record CheckpointMatch(ParkourCourse course, Checkpoint checkpoint) {
    }
}
