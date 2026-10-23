package com.willfp.libreforge.slot

import com.willfp.libreforge.Holder
import com.willfp.libreforge.ItemProvidedHolder
import com.willfp.libreforge.TypedProvidedHolder
import org.bukkit.inventory.ItemStack

class SlotItemProvidedHolder<T: Holder> internal constructor(
    override val holder: T,
    provider: ItemStack,
    val slotType: SlotType,
    providerHash: Int
): ItemProvidedHolder(
    holder,
    provider,
    providerHash
), TypedProvidedHolder<T> {
    constructor(holder: T, provider: ItemStack, slotType: SlotType) :
            this(holder, provider, slotType, provider.hashCode())
}
