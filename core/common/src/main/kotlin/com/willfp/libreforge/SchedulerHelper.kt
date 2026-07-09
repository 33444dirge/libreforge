package com.willfp.libreforge

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.TimeUnit

object SchedulerHelper {
    private val isFolia = try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, Bukkit::class.java.classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    fun runTask(plugin: Plugin, task: Runnable): Any {
        return if (isFolia) {
            Bukkit.getGlobalRegionScheduler().run(plugin) { task.run() }
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    fun runTaskLater(plugin: Plugin, task: Runnable, delay: Long): Any {
        return if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task.run() }, delay)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay)
        }
    }

    fun runTaskTimer(plugin: Plugin, task: Runnable, delay: Long, period: Long): Any {
        return if (isFolia) {
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, { task.run() }, delay, period)
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period)
        }
    }

    fun runTaskAsynchronously(plugin: Plugin, task: Runnable): Any {
        return if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin) { task.run() }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
        }
    }

    fun runTaskLaterAsynchronously(plugin: Plugin, task: Runnable, delay: Long): Any {
        return if (isFolia) {
            Bukkit.getAsyncScheduler().runDelayed(plugin, { task.run() }, delay * 50L, TimeUnit.MILLISECONDS)
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delay)
        }
    }

    fun runTaskTimerAsynchronously(plugin: Plugin, task: Runnable, delay: Long, period: Long): Any {
        return if (isFolia) {
            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, { task.run() }, delay * 50L, period * 50L, TimeUnit.MILLISECONDS)
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delay, period)
        }
    }

    fun runTask(plugin: Plugin, location: Location, task: Runnable): Any {
        return if (isFolia) {
            Bukkit.getRegionScheduler().run(plugin, location) { task.run() }
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    fun runTaskLater(plugin: Plugin, location: Location, task: Runnable, delay: Long): Any {
        return if (isFolia) {
            Bukkit.getRegionScheduler().runDelayed(plugin, location, { task.run() }, delay)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay)
        }
    }

    fun runTaskTimer(plugin: Plugin, location: Location, task: Runnable, delay: Long, period: Long): Any {
        return if (isFolia) {
            Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, { task.run() }, delay, period)
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period)
        }
    }

    fun runTask(plugin: Plugin, entity: Entity, task: Runnable): Any? {
        return if (isFolia) {
            entity.scheduler.run(plugin, { task.run() }, null)
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    fun runTaskLater(plugin: Plugin, entity: Entity, task: Runnable, delay: Long): Any? {
        return if (isFolia) {
            entity.scheduler.runDelayed(plugin, { task.run() }, null, delay)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delay)
        }
    }

    fun runTaskTimer(plugin: Plugin, entity: Entity, task: Runnable, delay: Long, period: Long): Any? {
        return if (isFolia) {
            entity.scheduler.runAtFixedRate(plugin, { task.run() }, null, delay, period)
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period)
        }
    }

    fun runTaskForEntity(plugin: Plugin, entity: Entity, task: Runnable, retired: Runnable?): Any? {
        return if (isFolia) {
            entity.scheduler.run(plugin, { task.run() }, retired)
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    fun cancelTask(plugin: Plugin, task: Any?) {
        if (task == null) return
        
        if (isFolia) {
            when (task) {
                is io.papermc.paper.threadedregions.scheduler.ScheduledTask -> {
                    task.cancel()
                }
            }
        } else {
            (task as? BukkitTask)?.cancel()
        }
    }

    fun cancelTasks(plugin: Plugin) {
        if (isFolia) {
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin)
            Bukkit.getAsyncScheduler().cancelTasks(plugin)
        } else {
            Bukkit.getScheduler().cancelTasks(plugin)
        }
    }

    fun teleportEntity(entity: Entity, location: Location): Boolean {
        return if (isFolia) {
            entity.teleportAsync(location)
            true
        } else {
            entity.teleport(location)
        }
    }
}
