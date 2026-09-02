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
import com.destroystokyo.paper.event.player.PlayerAttackEntityCooldownResetEvent
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private object AttackCooldownAccess {
    val current: Method? = runCatching {
        Player::class.java.getMethod("getCooledAttackStrength", Float::class.javaPrimitiveType)
    }.getOrNull()

    val legacy: Method? = runCatching {
        Player::class.java.getMethod("getAttackCooldown")
    }.getOrNull()
}

private data class PendingAttack(
    val target: UUID,
    val cooldown: Float
)

private val pendingAttacks = ConcurrentHashMap<UUID, PendingAttack>()

internal object AttackCooldownSnapshot : Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    fun onAttack(event: PlayerAttackEntityCooldownResetEvent) {
        val snapshot = PendingAttack(
            event.attackedEntity.uniqueId,
            event.cooledAttackStrength.coerceIn(0f, 1f)
        )
        pendingAttacks[event.player.uniqueId] = snapshot
        SchedulerHelper.runTaskLater(plugin, event.player, Runnable {
            pendingAttacks.remove(event.player.uniqueId, snapshot)
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

    val value = runCatching {
        when {
            AttackCooldownAccess.current != null -> AttackCooldownAccess.current.invoke(this, 0.5f)
            AttackCooldownAccess.legacy != null -> AttackCooldownAccess.legacy.invoke(this)
            else -> null
        }
    }.getOrNull()

    return (value as? Number)?.toFloat()?.coerceIn(0f, 1f) ?: 1f
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
