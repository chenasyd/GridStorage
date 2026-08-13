package com.gridstorage.scheduler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import com.gridstorage.GridStorage;

/**
 * Spigot / Folia scheduler. Folia APIs receive {@link Consumer} wrappers (not raw {@link Runnable}).
 * On Folia, failed reflection does <b>not</b> fall back to BukkitScheduler.
 */
public class FoliaScheduler {

    private final GridStorage plugin;
    private final boolean folia;
    private Object asyncScheduler;
    private Object globalRegionScheduler;
    private Object regionScheduler;
    private final List<Object> pendingTasks = new ArrayList<>();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public FoliaScheduler(GridStorage plugin) {
        this.plugin = plugin;
        boolean detected = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            detected = true;
        } catch (ClassNotFoundException ignored) {
        }
        this.folia = detected;

        if (folia) {
            try {
                Object server = plugin.getServer();
                asyncScheduler = server.getClass().getMethod("getAsyncScheduler").invoke(server);
                globalRegionScheduler = server.getClass().getMethod("getGlobalRegionScheduler").invoke(server);
                regionScheduler = server.getClass().getMethod("getRegionScheduler").invoke(server);
                plugin.getPluginLogger().info("Folia API ready (region schedulers)");
            } catch (Exception e) {
                plugin.getPluginLogger().error("Folia schedulers unavailable: " + e.getMessage(), e);
                asyncScheduler = null;
                globalRegionScheduler = null;
                regionScheduler = null;
            }
        }
    }

    public boolean isFolia() {
        return folia;
    }

    public void runAsync(Runnable task) {
        if (shuttingDown.get() || !plugin.isEnabled()) {
            try {
                task.run();
            } catch (Throwable t) {
                plugin.getPluginLogger().warning("runAsync immediate failed: " + t.getMessage());
            }
            return;
        }
        if (folia) {
            if (asyncScheduler == null) {
                plugin.getPluginLogger().warning("runAsync skipped: Folia AsyncScheduler missing");
                return;
            }
            try {
                Method runNow = asyncScheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class);
                Object scheduled = runNow.invoke(asyncScheduler, plugin, (Consumer<Object>) t -> task.run());
                track(scheduled);
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Folia runAsync failed: " + e.getMessage());
            }
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    public void runGlobal(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (folia) {
            if (globalRegionScheduler == null) {
                plugin.getPluginLogger().warning("runGlobal skipped: Folia GlobalRegionScheduler missing");
                return;
            }
            try {
                Method run = globalRegionScheduler.getClass().getMethod("run", Plugin.class, Consumer.class);
                Object scheduled = run.invoke(globalRegionScheduler, plugin, (Consumer<Object>) t -> task.run());
                track(scheduled);
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Folia runGlobal failed: " + e.getMessage());
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void runAtEntity(Entity entity, Runnable task) {
        if (!plugin.isEnabled() || entity == null) {
            return;
        }
        if (folia) {
            try {
                Object entityScheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
                Method run = entityScheduler.getClass()
                        .getMethod("run", Plugin.class, Consumer.class, Runnable.class);
                Object scheduled = run.invoke(entityScheduler, plugin,
                        (Consumer<Object>) t -> task.run(), (Runnable) () -> {});
                track(scheduled);
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Folia runAtEntity failed: " + e.getMessage());
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void runDelayedAtEntity(Entity entity, Runnable task, long delayTicks) {
        if (!plugin.isEnabled() || entity == null) {
            return;
        }
        long delay = Math.max(1L, delayTicks);
        if (folia) {
            try {
                Object entityScheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
                Method runDelayed = entityScheduler.getClass()
                        .getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
                Object scheduled = runDelayed.invoke(entityScheduler, plugin,
                        (Consumer<Object>) t -> task.run(), (Runnable) () -> {}, delay);
                track(scheduled);
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Folia runDelayedAtEntity failed: " + e.getMessage());
            }
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    public void runAtLocation(Location location, Runnable task) {
        if (!plugin.isEnabled() || location == null) {
            return;
        }
        if (folia) {
            if (regionScheduler == null) {
                plugin.getPluginLogger().warning("runAtLocation skipped: Folia RegionScheduler missing");
                return;
            }
            try {
                Method run = regionScheduler.getClass()
                        .getMethod("run", Plugin.class, Location.class, Consumer.class);
                Object scheduled = run.invoke(regionScheduler, plugin, location, (Consumer<Object>) t -> task.run());
                track(scheduled);
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Folia runAtLocation failed: " + e.getMessage());
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public void runDelayedAsync(Runnable task, long delay, TimeUnit unit) {
        if (shuttingDown.get() || !plugin.isEnabled()) {
            return;
        }
        if (folia) {
            if (asyncScheduler == null) {
                return;
            }
            try {
                Method runDelayed = asyncScheduler.getClass()
                        .getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);
                Object scheduled = runDelayed.invoke(asyncScheduler, plugin,
                        (Consumer<Object>) t -> task.run(), delay, unit);
                track(scheduled);
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Folia runDelayedAsync failed: " + e.getMessage());
            }
            return;
        }
        long ticks = Math.max(1L, unit.toMillis(delay) / 50L);
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, ticks);
    }

    public void runDelayedGlobal(Runnable task, long delay, TimeUnit unit) {
        runDelayedGlobalTicks(task, Math.max(1L, unit.toMillis(delay) / 50L));
    }

    public void runDelayedGlobalTicks(Runnable task, long delayTicks) {
        if (!plugin.isEnabled()) {
            return;
        }
        long delay = Math.max(1L, delayTicks);
        if (folia) {
            if (globalRegionScheduler == null) {
                return;
            }
            try {
                Method runDelayed = globalRegionScheduler.getClass()
                        .getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
                Object scheduled = runDelayed.invoke(globalRegionScheduler, plugin,
                        (Consumer<Object>) t -> task.run(), delay);
                track(scheduled);
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Folia runDelayedGlobal failed: " + e.getMessage());
            }
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, task, delay);
    }

    /**
     * Repeating global task (auto-save). Returns a cancel handle.
     */
    public CancelHandle runAtFixedRateGlobal(Runnable task, long delayTicks, long periodTicks) {
        if (!plugin.isEnabled()) {
            return () -> {};
        }
        long delay = Math.max(1L, delayTicks);
        long period = Math.max(1L, periodTicks);
        if (folia) {
            if (globalRegionScheduler == null) {
                return () -> {};
            }
            try {
                Method runAtFixedRate = globalRegionScheduler.getClass()
                        .getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
                Object scheduled = runAtFixedRate.invoke(globalRegionScheduler, plugin,
                        (Consumer<Object>) t -> task.run(), delay, period);
                track(scheduled);
                return () -> cancelObject(scheduled);
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Folia runAtFixedRateGlobal failed: " + e.getMessage());
                return () -> {};
            }
        }
        BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
        return bukkitTask::cancel;
    }

    public void cancelAllTasks() {
        shuttingDown.set(true);
        if (!folia) {
            Bukkit.getScheduler().cancelTasks(plugin);
            return;
        }
        synchronized (pendingTasks) {
            for (Object task : pendingTasks) {
                cancelObject(task);
            }
            pendingTasks.clear();
        }
        cancelPluginTasks(asyncScheduler);
        cancelPluginTasks(globalRegionScheduler);
    }

    private void cancelPluginTasks(Object schedulerObj) {
        if (schedulerObj == null) {
            return;
        }
        try {
            Method cancelTasks = schedulerObj.getClass().getMethod("cancelTasks", Plugin.class);
            cancelTasks.invoke(schedulerObj, plugin);
        } catch (Exception ignored) {
        }
    }

    private void track(Object scheduled) {
        if (scheduled == null) {
            return;
        }
        synchronized (pendingTasks) {
            pendingTasks.add(scheduled);
        }
    }

    private void cancelObject(Object task) {
        if (task == null) {
            return;
        }
        try {
            task.getClass().getMethod("cancel").invoke(task);
        } catch (Exception ignored) {
        }
    }

    @FunctionalInterface
    public interface CancelHandle {
        void cancel();
    }
}
