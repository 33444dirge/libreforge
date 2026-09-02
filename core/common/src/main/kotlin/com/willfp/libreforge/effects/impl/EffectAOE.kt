package com.willfp.libreforge.effects.impl

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.ArgType
import com.willfp.libreforge.ViolationContext
import com.willfp.libreforge.arguments
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.effects.impl.aoe.AOECompileData
import com.willfp.libreforge.effects.impl.aoe.AOEShapes
import com.willfp.libreforge.get
import com.willfp.libreforge.plugin
import com.willfp.libreforge.SchedulerHelper
import com.willfp.libreforge.toFloat3
import com.willfp.libreforge.triggers.TriggerData
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity

object EffectAOE : Effect<AOECompileData>("aoe") {
    override val description = "Runs a set of effects on all nearby entities within an AOE shape."
    override val categories = setOf("meta")

    override val isPermanent = false

    override val arguments = arguments {
        require(
            "effects",
            "You must specify the effects!",
            description = "The effects to run on each entity within the AOE.",
            type = ArgType.EFFECT_LIST
        )
        require("shape", "You must specify a valid shape!", Config::getString) {
            AOEShapes[it] != null
        }
        describe(
            "shape",
            description = "The AOE shape to use.",
            type = ArgType.STRING,
            choices = listOf("circle", "cone", "offset_circle", "scan_in_front", "beam")
        )
        inherit { AOEShapes[it.getString("shape")] }
        describeInherit("Configuration for the selected AOE shape.")
    }

    override fun onTrigger(config: Config, data: TriggerData, compileData: AOECompileData): Boolean {
        val location = data.location?.clone()
            ?: data.dispatcher.location?.clone()
            ?: return false
        val dispatcherLocation = data.dispatcher.location?.clone()
        val dispatcherEyeHeight = data.dispatcher.get<LivingEntity>()?.eyeHeight ?: 0.0

        // The source entity may be in another world/Region. Nearby-entity queries must run on the
        // Region owning the query location, never on the source entity's scheduler thread.
        if (SchedulerHelper.isFolia && !Bukkit.isOwnedByCurrentRegion(location)) {
            SchedulerHelper.runTask(plugin, location) {
                onTrigger(config, data.copy(location = location), compileData)
            }
            return true
        }

        if (dispatcherLocation != null) {
            if (location.world == dispatcherLocation.world
                && location.distanceSquared(dispatcherLocation) <= 1.0
            ) {
                location.add(0.0, dispatcherEyeHeight, 0.0)
                location.direction = dispatcherLocation.direction
            }
        }

        val shape = compileData.shape ?: return false

        for (entity in shape.getEntities(
            location.toFloat3(),
            location.direction.toFloat3(),
            location.world,
            data
        ).filterNot { it.uniqueId == data.dispatcher.uuid }) {
            compileData.chain
                ?.trigger(
                    data.copy(
                        victim = entity,
                        location = entity.location
                    ).dispatch(data.dispatcher)
                )
        }

        return true
    }

    override fun makeCompileData(config: Config, context: ViolationContext): AOECompileData {
        return AOECompileData(
            AOEShapes.compile(config, context),
            Effects.compileRichChain(
                config,
                context.with("aoe effects")
            )
        )
    }
}
