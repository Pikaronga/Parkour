package com.pikaronga.parkour.player;

import org.bukkit.Location;
import org.bukkit.World;

public class PlotRegion {
    private final String world;
    private final int minX;
    private final int minZ;
    private final int size;

    public PlotRegion(String world, int minX, int minZ, int size) {
        this.world = world;
        this.minX = minX;
        this.minZ = minZ;
        this.size = size;
    }

    public String world() { return world; }
    public int minX() { return minX; }
    public int minZ() { return minZ; }
    public int size() { return size; }

    public int maxX() { return minX + size - 1; }
    public int maxZ() { return minZ + size - 1; }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        World w = loc.getWorld();
        if (!w.getName().equalsIgnoreCase(world)) return false;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX() && z >= minZ && z <= maxZ();
    }
}

