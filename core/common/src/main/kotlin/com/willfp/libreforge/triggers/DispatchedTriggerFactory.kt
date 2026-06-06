package com.willfp.libreforge.triggers

import com.willfp.eco.core.EcoPlugin
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.FoliaRunnableTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/*

Prevents multiple identical triggers from being triggered in the same tick.

 */

class DispatchedTriggerFactory(
    private val plugin: EcoPlugin
) {
    private val dispatcherTriggers = ConcurrentHashMap<UUID, MutableList<Int>>()


    fun create(dispatcher: Dispatcher<*>, trigger: Trigger, data: TriggerData): DispatchedTrigger? {
        if (!trigger.isEnabled) {
            return null
        }

        val hash = (trigger.hashCode() shl 5) xor data.hashCode()
        val uuid = dispatcher.uuid

        val list = dispatcherTriggers.computeIfAbsent(uuid) { mutableListOf() }
        if (hash in list) {
            return null
        }
        list.add(hash)
        val dispatchData = if (data.dispatcher == dispatcher) data else data.copy(dispatcher = dispatcher)
        return DispatchedTrigger(dispatcher, trigger, dispatchData)
    }

    internal fun startTicking() {
        val task = FoliaRunnableTask(plugin) {
            dispatcherTriggers.clear()
        }

        task.runTask(1L, 1L)
    }
}