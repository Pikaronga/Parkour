package com.pikaronga.parkour.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Ensures collisions are disabled for all players by placing them in a no-collision team.
 */
public class CollisionManager implements Listener {
    private static final String TEAM_NAME = "parkour_nocollide";

    private Team ensureTeam() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(TEAM_NAME);
        if (team == null) {
            team = sb.registerNewTeam(TEAM_NAME);
        }
        try {
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
            // Keep name tags as default; do not affect visibility or other options
        } catch (Throwable ignored) { /* Older APIs may not support options here */ }
        return team;
    }

    public void applyToOnlinePlayers() {
        Team team = ensureTeam();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!team.hasEntry(p.getName())) team.addEntry(p.getName());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        try {
            ensureTeam().addEntry(event.getPlayer().getName());
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        try {
            Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(TEAM_NAME);
            if (t != null) t.removeEntry(event.getPlayer().getName());
        } catch (Throwable ignored) {}
    }
}

