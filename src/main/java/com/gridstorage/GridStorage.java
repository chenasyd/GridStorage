package com.gridstorage;

import org.bukkit.plugin.java.JavaPlugin;

import com.gridstorage.command.GridStorageCommand;
import com.gridstorage.config.ConfigManager;
import com.gridstorage.listener.GUIListener;
import com.gridstorage.manager.GUIManager;
import com.gridstorage.manager.StorageManager;
import com.gridstorage.scheduler.FoliaScheduler;

import de.tr7zw.nbtapi.NBT;

/**
 * GridStorage 主类
 * 网格存储系统插件
 * 支持 Folia 和标准 Bukkit 服务器
 */
public class GridStorage extends JavaPlugin {

    private static GridStorage instance;
    private ConfigManager configManager;
    private StorageManager storageManager;
    private GUIManager guiManager;
    private FoliaScheduler scheduler;

    @Override
    public void onEnable() {
        instance = this;

        // 初始化调度器
        this.scheduler = new FoliaScheduler(this);
        getLogger().info("运行模式: " + (scheduler.isFolia() ? "Folia (区域化多线程)" : "标准Bukkit"));

        // 初始化配置管理器
        this.configManager = new ConfigManager(this);

        // 初始化 NBT API
        if (!NBT.preloadApi()) {
            getLogger().severe("NBT API初始化失败！");
            getPluginLoader().disablePlugin(this);
            return;
        }

        // 初始化管理器
        this.storageManager = new StorageManager(this);
        this.guiManager = new GUIManager(this);

        // 注册命令
        getCommand("gridstorage").setExecutor(new GridStorageCommand(this));
        getCommand("gridstorageadmin").setExecutor(new GridStorageCommand(this));

        // 注册监听器
        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        // 启动定期自动保存任务（每5分钟保存一次）
        startAutoSaveTask();

        getLogger().info("GridStorage 插件已启用 v" + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        getLogger().info("开始保存数据...");

        // 强制关闭所有打开的GUI并同步保存数据
        if (storageManager != null) {
            storageManager.forceCloseAndSaveAll();
        }

        // 等待已经提交的异步任务完成（简单延迟，避免被取消）
        if (scheduler != null) {
            try {
                Thread.sleep(2000); // 等待2秒让异步保存任务有机会完成
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 关闭数据库管理器
        if (storageManager != null) {
            storageManager.getDatabaseManager().shutdown();
        }

        // 取消所有调度任务
        if (scheduler != null) {
            scheduler.cancelAllTasks();
        }

        getLogger().info("GridStorage 插件已禁用");
    }

    /**
     * 获取插件实例
     */
    public static GridStorage getInstance() {
        return instance;
    }

    /**
     * 获取配置管理器
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * 获取存储管理器
     */
    public StorageManager getStorageManager() {
        return storageManager;
    }

    /**
     * 获取 GUI 管理器
     */
    public GUIManager getGuiManager() {
        return guiManager;
    }

    /**
     * 获取调度器
     */
    public FoliaScheduler getScheduler() {
        return scheduler;
    }

    /**
     * 启动定期自动保存任务
     */
    private void startAutoSaveTask() {
        long interval = 300; // 5分钟 = 300 秒
        scheduler.runDelayedGlobal(() -> {
            // 使用异步保存以不影响服务器性能
            scheduler.runAsync(() -> {
                if (storageManager != null) {
                    storageManager.saveAll();
                    getLogger().info("定期自动保存完成");
                }
            });

            // 递归调用以持续执行
            startAutoSaveTask();
        }, interval, java.util.concurrent.TimeUnit.SECONDS);

        getLogger().info("已启动定期自动保存任务（每5分钟）");
    }
}
