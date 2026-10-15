package com.willfp.libreforge.triggers.impl

import com.willfp.eco.core.drops.DropQueue
import com.willfp.eco.core.integrations.antigrief.AntigriefManager
import com.willfp.eco.util.isPlayerPlaced
import com.willfp.libreforge.filterNotEmpty
import com.willfp.libreforge.SchedulerHelper
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.PlayerPlacedSnapshot
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import com.willfp.libreforge.triggers.event.DropCause
import com.willfp.libreforge.triggers.event.DropContext
import com.willfp.libreforge.triggers.event.EditableDropEvent
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.block.data.BlockData
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockBreakEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TriggerBlockItemDrop : Trigger("block_item_drop") {
    private data class BreakKey(val world: UUID, val x: Int, val y: Int, val z: Int, val player: UUID)
    private class SourceSnapshot(val placed: Boolean, val material: Material)
    private val pendingSources = ConcurrentHashMap<BreakKey, SourceSnapshot>()

    private fun key(block: Block, player: UUID) = BreakKey(
        block.world.uid, block.x, block.y, block.z, player
    )

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun captureSource(event: BlockBreakEvent) {
        val block = event.block
        val key = key(block, event.player.uniqueId)
        val snapshot = SourceSnapshot(block.isPlayerPlaced, block.type)
        pendingSources[key] = snapshot
        // Expire on this block's region: a global tick can run before its drop event finishes.
        // Breaks without drops and cancelled breaks must not leave a snapshot for a later break.
        SchedulerHelper.runTaskLater(plugin, block.location, {
            pendingSources.remove(key, snapshot)
        }, 1)
    }

    override val description = "Fires when a block broken by the player drops its items."

    override val categories = setOf("world")

    override val parameterDescriptions = mapOf(
        TriggerParameter.BLOCK to "The block that was broken.",
        TriggerParameter.LOCATION to "The location of the broken block.",
        TriggerParameter.ITEM to "Empty — the drops are exposed through the drop count, not a single item.",
        TriggerParameter.VALUE to "The total number of items dropped."
    )

    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.BLOCK,
        TriggerParameter.EVENT,
        TriggerParameter.LOCATION,
        TriggerParameter.ITEM,
        TriggerParameter.VALUE
    )

    @EventHandler(
        ignoreCancelled = true,
        priority = EventPriority.LOW
    )
    fun handle(event: BlockDropItemEvent) {
        val player = event.player
        val block = event.block
        val source = pendingSources.remove(key(block, player.uniqueId))

        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) {
            return
        }

        if (event.blockState is Container) {
            return
        }

        if (!AntigriefManager.canBreakBlock(player, block)) {
            return
        }

        // Missing snapshots are treated as ineligible, rather than rewarding a potentially
        // player-placed block based on an entry mcMMO has already deleted.
        val wasPlayerPlaced = source?.takeIf { it.material == event.blockState.type }?.placed ?: true
        val brokenBlock = BrokenBlock(block, event.blockState.type, event.blockState.blockData, wasPlayerPlaced)

        val itemEntityToStack = event.items.associateWith { it.itemStack }
        val originalDrops = itemEntityToStack.values.toList().filterNotEmpty()

        val editableEvent = EditableDropEvent(
            initialDrops = originalDrops,
            cause = DropCause.BLOCK,
            context = DropContext(
                player = player,
                block = brokenBlock,
                blockState = event.blockState,
                tool = player.inventory.itemInMainHand
            ),
            dropLocation = block.location,
            cancellable = event
        )

        this.dispatch(
            player.toDispatcher(),
            TriggerData(
                player = player,
                block = brokenBlock,
                location = block.location,
                event = editableEvent,
                item = null,
                value = originalDrops.sumOf { it.amount }.toDouble()
            )
        )

        val dropResults = editableEvent.items

        val remainingDrops = editableEvent.drops
        event.items.removeIf { item ->
            val stack = itemEntityToStack[item] ?: return@removeIf true
            remainingDrops.none { drop -> drop === stack }
        }

        for (item in event.items) {
            val stack = itemEntityToStack[item] ?: continue
            item.setItemStack(stack)
        }

        // Drops added by effects (e.g. drop_item with add_to_drops) have no backing
        // item entity, so the loop above can't emit them. Push them through a
        // DropQueue (telekinesis-aware). These stacks already have their modifiers
        // applied in place by the `dropResults` read above, so we must NOT read
        // editableEvent.items again here or modifiers would be applied twice.
        val addedStacks = remainingDrops.filter { stack ->
            itemEntityToStack.values.none { it === stack }
        }
        if (addedStacks.isNotEmpty()) {
            DropQueue(player)
                .setLocation(block.location)
                .addItems(addedStacks)
                .push()
        }

        val totalXP = dropResults.sumOf { it.xp }
        if (totalXP > 0) {
            DropQueue(player)
                .setLocation(block.location)
                .addXP(totalXP)
                .push()
        }
    }

    private class BrokenBlock(
        private val block: Block,
        private val type: Material,
        private val data: BlockData,
        override val wasPlayerPlaced: Boolean
    ): Block by block, PlayerPlacedSnapshot {
        override fun getType() = type
        override fun getBlockData(): BlockData = data
    }
}
