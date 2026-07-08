package com.willfp.libreforge.effects.templates

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.map.listMap
import com.willfp.eco.core.map.nestedListMap
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.IdentifiedModifier
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.get
import com.willfp.libreforge.toDispatcher
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

abstract class MultiMultiplierEffect<T : Any>(id: String) : Effect<NoCompileData>(id) {
    override val arguments = arguments {
        require(
            "multiplier",
            "You must specify the multiplier!",
            description = "The multiplier to apply. Supports expressions.",
            type = ArgType.EXPRESSION
        )
    }

    private val globalModifiers = ConcurrentHashMap<UUID, List<IdentifiedModifier>>()
    private val modifiers = ConcurrentHashMap<UUID, Map<T, List<IdentifiedModifier>>>()

    /**
     * The key to look for in arguments, e.g. "stat" or "skill".
     */
    abstract val key: String

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        val modifier = IdentifiedModifier(identifiers.uuid) {
            config.getDoubleFromExpression("multiplier", dispatcher.get())
        }

        if (config.has(key)) {
            val elements = config.getStrings(key).mapNotNull { getElement(it) }

            modifiers.compute(dispatcher.uuid) { _, existingMap ->
                val map = existingMap?.toMutableMap() ?: mutableMapOf()
                for (element in elements) {
                    map[element] = (map[element] ?: emptyList()) + modifier
                }
                map.toMap()
            }
        } else {
            globalModifiers.compute(dispatcher.uuid) { _, existing ->
                (existing ?: emptyList()) + modifier
            }
        }
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        globalModifiers.computeIfPresent(dispatcher.uuid) { _, existing ->
            existing.filter { it.uuid != identifiers.uuid }
                .ifEmpty { null }
        }

        modifiers.computeIfPresent(dispatcher.uuid) { _, existingMap ->
            val newMap = existingMap.mapValues { (_, list) ->
                list.filter { it.uuid != identifiers.uuid }
            }.filterValues { it.isNotEmpty() }

            if (newMap.isEmpty()) null else newMap
        }
    }

    protected fun getMultiplier(dispatcher: Dispatcher<*>, element: T): Double {
        var multiplier = 1.0

        for (modifier in globalModifiers[dispatcher.uuid] ?: emptyList()) {
            multiplier *= modifier.modifier
        }

        for (modifier in modifiers[dispatcher.uuid]?.get(element) ?: emptyList()) {
            multiplier *= modifier.modifier
        }

        return multiplier
    }

    /**
     * Get an element by [key], for example a stat.
     */
    abstract fun getElement(key: String): T?

    /**
     * Get all elements.
     */
    abstract fun getAllElements(): Collection<T>
}
