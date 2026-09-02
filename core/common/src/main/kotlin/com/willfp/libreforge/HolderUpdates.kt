package com.willfp.libreforge

import com.github.benmanes.caffeine.cache.Caffeine
import com.willfp.eco.core.events.ArmorChangeEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCreativeEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.util.UUID
import java.util.concurrent.TimeUnit

@Suppress("unused", "UNUSED_PARAMETER")
object ItemRefreshListener : Listener {
    private val inventoryClickTimeouts = Caffeine.newBuilder()
        .expireAfterWrite(
            plugin.configYml.getInt("refresh.inventory-click.timeout").toLong(),
            TimeUnit.MILLISECONDS
        )
        .build<UUID, Unit>()

    @EventHandler(priority = EventPriority.LOWEST)
    fun onItemPickup(event: EntityPickupItemEvent) {
        if (!plugin.configYml.getBool("refresh.pickup.enabled")) {
            return
        }

        if (plugin.configYml.getBool("refresh.pickup.require-meta")) {
            if (!event.item.itemStack.hasItemMeta()) {
                return
            }
        }

        val entity = event.entity
        val dispatcher = entity.toDispatcher()
        SchedulerHelper.runTask(plugin, entity) {
            dispatcher.refreshHolders()
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        Bukkit.getServer().onlinePlayers.forEach {
            val dispatcher = it.toDispatcher()
            SchedulerHelper.runTask(plugin, it) {
                dispatcher.refreshHolders()
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInventoryDrop(event: PlayerDropItemEvent) {
        val player = event.player
        val dispatcher = player.toDispatcher()
        SchedulerHelper.runTask(plugin, player) {
            dispatcher.refreshHolders()
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onChangeSlot(event: PlayerItemHeldEvent) {
        val player = event.player

        if (plugin.configYml.getBool("refresh.held.require-meta")) {
            val oldItem = player.inventory.getItem(event.previousSlot)
            val newItem = player.inventory.getItem(event.newSlot)
            if (((oldItem == null) || !oldItem.hasItemMeta()) && ((newItem == null) || !newItem.hasItemMeta())) {
                return
            }
        }

        val dispatcher = player.toDispatcher()

        SchedulerHelper.runTask(plugin, player) {
            dispatcher.refreshHolders()
        }
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val dispatcher = player.toDispatcher()
        SchedulerHelper.runTask(plugin, player) {
            dispatcher.refreshHolders()
        }
    }

    @EventHandler
    fun onArmorChange(event: ArmorChangeEvent) {
        val player = event.player
        val dispatcher = player.toDispatcher()
        SchedulerHelper.runTask(plugin, player) {
            dispatcher.refreshHolders()
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val placesCursorItem = event.action == InventoryAction.PLACE_ALL ||
                event.action == InventoryAction.PLACE_ONE ||
                event.action == InventoryAction.PLACE_SOME ||
                event.action == InventoryAction.SWAP_WITH_CURSOR
        val requiresInventorySync = event !is InventoryCreativeEvent &&
                placesCursorItem &&
                event.cursor.hasItemMeta()
        val shouldRefreshHolders = inventoryClickTimeouts.getIfPresent(player.uniqueId) == null

        if (!requiresInventorySync && !shouldRefreshHolders) {
            return
        }

        if (shouldRefreshHolders) {
            inventoryClickTimeouts.put(player.uniqueId, Unit)
        }

        val dispatcher = player.toDispatcher()
        SchedulerHelper.runTask(plugin, player) {
            if (shouldRefreshHolders) {
                dispatcher.refreshHolders()
            }
            if (requiresInventorySync) {
                // CraftBukkit broadcasts only the carried item here. A full
                // updateInventory() also re-sends every open-container slot,
                // which makes chest and hotbar items visibly jump.
                player.setItemOnCursor(player.itemOnCursor)
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!event.oldCursor.hasItemMeta()) {
            return
        }

        val shouldRefreshHolders = inventoryClickTimeouts.getIfPresent(player.uniqueId) == null
        if (shouldRefreshHolders) {
            inventoryClickTimeouts.put(player.uniqueId, Unit)
        }

        val dispatcher = player.toDispatcher()
        SchedulerHelper.runTask(plugin, player) {
            if (shouldRefreshHolders) {
                dispatcher.refreshHolders()
            }
            player.setItemOnCursor(player.itemOnCursor)
        }
    }
}
