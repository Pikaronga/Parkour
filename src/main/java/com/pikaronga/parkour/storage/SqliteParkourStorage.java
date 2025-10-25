package com.pikaronga.parkour.storage;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.Checkpoint;
import com.pikaronga.parkour.course.ParkourCourse;
import com.pikaronga.parkour.util.LocationUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.*;

public class SqliteParkourStorage {
    private final ParkourPlugin plugin;
    private final DatabaseManager db;

    public SqliteParkourStorage(ParkourPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
    }

    public List<ParkourCourse> loadCourses() {
        List<ParkourCourse> list = new ArrayList<>();
        String sql = "SELECT * FROM parkours";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                int loaded = 0;
                int skipped = 0;
                while (rs.next()) {
                    String name = rs.getString("name");
                    String worldName = rs.getString("world");
                    World world = worldName != null ? Bukkit.getWorld(worldName) : null;

                    if (world == null) {
                        plugin.getLogger().warning("Skipping parkour '" + name + "': world '" + worldName + "' not loaded.");
                        skipped++;
                        continue;
                    }

                    try {
                        ParkourCourse c = new ParkourCourse(name);
                        ResultSetMetaData md = rs.getMetaData();
                        Set<String> cols = new HashSet<>();
                        for (int ci = 1; ci <= md.getColumnCount(); ci++) cols.add(md.getColumnName(ci).toLowerCase());

                        // Teleports
                        c.setStartTeleport(readLocation(world, rs, "start_"));
                        c.setFinishTeleport(readLocation(world, rs, "finish_"));
                        // Plates
                        c.setStartPlate(readBlockLocation(world, rs, "start_plate_"));
                        c.setFinishPlate(readBlockLocation(world, rs, "finish_plate_"));

                        // Difficulty / Published
                        Integer diffObj = getNullableInteger(rs, "difficulty");
                        if (diffObj != null) c.setStaffDifficulty(diffObj);
                        Integer publishedObj = getNullableInteger(rs, "published");
                        c.setPublished(publishedObj != null && publishedObj == 1);

                        // Plot region
                        if (cols.contains("plot_min_x") && cols.contains("plot_min_z") && cols.contains("plot_size")) {
                            Integer minX = getNullableInteger(rs, "plot_min_x");
                            Integer minZ = getNullableInteger(rs, "plot_min_z");
                            Integer size = getNullableInteger(rs, "plot_size");
                            if (minX != null && minZ != null && size != null)
                                c.setPlotRegion(new com.pikaronga.parkour.player.PlotRegion(world, minX, minZ, size));
                        }

                        // Holograms
                        readHolo(rs, world, cols, c, "top_", c::setTopHologramLocation);
                        readHolo(rs, world, cols, c, "best_", c::setBestHologramLocation);
                        readHolo(rs, world, cols, c, "creator_", c::setCreatorHologramLocation);

                        // Creators, checkpoints, times
                        loadCreators(conn, rs.getInt("id"), c);
                        loadCheckpoints(conn, rs.getInt("id"), world, c);
                        loadTimes(conn, rs.getInt("id"), c);

                        list.add(c);
                        loaded++;
                        plugin.getLogger().info("Loaded parkour '" + name + "' in world '" + worldName + "'.");
                    } catch (Exception rowEx) {
                        plugin.getLogger().warning("Skipping parkour '" + name + "' due to invalid data: " + rowEx.getMessage());
                    }
                }
                plugin.getLogger().info("Course loading summary: " + loaded + " loaded, " + skipped + " skipped.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load parkours from DB: " + e.getMessage());
        }
        return list;
    }

    private void readHolo(ResultSet rs, World world, Set<String> cols, ParkourCourse c, String prefix,
                          java.util.function.Consumer<Location> setter) throws SQLException {
        if (cols.contains(prefix + "holo_x")) {
            Double x = getNullableDouble(rs, prefix + "holo_x");
            Double y = getNullableDouble(rs, prefix + "holo_y");
            Double z = getNullableDouble(rs, prefix + "holo_z");
            if (x != null && y != null && z != null)
                setter.accept(new Location(world, x, y, z));
        }
    }

    private void loadCreators(Connection conn, int parkourId, ParkourCourse c) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM creators WHERE parkour_id=?")) {
            ps.setInt(1, parkourId);
            try (ResultSet crs = ps.executeQuery()) {
                while (crs.next()) {
                    try {
                        c.addCreator(UUID.fromString(crs.getString(1)));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    private void loadCheckpoints(Connection conn, int parkourId, World world, ParkourCourse c) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM checkpoints WHERE parkour_id=? ORDER BY ord ASC")) {
            ps.setInt(1, parkourId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Double px = getNullableDouble(rs, "plate_x");
                    Double py = getNullableDouble(rs, "plate_y");
                    Double pz = getNullableDouble(rs, "plate_z");
                    Double rx = getNullableDouble(rs, "respawn_x");
                    Double ry = getNullableDouble(rs, "respawn_y");
                    Double rz = getNullableDouble(rs, "respawn_z");
                    Double ryaw = getNullableDouble(rs, "respawn_yaw");
                    Double rpitch = getNullableDouble(rs, "respawn_pitch");
                    if (px == null || py == null || pz == null || rx == null || ry == null || rz == null) continue;
                    float yaw = ryaw == null ? 0f : ryaw.floatValue();
                    float pitch = rpitch == null ? 0f : rpitch.floatValue();
                    c.addCheckpoint(new Checkpoint(new Location(world, px, py, pz), new Location(world, rx, ry, rz, yaw, pitch)));
                }
            }
        }
    }

    public void loadTimes(Connection conn, int parkourId, ParkourCourse c) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT uuid, best_nanos FROM times WHERE parkour_id=?")) {
            ps.setInt(1, parkourId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID u = UUID.fromString(rs.getString(1));
                        long best = rs.getLong(2);
                        c.getTimes().put(u, new ArrayList<>(List.of(best)));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    private Location readLocation(World world, ResultSet rs, String prefix) throws SQLException {
        Double x = getNullableDouble(rs, prefix + "x");
        Double y = getNullableDouble(rs, prefix + "y");
        Double z = getNullableDouble(rs, prefix + "z");
        Double yawD = getNullableDouble(rs, prefix + "yaw");
        Double pitchD = getNullableDouble(rs, prefix + "pitch");
        if (x == null || y == null || z == null) return null;
        float yaw = yawD == null ? 0f : yawD.floatValue();
        float pitch = pitchD == null ? 0f : pitchD.floatValue();
        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location readBlockLocation(World world, ResultSet rs, String prefix) throws SQLException {
        Double x = getNullableDouble(rs, prefix + "x");
        Double y = getNullableDouble(rs, prefix + "y");
        Double z = getNullableDouble(rs, prefix + "z");
        if (x == null || y == null || z == null) return null;
        return new Location(world, x, y, z);
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        try {
            Object obj = rs.getObject(column);
            if (obj == null) return null;
            if (obj instanceof Number n) return n.doubleValue();
            if (obj instanceof String s) try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return null; }
            return null;
        } catch (SQLException ex) { return null; }
    }

    private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        try {
            Object obj = rs.getObject(column);
            if (obj == null) return null;
            if (obj instanceof Number n) return n.intValue();
            if (obj instanceof String s) try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return null; }
            return null;
        } catch (SQLException ex) { return null; }
    }

    private String resolveWorld(ParkourCourse c) {
        if (c.getStartTeleport() != null && c.getStartTeleport().getWorld() != null)
            return c.getStartTeleport().getWorld().getName();
        if (c.getFinishTeleport() != null && c.getFinishTeleport().getWorld() != null)
            return c.getFinishTeleport().getWorld().getName();
        return plugin.getConfigManager().getPlayerWorldName();
    }

    private void writeLocation(PreparedStatement ps, int startIndex, Location loc) throws SQLException {
        if (loc == null) {
            ps.setNull(startIndex, Types.REAL);
            ps.setNull(startIndex + 1, Types.REAL);
            ps.setNull(startIndex + 2, Types.REAL);
            ps.setNull(startIndex + 3, Types.REAL);
            ps.setNull(startIndex + 4, Types.REAL);
        } else {
            ps.setDouble(startIndex, loc.getX());
            ps.setDouble(startIndex + 1, loc.getY());
            ps.setDouble(startIndex + 2, loc.getZ());
            ps.setDouble(startIndex + 3, loc.getYaw());
            ps.setDouble(startIndex + 4, loc.getPitch());
        }
    }

    private void writeBlock(PreparedStatement ps, int startIndex, Location loc) throws SQLException {
        if (loc == null) {
            ps.setNull(startIndex, Types.REAL);
            ps.setNull(startIndex + 1, Types.REAL);
            ps.setNull(startIndex + 2, Types.REAL);
        } else {
            ps.setDouble(startIndex, loc.getX());
            ps.setDouble(startIndex + 1, loc.getY());
            ps.setDouble(startIndex + 2, loc.getZ());
        }
    }

    private Integer getCourseId(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM parkours WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
                return null;
            }
        }
    }
}
