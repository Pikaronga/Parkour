package com.pikaronga.parkour.util;

import com.pikaronga.parkour.course.Checkpoint;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;

public final class RenameUtil {
    private RenameUtil() {}

    public static ParkourCourse cloneWithName(ParkourCourse src, String newName) {
        ParkourCourse dst = new ParkourCourse(newName);
        // Basic points
        dst.setStartPlate(cloneLoc(src.getStartPlate()));
        dst.setStartTeleport(cloneLoc(src.getStartTeleport()));
        dst.setFinishPlate(cloneLoc(src.getFinishPlate()));
        dst.setFinishTeleport(cloneLoc(src.getFinishTeleport()));
        // Checkpoints
        for (Checkpoint cp : src.getCheckpoints()) {
            dst.addCheckpoint(new Checkpoint(cloneLoc(cp.plateLocation()), cloneLoc(cp.respawnLocation())));
        }
        // Holograms
        dst.setTopHologramLocation(cloneLoc(src.getTopHologramLocation()));
        dst.setBestHologramLocation(cloneLoc(src.getBestHologramLocation()));
        dst.setCreatorHologramLocation(cloneLoc(src.getCreatorHologramLocation()));
        // Plot + creators + publish
        dst.setPlotRegion(src.getPlotRegion());
        for (UUID u : src.getCreators()) dst.addCreator(u);
        dst.setPublished(src.isPublished());
        // Ratings
        for (Map.Entry<UUID, Integer> e : src.getLookRatings().entrySet()) {
            dst.setLookRating(e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, Integer> e : src.getDifficultyRatings().entrySet()) {
            dst.setDifficultyRating(e.getKey(), e.getValue());
        }
        // Times
        for (Map.Entry<UUID, java.util.List<Long>> e : src.getTimes().entrySet()) {
            for (Long t : e.getValue()) {
                dst.addTime(e.getKey(), t);
            }
        }
        // Staff diff, blocks, runs
        dst.setStaffDifficulty(src.getStaffDifficulty());
        dst.setPlacedBlocks(src.getPlacedBlocks());
        dst.setTotalRunCount(src.getTotalRunCount());
        for (Map.Entry<UUID, Integer> e : src.getPlayerRunCounts().entrySet()) {
            dst.setPlayerRunCount(e.getKey(), e.getValue());
        }
        // Max fall
        dst.setMaxFallDistance(src.getMaxFallDistance());
        return dst;
    }

    private static Location cloneLoc(Location l) { return l == null ? null : l.clone(); }
}

