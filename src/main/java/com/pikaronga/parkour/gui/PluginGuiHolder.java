package com.pikaronga.parkour.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marker holder to robustly identify plugin GUIs regardless of title changes. */
public class PluginGuiHolder implements InventoryHolder {
    private final String id;

    public PluginGuiHolder(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    @Override
    public Inventory getInventory() { return null; }
}

