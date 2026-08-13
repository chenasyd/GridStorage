package com.gridstorage.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.inventory.ItemStack;

import com.gridstorage.GridStorage;
import com.gridstorage.model.PlayerStorage;
import com.gridstorage.model.StorageSlot;
import com.gridstorage.nbt.NbtItemSerializer;

/**
 * SQLite persistence only — no in-memory cache (owned by {@link com.gridstorage.manager.StorageManager}).
 */
public class DatabaseManager {

    private final GridStorage plugin;
    private final File databaseFile;
    private final String connectionUrl;

    public DatabaseManager(GridStorage plugin) {
        this.plugin = plugin;
        plugin.getDataFolder().mkdirs();
        this.databaseFile = new File(plugin.getDataFolder(), "gridstorage.db");
        this.connectionUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        initializeDatabase();
    }

    private Connection getConnection() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return DriverManager.getConnection(connectionUrl);
    }

    private void initializeDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            plugin.getPluginLogger().info("正在连接SQLite数据库: " + databaseFile.getAbsolutePath());
            try (Connection conn = getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA synchronous=NORMAL");
                    stmt.execute("PRAGMA cache_size=2000");
                    stmt.execute("PRAGMA foreign_keys=ON");
                    stmt.execute("PRAGMA page_size=4096");
                }
                createTables(conn);
            }
            plugin.getPluginLogger().info("SQLite数据库初始化成功");
        } catch (ClassNotFoundException e) {
            plugin.getPluginLogger().error("SQLite驱动未找到！请确保依赖正确配置。", e);
        } catch (SQLException e) {
            plugin.getPluginLogger().error("SQLite数据库初始化失败: " + e.getMessage(), e);
        }
    }

    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_storage (
                    uuid TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    max_slots INTEGER NOT NULL DEFAULT 100,
                    current_page INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS storage_slots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    slot_id INTEGER NOT NULL,
                    nbt_data TEXT NOT NULL,
                    last_access_time INTEGER NOT NULL,
                    UNIQUE(player_uuid, slot_id),
                    FOREIGN KEY (player_uuid) REFERENCES player_storage(uuid) ON DELETE CASCADE
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_slots_player_uuid ON storage_slots(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_slots_slot_id ON storage_slots(slot_id)");
            plugin.getPluginLogger().info("数据库表结构创建成功");
        }
    }

    /**
     * Loads from DB or creates a new storage. Caller owns caching.
     */
    public PlayerStorage loadPlayerStorage(UUID uuid) {
        PlayerStorage storage = loadFromDatabase(uuid);
        if (storage != null) {
            return storage;
        }
        String playerName = plugin.getServer().getOfflinePlayer(uuid).getName();
        if (playerName == null || playerName.isEmpty()) {
            playerName = "Unknown";
        }
        int maxSlots = Math.max(1, plugin.getConfigManager().getMaxStorageCount());
        storage = new PlayerStorage(uuid, playerName, maxSlots);
        savePlayerStorage(storage);
        return storage;
    }

    private PlayerStorage loadFromDatabase(UUID uuid) {
        String query = "SELECT player_name, max_slots, current_page FROM player_storage WHERE uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String playerName = rs.getString("player_name");
                int maxSlots = rs.getInt("max_slots");
                int currentPage = rs.getInt("current_page");
                PlayerStorage storage = new PlayerStorage(uuid, playerName, maxSlots);
                storage.setCurrentPage(currentPage);
                loadSlotsFromDatabase(storage);
                plugin.getPluginLogger().info("从数据库加载玩家数据: " + playerName + " (" + uuid + ")");
                return storage;
            }
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("加载玩家存储失败: " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    private void loadSlotsFromDatabase(PlayerStorage storage) {
        String query = "SELECT slot_id, nbt_data FROM storage_slots WHERE player_uuid = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, storage.getPlayerUUID().toString());
            ResultSet rs = stmt.executeQuery();
            int loadedSlotCount = 0;
            int totalItems = 0;
            while (rs.next()) {
                int slotId = rs.getInt("slot_id");
                String nbtData = rs.getString("nbt_data");
                StorageSlot slot = storage.getSlot(slotId);
                if (slot == null) {
                    continue;
                }
                ItemStack[] items = NbtItemSerializer.deserializeItems(nbtData, 54);
                System.arraycopy(items, 0, slot.getContents(), 0, Math.min(items.length, 54));
                slot.updateAccessTime();
                loadedSlotCount++;
                for (ItemStack item : items) {
                    if (item != null) {
                        totalItems++;
                    }
                }
            }
            if (loadedSlotCount > 0) {
                plugin.getPluginLogger().info("加载槽位数据: " + loadedSlotCount + " 个槽位, " + totalItems + " 个物品");
            }
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("加载槽位数据失败: " + e.getMessage());
        }
    }

    public void savePlayerStorage(PlayerStorage storage) {
        if (storage == null) {
            return;
        }
        long currentTime = System.currentTimeMillis();
        String upsertPlayerQuery = """
            INSERT INTO player_storage (uuid, player_name, max_slots, current_page, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                player_name = excluded.player_name,
                max_slots = excluded.max_slots,
                current_page = excluded.current_page,
                updated_at = excluded.updated_at
            """;
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertPlayerQuery)) {
            stmt.setString(1, storage.getPlayerUUID().toString());
            stmt.setString(2, storage.getPlayerName());
            stmt.setInt(3, storage.getMaxSlots());
            stmt.setInt(4, storage.getCurrentPage());
            stmt.setLong(5, currentTime);
            stmt.setLong(6, currentTime);
            stmt.executeUpdate();
            saveSlotsToDatabase(storage, conn);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存玩家存储失败: " + storage.getPlayerUUID(), e);
        }
    }

    private void saveSlotsToDatabase(PlayerStorage storage, Connection conn) throws SQLException {
        String upsertSlotQuery = """
            INSERT INTO storage_slots (player_uuid, slot_id, nbt_data, last_access_time)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(player_uuid, slot_id) DO UPDATE SET
                nbt_data = excluded.nbt_data,
                last_access_time = excluded.last_access_time
            """;
        String deleteSlotQuery = "DELETE FROM storage_slots WHERE player_uuid = ? AND slot_id = ?";
        boolean autoCommit = true;
        try {
            autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try (PreparedStatement upsertStmt = conn.prepareStatement(upsertSlotQuery);
                 PreparedStatement deleteStmt = conn.prepareStatement(deleteSlotQuery)) {
                int savedSlotCount = 0;
                int totalItems = 0;
                for (StorageSlot slot : storage.getSlots()) {
                    if (slot == null) {
                        continue;
                    }
                    String nbtData = NbtItemSerializer.serializeItems(slot.getContents());
                    if (nbtData != null) {
                        upsertStmt.setString(1, storage.getPlayerUUID().toString());
                        upsertStmt.setInt(2, slot.getSlotId());
                        upsertStmt.setString(3, nbtData);
                        upsertStmt.setLong(4, slot.getLastAccessTime());
                        upsertStmt.addBatch();
                        savedSlotCount++;
                        for (ItemStack item : slot.getContents()) {
                            if (item != null) {
                                totalItems++;
                            }
                        }
                    } else {
                        deleteStmt.setString(1, storage.getPlayerUUID().toString());
                        deleteStmt.setInt(2, slot.getSlotId());
                        deleteStmt.addBatch();
                    }
                }
                upsertStmt.executeBatch();
                deleteStmt.executeBatch();
                conn.commit();
                plugin.getPluginLogger().debug("保存槽位数据: " + savedSlotCount + " 个槽位, " + totalItems + " 个物品");
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getPluginLogger().warning("回滚事务失败: " + rollbackEx.getMessage());
                }
                throw e;
            }
        } finally {
            try {
                conn.setAutoCommit(autoCommit);
            } catch (SQLException e) {
                plugin.getPluginLogger().warning("恢复 auto-commit 模式失败: " + e.getMessage());
            }
        }
    }

    public void shutdown() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            plugin.getPluginLogger().info("WAL检查点已完成");
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("执行WAL检查点时出错: " + e.getMessage());
        }
    }
}
