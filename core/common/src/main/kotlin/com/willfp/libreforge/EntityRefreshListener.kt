package com.willfp.libreforge

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent
import org.bukkit.Registry
import org.bukkit.attribute.AttributeInstance
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EntityRefreshListener : Listener {

    private val MODIFIER_PATTERN = Regex("\\d+_\\d+")
    private val trackedEntities = ConcurrentHashMap<UUID, WeakReference<LivingEntity>>()

    @EventHandler
    fun onEntityAdded(event: EntityAddToWorldEvent) {
        track(event.entity as? LivingEntity ?: return)
    }

    @EventHandler
    fun onEntityRemoved(event: EntityRemoveFromWorldEvent) {
        trackedEntities.remove(event.entity.uniqueId)
    }

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        event.chunk.entities.filterIsInstance<LivingEntity>()
            .filterNot { it is Player }
            .forEach { entity ->
                track(entity)
                SchedulerHelper.runTask(plugin, entity) {
                    removeEcoAttributeModifiers(entity)
                }
            }
    }

    private fun track(entity: LivingEntity) {
        if (entity is Player) {
            return
        }
        trackedEntities[entity.uniqueId] = WeakReference(entity)
    }

    internal fun pollTrackedEntities() {
        for ((uuid, reference) in trackedEntities) {
            val entity = reference.get()
            if (entity == null) {
                trackedEntities.remove(uuid, reference)
                continue
            }

            SchedulerHelper.runTaskForEntity(
                plugin,
                entity,
                Runnable {
                    if (!entity.isValid || entity.isDead) {
                        trackedEntities.remove(uuid, reference)
                        return@Runnable
                    }
                    entity.toDispatcher().pollEffects()
                },
                Runnable { trackedEntities.remove(uuid, reference) }
            )
        }
    }

    internal fun clear() {
        trackedEntities.clear()
    }

    private fun removeEcoAttributeModifiers(entity: LivingEntity) {
        for (attribute in Registry.ATTRIBUTE) {
            val attributeInstance: AttributeInstance = entity.getAttribute(attribute) ?: continue

            @Suppress("USELESS_ELVIS")
            val modifiers = attributeInstance.modifiers ?: continue

            modifiers.filter { it.name.matches(MODIFIER_PATTERN) }
                .forEach { modifier ->
                    attributeInstance.removeModifier(modifier)
                }
        }
    }
}
