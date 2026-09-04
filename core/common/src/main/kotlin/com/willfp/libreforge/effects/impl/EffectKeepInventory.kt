package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Identifiers
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EffectKeepInventory : Effect<NoCompileData>("keep_inventory") {
    override val description = "Prevents the player from dropping their inventory on death."
    override val categories = setOf("player", "inventory")

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
    fun handle(event: PlayerDeathEvent) {
        val player = event.player

        if (players[player.uniqueId]?.isNotEmpty() == true) {
            event.keepInventory = true
            event.drops.clear()
        }
    }
}
