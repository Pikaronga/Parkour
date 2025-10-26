package com.pikaronga.parkour.storage;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.Checkpoint;
import com.pikaronga.parkour.course.ParkourCourse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.*;
import java.util.function.BiConsumer;

public class SqliteParkourStorage {
    private final ParkourPlugin plugin;
    private final DatabaseManager db;
    private final java.util.concurrent.ExecutorService runWriteExecutor;

    public SqliteParkourStorage(ParkourPlugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;
        this.runWriteExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Parkour-RunWrite");
            t.setDaemon(true);
            return t;
        });
    }

    /* =========================================================
     *                        LOAD
     * ========================================================= */
    public List<ParkourCourse> loadCourses() {
        List<ParkourCourse> list = new ArrayList<>();
        String sql = "SELECT * FROM parkours";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

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
                    for (int ci = 1; ci <= md.getColumnCount(); ci++) {
                        cols.add(md.getColumnName(ci).toLowerCase());
                    }

                    // Teleports
                    c.setStartTeleport(readLocation(world, rs, "start_"));
                    c.setFinishTeleport(readLocation(world, rs, "finish_"));

                    // Plates
                    c.setStartPlate(readBlockLocation(world, rs, "start_plate_"));
                    c.setFinishPlate(readBlockLocation(world, rs, "finish_plate_"));

                    // Difficulty / Published (nullable + guarded)
                    Integer diffObj = getNullableInteger(rs, "difficulty");
                    if (diffObj != null) c.setStaffDifficulty(diffObj);
                    Integer publishedObj = getNullableInteger(rs, "published");
                    c.setPublished(publishedObj != null && publishedObj == 1);

                    // Plot region (optional columns)
                    if (cols.contains("plot_min_x") && cols.contains("plot_min_z") && cols.contains("plot_size")) {
                        Integer minX = getNullableInteger(rs, "plot_min_x");
                        Integer minZ = getNullableInteger(rs, "plot_min_z");
                        Integer size = getNullableInteger(rs, "plot_size");
                        if (minX != null && minZ != null && size != null) {
                            // PlotRegion stores the world name (String) — pass world.getName()
                            c.setPlotRegion(new com.pikaronga.parkour.player.PlotRegion(world.getName(), minX, minZ, size));
                        }
                    }

                    // Holograms (optional columns)
                    readHolo(rs, world, cols, c, "top_", c::setTopHologramLocation);
                    readHolo(rs, world, cols, c, "best_", c::setBestHologramLocation);
                    readHolo(rs, world, cols, c, "creator_", c::setCreatorHologramLocation);

                    // Creators, checkpoints, times
                    int id = rs.getInt("id");
                    loadCreators(conn, id, c);
                    loadCheckpoints(conn, id, world, c);
                    loadTimes(conn, id, c);
                    // Load run counters (per-player + total)
                    try { loadRunCounts(c); } catch (SQLException e) { plugin.getLogger().warning("Failed to load run counts for '" + name + "': " + e.getMessage()); }

                    list.add(c);
                    loaded++;
                    plugin.getLogger().info("Loaded parkour '" + name + "' in world '" + worldName + "'.");
                } catch (Exception rowEx) {
                    plugin.getLogger().warning("Skipping parkour '" + name + "' due to invalid data: " + rowEx.getMessage());
                }
            }

            plugin.getLogger().info("Course loading summary: " + loaded + " loaded, " + skipped + " skipped.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load parkours from DB: " + e.getMessage());
        }
        return list;
    }

    /* =========================================================
     *                    RUN COUNTS: LOAD/WRITE
     * ========================================================= */
    public void loadRunCounts(ParkourCourse course) throws SQLException {
        if (course == null) return;
        try (java.sql.Connection conn = db.getConnection()) {
            // Total runs
            try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT total_runs FROM parkour_run_totals WHERE course=?")) {
                ps.setString(1, course.getName());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        course.setTotalRunCount(Math.max(0, rs.getInt(1)));
                    } else {
                        course.setTotalRunCount(0);
                    }
                }
            }
            // Per-player runs
            try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT player, runs FROM parkour_runs WHERE course=?")) {
                ps.setString(1, course.getName());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            java.util.UUID u = java.util.UUID.fromString(rs.getString(1));
                            int runs = Math.max(0, rs.getInt(2));
                            course.setPlayerRunCount(u, runs);
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
        }
    }

    public void queueRunIncrement(String courseName, java.util.UUID playerId) {
        if (courseName == null || playerId == null) return;
        runWriteExecutor.submit(() -> saveRunIncrement(courseName, playerId));
    }

    private void saveRunIncrement(String courseName, java.util.UUID playerId) {
        try (java.sql.Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            // Per-player upsert
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO parkour_runs(course, player, runs) VALUES(?,?,1) " +
                            "ON CONFLICT(course, player) DO UPDATE SET runs = runs + 1")) {
                ps.setString(1, courseName);
                ps.setString(2, playerId.toString());
                ps.executeUpdate();
            }
            // Total upsert
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO parkour_run_totals(course, total_runs) VALUES(?,1) " +
                            "ON CONFLICT(course) DO UPDATE SET total_runs = total_runs + 1")) {
                ps.setString(1, courseName);
                ps.executeUpdate();
            }
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to persist run increment for '" + courseName + "': " + e.getMessage());
        }
    }

    private void readHolo(ResultSet rs, World world, Set<String> cols, ParkourCourse c, String prefix,
                          java.util.function.Consumer<Location> setter) throws SQLException {
        if (cols.contains(prefix + "holo_x")) {
            Double x = getNullableDouble(rs, prefix + "holo_x");
            Double y = getNullableDouble(rs, prefix + "holo_y");
            Double z = getNullableDouble(rs, prefix + "holo_z");
            if (x != null && y != null && z != null) setter.accept(new Location(world, x, y, z));
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
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM checkpoints WHERE parkour_id=? ORDER BY ord ASC")) {
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
                    c.addCheckpoint(new Checkpoint(new Location(world, px, py, pz),
                            new Location(world, rx, ry, rz, yaw, pitch)));
                }
            }
        }
    }

    public void loadTimes(Connection conn, int parkourId, ParkourCourse c) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT uuid, best_nanos FROM times WHERE parkour_id=?")) {
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
            if (obj instanceof String s) { try { return Double.parseDouble(s); } catch (NumberFormatException ex) { return null; } }
            return null;
        } catch (SQLException ex) { return null; }
    }

    private Integer getNullableInteger(ResultSet rs, String column) throws SQLException {
        try {
            Object obj = rs.getObject(column);
            if (obj == null) return null;
            if (obj instanceof Number n) return n.intValue();
            if (obj instanceof String s) { try { return Integer.parseInt(s); } catch (NumberFormatException ex) { return null; } }
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

    /* =========================================================
     *                        SAVE
     * ========================================================= */
    public void saveCourses(Map<String, ParkourCourse> courses) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            for (ParkourCourse c : courses.values()) {
                int id = upsertCourse(conn, c);
                saveExtras(conn, id, c);

                // Creators (rewrite)
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM creators WHERE parkour_id=?")) {
                    del.setInt(1, id);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO creators(parkour_id, uuid) VALUES(?,?)")) {
                    for (UUID u : c.getCreators()) {
                        ins.setInt(1, id);
                        ins.setString(2, u.toString());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }

                // Checkpoints (rewrite)
                try (PreparedStatement delc = conn.prepareStatement("DELETE FROM checkpoints WHERE parkour_id=?")) {
                    delc.setInt(1, id);
                    delc.executeUpdate();
                }
                try (PreparedStatement insc = conn.prepareStatement(
                        "INSERT INTO checkpoints(parkour_id, ord, plate_x, plate_y, plate_z, respawn_x, respawn_y, respawn_z, respawn_yaw, respawn_pitch) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                    int ord = 0;
                    for (Checkpoint cp : c.getCheckpoints()) {
                        Location plate = cp.plateLocation();
                        Location resp = cp.respawnLocation();
                        insc.setInt(1, id);
                        insc.setInt(2, ord++);
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

                // Times (rewrite best per player)
                try (PreparedStatement delt = conn.prepareStatement("DELETE FROM times WHERE parkour_id=?")) {
                    delt.setInt(1, id);
                    delt.executeUpdate();
                }
                try (PreparedStatement inst = conn.prepareStatement(
                        "INSERT INTO times(parkour_id, uuid, best_nanos) VALUES(?,?,?)")) {
                    for (Map.Entry<UUID, List<Long>> e : c.getTimes().entrySet()) {
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

    private int upsertCourse(Connection conn, ParkourCourse c) throws SQLException {
        Integer id = getCourseId(conn, c.getName());
        if (id == null) {
            // 21 placeholders, keep order in bindCourse()
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO parkours(" +
                            "name, world, " +
                            "start_x, start_y, start_z, start_yaw, start_pitch, " +
                            "start_plate_x, start_plate_y, start_plate_z, " +
                            "finish_x, finish_y, finish_z, finish_yaw, finish_pitch, " +
                            "finish_plate_x, finish_plate_y, finish_plate_z, " +
                            "difficulty, reward, published" +
                            ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                bindCourse(ps, c);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
            return Objects.requireNonNull(getCourseId(conn, c.getName()));
        } else {
            // 20 setters + WHERE id=?
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE parkours SET " +
                            "world=?, " +
                            "start_x=?, start_y=?, start_z=?, start_yaw=?, start_pitch=?, " +
                            "start_plate_x=?, start_plate_y=?, start_plate_z=?, " +
                            "finish_x=?, finish_y=?, finish_z=?, finish_yaw=?, finish_pitch=?, " +
                            "finish_plate_x=?, finish_plate_y=?, finish_plate_z=?, " +
                            "difficulty=?, reward=?, published=? " +
                            "WHERE id=?")) {
                bindCourseUpdate(ps, c);
                ps.setInt(21, id);
                ps.executeUpdate();
                return id;
            }
        }
    }

    private void bindCourse(PreparedStatement ps, ParkourCourse c) throws SQLException {
        // Explicit indices to guarantee correct binding order for INSERT with 21 placeholders
        // 1 name, 2 world,
        // 3-7 start_x,start_y,start_z,start_yaw,start_pitch
        // 8-10 start_plate_x,start_plate_y,start_plate_z
        // 11-15 finish_x,finish_y,finish_z,finish_yaw,finish_pitch
        // 16-18 finish_plate_x,finish_plate_y,finish_plate_z
        // 19 difficulty, 20 reward, 21 published
        ps.setString(1, c.getName());                 // name
        ps.setString(2, resolveWorld(c));             // world (STRING)
        writeLocation(ps, 3, c.getStartTeleport());   // 3..7
        writeBlock(ps, 8, c.getStartPlate());         // 8..10
        writeLocation(ps, 11, c.getFinishTeleport()); // 11..15
        writeBlock(ps, 16, c.getFinishPlate());       // 16..18
        if (c.getStaffDifficulty() != null) ps.setInt(19, c.getStaffDifficulty()); else ps.setNull(19, Types.INTEGER); // difficulty
        ps.setNull(20, Types.INTEGER);                 // reward (unknown in model -> NULL int)
        ps.setInt(21, c.isPublished() ? 1 : 0);        // published
    }

    private void bindCourseUpdate(PreparedStatement ps, ParkourCourse c) throws SQLException {
        // Explicit indices for UPDATE parameter order (20 setters)
        // 1 world,
        // 2-6 start_x,start_y,start_z,start_yaw,start_pitch
        // 7-9 start_plate_x,start_plate_y,start_plate_z
        // 10-14 finish_x,finish_y,finish_z,finish_yaw,finish_pitch
        // 15-17 finish_plate_x,finish_plate_y,finish_plate_z
        // 18 difficulty, 19 reward, 20 published
        ps.setString(1, resolveWorld(c));             // world (STRING)
        writeLocation(ps, 2, c.getStartTeleport());   // 2..6
        writeBlock(ps, 7, c.getStartPlate());         // 7..9
        writeLocation(ps, 10, c.getFinishTeleport()); // 10..14
        writeBlock(ps, 15, c.getFinishPlate());       // 15..17
        if (c.getStaffDifficulty() != null) ps.setInt(18, c.getStaffDifficulty()); else ps.setNull(18, Types.INTEGER); // difficulty
        ps.setNull(19, Types.INTEGER);                 // reward (unknown in model -> NULL int)
        ps.setInt(20, c.isPublished() ? 1 : 0);        // published
    }

    private void saveExtras(Connection conn, int id, ParkourCourse c) {
        // Optional columns: ignore if schema doesn't have them
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE parkours SET " +
                        "plot_min_x=?, plot_min_z=?, plot_size=?, " +
                        "top_holo_x=?, top_holo_y=?, top_holo_z=?, " +
                        "best_holo_x=?, best_holo_y=?, best_holo_z=?, " +
                        "creator_holo_x=?, creator_holo_y=?, creator_holo_z=? " +
                        "WHERE id=?")) {
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
        } catch (SQLException ignored) {
            // If columns don't exist (older schema), just skip without failing the whole save.
        }
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

    /* =========================================================
     *                  TIMES: ASYNC LOAD/SAVE
     * ========================================================= */
    public void loadTimesForCourseAsync(String courseName, BiConsumer<ParkourCourse, Boolean> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean ok = true;
            ParkourCourse course = plugin.getParkourManager().getCourse(courseName);
            if (course == null) ok = false;

            Map<UUID, List<Long>> loaded = new HashMap<>();
            try (Connection conn = db.getConnection()) {
                Integer id = (course != null) ? getCourseId(conn, courseName) : null;
                if (id == null || course == null) {
                    ok = false;
                } else {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT uuid, best_nanos FROM times WHERE parkour_id=?")) {
                        ps.setInt(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                try {
                                    UUID u = UUID.fromString(rs.getString(1));
                                    long best = rs.getLong(2);
                                    loaded.put(u, List.of(best));
                                } catch (IllegalArgumentException ignored) {}
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                ok = false;
                plugin.getLogger().warning("Failed to load times: " + e.getMessage());
            }

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
        // Snapshot on main thread, write async
        Bukkit.getScheduler().runTask(plugin, () -> {
            Map<UUID, List<Long>> snapshot = new HashMap<>();
            for (Map.Entry<UUID, List<Long>> e : course.getTimes().entrySet()) {
                snapshot.put(e.getKey(), new ArrayList<>(e.getValue()));
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try (Connection conn = db.getConnection()) {
                    conn.setAutoCommit(false);
                    Integer id = getCourseId(conn, course.getName());
                    if (id != null) {
                        try (PreparedStatement del = conn.prepareStatement("DELETE FROM times WHERE parkour_id=?")) {
                            del.setInt(1, id);
                            del.executeUpdate();
                        }
                        try (PreparedStatement ins = conn.prepareStatement(
                                "INSERT INTO times(parkour_id, uuid, best_nanos) VALUES(?,?,?)")) {
                            for (Map.Entry<UUID, List<Long>> e : snapshot.entrySet()) {
                                long best = e.getValue().stream().min(Long::compareTo).orElse(Long.MAX_VALUE);
                                if (best == Long.MAX_VALUE) continue;
                                ins.setInt(1, id);
                                ins.setString(2, e.getKey().toString());
                                ins.setLong(3, best);
                                ins.addBatch();
                            }
                            ins.executeBatch();
                        }
                    }
                    conn.commit();
                } catch (SQLException e) {
                    plugin.getLogger().warning("Failed to save times: " + e.getMessage());
                }
            });
        });
    }

    /* =========================================================
     *                 DATA CLEANUP / MIGRATION
     * ========================================================= */
    public void cleanupInvalidNumericColumns() {
        String[] numericCols = new String[]{
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
                // If column missing, ignore error
                try (Statement st = conn.createStatement()) {
                    String sql = "UPDATE parkours SET " + col + " = NULL " +
                            "WHERE typeof(" + col + ") NOT IN ('integer','real','null')";
                    st.executeUpdate(sql);
                } catch (SQLException ignoreIfMissing) {}
            }
            conn.commit();
        } catch (SQLException e) {
            plugin.getLogger().warning("Cleanup of invalid numeric columns failed: " + e.getMessage());
        }
    }
}
