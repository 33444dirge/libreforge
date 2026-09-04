package com.willfp.libreforge.triggers.impl

import com.willfp.libreforge.ProvidedHolder
import com.willfp.libreforge.holders
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Trident
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TriggerTridentHit : Trigger("trident_hit") {
    private val holderSnapshots = ConcurrentHashMap<UUID, Collection<ProvidedHolder>>()
    override val description = "Fires when the player's thrown trident hits a block or entity."

    override val categories = setOf("combat")

    override val parameterDescriptions = mapOf(
        TriggerParameter.VICTIM to "The entity hit by the trident, if any.",
        TriggerParameter.PROJECTILE to "The trident projectile.",
        TriggerParameter.LOCATION to "The trident's location at impact.",
        TriggerParameter.BLOCK to "The block hit by the trident, if any.",
        TriggerParameter.VELOCITY to "The velocity of the trident at impact."
    )

    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.VICTIM,
        TriggerParameter.BLOCK,
        TriggerParameter.EVENT,
        TriggerParameter.LOCATION,
        TriggerParameter.PROJECTILE,
        TriggerParameter.VELOCITY
    )

    @EventHandler(ignoreCancelled = true)
    fun onProjectileLaunch(event: ProjectileLaunchEvent) {
        val trident = event.entity as? Trident ?: return
        val shooter = trident.shooter as? LivingEntity ?: return

        holderSnapshots[trident.uniqueId] = shooter.toDispatcher().holders.toList()
    }

    @EventHandler(ignoreCancelled = true)
    fun handle(event: ProjectileHitEvent) {
        val trident = event.entity as? Trident ?: return
        val shooter = trident.shooter as? LivingEntity ?: return

        @Suppress("UNCHECKED_CAST")
        this.dispatch(
            shooter.toDispatcher(),
            TriggerData(
                player = shooter as? Player,
                projectile = trident,
                location = trident.location,
                block = event.hitBlock,
                event = event,
                velocity = trident.velocity
            ),
            forceHolders = holderSnapshots[trident.uniqueId]
        )
    }

    @EventHandler(ignoreCancelled = true)
    fun handle(event: EntityDamageByEntityEvent) {
        val trident = event.damager as? Trident ?: return
        val victim = event.entity as? LivingEntity ?: return
        val shooter = trident.shooter as? LivingEntity ?: return

        @Suppress("UNCHECKED_CAST")
        this.dispatch(
            shooter.toDispatcher(),
            TriggerData(
                player = shooter as? Player,
                victim = victim,
                location = trident.location,
                event = event,
                velocity = trident.velocity,
                projectile = trident
            ),
            forceHolders = holderSnapshots[trident.uniqueId]
        )
    }

    internal fun clearSnapshot(uuid: UUID) {
        holderSnapshots.remove(uuid)
    }

    internal fun clearSnapshots() {
        holderSnapshots.clear()
    }
}
