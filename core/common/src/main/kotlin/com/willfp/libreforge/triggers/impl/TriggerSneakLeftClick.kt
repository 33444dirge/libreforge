package com.willfp.libreforge.triggers.impl

import com.willfp.libreforge.plugin
import com.willfp.libreforge.toDispatcher
import com.willfp.libreforge.triggers.Trigger
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TriggerSneakLeftClick : Trigger("sneak_left_click") {
    override val parameters = setOf(
        TriggerParameter.PLAYER,
        TriggerParameter.VICTIM,
        TriggerParameter.LOCATION,
        TriggerParameter.EVENT,
        TriggerParameter.ITEM,
        TriggerParameter.BLOCK
    )

    private val preventDoubleTriggers = ConcurrentHashMap.newKeySet<UUID>()

    @EventHandler
    fun handle(event: PlayerInteractEvent) {
        val player = event.player

        if (!player.isSneaking) {
            return
        }

        if (player.uniqueId in preventDoubleTriggers) {
            return
        }

        if (event.action != Action.LEFT_CLICK_AIR && event.action != Action.LEFT_CLICK_BLOCK) {
            return
        }

        val location: Location?
        val world = player.location.world ?: return
        val result = player.rayTraceBlocks(
            plugin.configYml.getDouble("raytrace-distance"),
            FluidCollisionMode.NEVER
        )

        val entityResult = world.rayTraceEntities(
            player.eyeLocation,
            player.eyeLocation.direction, 50.0, 3.0
        ) { entity: Entity? -> entity is LivingEntity }

        location = result?.hitPosition?.toLocation(world)
            ?: if (entityResult != null) {
                entityResult.hitPosition.toLocation(world)
            } else {
                val dir = player.location.direction.normalize()
                    .multiply(plugin.configYml.getDoubleFromExpression("raytrace-distance"))
                player.location.add(dir)
            }

        val victim = entityResult?.hitEntity as? LivingEntity

        preventDoubleTriggers += player.uniqueId

        plugin.scheduler.run {
            preventDoubleTriggers -= player.uniqueId
        }

        this.dispatch(
            player.toDispatcher(),
            TriggerData(
                player = player,
                victim = victim,
                location = location,
                event = event,
                item = player.inventory.itemInMainHand,
                block = event.clickedBlock ?: result?.hitBlock ?: victim?.location?.block
            )
        )
    }
}