package com.willfp.libreforge.slot

import ca.spottedleaf.concurrentutil.map.concurrent.objects.ConcurrentChainedObject2ObjectHashTable
import com.willfp.libreforge.Dispatcher
import com.willfp.libreforge.Holder
import com.willfp.libreforge.HolderProvider
import com.willfp.libreforge.TypedHolderProvider
import com.willfp.libreforge.TypedProvidedHolder
import com.willfp.libreforge.get
import com.willfp.libreforge.registerRefreshFunctionOwnedBy
import com.willfp.libreforge.slot.impl.NumericSlotType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

private const val ITEM_HOLDER_CACHE_CAPACITY = 128
private const val ITEM_HOLDER_CACHE_TTL_NANOS = 500_000_000L

private data class ItemHolderCacheEntry<T : Holder>(
    val expiresAt: Long,
    val holders: List<TypedProvidedHolder<T>>,
)

/**
 * Finds holders on items for entities, allows for easy implementation of [HolderProvider].
 */
abstract class ItemHolderFinder<T : Holder> {
    /**
     * The [HolderProvider] for this finder.
     */
    private val provider: TypedHolderProvider<T> = ItemHolderFinderProvider()

    /**
     * Find holders on an [item].
     */
    abstract fun find(item: ItemStack): List<T>

    /**
     * Check if a given [holder] is valid for a given [slot].
     */
    abstract fun isValidInSlot(holder: T, slot: SlotType): Boolean

    /**
     * Find holders on an [entity] for a given [slot].
     */
    fun findHolders(entity: LivingEntity, slot: SlotType): List<TypedProvidedHolder<T>> {
        val holders = ArrayList<TypedProvidedHolder<T>>()
        appendHolders(entity, slot, holders)
        return holders
    }

    private fun appendHolders(
        entity: LivingEntity,
        slot: SlotType,
        destination: MutableCollection<TypedProvidedHolder<T>>
    ) {
        for (item in slot.getItems(entity)) {
            for (holder in find(item)) {
                if (isValidInSlot(holder, slot)) {
                    destination.add(SlotItemProvidedHolder(holder, item, slot))
                }
            }
        }
    }

    /**
     * Convert this finder to a [HolderProvider].
     */
    fun toHolderProvider(): TypedHolderProvider<T> {
        return provider
    }

    private inner class ItemHolderFinderProvider : TypedHolderProvider<T> {
        private val cache = ConcurrentChainedObject2ObjectHashTable
            .createWithExpected<UUID, ItemHolderCacheEntry<T>>(ITEM_HOLDER_CACHE_CAPACITY)

        init {
            registerRefreshFunctionOwnedBy({
                cache.remove(it.uuid)
            }, this@ItemHolderFinder.javaClass.classLoader)
        }

        override fun provide(dispatcher: Dispatcher<*>): Collection<TypedProvidedHolder<T>> {
            val now = System.nanoTime()
            cache.get(dispatcher.uuid)?.takeIf { it.expiresAt > now }?.let { return it.holders }

            val entity = dispatcher.get<LivingEntity>() ?: return emptyList()
            val heldSlot = (entity as? Player)?.inventory?.heldItemSlot
            val holders = ArrayList<TypedProvidedHolder<T>>()

            // Only check for non-combined slot types
            for (slot in SlotTypes.baseTypes) {
                if (slot is NumericSlotType) {
                    // Numeric inventory slots are empty for non-player entities. A player's
                    // selected numeric slot is already represented by the main-hand slot.
                    if (heldSlot == null || slot.slot == heldSlot) {
                        continue
                    }
                }

                appendHolders(entity, slot, holders)
            }

            // The map is intentionally bounded: all entries naturally expire after 500ms, but a burst of unique
            // dispatchers must never retain an unbounded number of item-holder lists between cleanups.
            if (cache.size() >= ITEM_HOLDER_CACHE_CAPACITY) {
                cache.clear()
            }
            cache.put(dispatcher.uuid, ItemHolderCacheEntry(now + ITEM_HOLDER_CACHE_TTL_NANOS, holders))

            return holders
        }
    }
}
