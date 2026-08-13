package com.gridstorage.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Marks a single storage-slot chest inventory. */
public final class StorageSlotHolder implements InventoryHolder {

    private final UUID owner;
    private final int slotId;
    private Inventory inventory;
    private ItemStack[] openSnapshot = new ItemStack[0];

    public StorageSlotHolder(UUID owner, int slotId) {
        this.owner = owner;
        this.slotId = slotId;
    }

    public UUID getOwner() {
        return owner;
    }

    public int getSlotId() {
        return slotId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void captureOpenSnapshot(ItemStack[] contents) {
        if (contents == null) {
            openSnapshot = new ItemStack[0];
            return;
        }
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        openSnapshot = copy;
    }

    public ItemStack[] getOpenSnapshot() {
        return openSnapshot;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
