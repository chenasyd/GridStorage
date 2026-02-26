package com.gridstorage.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.gridstorage.GridStorage;
import com.gridstorage.model.PlayerStorage;
import com.gridstorage.model.StorageSlot;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT; // 新增

/**
 * 数据库管理器
 * 支持SQLite数据库存储
 * 使用NBTAPI进行物品数据序列化，支持潜影盒等特殊容器
 */
public class DatabaseManager {

    private final GridStorage plugin;
    private final Map<UUID, PlayerStorage> loadedStorages;
    private final File databaseFile;
    private final String connectionUrl;

    public DatabaseManager(GridStorage plugin) {
        this.plugin = plugin;
        this.loadedStorages = new ConcurrentHashMap<>();
        // make sure the plugin folder exists (shutdown 期间有时会被删除)
        plugin.getDataFolder().mkdirs();
        this.databaseFile = new File(plugin.getDataFolder(), "gridstorage.db");
        this.connectionUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

        initializeDatabase();
    }

    /**
     * 获取数据库连接，确保目录存在
     */
    private Connection getConnection() throws SQLException {
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        return DriverManager.getConnection(connectionUrl);
    }

    /**
     * 初始化数据库连接和表结构
     */
    private void initializeDatabase() {
        try {
            // 加载SQLite驱动
            Class.forName("org.sqlite.JDBC");

            // 创建数据库连接（仅用于初始化）
            plugin.getLogger().info("正在连接SQLite数据库: " + databaseFile.getAbsolutePath());
            try (Connection conn = getConnection()) {
                // 启用WAL模式和优化设置
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA synchronous=NORMAL");
                    stmt.execute("PRAGMA cache_size=2000");
                    stmt.execute("PRAGMA foreign_keys=ON");
                    stmt.execute("PRAGMA page_size=4096");
                }

                // 创建表结构
                createTables(conn);
            }

            plugin.getLogger().info("SQLite数据库初始化成功");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("SQLite驱动未找到！请确保依赖正确配置。");
            e.printStackTrace();
        } catch (SQLException e) {
            plugin.getLogger().severe("SQLite数据库初始化失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建数据库表结构
     */
    private void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 创建玩家存储表
            String createPlayerStorageTable = """
                CREATE TABLE IF NOT EXISTS player_storage (
                    uuid TEXT PRIMARY KEY,
                    player_name TEXT NOT NULL,
                    max_slots INTEGER NOT NULL DEFAULT 100,
                    current_page INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """;
            stmt.execute(createPlayerStorageTable);
            
            // 创建槽位表
            String createSlotTable = """
                CREATE TABLE IF NOT EXISTS storage_slots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    slot_id INTEGER NOT NULL,
                    nbt_data TEXT NOT NULL,
                    last_access_time INTEGER NOT NULL,
                    UNIQUE(player_uuid, slot_id),
                    FOREIGN KEY (player_uuid) REFERENCES player_storage(uuid) ON DELETE CASCADE
                )
                """;
            stmt.execute(createSlotTable);
            
            // 创建索引
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_slots_player_uuid ON storage_slots(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_slots_slot_id ON storage_slots(slot_id)");
            
            plugin.getLogger().info("数据库表结构创建成功");
        }
    }

    public PlayerStorage loadPlayerStorage(UUID uuid) {
        // 先检查缓存
        if (loadedStorages.containsKey(uuid)) {
            return loadedStorages.get(uuid);
        }

        // 从数据库加载
        PlayerStorage storage = loadFromDatabase(uuid);
        if (storage == null) {
            // 创建新的存储
            String playerName = plugin.getServer().getOfflinePlayer(uuid).getName();
            if (playerName == null || playerName.isEmpty()) {
                playerName = "Unknown";
            }
            storage = new PlayerStorage(uuid, playerName, 100);
            // 保存到数据库
            savePlayerStorageToDatabase(storage);
        }
        
        loadedStorages.put(uuid, storage);
        return storage;
    }

    /**
     * 从数据库加载玩家存储
     */
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
                
                // 加载槽位数据
                loadSlotsFromDatabase(storage);
                
                plugin.getLogger().info("从数据库加载玩家数据: " + playerName + " (" + uuid + ")");
                return storage;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "加载玩家存储失败: " + uuid, e);
        }
        
        return null;
    }

    /**
     * 从数据库加载槽位数据
     */
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

                plugin.getLogger().info("从数据库加载槽位 #" + slotId + "，NBT数据长度: " + (nbtData != null ? nbtData.length() : 0));

                StorageSlot slot = storage.getSlot(slotId);
                if (slot != null) {
                    ItemStack[] items = deserializeItemsFromNBT(nbtData);
                    System.arraycopy(items, 0, slot.getContents(), 0, Math.min(items.length, 54));

                    // 更新最后访问时间
                    slot.updateAccessTime();

                    loadedSlotCount++;
                    for (ItemStack item : items) {
                        if (item != null) {
                            totalItems++;
                        }
                    }
                }
            }

            if (loadedSlotCount > 0) {
                plugin.getLogger().info("加载槽位数据: " + loadedSlotCount + " 个槽位, " + totalItems + " 个物品");
            } else {
                plugin.getLogger().info("未加载到任何槽位数据");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "加载槽位数据失败", e);
        }
    }

    public void savePlayerStorage(UUID uuid) {
        PlayerStorage storage = loadedStorages.get(uuid);
        if (storage == null) {
            return;
        }
        
        savePlayerStorageToDatabase(storage);
    }

    /**
     * 保存玩家存储到数据库
     */
    private void savePlayerStorageToDatabase(PlayerStorage storage) {
        long currentTime = System.currentTimeMillis();

        // 保存或更新玩家存储基本信息
        String upsertPlayerQuery = """
            INSERT INTO player_storage (uuid, player_name, max_slots, current_page, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                player_name = excluded.player_name,
                max_slots = excluded.max_slots,
                current_page = excluded.current_page,
                updated_at = excluded.updated_at
            """;

        // 使用独立连接进行事务操作
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertPlayerQuery)) {

            stmt.setString(1, storage.getPlayerUUID().toString());
            stmt.setString(2, storage.getPlayerName());
            stmt.setInt(3, storage.getMaxSlots());
            stmt.setInt(4, storage.getCurrentPage());
            stmt.setLong(5, currentTime);
            stmt.setLong(6, currentTime);
            stmt.executeUpdate();

            // 保存槽位数据
            saveSlotsToDatabase(storage, conn);

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "保存玩家存储失败: " + storage.getPlayerUUID(), e);
        }
    }

    /**
     * 保存槽位数据到数据库
     */
    private void saveSlotsToDatabase(PlayerStorage storage, Connection conn) throws SQLException {
        String upsertSlotQuery = """
            INSERT INTO storage_slots (player_uuid, slot_id, nbt_data, last_access_time)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(player_uuid, slot_id) DO UPDATE SET
                nbt_data = excluded.nbt_data,
                last_access_time = excluded.last_access_time
            """;

        boolean autoCommit = true;
        try {
            // 关闭 auto-commit 模式以启用事务
            autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (PreparedStatement upsertStmt = conn.prepareStatement(upsertSlotQuery)) {

                int savedSlotCount = 0;
                int totalItems = 0;

                for (StorageSlot slot : storage.getSlots()) {
                    if (slot != null) {
                        String nbtData = serializeItemsToNBT(slot.getContents());
                        if (nbtData != null) {
                            // 只保存有物品的槽位
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
                        }
                        // 注意：不再删除空槽位的数据，避免用空数据覆盖已有数据
                    }
                }

                if (savedSlotCount > 0) {
                    upsertStmt.executeBatch();
                    conn.commit();
                } else {
                    conn.rollback(); // 没有需要保存的数据，回滚
                }

                plugin.getLogger().info("保存槽位数据: " + savedSlotCount + " 个槽位, " + totalItems + " 个物品");
            } catch (SQLException e) {
                // 回滚事务
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    plugin.getLogger().log(Level.WARNING, "回滚事务失败", rollbackEx);
                }
                throw e;
            }
        } finally {
            // 恢复 auto-commit 模式
            try {
                conn.setAutoCommit(autoCommit);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "恢复 auto-commit 模式失败", e);
            }
        }
    }

    private String serializeItemsToNBT(ItemStack[] items) {
        // 检查是否有任何非空气物品
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

        de.tr7zw.nbtapi.NBTContainer compound = new de.tr7zw.nbtapi.NBTContainer();
        int itemCount = 0;

        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item != null && item.getType() != Material.AIR) {
                try {
                    // 改为 ReadWriteNBT 类型
                    ReadWriteNBT itemNbt = NBT.itemStackToNBT(item);
                    compound.getOrCreateCompound("slot_" + i).mergeCompound(itemNbt);

                    itemCount++;
                    plugin.getLogger().info("序列化槽位 " + i + " 的物品: " + item.getType()
                            + ", NBT: " + itemNbt.toString());
                } catch (Exception e) {
                    plugin.getLogger().warning("序列化槽位 " + i + " 失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        compound.setString("size", String.valueOf(items.length));
        String result = compound.toString();
        plugin.getLogger().info("序列化完成，总物品数: " + itemCount + ", NBT结果: " + result);
        return result;
    }

    private ItemStack[] deserializeItemsFromNBT(String nbtData) {
        ItemStack[] items = new ItemStack[54];
        int itemCount = 0;

        if (nbtData == null || nbtData.isEmpty() || nbtData.equals("{}")) {
            plugin.getLogger().fine("NBT数据为空，返回空数组");
            return items;
        }

        try {
            plugin.getLogger().info("开始反序列化NBT数据: " + nbtData);
            de.tr7zw.nbtapi.NBTContainer compound = new de.tr7zw.nbtapi.NBTContainer(nbtData);

            plugin.getLogger().info("NBTContainer创建成功，size=" + compound.getString("size"));

            for (int i = 0; i < 54; i++) {
                String slotKey = "slot_" + i;
                if (compound.hasTag(slotKey)) {
                    // 同样使用 ReadWriteNBT
                    ReadWriteNBT itemCompound = compound.getCompound(slotKey);
                    if (itemCompound != null) {
                        try {
                            ItemStack stack = NBT.itemStackFromNBT(itemCompound);
                            if (stack != null && stack.getType() != Material.AIR) {
                                items[i] = stack;
                                itemCount++;
                                plugin.getLogger().info("成功恢复槽位 " + i + " 的物品: " + stack.getType());
                            }
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING,
                                    "解析槽位 " + i + " 的物品数据失败，跳过: " + e.getMessage());
                        }
                    }
                }
            }

            plugin.getLogger().info("NBT解析完成，共 " + itemCount + " 个物品");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "解析物品NBT数据失败: " + e.getMessage(), e);
        }

        return items;
    }

    public void saveAll() {
        int count = 0;
        for (UUID uuid : loadedStorages.keySet()) {
            savePlayerStorage(uuid);
            count++;
        }
        plugin.getLogger().info("已保存 " + count + " 个玩家的数据");
    }

    public void unloadPlayerStorage(UUID uuid) {
        savePlayerStorage(uuid);
        loadedStorages.remove(uuid);
    }

    public void shutdown() {
        saveAll();

        // 执行WAL检查点以将WAL文件合并到主数据库
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            plugin.getLogger().info("WAL检查点已完成");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "执行WAL检查点时出错", e);
        }

        loadedStorages.clear();
    }
}
