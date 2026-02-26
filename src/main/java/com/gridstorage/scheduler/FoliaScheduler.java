package com.gridstorage.scheduler;

import com.gridstorage.GridStorage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.concurrent.TimeUnit;

/**
 * Folia调度器适配器
 * 为Folia和标准服务器提供统一的调度接口
 * 使用反射避免硬依赖Folia API
 */
public class FoliaScheduler {

    private final GridStorage plugin;
    private boolean isFolia;
    private final BukkitScheduler scheduler;
    private Object asyncScheduler;
    private Object globalRegionScheduler;

    public FoliaScheduler(GridStorage plugin) {
        this.plugin = plugin;
        this.scheduler = Bukkit.getScheduler();

        // 检测是否在Folia环境运行
        isFolia = Bukkit.getVersion().contains("Folia");

        if (isFolia) {
            try {
                // 获取AsyncScheduler
                asyncScheduler = plugin.getServer().getClass()
                    .getMethod("getAsyncScheduler")
                    .invoke(plugin.getServer());
                // 获取GlobalRegionScheduler
                globalRegionScheduler = plugin.getServer().getClass()
                    .getMethod("getGlobalRegionScheduler")
                    .invoke(plugin.getServer());
            } catch (Exception e) {
                plugin.getLogger().warning("Folia API检测失败，降级为标准模式");
                isFolia = false;
            }
        }
    }

    /**
     * 判断是否运行在Folia服务器
     */
    public boolean isFolia() {
        return isFolia;
    }

    /**
     * 异步执行任务
     */
    public void runAsync(Runnable task) {
        if (isFolia && asyncScheduler != null) {
            try {
                asyncScheduler.getClass()
                    .getMethod("runNow", Plugin.class, Runnable.class)
                    .invoke(asyncScheduler, plugin, task);
            } catch (Exception e) {
                scheduler.runTaskAsynchronously(plugin, task);
            }
        } else {
            scheduler.runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * 主线程执行任务（在Folia中是全局Region）
     */
    public void runGlobal(Runnable task) {
        if (isFolia && globalRegionScheduler != null) {
            try {
                globalRegionScheduler.getClass()
                    .getMethod("execute", Plugin.class, Runnable.class)
                    .invoke(globalRegionScheduler, plugin, task);
            } catch (Exception e) {
                scheduler.runTask(plugin, task);
            }
        } else {
            scheduler.runTask(plugin, task);
        }
    }

    /**
     * 延迟执行异步任务
     */
    public void runDelayedAsync(Runnable task, long delay, TimeUnit unit) {
        if (isFolia && asyncScheduler != null) {
            try {
                asyncScheduler.getClass()
                    .getMethod("runDelayed", Plugin.class, Runnable.class, long.class, TimeUnit.class)
                    .invoke(asyncScheduler, plugin, task, delay, unit);
            } catch (Exception e) {
                scheduler.runTaskLaterAsynchronously(plugin, task, unit.toMillis(delay) / 50);
            }
        } else {
            scheduler.runTaskLaterAsynchronously(plugin, task, unit.toMillis(delay) / 50);
        }
    }

    /**
     * 延迟执行主线程任务
     */
    public void runDelayedGlobal(Runnable task, long delay, TimeUnit unit) {
        if (isFolia && globalRegionScheduler != null) {
            try {
                globalRegionScheduler.getClass()
                    .getMethod("runDelayed", Plugin.class, Runnable.class, long.class, TimeUnit.class)
                    .invoke(globalRegionScheduler, plugin, task, delay, unit);
            } catch (Exception e) {
                scheduler.runTaskLater(plugin, task, unit.toMillis(delay) / 50);
            }
        } else {
            scheduler.runTaskLater(plugin, task, unit.toMillis(delay) / 50);
        }
    }

    /**
     * 取消所有任务
     */
    public void cancelAllTasks() {
        if (!isFolia) {
            scheduler.cancelTasks(plugin);
        }
        // Folia会自动管理任务生命周期，无需手动取消
    }
}
