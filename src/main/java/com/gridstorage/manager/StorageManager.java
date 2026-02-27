package com.gridstorage.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import com.gridstorage.GridStorage;
import com.gridstorage.database.DatabaseManager;
import com.gridstorage.model.PlayerStorage;
import com.gridstorage.model.StorageSlot;

/**
 * 存储管理器
 * 管理所有玩家的仓库数据
 */
public class StorageManager {

    private final GridStorage plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, PlayerStorage> playerStorages;
    private final Map<UUID, PlayerStorage> activeViewers;

    public StorageManager(GridStorage plugin) {
        this.plugin = plugin;
        this.databaseManager = new DatabaseManager(plugin);
        this.playerStorages = new HashMap<>();
        this.activeViewers = new HashMap<>();
    }

    /**
     * 获取玩家的仓库数据
     */
    public PlayerStorage getPlayerStorage(Player player) {
        return getPlayerStorage(player.getUniqueId());
    }

    /**
     * 获取玩家的仓库数据
     */
    public PlayerStorage getPlayerStorage(UUID uuid) {
        return playerStorages.computeIfAbsent(uuid,
            k -> databaseManager.loadPlayerStorage(uuid));
    }

    /**
     * 获取指定槽位
     */
    public StorageSlot getSlot(UUID playerUUID, int slotId) {
        PlayerStorage storage = getPlayerStorage(playerUUID);
        if (storage != null) {
            return storage.getSlot(slotId);
        }
        return null;
    }

    /**
     * 玩家打开仓库GUI
     */
    public void openStorage(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStorage storage = getPlayerStorage(uuid);
        if (storage != null) {
            // 记录正在查看的玩家（用于更新GUI时仅影响当前操作者）
            activeViewers.put(uuid, storage);
            plugin.getGuiManager().openGridGUI(player, storage);
        }
    }

    /**
     * 玩家打开指定槽位
     */
    public void openSlot(Player player, int slotId) {
        UUID uuid = player.getUniqueId();
        StorageSlot slot = getSlot(uuid, slotId);
        if (slot != null) {
            slot.updateAccessTime();
            plugin.getGuiManager().openSlotGUI(player, slot);
        }
    }

    /**
     * 关闭仓库GUI
     */
    public void closeStorage(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerStorage storage = activeViewers.remove(uuid);
        if (storage != null) {
            // 保存数据到数据库
            databaseManager.savePlayerStorage(uuid);
        }
    }

    /**
     * 保存玩家数据
     */
    public void savePlayerStorage(UUID uuid) {
        databaseManager.savePlayerStorage(uuid);
    }

    /**
     * 异步保存玩家数据（用于自动保存）
     */
    public void savePlayerStorageAsync(UUID uuid) {
        plugin.getScheduler().runAsync(() -> {
            savePlayerStorage(uuid);
        });
    }

    /**
     * 保存所有玩家数据
     */
    public void saveAll() {
        databaseManager.saveAll();
    }

    /**
     * 强制关闭所有在线玩家的GUI并保存数据
     */
    public void forceCloseAndSaveAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Inventory top = player.getOpenInventory().getTopInventory();
            // 如果玩家正在浏览某个槽位，先把 GUI 里的物品写回 StorageSlot
            if (top != null && plugin.getGuiManager().isSlotGUI(top)) {
                plugin.getPluginLogger().info("关服前同步玩家 " + player.getName() + " 的槽位内容");
                // autoSave=true 不需要给玩家发消息
                plugin.getGuiManager().saveSlotContents(player, top, true);
            }
            // 关闭所有打开的GUI
            if (top != null) {
                player.closeInventory();
            }
        }
        // 最后写入数据库
        saveAll();
    }

    /**
     * 卸载玩家数据
     */
    public void unloadPlayerStorage(UUID uuid) {
        databaseManager.unloadPlayerStorage(uuid);
        playerStorages.remove(uuid);
        activeViewers.remove(uuid);
    }

    /**
     * 关闭管理器
     */
    public void shutdown() {
        forceCloseAndSaveAll();
        databaseManager.shutdown();
        playerStorages.clear();
        activeViewers.clear();
    }

    /**
     * 获取数据库管理器
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
