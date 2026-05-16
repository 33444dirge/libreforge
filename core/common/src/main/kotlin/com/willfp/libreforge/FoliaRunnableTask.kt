package com.willfp.libreforge

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

class FoliaRunnableTask(
    private val plugin: Plugin,
    private val task: Runnable
) : BukkitTask {
    private var cancelled = false
    private var bukkitTask: Any? = null

    fun runTask(delay: Long = 0L, period: Long = 0L): BukkitTask {
        bukkitTask = SchedulerHelper.runTaskTimer(plugin, task, delay, period)
        return this
    }

    fun runTaskAsynchronously(delay: Long = 0L, period: Long = 0L): BukkitTask {
        bukkitTask = SchedulerHelper.runTaskTimerAsynchronously(plugin, task, delay, period)
        return this
    }

    fun runTask(location: Location, delay: Long = 0L, period: Long = 0L): BukkitTask {
        bukkitTask = SchedulerHelper.runTaskTimer(plugin, location, task, delay, period)
        return this
    }

    fun runTask(entity: Entity, delay: Long = 0L, period: Long = 0L): BukkitTask {
        bukkitTask = SchedulerHelper.runTaskTimer(plugin, entity, task, delay, period)
        return this
    }

    override fun cancel() {
        cancelled = true
        SchedulerHelper.cancelTask(plugin, bukkitTask)
    }

    override fun isCancelled(): Boolean {
        return cancelled
    }

    override fun isSync(): Boolean {
        return true
    }

    override fun getTaskId(): Int {
        return -1
    }

    override fun getOwner(): Plugin {
        return plugin
    }
}