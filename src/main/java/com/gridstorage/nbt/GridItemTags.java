package com.gridstorage.nbt;

import org.bukkit.inventory.ItemStack;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTItem;

/**
 * NBT tags for grid GUI buttons (only call when NBTAPI is available).
 */
public final class GridItemTags {

    private GridItemTags() {
    }

    public static void markSlot(ItemStack item, int slotId) {
        NBT.modify(item, nbt -> {
            nbt.setString("gridstorage_type", "slot");
            nbt.setString("gridstorage_slot_id", String.valueOf(slotId));
        });
    }

    public static void markNavigation(ItemStack item, String action) {
        NBT.modify(item, nbt -> {
            nbt.setString("gridstorage_type", "navigation");
            nbt.setString("gridstorage_action", action);
        });
    }

    public static String getType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        return new NBTItem(item).getString("gridstorage_type");
    }

    public static String getSlotId(ItemStack item) {
        if (item == null) {
            return "";
        }
        return new NBTItem(item).getString("gridstorage_slot_id");
    }

    public static String getAction(ItemStack item) {
        if (item == null) {
            return "";
        }
        return new NBTItem(item).getString("gridstorage_action");
    }
}
