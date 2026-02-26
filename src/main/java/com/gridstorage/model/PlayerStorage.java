package com.gridstorage.model;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家仓库数据模型
 */
public class PlayerStorage implements ConfigurationSerializable {

    private final UUID playerUUID;
    private final String playerName;
    private final StorageSlot[] slots;
    private final int maxSlots;
    private int currentPage;

    public PlayerStorage(UUID playerUUID, String playerName, int maxSlots) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.maxSlots = maxSlots;
        this.slots = new StorageSlot[maxSlots];
        for (int i = 0; i < maxSlots; i++) {
            slots[i] = new StorageSlot(i + 1);
        }
        this.currentPage = 0;
    }

    public PlayerStorage(Map<String, Object> map) {
        this.playerUUID = UUID.fromString((String) map.get("playerUUID"));
        this.playerName = (String) map.get("playerName");
        this.maxSlots = map.containsKey("maxSlots") ? ((Number) map.get("maxSlots")).intValue() : 100;
        Object[] slotData = (Object[]) map.get("slots");
        this.slots = new StorageSlot[maxSlots];
        for (int i = 0; i < slotData.length && i < maxSlots; i++) {
            if (slotData[i] instanceof StorageSlot) {
                slots[i] = (StorageSlot) slotData[i];
            } else {
                slots[i] = new StorageSlot(i + 1);
            }
        }
        this.currentPage = map.containsKey("currentPage") ? ((Number) map.get("currentPage")).intValue() : 0;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public StorageSlot[] getSlots() {
        return slots;
    }

    public StorageSlot getSlot(int slotId) {
        if (slotId >= 1 && slotId <= maxSlots) {
            return slots[slotId - 1];
        }
        return null;
    }

    public int getMaxSlots() {
        return maxSlots;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int page) {
        if (page >= 0 && page < getMaxPages()) {
            this.currentPage = page;
        }
    }

    public int getMaxPages() {
        return (int) Math.ceil((double) maxSlots / 45.0);
    }

    public void nextPage() {
        if (currentPage < getMaxPages() - 1) {
            currentPage++;
        }
    }

    public void previousPage() {
        if (currentPage > 0) {
            currentPage--;
        }
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("playerUUID", playerUUID.toString());
        map.put("playerName", playerName);
        map.put("maxSlots", maxSlots);
        map.put("slots", slots);
        map.put("currentPage", currentPage);
        return map;
    }
}
