package com.willfp.libreforge.effects.arguments.impl

import com.willfp.libreforge.ConfigurableElement
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.effects.arguments.EffectArgument
import com.willfp.libreforge.getIntFromExpression
import com.willfp.libreforge.triggers.DispatchedTrigger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ArgumentEvery: EffectArgument<NoCompileData>("every") {
    private val everyHandler = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Int>>()

    override fun isMet(element: ConfigurableElement, trigger: DispatchedTrigger, compileData: NoCompileData): Boolean {
        val current = everyHandler[element.uuid]?.get(trigger.dispatcher.uuid) ?: 1

        return current == 0
    }

    override fun ifMet(element: ConfigurableElement, trigger: DispatchedTrigger, compileData: NoCompileData) {
        increment(element, trigger)
    }

    override fun ifNotMet(element: ConfigurableElement, trigger: DispatchedTrigger, compileData: NoCompileData) {
        increment(element, trigger)
    }

    private fun increment(element: ConfigurableElement, trigger: DispatchedTrigger) {
        val every = element.config.getIntFromExpression("every", trigger.data)

        everyHandler.compute(element.uuid) { _, existing ->
            val inner = existing ?: ConcurrentHashMap()
            var current = (inner[trigger.dispatcher.uuid] ?: 1) + 1
            if (current >= every) {
                current = 0
            }
            inner[trigger.dispatcher.uuid] = current
            inner
        }
    }

    internal fun clearDispatcher(uuid: UUID) {
        everyHandler.keys.forEach { elementId ->
            everyHandler.computeIfPresent(elementId) { _, inner ->
                inner.remove(uuid)
                inner.takeIf { it.isNotEmpty() }
            }
        }
    }
}
