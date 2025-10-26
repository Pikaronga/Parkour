package com.pikaronga.parkour.placeholder;

import com.pikaronga.parkour.ParkourPlugin;
import com.pikaronga.parkour.course.ParkourCourse;
import com.pikaronga.parkour.session.ParkourSession;
import com.pikaronga.parkour.util.ParkourManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

public class ParkourPlaceholders extends PlaceholderExpansion {
    private final ParkourPlugin plugin;

    public ParkourPlaceholders(ParkourPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "parkour";
    }

    @Override
    public String getAuthor() {
        return "OpenAI";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offline, String params) {
        if (params == null) return "";
        String key = params.toLowerCase(Locale.ROOT).trim();

        Player player = offline != null ? offline.getPlayer() : null; // may be null if offline
        ParkourManager pm = plugin.getParkourManager();

        switch (key) {
            // Current course context (if the player is in a session)
            case "current_course": {
                if (player == null) return "";
                ParkourSession s = plugin.getSessionManager().getSession(player);
                return s == null ? "" : s.getCourse().getName();
            }
            case "current_best_time": {
                if (player == null) return "";
                ParkourSession s = plugin.getSessionManager().getSession(player);
                if (s == null) return "";
                ParkourCourse c = s.getCourse();
                long bestNanos = c.getBestTime(player.getUniqueId());
                if (bestNanos <= 0) return "";
                return formatHuman(bestNanos);
            }

            // Personal totals
            case "completed_parkours": {
                if (offline == null || offline.getUniqueId() == null) return "0";
                int count = 0;
                for (ParkourCourse c : pm.getCourses().values()) {
                    if (c.getBestTime(offline.getUniqueId()) > 0) count++;
                }
                return Integer.toString(count);
            }
            case "created_parkours": {
                if (offline == null || offline.getUniqueId() == null) return "0";
                int count = 0;
                for (ParkourCourse c : pm.getCourses().values()) {
                    if (c.getCreators().contains(offline.getUniqueId())) count++;
                }
                return Integer.toString(count);
            }
            case "completed_runs": {
                // Best-effort: sums recorded time entries per course for this player.
                // Note: due to storage, only best time persists across restarts.
                if (offline == null || offline.getUniqueId() == null) return "0";
                int total = 0;
                for (ParkourCourse c : pm.getCourses().values()) {
                    Map<java.util.UUID, java.util.List<Long>> times = c.getTimes();
                    java.util.List<Long> list = times.get(offline.getUniqueId());
                    if (list != null) total += list.size();
                }
                return Integer.toString(total);
            }

            // Server totals
            case "completed_runs_total": {
                int total = 0;
                for (ParkourCourse c : pm.getCourses().values()) {
                    for (java.util.List<Long> list : c.getTimes().values()) {
                        total += list.size();
                    }
                }
                return Integer.toString(total);
            }
            case "created_parkours_total": {
                int count = 0;
                for (ParkourCourse c : pm.getCourses().values()) {
                    if (c.isPublished()) count++;
                }
                return Integer.toString(count);
            }

            // Accurate run counters (persisted)
            case "current_runs_player": {
                if (player == null) return "0";
                ParkourSession s = plugin.getSessionManager().getSession(player);
                if (s == null) return "0";
                return Integer.toString(s.getCourse().getPlayerRunCount(player.getUniqueId()));
            }
            case "current_runs_total": {
                ParkourSession s = (player != null) ? plugin.getSessionManager().getSession(player) : null;
                if (s == null) return "0";
                return Integer.toString(s.getCourse().getTotalRunCount());
            }

            // Additional useful placeholders
            case "courses_total": {
                return Integer.toString(pm.getCourses().size());
            }
            case "courses_published": {
                int count = 0;
                for (ParkourCourse c : pm.getCourses().values()) if (c.isPublished()) count++;
                return Integer.toString(count);
            }
            case "current_best_rank": {
                if (player == null) return "";
                ParkourSession s = plugin.getSessionManager().getSession(player);
                if (s == null) return "";
                ParkourCourse c = s.getCourse();
                long best = c.getBestTime(player.getUniqueId());
                if (best <= 0) return "";
                int idx = 1;
                for (var e : c.getTopTimes(plugin.getConfigManager().getTopHologramSize())) {
                    if (e.getKey().equals(player.getUniqueId())) return Integer.toString(idx);
                    idx++;
                }
                return "";
            }

            default:
                return "";
        }
    }

    private String formatHuman(long nanos) {
        if (nanos < 0) nanos = 0;
        long minutes = nanos / 60_000_000_000L;
        long rem = nanos - minutes * 60_000_000_000L;
        double seconds = rem / 1_000_000_000.0; // fractional seconds
        // Show one decimal for seconds with comma as decimal separator
        String secOneDecimal = String.format(java.util.Locale.US, "%.1f", seconds).replace('.', ',');
        if (minutes <= 0) {
            return secOneDecimal + "sec";
        }
        return minutes + "min " + secOneDecimal + "sec";
    }
}
