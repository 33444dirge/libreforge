package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.map.listMap
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.IdentifiedModifier
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.get
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityShootBowEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

object EffectRapidBows : Effect<NoCompileData>("rapid_bows") {
    override val description = "Allows the player to fire bows faster by a given percentage, as a permanent passive effect."
    override val categories = setOf("combat")

    override val arguments = arguments {
        require(
            "percent_faster",
            "You must specify how many percent faster to make bow pulls!",
            description = "How many percent faster the bow charges. Supports expressions.",
            type = ArgType.EXPRESSION
        )
    }

    private val modifiers = ConcurrentHashMap<UUID, List<IdentifiedModifier>>()

    private const val MAX_FORCE = 3.0

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        val modifier = IdentifiedModifier(identifiers.uuid) {
            config.getDoubleFromExpression("percent_faster", dispatcher.get())
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

    @EventHandler(
        priority = EventPriority.LOW,
        ignoreCancelled = true
    )
    fun handle(event: EntityShootBowEvent) {
        val entity = event.entity

        val totalPercentFaster = modifiers[entity.uniqueId]
            ?.sumOf { it.modifier }
            ?: 0.0
            .coerceAtMost(100.0)

        val multiplier = 1 - totalPercentFaster / 100

        val bowForce = event.force / MAX_FORCE

        if (bowForce < multiplier) {
            return
        }

        val force = min(1.0 / bowForce, Double.MAX_VALUE)
        var velocity = event.projectile.velocity.multiply(force)

        if (velocity.length() > 3) {
            velocity = velocity.normalize().multiply(3)
        }

        event.projectile.velocity = velocity
    }
}
