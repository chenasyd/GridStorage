package com.gridstorage.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import com.gridstorage.GridStorage;
import com.gridstorage.database.DatabaseManager;
import com.gridstorage.gui.StorageSlotHolder;
import com.gridstorage.model.PlayerStorage;
import com.gridstorage.model.StorageSlot;

public class StorageManager {

    private final GridStorage plugin;
    private final DatabaseManager databaseManager;
    /** Single in-memory cache (replaces old playerStorages + loadedStorages dual write). */
    private final Map<UUID, PlayerStorage> playerStorages = new ConcurrentHashMap<>();
    /** Player currently holding an open slot GUI (slot id). */
    private final ConcurrentHashMap<UUID, Integer> openSessions = new ConcurrentHashMap<>();
    /** In-flight saves; quit must not drop session until these finish. */
    private final ConcurrentHashMap<UUID, CompletableFuture<Boolean>> pendingSaves = new ConcurrentHashMap<>();

    public StorageManager(GridStorage plugin) {
        this.plugin = plugin;
        this.databaseManager = new DatabaseManager(plugin);
    }

    public boolean isFeatureAvailable() {
        return plugin.isNbtApiAvailable();
    }

    public PlayerStorage getCachedStorage(UUID uuid) {
        return playerStorages.get(uuid);
    }

    public PlayerStorage getPlayerStorage(Player player) {
        return getOrLoadSync(player.getUniqueId());
    }

    public PlayerStorage getPlayerStorage(UUID uuid) {
        return getOrLoadSync(uuid);
    }

    private PlayerStorage getOrLoadSync(UUID uuid) {
        return playerStorages.computeIfAbsent(uuid, databaseManager::loadPlayerStorage);
    }

