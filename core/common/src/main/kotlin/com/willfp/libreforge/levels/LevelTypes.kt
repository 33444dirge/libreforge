package com.willfp.libreforge.levels

import com.willfp.eco.core.EcoPlugin
import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.core.registry.Registry
import com.willfp.libreforge.configs.category.IdentifiedConfig
import com.willfp.libreforge.configs.category.NativeConfigCategory

object LevelTypes : NativeConfigCategory("levels") {
    @Volatile
    private var registry = Registry<LevelType>()

    override fun clear(plugin: EcoPlugin) {
        registry = Registry()
    }

    override fun acceptConfig(plugin: EcoPlugin, id: String, config: Config) {
        registry.register(LevelType(id, config, plugin))
    }

    override fun replaceConfigs(plugin: EcoPlugin, configs: Collection<IdentifiedConfig>) {
        val replacement = Registry<LevelType>()
        for ((config, id) in configs) {
            replacement.register(LevelType(id, config, plugin))
        }
        registry = replacement
    }

    operator fun get(id: String): LevelType? {
        return registry[id]
    }
}
