package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.FoliaRunnableTask
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.SchedulerHelper
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.getDoubleFromExpression
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.UUID

object EffectTraceback : Effect<NoCompileData>("traceback") {
    override val description = "Teleports the player back to where they were a specified number of seconds ago."
    override val categories = setOf("movement")

    override val parameters = setOf(
        TriggerParameter.PLAYER
    )

    override val arguments = arguments {
        require(
            "seconds",
            "You must specify the amount of seconds to go back in time (1-30)!",
            description = "How many seconds into the past to teleport the player (clamped to 1鈥?0). Supports expressions.",
            type = ArgType.EXPRESSION
        )
    }

    private const val key = "libreforge_traceback"

    override fun onTrigger(config: Config, data: TriggerData, compileData: NoCompileData): Boolean {
        val player = data.player ?: return false

        val time = config.getDoubleFromExpression("seconds", data).toInt().coerceIn(1..30)

        @Suppress("UNCHECKED_CAST")
        val times = player.getMetadata(key)
            .firstOrNull { it.owningPlugin == plugin }
            ?.value() as? List<TracebackPoint> ?: emptyList()

        // Most recent is last
        val index = times.size - time

        val location = times.getOrElse(index) { times.lastOrNull() }?.toLocation() ?: return false

        SchedulerHelper.teleportEntity(player, location)

        return true
    }

    override fun postRegister() {
        val task = FoliaRunnableTask(plugin) {
            for (player in Bukkit.getOnlinePlayers()) {
                SchedulerHelper.runTask(plugin, player) {
                    @Suppress("UNCHECKED_CAST")
                    val times = player.getMetadata(key)
                        .firstOrNull { it.owningPlugin == plugin }
                        ?.value() as? List<TracebackPoint> ?: emptyList()
                    val location = player.location
                    val worldId = location.world?.uid ?: return@runTask
                    val point = TracebackPoint(
                        worldId,
                        location.x,
                        location.y,
                        location.z,
                        location.yaw,
                        location.pitch
                    )
                    val newTimes = (if (times.size < 30) times else times.drop(1)) + point

                    player.removeMetadata(key, plugin)
                    player.setMetadata(key, plugin.metadataValueFactory.create(newTimes))
                }
            }
        }

        task.runTask(20L, 20L)
    }

    internal fun clear(player: org.bukkit.entity.Player) {
        player.removeMetadata(key, plugin)
    }

    private data class TracebackPoint(
        val worldId: UUID,
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float,
        val pitch: Float
    ) {
        fun toLocation(): Location? = Bukkit.getWorld(worldId)?.let {
            Location(it, x, y, z, yaw, pitch)
        }
    }
}
