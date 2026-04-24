package com.gridstorage.scheduler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

import com.gridstorage.GridStorage;

public class FoliaScheduler {

    private final GridStorage plugin;
    private boolean isFolia;
    private final BukkitScheduler scheduler;
    private Object asyncScheduler;
    private Object globalRegionScheduler;
    private Object regionScheduler;
    private final List<Object> pendingTasks = new ArrayList<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final ConcurrentHashMap<String, Method> methodCache = new ConcurrentHashMap<>();

    public FoliaScheduler(GridStorage plugin) {
        this.plugin = plugin;
        this.scheduler = Bukkit.getScheduler();

        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (ClassNotFoundException e) {
            isFolia = false;
        }

        if (isFolia) {
            try {
                asyncScheduler = plugin.getServer().getClass()
                    .getMethod("getAsyncScheduler")
                    .invoke(plugin.getServer());
                globalRegionScheduler = plugin.getServer().getClass()
                    .getMethod("getGlobalRegionScheduler")
                    .invoke(plugin.getServer());
                regionScheduler = plugin.getServer().getClass()
                    .getMethod("getRegionScheduler")
                    .invoke(plugin.getServer());

                plugin.getPluginLogger().info("Folia API 检测成功，区域化调度器已就绪");
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Folia API反射获取失败，降级为标准模式: " + e.getMessage());
                isFolia = false;
            }
        }
    }

    public boolean isFolia() {
        return isFolia;
    }

    private Method findMethod(Object obj, String name, int paramCount) {
        String cacheKey = obj.getClass().getName() + "#" + name + "#" + paramCount;
        Method cached = methodCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        for (Class<?> iface : obj.getClass().getInterfaces()) {
            for (Method method : iface.getMethods()) {
                if (!method.isSynthetic() && !method.isBridge()
                        && method.getName().equals(name)
                        && method.getParameterCount() == paramCount) {
                    method.setAccessible(true);
                    methodCache.put(cacheKey, method);
                    return method;
                }
            }
        }

        for (Method method : obj.getClass().getMethods()) {
            if (!method.isSynthetic() && !method.isBridge()
                    && Modifier.isPublic(method.getModifiers())
                    && method.getName().equals(name)
                    && method.getParameterCount() == paramCount) {
                method.setAccessible(true);
                methodCache.put(cacheKey, method);
                return method;
            }
        }

        return null;
    }

    public void runAsync(Runnable task) {
        if (shuttingDown.get()) {
            task.run();
            return;
        }

        if (!plugin.isEnabled()) {
            task.run();
            return;
        }

        if (isFolia && asyncScheduler != null) {
            try {
                Method runNow = findMethod(asyncScheduler, "runNow", 2);
                Object scheduledTask = runNow.invoke(asyncScheduler, plugin, task);
                synchronized (pendingTasks) {
                    pendingTasks.add(scheduledTask);
                }
            } catch (Exception e) {
                plugin.getPluginLogger().debug("异步调度降级: " + e.getMessage());
                scheduler.runTaskAsynchronously(plugin, task);
            }
        } else {
            scheduler.runTaskAsynchronously(plugin, task);
        }
    }

    public void runGlobal(Runnable task) {
        if (isFolia && globalRegionScheduler != null) {
            try {
                Method execute = findMethod(globalRegionScheduler, "execute", 2);
                execute.invoke(globalRegionScheduler, plugin, task);
            } catch (Exception e) {
                plugin.getPluginLogger().debug("全局调度降级: " + e.getMessage());
                scheduler.runTask(plugin, task);
            }
        } else {
            scheduler.runTask(plugin, task);
        }
    }

    public void runAtEntity(Entity entity, Runnable task) {
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }

