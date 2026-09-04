package com.willfp.libreforge.integrations.custom_blocks.oraxen.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.drops.DropQueue
import com.willfp.eco.core.integrations.antigrief.AntigriefManager
import com.willfp.eco.util.TelekinesisUtils
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.plugin
import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureBreakEvent
import io.th0rgal.oraxen.api.events.noteblock.OraxenNoteBlockBreakEvent
import io.th0rgal.oraxen.api.events.stringblock.OraxenStringBlockBreakEvent
import io.th0rgal.oraxen.utils.drops.Drop
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EffectOraxenTelekinesis : Effect<NoCompileData>("telekinesis") {
    override val description = "Causes Oraxen block and furniture drops to go directly into the player's inventory instead of dropping on the ground."
    override val categories = setOf("inventory")

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

    override fun postRegister() {
        TelekinesisUtils.registerTest { players[it.uniqueId]?.isNotEmpty() ?: false }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun OraxenFurnitureBreakEvent.handle() {
        if (!TelekinesisUtils.testPlayer(player)) {
            return
        }

        if (!AntigriefManager.canBreakBlock(player, block)) {
            return
        }

        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
            return
        }

        val drops = drop.loots.mapNotNull { it.itemStack }
        drop = Drop.emptyDrop()

        DropQueue(player)
            .setLocation(block.location)
            .addItems(drops)
            .forceTelekinesis()
            .push()
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun OraxenNoteBlockBreakEvent.handle() {
        if (!TelekinesisUtils.testPlayer(player)) {
            return
        }

        if (!AntigriefManager.canBreakBlock(player, block)) {
            return
        }

        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
            return
        }

        val drops = drop.loots.mapNotNull { it.itemStack }
        drop = Drop.emptyDrop()

        DropQueue(player)
            .setLocation(block.location)
            .addItems(drops)
            .forceTelekinesis()
            .push()
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun OraxenStringBlockBreakEvent.handle() {
        if (!TelekinesisUtils.testPlayer(player)) {
            return
        }

        if (!AntigriefManager.canBreakBlock(player, block)) {
            return
        }

        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
            return
        }

        val drops = drop.loots.mapNotNull { it.itemStack }
        drop = Drop.emptyDrop()

        DropQueue(player)
            .setLocation(block.location)
            .addItems(drops)
            .forceTelekinesis()
            .push()
    }
}
