package com.willfp.libreforge.effects.arguments.custom

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.registry.Registry
import com.willfp.libreforge.configs.category.IdentifiedConfig
import com.willfp.libreforge.configs.category.NativeConfigCategory

object CustomEffectArguments : NativeConfigCategory("arguments") {
    @Volatile
    private var registry = Registry<CustomEffectArgument>()

    override fun clear(plugin: EcoPlugin) {
        registry = Registry()
    }

    override fun acceptConfig(plugin: EcoPlugin, id: String, config: Config) {
        registry.register(CustomEffectArgument(id, config, plugin))
    }

    override fun replaceConfigs(plugin: EcoPlugin, configs: Collection<IdentifiedConfig>) {
        val replacement = Registry<CustomEffectArgument>()
        for ((config, id) in configs) {
            replacement.register(CustomEffectArgument(id, config, plugin))
        }
        registry = replacement
    }

    operator fun get(id: String): CustomEffectArgument? {
        return registry[id]
    }
}