        if (isFolia) {
            try {
                Object entityScheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
                Method execute = findMethod(entityScheduler, "execute", 3);
                Object scheduledTask = execute.invoke(entityScheduler, plugin, task, null);
                if (scheduledTask != null) {
                    synchronized (pendingTasks) {
                        pendingTasks.add(scheduledTask);
                    }
                }
            } catch (Exception e) {
                plugin.getPluginLogger().debug("Folia实体调度降级: " + e.getMessage());
                runGlobal(task);
            }
        } else {
            scheduler.runTask(plugin, task);
        }
    }

    public void runDelayedAtEntity(Entity entity, Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }

        if (isFolia) {
            try {
                Object entityScheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
                Method runDelayed = findMethod(entityScheduler, "runDelayed", 4);
                Object scheduledTask = runDelayed.invoke(entityScheduler, plugin, task, null, delayTicks);
                if (scheduledTask != null) {
                    synchronized (pendingTasks) {
                        pendingTasks.add(scheduledTask);
                    }
                }
            } catch (Exception e) {
                plugin.getPluginLogger().debug("Folia实体延迟调度降级: " + e.getMessage());
                runDelayedGlobalTicks(task, delayTicks);
            }
        } else {
            scheduler.runTaskLater(plugin, task, delayTicks);
        }
    }

    public void runAtLocation(Location location, Runnable task) {
        if (!plugin.isEnabled()) {
            task.run();
            return;
        }

        if (isFolia && regionScheduler != null) {
            try {
                Method execute = findMethod(regionScheduler, "execute", 3);
                execute.invoke(regionScheduler, plugin, location, task);
            } catch (Exception e) {
                plugin.getPluginLogger().debug("Folia位置调度降级: " + e.getMessage());
                scheduler.runTask(plugin, task);
            }
        } else {
            scheduler.runTask(plugin, task);
        }
    }

    public void runDelayedAsync(Runnable task, long delay, TimeUnit unit) {
        if (shuttingDown.get()) {
            task.run();
            return;
        }

        if (!plugin.isEnabled()) {
            task.run();
            return;
        }

        if (isFolia && asyncScheduler != null) {
            try {
                Method runDelayed = findMethod(asyncScheduler, "runDelayed", 4);
                Object scheduledTask = runDelayed.invoke(asyncScheduler, plugin, task, delay, unit);
                synchronized (pendingTasks) {
                    pendingTasks.add(scheduledTask);
                }
            } catch (Exception e) {
                plugin.getPluginLogger().debug("异步延迟调度降级: " + e.getMessage());
                scheduler.runTaskLaterAsynchronously(plugin, task, unit.toMillis(delay) / 50);
            }
        } else {
            scheduler.runTaskLaterAsynchronously(plugin, task, unit.toMillis(delay) / 50);
        }
    }

    public void runDelayedGlobal(Runnable task, long delay, TimeUnit unit) {
        long delayTicks = unit.toMillis(delay) / 50;
        runDelayedGlobalTicks(task, delayTicks);
    }

    private void runDelayedGlobalTicks(Runnable task, long delayTicks) {
        if (isFolia && globalRegionScheduler != null) {
            try {
                Method runDelayed = findMethod(globalRegionScheduler, "runDelayed", 3);
                Object scheduledTask = runDelayed.invoke(globalRegionScheduler, plugin, task, delayTicks);
                synchronized (pendingTasks) {
                    pendingTasks.add(scheduledTask);
                }
            } catch (Exception e) {
                plugin.getPluginLogger().debug("全局延迟调度降级: " + e.getMessage());
                scheduler.runTaskLater(plugin, task, delayTicks);
            }
        } else {
            scheduler.runTaskLater(plugin, task, delayTicks);
        }
    }

    public void cancelAllTasks() {
        shuttingDown.set(true);

        if (!isFolia) {
            scheduler.cancelTasks(plugin);
            return;
        }

        synchronized (pendingTasks) {
            for (Object task : pendingTasks) {
                try {
                    Method cancel = findMethod(task, "cancel", 0);
                    if (cancel != null) {
                        cancel.invoke(task);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            pendingTasks.clear();
        }

        if (isFolia) {
            try {
                Method cancelTasks = findMethod(asyncScheduler, "cancelTasks", 1);
                if (cancelTasks != null) {
                    cancelTasks.invoke(asyncScheduler, plugin);
                }
            } catch (Exception e) {
                // ignore
            }
            try {
                Method cancelTasks = findMethod(globalRegionScheduler, "cancelTasks", 1);
                if (cancelTasks != null) {
                    cancelTasks.invoke(globalRegionScheduler, plugin);
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
