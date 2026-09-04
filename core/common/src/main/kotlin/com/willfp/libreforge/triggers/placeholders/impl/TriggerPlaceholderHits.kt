package com.willfp.libreforge.triggers.placeholders.impl

import com.willfp.libreforge.NamedValue
import com.willfp.libreforge.integrations.paper.impl.TriggerTridentAttack
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.event.TriggerDispatchEvent
import com.willfp.libreforge.triggers.impl.TriggerBowAttack
import com.willfp.libreforge.triggers.impl.TriggerMeleeAttack
import com.willfp.libreforge.triggers.placeholders.TriggerPlaceholder
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TriggerPlaceholderHits : TriggerPlaceholder("hits") {
    private val hitsByEntity = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, Int>>()

    override fun createPlaceholders(data: TriggerData): Collection<NamedValue> {
        val victim = data.victim ?: return emptyList()
        val player = data.player ?: return emptyList()

        return listOf(
            NamedValue(
                "hits",
                victim.getHits(player)
            )
        )
    }

    @EventHandler
    fun trackHits(event: TriggerDispatchEvent) {
        if (event.trigger.trigger !in listOf(
                TriggerMeleeAttack,
                TriggerBowAttack,
                TriggerTridentAttack
            )
        ) {
            return
        }

        val player = event.trigger.data.player ?: return
        val entity = event.trigger.data.victim ?: return

        val map = hitsByEntity.computeIfAbsent(entity.uniqueId) { ConcurrentHashMap() }
        val hits = entity.getHits(player)
        if (entity.health >= entity.getAttribute(Attribute.MAX_HEALTH)!!.value) {
            map.clear()
            map[player.uniqueId] = 1
        } else {
            map[player.uniqueId] = hits + 1
        }

    }

    private fun LivingEntity.getHits(player: Player): Int {
        return hitsByEntity[this.uniqueId]?.get(player.uniqueId) ?: 0
    }

    internal fun clearEntity(uuid: UUID) {
        hitsByEntity.remove(uuid)
    }

    internal fun clearAll() {
        hitsByEntity.clear()
    }
}
