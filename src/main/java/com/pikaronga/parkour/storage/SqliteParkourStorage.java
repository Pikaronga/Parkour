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

    public record LoadResult(List<ParkourCourse> courses, List<String> missingWorlds) {}

    public LoadResult loadCourses() {
        List<ParkourCourse> list = new ArrayList<>();
        java.util.Set<String> missingWorlds = new java.util.LinkedHashSet<>();
        String sql = "SELECT * FROM parkours";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String world = rs.getString("world");
                    if (world == null || world.isBlank()) {
                        plugin.getLogger().warning("Skipping parkour '" + name + "': no world recorded in storage.");
                        missingWorlds.add("<unknown>");
                        continue;
                    }
                    World loadedWorld = Bukkit.getWorld(world);
                    if (loadedWorld == null) {
                        plugin.getLogger().warning("Skipping parkour '" + name + "': world '" + world + "' not loaded yet.");
                        missingWorlds.add(world);
                        continue;
                    }
                    try {
                        ParkourCourse c = new ParkourCourse(name);
                        java.sql.ResultSetMetaData md = rs.getMetaData();
                        java.util.HashSet<String> cols = new java.util.HashSet<>();
                        for (int ci = 1; ci <= md.getColumnCount(); ci++) cols.add(md.getColumnName(ci).toLowerCase());
                        // Start/finish teleports (guard invalid types)
                        c.setStartTeleport(readLocation(loadedWorld, rs, "start_"));
                        c.setFinishTeleport(readLocation(loadedWorld, rs, "finish_"));
                        // Plates
                        c.setStartPlate(readBlockLocation(loadedWorld, rs, "start_plate_"));
                        c.setFinishPlate(readBlockLocation(loadedWorld, rs, "finish_plate_"));
                        // Published and difficulty
                        Integer diffObj = getNullableInteger(rs, "difficulty");
                        if (diffObj != null) c.setStaffDifficulty(diffObj);
                        Integer publishedObj = getNullableInteger(rs, "published");
                        c.setPublished(publishedObj != null && publishedObj == 1);
                        // Plot region
                        if (cols.contains("plot_min_x") && cols.contains("plot_min_z") && cols.contains("plot_size")) {
                            Integer minX = getNullableInteger(rs, "plot_min_x");
                            Integer minZ = getNullableInteger(rs, "plot_min_z");
                            Integer size = getNullableInteger(rs, "plot_size");
                            if (minX != null && minZ != null && size != null) {
                                c.setPlotRegion(new com.pikaronga.parkour.player.PlotRegion(world, minX, minZ, size));
                            }
                        }
                        // Holograms (guarded)
                        if (cols.contains("top_holo_x")) {
                            Double x = getNullableDouble(rs, "top_holo_x");
                            Double y = getNullableDouble(rs, "top_holo_y");
                            Double z = getNullableDouble(rs, "top_holo_z");
                            if (x != null && y != null && z != null) c.setTopHologramLocation(new Location(loadedWorld, x, y, z));
                        }
                        if (cols.contains("best_holo_x")) {
                            Double x = getNullableDouble(rs, "best_holo_x");
                            Double y = getNullableDouble(rs, "best_holo_y");
                            Double z = getNullableDouble(rs, "best_holo_z");
                            if (x != null && y != null && z != null) c.setBestHologramLocation(new Location(loadedWorld, x, y, z));
                        }
                        if (cols.contains("creator_holo_x")) {
                            Double x = getNullableDouble(rs, "creator_holo_x");
                            Double y = getNullableDouble(rs, "creator_holo_y");
                            Double z = getNullableDouble(rs, "creator_holo_z");
                            if (x != null && y != null && z != null) c.setCreatorHologramLocation(new Location(loadedWorld, x, y, z));
                        }
                        // Creators
                        loadCreators(conn, rs.getInt("id"), c);
                        // Checkpoints
                        loadCheckpoints(conn, rs.getInt("id"), loadedWorld, c);
                        // Times (best per player)
                        loadTimes(conn, rs.getInt("id"), c);
                        plugin.getLogger().info("Loaded parkour '" + name + "' in world '" + loadedWorld.getName() + "'.");
                        list.add(c);
                    } catch (Exception rowEx) {
                        plugin.getLogger().warning("Skipping parkour '" + name + "' due to invalid data: " + rowEx.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load parkours from DB: " + e.getMessage());
        }
        return new LoadResult(list, new java.util.ArrayList<>(missingWorlds));
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
        if (world == null) {
            return;
        }
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
                    if (px == null || py == null || pz == null || rx == null || ry == null || rz == null) {
                        continue; // skip invalid checkpoint row
                    }
                    float yaw = ryaw == null ? 0f : ryaw.floatValue();
                    float pitch = rpitch == null ? 0f : rpitch.floatValue();
                    Location plate = new Location(world, px, py, pz);
                    Location resp = new Location(world, rx, ry, rz, yaw, pitch);
                    c.addCheckpoint(new Checkpoint(plate, resp));
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
                        java.util.List<Long> list = new java.util.ArrayList<>();
                        list.add(best);
                        c.getTimes().put(u, list);
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
        if (x == null || y == null || z == null || world == null) return null;
        float yaw = yawD == null ? 0f : yawD.floatValue();
        float pitch = pitchD == null ? 0f : pitchD.floatValue();
        return new Location(world, x, y, z, yaw, pitch);
    }

    private Location readBlockLocation(World world, ResultSet rs, String prefix) throws SQLException {
        Double x = getNullableDouble(rs, prefix + "x");
        Double y = getNullableDouble(rs, prefix + "y");
        Double z = getNullableDouble(rs, prefix + "z");
        if (x == null || y == null || z == null || world == null) return null;
        return new Location(world, x, y, z);
    }

    private Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        try {
            Object obj = rs.getObject(column);
            if (obj == null) return null;
            if (obj instanceof Number n) return n.doubleValue();
            if (obj instanceof String s) {
                try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return null; }
            }
            return null;
        } catch (SQLException ex) {
            // Some drivers may throw when type mismatches (e.g., "Bad value for type Double")
            return null;
        }
    }

    private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        try {
            Object obj = rs.getObject(column);
            if (obj == null) return null;
            if (obj instanceof Number n) return n.intValue();
            if (obj instanceof String s) {
                try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return null; }
            }
            return null;
        } catch (SQLException ex) {
            return null;
        }
    }

    public void cleanupInvalidNumericColumns() {
        String[] numericCols = new String[] {
                "start_x","start_y","start_z","start_yaw","start_pitch",
                "start_plate_x","start_plate_y","start_plate_z",
                "finish_x","finish_y","finish_z","finish_yaw","finish_pitch",
                "finish_plate_x","finish_plate_y","finish_plate_z",
                "plot_min_x","plot_min_z","plot_size",
                "top_holo_x","top_holo_y","top_holo_z",
                "best_holo_x","best_holo_y","best_holo_z",
                "creator_holo_x","creator_holo_y","creator_holo_z",
                "difficulty","published"
        };
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            for (String col : numericCols) {
                try (Statement st = conn.createStatement()) {
                    // Set to NULL where the stored type is text or blob
                    String sql = "UPDATE parkours SET " + col + "=NULL WHERE typeof(" + col + ") NOT IN ('integer','real','null')";
                    st.executeUpdate(sql);
                } catch (SQLException ignoreOne) {
                    // Column may not exist on older schemas; ignore
                }
            }
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("Cleanup of invalid numeric columns failed: " + e.getMessage());
        }
    }

    public void saveCourses(Map<String, ParkourCourse> courses) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            // Upsert each course
            for (ParkourCourse c : courses.values()) {
                int id = upsertCourse(conn, c);
                saveExtras(conn, id, c);
                // Creators
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM creators WHERE parkour_id=?")) {
                    del.setInt(1, id);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = conn.prepareStatement("INSERT INTO creators(parkour_id, uuid) VALUES(?,?)")) {
                    for (UUID u : c.getCreators()) {
                        ins.setInt(1, id);
                        ins.setString(2, u.toString());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                // Checkpoints
                try (PreparedStatement delc = conn.prepareStatement("DELETE FROM checkpoints WHERE parkour_id=?")) {
                    delc.setInt(1, id);
                    delc.executeUpdate();
                }
                try (PreparedStatement insc = conn.prepareStatement("INSERT INTO checkpoints(parkour_id, ord, plate_x, plate_y, plate_z, respawn_x, respawn_y, respawn_z, respawn_yaw, respawn_pitch) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                    int ord = 0;
                    for (Checkpoint cp : c.getCheckpoints()) {
                        insc.setInt(1, id);
                        insc.setInt(2, ord++);
                        Location plate = cp.plateLocation();
                        Location resp = cp.respawnLocation();
                        insc.setDouble(3, plate.getX());
                        insc.setDouble(4, plate.getY());
                        insc.setDouble(5, plate.getZ());
                        insc.setDouble(6, resp.getX());
                        insc.setDouble(7, resp.getY());
                        insc.setDouble(8, resp.getZ());
                        insc.setDouble(9, resp.getYaw());
                        insc.setDouble(10, resp.getPitch());
                        insc.addBatch();
                    }
                    insc.executeBatch();
                }
                // Times: rewrite best per player
                try (PreparedStatement delt = conn.prepareStatement("DELETE FROM times WHERE parkour_id=?")) {
                    delt.setInt(1, id);
                    delt.executeUpdate();
                }
                try (PreparedStatement inst = conn.prepareStatement("INSERT INTO times(parkour_id, uuid, best_nanos) VALUES(?,?,?)")) {
                    for (Map.Entry<UUID, java.util.List<Long>> e : c.getTimes().entrySet()) {
                        long best = e.getValue().stream().min(Long::compareTo).orElse(Long.MAX_VALUE);
                        if (best == Long.MAX_VALUE) continue;
                        inst.setInt(1, id);
                        inst.setString(2, e.getKey().toString());
                        inst.setLong(3, best);
                        inst.addBatch();
                    }
                    inst.executeBatch();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save parkours to DB: " + e.getMessage());
        }
    }

    private void saveExtras(Connection conn, int id, ParkourCourse c) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE parkours SET plot_min_x=?, plot_min_z=?, plot_size=?, top_holo_x=?, top_holo_y=?, top_holo_z=?, best_holo_x=?, best_holo_y=?, best_holo_z=?, creator_holo_x=?, creator_holo_y=?, creator_holo_z=? WHERE id=?")) {
            if (c.getPlotRegion() != null) {
                ps.setInt(1, c.getPlotRegion().minX());
                ps.setInt(2, c.getPlotRegion().minZ());
                ps.setInt(3, c.getPlotRegion().size());
            } else {
                ps.setNull(1, Types.INTEGER);
                ps.setNull(2, Types.INTEGER);
                ps.setNull(3, Types.INTEGER);
            }
            writeBlock(ps, 4, c.getTopHologramLocation());
            writeBlock(ps, 7, c.getBestHologramLocation());
            writeBlock(ps, 10, c.getCreatorHologramLocation());
            ps.setInt(13, id);
            ps.executeUpdate();
        }
    }

    private int upsertCourse(Connection conn, ParkourCourse c) throws SQLException {
        Integer id = getCourseId(conn, c.getName());
        if (id == null) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO parkours(name, world, start_x, start_y, start_z, start_yaw, start_pitch, start_plate_x, start_plate_y, start_plate_z, finish_x, finish_y, finish_z, finish_yaw, finish_pitch, finish_plate_x, finish_plate_y, finish_plate_z, difficulty, reward, published) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                bindCourse(ps, c);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
            return Objects.requireNonNull(getCourseId(conn, c.getName()));
        } else {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE parkours SET world=?, start_x=?, start_y=?, start_z=?, start_yaw=?, start_pitch=?, start_plate_x=?, start_plate_y=?, start_plate_z=?, finish_x=?, finish_y=?, finish_z=?, finish_yaw=?, finish_pitch=?, finish_plate_x=?, finish_plate_y=?, finish_plate_z=?, difficulty=?, reward=?, published=? WHERE id=?")) {
                bindCourseUpdate(ps, c);
                ps.setInt(21, id);
                ps.executeUpdate();
                return id;
            }
        }
    }

    private void bindCourse(PreparedStatement ps, ParkourCourse c) throws SQLException {
        // INSERT columns: (name, world, start_x, start_y, start_z, start_yaw, start_pitch,
        // start_plate_x, start_plate_y, start_plate_z, finish_x, finish_y, finish_z, finish_yaw, finish_pitch,
        // finish_plate_x, finish_plate_y, finish_plate_z, difficulty, reward, published)
        int i = 0;
        ps.setString(++i, c.getName());
        ps.setString(++i, resolveWorld(c));
        writeLocation(ps, ++i, c.getStartTeleport()); i += 4;
        writeBlock(ps, ++i, c.getStartPlate()); i += 2;
        writeLocation(ps, ++i, c.getFinishTeleport()); i += 4;
        writeBlock(ps, ++i, c.getFinishPlate()); i += 2;
        if (c.getStaffDifficulty() != null) ps.setInt(++i, c.getStaffDifficulty()); else ps.setNull(++i, Types.INTEGER);
        ps.setString(++i, null);
        ps.setInt(++i, c.isPublished() ? 1 : 0);
    }

    private void bindCourseUpdate(PreparedStatement ps, ParkourCourse c) throws SQLException {
        // UPDATE sets: world=?, start_x=?, ..., published=? WHERE id=?
        int i = 0;
        ps.setString(++i, resolveWorld(c));
        writeLocation(ps, ++i, c.getStartTeleport()); i += 4;
        writeBlock(ps, ++i, c.getStartPlate()); i += 2;
        writeLocation(ps, ++i, c.getFinishTeleport()); i += 4;
        writeBlock(ps, ++i, c.getFinishPlate()); i += 2;
        if (c.getStaffDifficulty() != null) ps.setInt(++i, c.getStaffDifficulty()); else ps.setNull(++i, Types.INTEGER);
        ps.setString(++i, null);
        ps.setInt(++i, c.isPublished() ? 1 : 0);
    }

    private String resolveWorld(ParkourCourse c) {
        if (c.getStartTeleport() != null && c.getStartTeleport().getWorld() != null) return c.getStartTeleport().getWorld().getName();
        if (c.getFinishTeleport() != null && c.getFinishTeleport().getWorld() != null) return c.getFinishTeleport().getWorld().getName();
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

    public void loadTimesForCourseAsync(String courseName, java.util.function.BiConsumer<ParkourCourse, Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = true;
            ParkourCourse course = plugin.getParkourManager().getCourse(courseName);
            if (course == null) { ok = false; }
            Map<UUID, java.util.List<Long>> loaded = new HashMap<>();
            try (Connection conn = db.getConnection()) {
                Integer id = course != null ? getCourseId(conn, courseName) : null;
                if (id == null || course == null) ok = false; else {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT uuid, best_nanos FROM times WHERE parkour_id=?")) {
                        ps.setInt(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                try {
                                    UUID u = UUID.fromString(rs.getString(1));
                                    long best = rs.getLong(2);
                                    loaded.put(u, java.util.List.of(best));
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                }
            } catch (SQLException e) { ok = false; plugin.getLogger().warning("Failed to load times: " + e.getMessage()); }
            boolean success = ok;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (course != null) {
                    course.getTimes().clear();
                    course.getTimes().putAll(loaded);
                }
                callback.accept(course, success);
            });
        });
    }

    public void saveTimesAsync(ParkourCourse course) {
        // Snapshot times on main thread, then write async
        Bukkit.getScheduler().runTask(plugin, () -> {
            Map<UUID, java.util.List<Long>> snapshot = new HashMap<>();
            for (Map.Entry<UUID, java.util.List<Long>> e : course.getTimes().entrySet()) {
                snapshot.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = db.getConnection()) {
                    conn.setAutoCommit(false);
                    Integer id = getCourseId(conn, course.getName());
                    if (id != null) {
                        try (PreparedStatement delt = conn.prepareStatement("DELETE FROM times WHERE parkour_id=?")) {
                            delt.setInt(1, id);
                            delt.executeUpdate();
                        }
                        try (PreparedStatement inst = conn.prepareStatement("INSERT INTO times(parkour_id, uuid, best_nanos) VALUES(?,?,?)")) {
                            for (Map.Entry<UUID, java.util.List<Long>> e : snapshot.entrySet()) {
                                long best = e.getValue().stream().min(Long::compareTo).orElse(Long.MAX_VALUE);
                                if (best == Long.MAX_VALUE) continue;
                                inst.setInt(1, id);
                                inst.setString(2, e.getKey().toString());
                                inst.setLong(3, best);
                                inst.addBatch();
                            }
                            inst.executeBatch();
                        }
                    }
                    conn.commit();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to save times: " + e.getMessage());
                }
            });
        });
    }
}
