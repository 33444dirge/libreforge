package com.willfp.libreforge.filters.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.NoCompileData
import com.willfp.libreforge.SchedulerHelper
import com.willfp.libreforge.filters.Filter
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.player.PlayerQuitEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private data class PendingAttack(
    val target: UUID,
    val cooldown: Float
)

private val pendingAttacks = ConcurrentHashMap<UUID, PendingAttack>()

internal object AttackCooldownSnapshot : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onAttack(event: PrePlayerAttackEntityEvent) {
        if (!event.willAttack()) return
        val player = event.player
        val snapshot = PendingAttack(event.attacked.uniqueId, player.getCooledAttackStrength(0.5f).coerceIn(0f, 1f))
        pendingAttacks[player.uniqueId] = snapshot
        SchedulerHelper.runTaskLater(plugin, player, Runnable {
            pendingAttacks.remove(player.uniqueId, snapshot)
        }, 1)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        pendingAttacks.remove(event.player.uniqueId)
    }
}

internal fun clearAttackCooldownSnapshots() {
    pendingAttacks.clear()
}

private fun Player.compatAttackCooldown(target: org.bukkit.entity.Entity? = null): Float {
    if (target != null) {
        val pending = pendingAttacks[uniqueId]
        if (pending != null) {
            return if (pending.target == target.uniqueId) pending.cooldown else 0f
        }
    }

    return getCooledAttackStrength(0.5f).coerceIn(0f, 1f)
}

object FilterFullyCharged : Filter<NoCompileData, Boolean>("fully_charged") {
    override val description = "Matches when the attack or bow shot is (or is not) fully charged."
    override val categories = setOf("combat")
    override val valueType = ArgType.BOOLEAN
    override val additionalInfo = listOf("Passes automatically when the event is not an attack or bow shot event.")

    override fun getValue(config: Config, data: TriggerData?, key: String): Boolean {
        return config.getBool(key)
    }

    override fun isMet(data: TriggerData, value: Boolean, compileData: NoCompileData): Boolean {
        return when (val event = data.event) {
            is EntityDamageByEntityEvent -> {
                val player = event.damager as? Player ?: return true
                player.compatAttackCooldown(event.entity) >= 1f == value
            }
            is EntityShootBowEvent -> {
                event.force >= 1f == value
            }
            else -> true
        }
    }
}
