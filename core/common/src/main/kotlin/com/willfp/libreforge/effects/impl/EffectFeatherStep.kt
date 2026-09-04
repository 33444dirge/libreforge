package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Identifiers
import org.bukkit.Tag
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EffectFeatherStep : Effect<NoCompileData>("feather_step") {
    override val description = "Prevents the player trampling crops."
    override val categories = setOf("movement", "player")

    private val players = ConcurrentHashMap<UUID, MutableList<UUID>>()

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        players.computeIfAbsent(dispatcher.uuid) { mutableListOf() }.add(identifiers.uuid)
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        players.computeIfPresent(dispatcher.uuid) { _, active ->
            active.remove(identifiers.uuid)
            active.takeIf { it.isNotEmpty() }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun handle(event: PlayerInteractEvent) {
        if (event.action != Action.PHYSICAL) {
            return
        }

        val player = event.player

        // Extra check for pressure plates
        if (player.location.block.type in Tag.PRESSURE_PLATES.values
            || player.location.subtract(0.0, 1.0, 0.0).block.type in Tag.PRESSURE_PLATES.values
        ) {
            return
        }

        if (players[player.uniqueId]?.isNotEmpty() == true) {
            event.isCancelled = true
        }
    }
}
