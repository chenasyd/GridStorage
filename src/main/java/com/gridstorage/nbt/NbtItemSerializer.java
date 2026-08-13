package com.gridstorage.nbt;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;

/**
 * Isolates NBTAPI item array serialization (only call when NBTAPI is available).
 */
public final class NbtItemSerializer {

    private NbtItemSerializer() {
    }

    public static String serializeItems(ItemStack[] items) {
        if (items == null) {
            return null;
        }
        boolean hasItem = false;
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                hasItem = true;
                break;
            }
        }
        if (!hasItem) {
            return null;
        }

        NBTContainer compound = new NBTContainer();
        int itemCount = 0;
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            try {
                ReadWriteNBT itemNbt = NBT.itemStackToNBT(item);
                if (itemNbt != null) {
                    compound.setString("slot_" + i, itemNbt.toString());
                    itemCount++;
                }
            } catch (Exception ignored) {
            }
        }
        if (itemCount == 0) {
            return null;
        }
        compound.setString("size", String.valueOf(items.length));
        return compound.toString();
    }

    public static ItemStack[] deserializeItems(String nbtData, int size) {
        ItemStack[] items = new ItemStack[Math.max(1, size)];
        if (nbtData == null || nbtData.isEmpty() || "{}".equals(nbtData)) {
            return items;
        }
        try {
            NBTContainer compound = new NBTContainer(nbtData);
            for (int i = 0; i < items.length; i++) {
                String slotKey = "slot_" + i;
                if (!compound.hasTag(slotKey)) {
                    continue;
                }
                try {
                    String slotNbtString = compound.getString(slotKey);
                    if (slotNbtString != null && !slotNbtString.isEmpty() && !"{}".equals(slotNbtString)) {
                        ReadWriteNBT itemCompound = new NBTContainer(slotNbtString);
                        ItemStack stack = NBT.itemStackFromNBT(itemCompound);
                        if (stack != null && stack.getType() != Material.AIR) {
                            items[i] = stack;
                            continue;
                        }
                    }
                } catch (Exception ignored) {
                }
                try {
                    ReadWriteNBT itemCompound = compound.getCompound(slotKey);
                    if (itemCompound != null) {
                        ItemStack stack = NBT.itemStackFromNBT(itemCompound);
                        if (stack != null && stack.getType() != Material.AIR) {
                            items[i] = stack;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }
}
