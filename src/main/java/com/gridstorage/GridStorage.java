package com.gridstorage;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.gridstorage.command.GridStorageCommand;
import com.gridstorage.config.ConfigManager;
import com.gridstorage.listener.GUIListener;
import com.gridstorage.logging.PluginLogger;
import com.gridstorage.manager.GUIManager;
import com.gridstorage.manager.StorageManager;
import com.gridstorage.scheduler.FoliaScheduler;
import com.gridstorage.scheduler.FoliaScheduler.CancelHandle;

public class GridStorage extends JavaPlugin {

    private static GridStorage instance;
    private ConfigManager configManager;
    private StorageManager storageManager;
    private GUIManager guiManager;
    private FoliaScheduler scheduler;
    private PluginLogger pluginLogger;
    private boolean nbtApiAvailable;
    private CancelHandle autoSaveHandle;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.pluginLogger = new PluginLogger(this);
        pluginLogger.info("初始化日志系统，日志级别: " + pluginLogger.getLevel().name());

        this.scheduler = new FoliaScheduler(this);
        pluginLogger.info("运行模式: " + (scheduler.isFolia() ? "Folia (区域化多线程)" : "标准Bukkit"));

        this.configManager = new ConfigManager(this);

        this.nbtApiAvailable = detectNbtApi();
        if (!nbtApiAvailable) {
            pluginLogger.error("未检测到可用的 NBTAPI，个人仓库功能已禁用。请安装 NBTAPI 后重启。");
        } else {
            pluginLogger.info("NBTAPI 可用");
        }

        this.storageManager = new StorageManager(this);
        this.guiManager = new GUIManager(this);
        pluginLogger.debug("管理器初始化完成");

        getCommand("gridstorage").setExecutor(new GridStorageCommand(this));
        getCommand("gridstorageadmin").setExecutor(new GridStorageCommand(this));
        pluginLogger.debug("命令注册完成");

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        pluginLogger.debug("监听器注册完成");

        if (nbtApiAvailable) {
            startAutoSaveTask();
        }

        pluginLogger.info("GridStorage 插件已启用 v" + getDescription().getVersion()
                + (nbtApiAvailable ? "" : "（仓库功能关闭：缺少 NBTAPI）"));
    }

    private boolean detectNbtApi() {
        Plugin nbtPlugin = getServer().getPluginManager().getPlugin("NBTAPI");
        if (nbtPlugin == null || !nbtPlugin.isEnabled()) {
            return false;
        }
        try {
            Class<?> nbtClass = Class.forName("de.tr7zw.nbtapi.NBT");
            Object ok = nbtClass.getMethod("preloadApi").invoke(null);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            pluginLogger.warning("NBTAPI 检测失败: " + t.getMessage());
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (pluginLogger != null) {
            pluginLogger.info("开始保存数据...");
        }

        if (autoSaveHandle != null) {
            try {
                autoSaveHandle.cancel();
            } catch (Exception ignored) {
            }
            autoSaveHandle = null;
        }

        if (storageManager != null && nbtApiAvailable) {
            // Sync snapshot + single saveAll inside forceCloseAndSaveAll, then WAL checkpoint
            storageManager.shutdown();
        }

        if (scheduler != null) {
            scheduler.cancelAllTasks();
        }

        if (pluginLogger != null) {
            pluginLogger.info("GridStorage 插件已禁用");
            pluginLogger.close();
        }
    }

    public static GridStorage getInstance() {
        return instance;
    }

    public boolean isNbtApiAvailable() {
        return nbtApiAvailable;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    public FoliaScheduler getScheduler() {
        return scheduler;
    }

    public PluginLogger getPluginLogger() {
        return pluginLogger;
    }

    private void startAutoSaveTask() {
        // 5 minutes = 6000 ticks
        long periodTicks = 20L * 60L * 5L;
        autoSaveHandle = scheduler.runAtFixedRateGlobal(() -> {
            scheduler.runAsync(() -> {
                if (storageManager != null) {
                    storageManager.saveAll();
                    pluginLogger.info("定期自动保存完成");
                }
            });
        }, periodTicks, periodTicks);
        pluginLogger.info("已启动定期自动保存任务（每5分钟）");
    }
}
