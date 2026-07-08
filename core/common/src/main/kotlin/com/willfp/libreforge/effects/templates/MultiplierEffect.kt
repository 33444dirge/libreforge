package com.willfp.libreforge.effects.templates

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.IdentifiedModifier
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.get
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

abstract class MultiplierEffect(id: String) : Effect<NoCompileData>(id) {
    override val arguments = arguments {
        require("multiplier", "You must specify the multiplier!")
    }

    private val modifiers = ConcurrentHashMap<UUID, List<IdentifiedModifier>>()

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        val modifier = IdentifiedModifier(identifiers.uuid) {
            config.getDoubleFromExpression("multiplier", dispatcher.get<Player>()!!)
        }
        modifiers.compute(dispatcher.uuid) { _, existing ->
            (existing ?: emptyList()) + modifier
        }
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        modifiers.computeIfPresent(dispatcher.uuid) { _, existing ->
            existing.filter { it.uuid != identifiers.uuid }
                .ifEmpty { null }
        }
    }

    protected fun getMultiplier(dispatcher: Dispatcher<*>): Double {
        val list = modifiers[dispatcher.uuid] ?: return 1.0
        var multiplier = 1.0

        for (modifier in list) {
            multiplier *= modifier.modifier
        }

        return multiplier
    }
}