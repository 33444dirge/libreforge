package com.willfp.libreforge.effects.templates

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.util.randDouble
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

abstract class ChanceMultiplierEffect(id: String) : Effect<NoCompileData>(id) {
    override val arguments = arguments {
        require(
            "chance",
            "You must specify the chance!",
            description = "The percentage chance (0–100) for this effect to activate. Supports expressions.",
            type = ArgType.EXPRESSION
        )
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
            config.getDoubleFromExpression("chance", dispatcher.get<Player>()!!)
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

    protected fun passesChance(dispatcher: Dispatcher<*>): Boolean {
        var chance = 1.0

        for (modifier in modifiers[dispatcher.uuid] ?: emptyList()) {
            chance *= (100 - modifier.modifier) / 100
        }

        return randDouble(0.0, 1.0) > chance
    }
}