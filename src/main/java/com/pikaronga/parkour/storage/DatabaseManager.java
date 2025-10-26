package com.pikaronga.parkour.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private final JavaPlugin plugin;
    private final String url;

    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        File folder = plugin.getDataFolder();
        if (!folder.exists()) {
            //noinspection ResultOfMethodCallIgnored
            folder.mkdirs();
        }
        File dbFile = new File(folder, "data.db");
        this.url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        try (Connection ignored = getConnection()) {
            setup();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize SQLite DB: " + e.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void setup() throws SQLException {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("PRAGMA foreign_keys = ON");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS parkours (\n" +
                    " id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    " name TEXT UNIQUE NOT NULL,\n" +
                    " world TEXT NOT NULL,\n" +
                    " start_x REAL, start_y REAL, start_z REAL, start_yaw REAL, start_pitch REAL,\n" +
                    " start_plate_x REAL, start_plate_y REAL, start_plate_z REAL,\n" +
                    " finish_x REAL, finish_y REAL, finish_z REAL, finish_yaw REAL, finish_pitch REAL,\n" +
                    " finish_plate_x REAL, finish_plate_y REAL, finish_plate_z REAL,\n" +
                    " difficulty INTEGER,\n" +
                    " reward TEXT,\n" +
                    " published INTEGER DEFAULT 0\n" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS creators (\n" +
                    " parkour_id INTEGER NOT NULL,\n" +
                    " uuid TEXT NOT NULL,\n" +
                    " PRIMARY KEY (parkour_id, uuid),\n" +
                    " FOREIGN KEY (parkour_id) REFERENCES parkours(id) ON DELETE CASCADE\n" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS checkpoints (\n" +
                    " id INTEGER PRIMARY KEY AUTOINCREMENT,\n" +
                    " parkour_id INTEGER NOT NULL,\n" +
                    " ord INTEGER NOT NULL,\n" +
                    " plate_x REAL, plate_y REAL, plate_z REAL,\n" +
                    " respawn_x REAL, respawn_y REAL, respawn_z REAL, respawn_yaw REAL, respawn_pitch REAL,\n" +
                    " FOREIGN KEY (parkour_id) REFERENCES parkours(id) ON DELETE CASCADE\n" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS times (\n" +
                    " parkour_id INTEGER NOT NULL,\n" +
                    " uuid TEXT NOT NULL,\n" +
                    " best_nanos INTEGER NOT NULL,\n" +
                    " last_updated INTEGER,\n" +
                    " PRIMARY KEY (parkour_id, uuid),\n" +
                    " FOREIGN KEY (parkour_id) REFERENCES parkours(id) ON DELETE CASCADE\n" +
                    ")");
            // Ratings (per-player look and difficulty 1-5)
            st.executeUpdate("CREATE TABLE IF NOT EXISTS ratings (\n" +
                    " parkour_id INTEGER NOT NULL,\n" +
                    " uuid TEXT NOT NULL,\n" +
                    " look INTEGER,\n" +
                    " difficulty INTEGER,\n" +
                    " PRIMARY KEY (parkour_id, uuid),\n" +
                    " FOREIGN KEY (parkour_id) REFERENCES parkours(id) ON DELETE CASCADE\n" +
                    ")");
            // Performance indexes for lookups and leaderboards
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_parkours_name ON parkours(name)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_creators_uuid ON creators(uuid)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_times_parkour_best ON times(parkour_id, best_nanos)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_checkpoints_p_on ON checkpoints(parkour_id, ord)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_ratings_parkour ON ratings(parkour_id)");

            // Add missing column for legacy installs
            try { st.executeUpdate("ALTER TABLE times ADD COLUMN last_updated INTEGER"); } catch (SQLException ignored) {}

            // Extra columns for plot region and holograms (ALTER TABLE if missing)
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN plot_min_x INTEGER"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN plot_min_z INTEGER"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN plot_size INTEGER"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN top_holo_x REAL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN top_holo_y REAL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN top_holo_z REAL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN best_holo_x REAL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN best_holo_y REAL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN best_holo_z REAL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN creator_holo_x REAL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN creator_holo_y REAL"); } catch (SQLException ignored) {}
            try { st.executeUpdate("ALTER TABLE parkours ADD COLUMN creator_holo_z REAL"); } catch (SQLException ignored) {}

            // Run counters (per-player and totals) for accurate completion counts
            st.executeUpdate("CREATE TABLE IF NOT EXISTS parkour_runs (\n" +
                    " course TEXT NOT NULL,\n" +
                    " player TEXT NOT NULL,\n" +
                    " runs INTEGER NOT NULL DEFAULT 0,\n" +
                    " PRIMARY KEY (course, player)\n" +
                    ")");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS parkour_run_totals (\n" +
                    " course TEXT NOT NULL PRIMARY KEY,\n" +
                    " total_runs INTEGER NOT NULL DEFAULT 0\n" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_runs_course ON parkour_runs(course)");

            // Global per-player stats
            st.executeUpdate("CREATE TABLE IF NOT EXISTS parkour_stats (\n" +
                    " uuid TEXT NOT NULL PRIMARY KEY,\n" +
                    " total_runs INTEGER NOT NULL DEFAULT 0,\n" +
                    " completed_courses INTEGER NOT NULL DEFAULT 0,\n" +
                    " last_run INTEGER\n" +
                    ")");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_stats_uuid ON parkour_stats(uuid)");
        }
    }
}
