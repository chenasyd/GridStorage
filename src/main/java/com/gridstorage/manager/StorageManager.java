package com.gridstorage.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.gridstorage.GridStorage;
import com.gridstorage.database.DatabaseManager;
import com.gridstorage.model.PlayerStorage;
import com.gridstorage.model.StorageSlot;

public class StorageManager {

    private final GridStorage plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerStorage> playerStorages;
    private final Map<UUID, PlayerStorage> activeViewers;

    public StorageManager(GridStorage plugin) {
        this.plugin = plugin;
        this.databaseManager = new DatabaseManager(plugin);
        this.playerStorages = new ConcurrentHashMap<>();
        this.activeViewers = new ConcurrentHashMap<>();
    }

    public PlayerStorage getPlayerStorage(Player player) {
        return getPlayerStorage(player.getUniqueId());
    }

    public PlayerStorage getPlayerStorage(UUID uuid) {
        return playerStorages.computeIfAbsent(uuid,
            k -> databaseManager.loadPlayerStorage(uuid));
    }

    public StorageSlot getSlot(UUID playerUUID, int slotId) {
        PlayerStorage storage = getPlayerStorage(playerUUID);
        if (storage != null) {
            return storage.getSlot(slotId);
        }
        return null;
    }

    public void openStorage(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStorage storage = getPlayerStorage(uuid);
        if (storage != null) {
            activeViewers.put(uuid, storage);
            plugin.getGuiManager().openGridGUI(player, storage);
        }
    }

    public void openSlot(Player player, int slotId) {
        UUID uuid = player.getUniqueId();
        StorageSlot slot = getSlot(uuid, slotId);
        if (slot != null) {
            slot.updateAccessTime();
            plugin.getGuiManager().openSlotGUI(player, slot);
        }
    }

    public void closeStorage(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStorage storage = activeViewers.remove(uuid);
        if (storage != null) {
            databaseManager.savePlayerStorage(uuid);
        }
    }

    public void savePlayerStorage(UUID uuid) {
        databaseManager.savePlayerStorage(uuid);
    }

    public void savePlayerStorageAsync(UUID uuid) {
        plugin.getScheduler().runAsync(() -> {
            savePlayerStorage(uuid);
        });
    }

    public void saveAll() {
        databaseManager.saveAll();
    }

    public void forceCloseAndSaveAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top != null && plugin.getGuiManager().isSlotGUI(top)) {
                    plugin.getPluginLogger().info("关服前同步玩家 " + player.getName() + " 的槽位内容");

                    ItemStack[] snapshot = new ItemStack[top.getSize()];
                    ItemStack[] contents = top.getContents();
                    for (int i = 0; i < contents.length && i < snapshot.length; i++) {
                        snapshot[i] = contents[i] != null ? contents[i].clone() : null;
                    }

                    Integer slotId = plugin.getGuiManager().getPlayerSlotId(player);
                    plugin.getGuiManager().saveSlotContentsFromSnapshot(player, snapshot, slotId, true);
                }
                if (top != null) {
                    player.closeInventory();
                }
            } catch (Exception e) {
                plugin.getPluginLogger().warning("关服保存玩家 " + player.getName() + " 数据时出错: " + e.getMessage());
            }
        }

        saveAll();
    }

    public void unloadPlayerStorage(UUID uuid) {
        databaseManager.unloadPlayerStorage(uuid);
        playerStorages.remove(uuid);
        activeViewers.remove(uuid);
    }

    public void shutdown() {
        forceCloseAndSaveAll();
        databaseManager.shutdown();
        playerStorages.clear();
        activeViewers.clear();
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public Map<UUID, PlayerStorage> getPlayerStorages() {
        return playerStorages;
    }
}
