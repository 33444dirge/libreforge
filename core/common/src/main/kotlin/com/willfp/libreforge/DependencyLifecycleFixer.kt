package com.willfp.libreforge

import com.willfp.eco.core.data.ExternalDataStore
import com.willfp.eco.core.version.Version
import com.willfp.libreforge.conditions.Conditions
import com.willfp.libreforge.effects.Effects
import com.willfp.libreforge.effects.arguments.EffectArguments
import com.willfp.libreforge.effects.impl.animations.Animations
import com.willfp.libreforge.effects.impl.aoe.AOEShapes
import com.willfp.libreforge.effects.impl.particles.ParticleAnimations
import com.willfp.libreforge.filters.Filters
import com.willfp.libreforge.mutators.Mutators
import com.willfp.libreforge.slot.SlotTypes
import com.willfp.libreforge.triggers.Triggers
import com.willfp.libreforge.triggers.placeholders.TriggerPlaceholders
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent

private const val HIGHEST_VERSION_KEY = "highest-libreforge-version"
private const val HIGHEST_VERSION_CLASSLOADER_KEY = "highest-libreforge-version-classloader"

/**
 * Libreforge is a shared registry host. Remove entries owned by a dependent
 * plugin as that plugin is disabled so its class loader is not retained.
 */
object DependencyLifecycleFixer : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin === plugin) {
            return
        }

        val classLoader = event.plugin.javaClass.classLoader

        Effects.values().filter { it.javaClass.classLoader === classLoader }.forEach { Effects.remove(it) }
        Conditions.values().filter { it.javaClass.classLoader === classLoader }.forEach { Conditions.remove(it) }
        Filters.values().filter { it.javaClass.classLoader === classLoader }.forEach { Filters.remove(it) }
        Mutators.values().filter { it.javaClass.classLoader === classLoader }.forEach { Mutators.remove(it) }
        Triggers.values().filter { it.javaClass.classLoader === classLoader }.forEach { Triggers.remove(it) }
        TriggerPlaceholders.values().filter { it.javaClass.classLoader === classLoader }
            .forEach { TriggerPlaceholders.remove(it) }
        SlotTypes.values().filter { it.javaClass.classLoader === classLoader }.forEach { SlotTypes.remove(it) }
        EffectArguments.values().filter { it.javaClass.classLoader === classLoader }.forEach { EffectArguments.remove(it) }
        AOEShapes.values().filter { it.javaClass.classLoader === classLoader }.forEach { AOEShapes.remove(it) }
        Animations.values().filter { it.javaClass.classLoader === classLoader }.forEach { Animations.remove(it) }
        ParticleAnimations.values().filter { it.javaClass.classLoader === classLoader }
            .forEach { ParticleAnimations.remove(it) }
        Plugins.values().filter { it.javaClass.classLoader === classLoader }.forEach { Plugins.remove(it) }
        unregisterHolderProvidersOwnedBy(classLoader)

        val selectedLoader = ExternalDataStore.get(HIGHEST_VERSION_CLASSLOADER_KEY, ClassLoader::class.java)
        if (selectedLoader === classLoader) {
            ExternalDataStore.put(HIGHEST_VERSION_CLASSLOADER_KEY, plugin.javaClass.classLoader)
            ExternalDataStore.put(HIGHEST_VERSION_KEY, Version(plugin.description.version))
        }
    }
}
