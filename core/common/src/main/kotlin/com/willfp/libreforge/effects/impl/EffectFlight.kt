package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.get
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EffectFlight : Effect<NoCompileData>("flight") {
    override val description = "Grants the player the ability to fly while the holder is active."
    override val categories = setOf("movement", "player")

    override val shouldReload = false

    private val players = ConcurrentHashMap<UUID, MutableList<UUID>>()

    override fun onEnable(
        dispatcher: Dispatcher<*>,
        config: Config,
        identifiers: Identifiers,
        holder: ProvidedHolder,
        compileData: NoCompileData
    ) {
        val player = dispatcher.get<Player>() ?: return

        players.computeIfAbsent(player.uniqueId) { mutableListOf() }.add(identifiers.uuid)
        player.allowFlight = players[player.uniqueId]?.isNotEmpty() ?: false
    }

    override fun onDisable(dispatcher: Dispatcher<*>, identifiers: Identifiers, holder: ProvidedHolder) {
        val player = dispatcher.get<Player>() ?: return

        players.computeIfPresent(player.uniqueId) { _, active ->
            active.remove(identifiers.uuid)
            active.takeIf { it.isNotEmpty() }
        }
        player.allowFlight = players[player.uniqueId]?.isNotEmpty() ?: false
    }
}
