package com.gridstorage.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/** Marks the main grid (slot picker) inventory. */
public final class GridGuiHolder implements InventoryHolder {

    private final UUID owner;
    private final int page;
    private Inventory inventory;

    public GridGuiHolder(UUID owner, int page) {
        this.owner = owner;
        this.page = page;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getPage() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
