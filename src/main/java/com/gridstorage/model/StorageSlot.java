package com.gridstorage.model;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 仓库槽位数据模型
 */
public class StorageSlot implements ConfigurationSerializable {

    private final int slotId;
    private final ItemStack[] contents;
    private long lastAccessTime;

    public StorageSlot(int slotId) {
        this.slotId = slotId;
        this.contents = new ItemStack[54];
        this.lastAccessTime = System.currentTimeMillis();
    }

    public StorageSlot(Map<String, Object> map) {
        this.slotId = (int) map.get("slotId");
        Object[] items = (Object[]) map.get("contents");
        this.contents = new ItemStack[54];
        for (int i = 0; i < items.length && i < 54; i++) {
            if (items[i] instanceof ItemStack) {
                this.contents[i] = (ItemStack) items[i];
            }
        }
        this.lastAccessTime = map.containsKey("lastAccessTime") ? 
            ((Number) map.get("lastAccessTime")).longValue() : System.currentTimeMillis();
    }

    public int getSlotId() {
        return slotId;
    }

    public ItemStack[] getContents() {
        return contents;
    }

    public ItemStack getItem(int index) {
        if (index >= 0 && index < contents.length) {
            return contents[index];
        }
        return null;
    }

    public void setItem(int index, ItemStack item) {
        if (index >= 0 && index < contents.length) {
            contents[index] = item;
            lastAccessTime = System.currentTimeMillis();
        }
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public void updateAccessTime() {
        this.lastAccessTime = System.currentTimeMillis();
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("slotId", slotId);
        map.put("contents", contents);
        map.put("lastAccessTime", lastAccessTime);
        return map;
    }
}
