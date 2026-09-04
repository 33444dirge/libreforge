package com.willfp.libreforge.effects.templates

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.eco.util.runExempted
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.plugin
import com.willfp.libreforge.triggers.TriggerData
import com.willfp.libreforge.triggers.TriggerParameter
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

abstract class MineBlockEffect<T : Any>(id: String) : Effect<T>(id) {
    private val ignoreKey = "blockbreakevent-ignore"
    private val breakingPlayers = ConcurrentHashMap.newKeySet<UUID>()

    override val parameters = setOf(
        TriggerParameter.PLAYER
    )

    override fun shouldTrigger(config: Config, data: TriggerData, compileData: T): Boolean {
        val block = data.block ?: data.location?.block ?: return false
        return !block.hasMetadata(ignoreKey)
    }

    protected fun Player.breakBlocksSafely(blocks: Collection<Block>, preventTriggers: Boolean = false) {
        if (!breakingPlayers.add(this.uniqueId)) {
            return
        }

        try {
            if (plugin.configYml.getBool("effects.use-setblock-break")) {
                blocks.forEach { it.type = Material.AIR }
            } else if (preventTriggers) {
                blocks.forEach { it.breakNaturally() }
            } else {
                this.runExempted {
                    for (block in blocks) {
                        if (block.world != this.world) {
                            continue
                        }

                        block.setMetadata(ignoreKey, plugin.createMetadataValue(true))
                        try {
                            this.breakBlock(block)
                        } finally {
                            block.removeMetadata(ignoreKey, plugin)
                        }
                    }
                }
            }
        } finally {
            breakingPlayers.remove(this.uniqueId)
        }
    }
}
