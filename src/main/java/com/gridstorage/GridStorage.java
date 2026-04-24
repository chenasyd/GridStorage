package com.gridstorage;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.bukkit.plugin.java.JavaPlugin;

import com.gridstorage.command.GridStorageCommand;
import com.gridstorage.config.ConfigManager;
import com.gridstorage.listener.GUIListener;
import com.gridstorage.manager.GUIManager;
import com.gridstorage.manager.StorageManager;
import com.gridstorage.scheduler.FoliaScheduler;
import com.gridstorage.logging.PluginLogger;

import de.tr7zw.nbtapi.NBT;

public class GridStorage extends JavaPlugin {

    private static GridStorage instance;
    private ConfigManager configManager;
    private StorageManager storageManager;
    private GUIManager guiManager;
    private FoliaScheduler scheduler;
    private PluginLogger pluginLogger;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.pluginLogger = new PluginLogger(this);
        pluginLogger.info("初始化日志系统，日志级别: " + pluginLogger.getLevel().name());

        this.scheduler = new FoliaScheduler(this);
        pluginLogger.info("运行模式: " + (scheduler.isFolia() ? "Folia (区域化多线程)" : "标准Bukkit"));

        this.configManager = new ConfigManager(this);

        if (!NBT.preloadApi()) {
            pluginLogger.error("NBT API初始化失败！");
            getPluginLoader().disablePlugin(this);
            return;
        }
        pluginLogger.debug("NBT API 初始化成功");

        this.storageManager = new StorageManager(this);
        this.guiManager = new GUIManager(this);
        pluginLogger.debug("管理器初始化完成");

        getCommand("gridstorage").setExecutor(new GridStorageCommand(this));
        getCommand("gridstorageadmin").setExecutor(new GridStorageCommand(this));
        pluginLogger.debug("命令注册完成");

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);
        pluginLogger.debug("监听器注册完成");

        startAutoSaveTask();

        pluginLogger.info("GridStorage 插件已启用 v" + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        pluginLogger.info("开始保存数据...");

        if (storageManager != null) {
            storageManager.forceCloseAndSaveAll();
        }

        CountDownLatch saveLatch = new CountDownLatch(1);
        if (scheduler != null && isEnabled()) {
            scheduler.runAsync(() -> {
                try {
                    if (storageManager != null) {
                        storageManager.getDatabaseManager().saveAll();
                        storageManager.getDatabaseManager().shutdown();
                    }
                } finally {
                    saveLatch.countDown();
                }
            });
        } else {
            if (storageManager != null) {
                storageManager.getDatabaseManager().saveAll();
                storageManager.getDatabaseManager().shutdown();
            }
            saveLatch.countDown();
        }

        try {
            boolean completed = saveLatch.await(10, TimeUnit.SECONDS);
            if (!completed) {
                pluginLogger.warning("异步保存超时（10秒），部分数据可能未保存");
            }
        } catch (InterruptedException e) {
            pluginLogger.error("等待异步保存时被中断", e);
            Thread.currentThread().interrupt();
        }

        if (scheduler != null) {
            scheduler.cancelAllTasks();
        }

        pluginLogger.info("GridStorage 插件已禁用");

        if (pluginLogger != null) {
            pluginLogger.close();
        }
    }

    public static GridStorage getInstance() {
        return instance;
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
        long interval = 300;
        scheduler.runDelayedGlobal(() -> {
            scheduler.runAsync(() -> {
                if (storageManager != null) {
                    storageManager.saveAll();
                    pluginLogger.info("定期自动保存完成");
                }
            });

            startAutoSaveTask();
        }, interval, java.util.concurrent.TimeUnit.SECONDS);

        pluginLogger.info("已启动定期自动保存任务（每5分钟）");
    }
}
