package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.map.nestedMap
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.get
import com.willfp.libreforge.points
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EffectAddPoints : Effect<NoCompileData>("add_points") {
    override val description = "Permanently increases a player's point counter while the holder is active."
    override val categories = setOf("economy", "points")

    override val arguments = arguments {
        require(
            "type",
            "You must specify the type of points!",
            description = "The player point type to add to.",
            type = ArgType.STRING
        )
        require(
            "amount",
            "You must specify the amount of points!",
            description = "The amount of points to add. Supports expressions.",
            type = ArgType.EXPRESSION
        )
    }

    private val tracker = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, AddedPoint>>()

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        val player = dispatcher.get<Player>() ?: return

        val point = config.getString("type")
        val amount = config.getDoubleFromExpression("amount", player)

        tracker.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }[identifiers.uuid] = AddedPoint(
            point,
            amount
        )

        player.points[point] += amount
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        val player = dispatcher.get<Player>() ?: return

        var addedPoint: AddedPoint? = null
        tracker.computeIfPresent(player.uniqueId) { _, points ->
            addedPoint = points.remove(identifiers.uuid)
            points.takeIf { it.isNotEmpty() }
        }
        val removedPoint = addedPoint ?: return

        player.points[removedPoint.point] -= removedPoint.amount
    }

    private data class AddedPoint(
        val point: String,
        val amount: Double
    )
}
