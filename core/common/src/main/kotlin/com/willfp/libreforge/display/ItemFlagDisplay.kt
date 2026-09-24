package com.willfp.libreforge.display

import com.willfp.eco.core.display.DisplayModule
import com.willfp.eco.core.display.DisplayPriority
import com.willfp.eco.core.fast.fast
import com.willfp.libreforge.LibreforgeSpigotPlugin
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

class ItemFlagDisplay(
    private val plugin: LibreforgeSpigotPlugin
) : DisplayModule(plugin, DisplayPriority.HIGHEST) {
    private data class DisplayState(
        val enabled: Boolean,
        val flags: Set<ItemFlag>
    )

    @Volatile
    private var state = DisplayState(false, emptySet())

    private val pdcKey = plugin.createNamespacedKey("display_flags")

    init {
        reload()
    }

    internal fun reload() {
        val flags = mutableSetOf<ItemFlag>()

        for (flagName in plugin.configYml.getStrings("display.item-flags")) {
            try {
                flags += ItemFlag.valueOf(flagName.uppercase())
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Invalid item flag for display.item-flags: $flagName")
                plugin.logger.warning("Valid options are: ${ItemFlag.entries.joinToString(", ") { it.name.lowercase() }}")
            }
        }

        state = DisplayState(plugin.configYml.getBool("display.enabled"), flags.toSet())
    }

    override fun display(itemStack: ItemStack, vararg args: Any) {
        val current = state
        if (!current.enabled) {
            return
        }

        val fis = itemStack.fast()

        val existingFlags = current.flags.filter { fis.hasItemFlag(it) }

        fis.persistentDataContainer.set(
            pdcKey,
            PersistentDataType.STRING,
            current.flags.joinToString(",") { it.name } + "|" +
                    existingFlags.joinToString(",") { it.name }
        )

        fis.addItemFlags(*current.flags.toTypedArray())
    }

    override fun revert(itemStack: ItemStack) {
        val current = state
        if (!current.enabled) {
            return
        }

        val fis = itemStack.fast()

        val stored = fis.persistentDataContainer.get(pdcKey, PersistentDataType.STRING) ?: return

        fis.persistentDataContainer.remove(pdcKey)

        val (appliedNames, existingNames) = if ('|' in stored) {
            stored.substringBefore('|') to stored.substringAfter('|')
        } else {
            // Legacy entries stored only one list and restored that same list.
            stored to stored
        }
        val appliedFlags = appliedNames.split(",")
            .filter { it.isNotEmpty() }
            .mapNotNull { runCatching { ItemFlag.valueOf(it) }.getOrNull() }
        val serverFlags = existingNames.split(",")
            .filter { it.isNotEmpty() }
            .mapNotNull { runCatching { ItemFlag.valueOf(it) }.getOrNull() }

        fis.removeItemFlags(*appliedFlags.toTypedArray())
        fis.addItemFlags(*serverFlags.toTypedArray())
    }
}