    public CompletableFuture<PlayerStorage> loadPlayerStorageAsync(UUID uuid) {
        PlayerStorage cached = playerStorages.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() ->
                playerStorages.computeIfAbsent(uuid, databaseManager::loadPlayerStorage));
    }

    public StorageSlot getSlot(UUID playerUUID, int slotId) {
        PlayerStorage storage = getCachedStorage(playerUUID);
        if (storage == null) {
            storage = getOrLoadSync(playerUUID);
        }
        return storage != null ? storage.getSlot(slotId) : null;
    }

    public boolean tryAcquireSession(UUID playerUuid, int slotId) {
        Integer existing = openSessions.putIfAbsent(playerUuid, slotId);
        if (existing == null) {
            return true;
        }
        if (existing == slotId) {
            return true;
        }
        openSessions.put(playerUuid, slotId);
        return true;
    }

    public void releaseSession(UUID playerUuid) {
        openSessions.remove(playerUuid);
    }

    public void releaseSessionIfIdle(UUID playerUuid) {
        CompletableFuture<Boolean> pending = pendingSaves.get(playerUuid);
        if (pending == null || pending.isDone()) {
            openSessions.remove(playerUuid);
        }
    }

    public Integer getSessionSlot(UUID playerUuid) {
        return openSessions.get(playerUuid);
    }

    public void openStorage(Player player) {
        if (!isFeatureAvailable()) {
            player.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("storage.messages.nbtapi-missing"));
            return;
        }
        UUID uuid = player.getUniqueId();
        loadPlayerStorageAsync(uuid).whenComplete((storage, err) -> {
            if (err != null || storage == null) {
                Throwable cause = unwrap(err);
                plugin.getPluginLogger().warning("加载仓库失败: " + uuid
                        + (cause != null ? ": " + cause.getMessage() : ""));
                plugin.getScheduler().runAtEntity(player, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage("storage.messages.load-failed"));
                    }
                });
                return;
            }
            plugin.getScheduler().runAtEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                plugin.getGuiManager().openGridGUI(player, storage);
                player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("storage.messages.open-storage"));
            });
        });
    }

    public void openSlot(Player player, int slotId) {
        if (!isFeatureAvailable()) {
            player.sendMessage(plugin.getConfigManager().getPrefix()
                    + plugin.getConfigManager().getMessage("storage.messages.nbtapi-missing"));
            return;
        }
        UUID uuid = player.getUniqueId();
        loadPlayerStorageAsync(uuid).whenComplete((storage, err) -> {
            if (err != null || storage == null) {
                Throwable cause = unwrap(err);
                plugin.getPluginLogger().warning("加载仓库失败: " + uuid
                        + (cause != null ? ": " + cause.getMessage() : ""));
                plugin.getScheduler().runAtEntity(player, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(plugin.getConfigManager().getPrefix()
                                + plugin.getConfigManager().getMessage("storage.messages.load-failed"));
                    }
                });
                return;
            }
            plugin.getScheduler().runAtEntity(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                StorageSlot slot = storage.getSlot(slotId);
                if (slot == null) {
                    player.sendMessage(plugin.getConfigManager().getPrefix()
                            + plugin.getConfigManager().getMessage("storage.messages.invalid-slot"));
                    return;
                }
                Integer previous = openSessions.get(uuid);
                if (previous != null && previous != slotId) {
                    Inventory top = player.getOpenInventory().getTopInventory();
                    if (top.getHolder() instanceof StorageSlotHolder holder && holder.getSlotId() == previous) {
                        applySnapshotToSlot(uuid, previous, cloneContents(top.getContents()));
                        savePlayerStorageAsync(uuid);
                    }
                }
                tryAcquireSession(uuid, slotId);
                slot.updateAccessTime();
                plugin.getGuiManager().openSlotGUI(player, slot);
                player.sendMessage(plugin.getConfigManager().getPrefix()
                        + plugin.getConfigManager().getMessage("storage.messages.open-slot",
                        String.valueOf(slotId)));
            });
        });
    }

    public void closeStorage(Player player) {
        // Grid close only; slot session released after async save completes
    }

    /**
     * Region/main thread: clone contents, write memory, async DB save, then release session if idle.
     */
    public void handleSlotClose(Player player, StorageSlotHolder holder, Inventory inventory) {
        UUID uuid = player.getUniqueId();
        int slotId = holder.getSlotId();
        ItemStack[] snapshot = cloneContents(inventory.getContents());
        applySnapshotToSlot(uuid, slotId, snapshot);
        UUID playerUuid = uuid;
        savePlayerStorageAsync(uuid).whenComplete((ok, err) -> {
            if (!Boolean.TRUE.equals(ok) || err != null) {
                Throwable cause = unwrap(err);
                plugin.getPluginLogger().warning("保存槽位失败: " + playerUuid
                        + (cause != null ? ": " + cause.getMessage() : ""));
            }
            plugin.getScheduler().runAtEntity(player, () -> {
                if (!player.isOnline()) {
                    releaseSession(playerUuid);
                    return;
                }
                Inventory top = player.getOpenInventory().getTopInventory();
                InventoryHolder h = top.getHolder();
                if (!(h instanceof StorageSlotHolder open) || open.getSlotId() != slotId
                        || !open.getOwner().equals(playerUuid)) {
                    Integer session = openSessions.get(playerUuid);
                    if (session == null || session == slotId) {
                        releaseSession(playerUuid);
                    }
                }
            });
        });
    }

    public void applySnapshotToSlot(UUID uuid, int slotId, ItemStack[] snapshot) {
        StorageSlot slot = getSlot(uuid, slotId);
        if (slot == null || snapshot == null) {
            return;
        }
        ItemStack[] slotContents = slot.getContents();
        for (int i = 0; i < Math.min(snapshot.length, slotContents.length); i++) {
            slotContents[i] = snapshot[i] != null ? snapshot[i].clone() : null;
        }
        slot.updateAccessTime();
    }

    public CompletableFuture<Boolean> savePlayerStorageAsync(UUID uuid) {
        PlayerStorage storage = playerStorages.get(uuid);
        if (storage == null) {
            return CompletableFuture.completedFuture(true);
        }
        PlayerStorage snapshot = storage;
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            try {
                databaseManager.savePlayerStorage(snapshot);
                return true;
            } catch (Exception e) {
                plugin.getPluginLogger().warning("异步保存失败: " + uuid + ": " + e.getMessage());
                return false;
            }
        });
        pendingSaves.put(uuid, future);
        future.whenComplete((ok, err) -> pendingSaves.remove(uuid, future));
        return future;
    }

    public void savePlayerStorage(UUID uuid) {
        PlayerStorage storage = playerStorages.get(uuid);
        if (storage != null) {
            databaseManager.savePlayerStorage(storage);
        }
    }

    public void saveAll() {
        int count = 0;
        for (UUID uuid : playerStorages.keySet()) {
            savePlayerStorage(uuid);
            count++;
        }
        plugin.getPluginLogger().info("已保存 " + count + " 个玩家的数据");
    }

    /**
     * Shutdown path: sync snapshot open GUIs, save once, then close DB.
     */
    public void forceCloseAndSaveAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top != null && top.getHolder() instanceof StorageSlotHolder holder) {
                    plugin.getPluginLogger().info("关服前同步玩家 " + player.getName() + " 的槽位内容");
                    applySnapshotToSlot(holder.getOwner(), holder.getSlotId(), cloneContents(top.getContents()));
                }
                if (top != null) {
                    player.closeInventory();
                }
            } catch (Exception e) {
                plugin.getPluginLogger().warning("关服保存玩家 " + player.getName() + " 数据时出错: " + e.getMessage());
            }
        }
        saveAll();
        openSessions.clear();
        pendingSaves.clear();
    }

    public void unloadPlayerStorage(UUID uuid) {
        PlayerStorage storage = playerStorages.get(uuid);
        if (storage != null) {
            databaseManager.savePlayerStorage(storage);
        }
        playerStorages.remove(uuid);
        openSessions.remove(uuid);
        pendingSaves.remove(uuid);
    }

    public void shutdown() {
        forceCloseAndSaveAll();
        databaseManager.shutdown();
        playerStorages.clear();
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public Map<UUID, PlayerStorage> getPlayerStorages() {
        return playerStorages;
    }

    private static ItemStack[] cloneContents(ItemStack[] source) {
        if (source == null) {
            return new ItemStack[0];
        }
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    private static Throwable unwrap(Throwable err) {
        if (err instanceof CompletionException && err.getCause() != null) {
            return err.getCause();
        }
        return err;
    }
}
