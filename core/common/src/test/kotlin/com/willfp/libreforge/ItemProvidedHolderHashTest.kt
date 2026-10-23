package com.willfp.libreforge

import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.EffectList
import com.willfp.libreforge.slot.ItemHolderFinder
import com.willfp.libreforge.slot.SlotItemProvidedHolder
import com.willfp.libreforge.slot.SlotType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ItemProvidedHolderHashTest {
    private class CountingItem : ItemStack() {
        var calls = 0
        var value = 1234567
        override fun getType() = Material.DIAMOND_SWORD
        override fun hashCode(): Int { calls++; return value }
        override fun equals(other: Any?) = this === other
    }

    private class H(private val number: Int) : Holder {
        override val id = NamespacedKey("hash_test", "holder_$number")
        override val effects = EffectList(emptyList())
        override val conditions = ConditionList(emptyList())
        override fun hashCode() = number
    }

    @AfterEach
    fun cleanup() = unmockkAll()

    @Test
    fun `equal base and slotted holders are interchangeable map keys`() {
        val holder = H(17)
        val item = CountingItem()
        val plain = ItemProvidedHolder(holder, item)
        val slotted = SlotItemProvidedHolder(holder, item, mockk<SlotType>())
        assertEquals(plain, slotted)
        assertEquals(slotted, plain)
        assertEquals(1236055, plain.hashCode())
        assertEquals(plain.hashCode(), slotted.hashCode())
        assertEquals("found", hashMapOf<ProvidedHolder, String>(plain to "found")[slotted])
        assertEquals("found", hashMapOf<ProvidedHolder, String>(slotted to "found")[plain])
    }

    @Test
    fun `different holders on one item retain their own hash contribution`() {
        val item = CountingItem()
        val slot = mockk<SlotType>()
        assertEquals(20, (1..20).map { SlotItemProvidedHolder(H(it), item, slot).hashCode() }.toSet().size)
    }

    @Test
    fun `one scan hashes each accepted item once and next scan observes changes`() {
        mockkStatic("com.willfp.libreforge.LibreforgeSpigotPluginKt")
        every { plugin } returns mockk<LibreforgeSpigotPlugin>(relaxed = true)
        mockkStatic("com.willfp.libreforge.HolderProviderKt")
        every { registerRefreshFunctionOwnedBy(any(), any()) } returns Unit
        val item = CountingItem()
        val entity = mockk<LivingEntity>()
        val slot = mockk<SlotType>()
        val holders = (1..20).map { H(it) }
        var available = holders
        var valid = true
        val finder = object : ItemHolderFinder<H>() {
            override fun find(item: ItemStack) = available
            override fun isValidInSlot(holder: H, slot: SlotType) = valid
        }
        every { slot.getItems(entity) } returns listOf(item)
        val first = finder.findHolders(entity, slot)
        assertEquals(20, first.size)
        assertEquals(1, item.calls)
        first.forEachIndexed { i, h ->
            assertSame(holders[i], h.holder)
            assertSame(item, h.provider)
            assertEquals(1235528 + 31 * (i + 1), h.hashCode())
        }
        item.value = 55
        val second = finder.findHolders(entity, slot)
        assertEquals(2, item.calls)
        assertEquals(1047, second.first().hashCode())
        valid = false
        assertTrue(finder.findHolders(entity, slot).isEmpty())
        assertEquals(2, item.calls)
        available = holders.take(1)
        valid = true
        assertEquals(1047, finder.findHolders(entity, slot).single().hashCode())
        assertEquals(3, item.calls)
        valid = false
        assertTrue(finder.findHolders(entity, slot).isEmpty())
        available = emptyList()
        assertTrue(finder.findHolders(entity, slot).isEmpty())
        assertEquals(3, item.calls)
    }

    @Test
    fun `validation updates are visible before the shared hash is captured`() {
        mockkStatic("com.willfp.libreforge.LibreforgeSpigotPluginKt")
        every { plugin } returns mockk<LibreforgeSpigotPlugin>(relaxed = true)
        mockkStatic("com.willfp.libreforge.HolderProviderKt")
        every { registerRefreshFunctionOwnedBy(any(), any()) } returns Unit
        val item = CountingItem()
        val entity = mockk<LivingEntity>()
        val slot = mockk<SlotType>()
        val holders = listOf(H(1), H(2))
        val visited = mutableListOf<Holder>()
        val finder = object : ItemHolderFinder<H>() {
            override fun find(item: ItemStack) = holders
            override fun isValidInSlot(holder: H, slot: SlotType): Boolean {
                visited.add(holder)
                if (holder === holders.last()) item.value = 55
                return true
            }
        }
        every { slot.getItems(entity) } returns listOf(item)
        val actual = finder.findHolders(entity, slot)
        assertEquals(holders, visited)
        assertEquals(listOf(1047, 1078), actual.map { it.hashCode() })
        val plain = ItemProvidedHolder(holders.first(), item)
        assertEquals("found", hashMapOf<ProvidedHolder, String>(actual.first() to "found")[plain])
    }

    @Test
    fun `slot is not an equality component and item occurrences do not share a global hash`() {
        val h = H(17)
        val item = CountingItem()
        val first = SlotItemProvidedHolder(h, item, mockk<SlotType>())
        val second = SlotItemProvidedHolder(h, item, mockk<SlotType>())
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        item.value = 55
        val changed = SlotItemProvidedHolder(h, item, mockk<SlotType>())
        assertEquals(1543, changed.hashCode())
        assertEquals(1236055, first.hashCode()) // Existing per-wrapper snapshot semantics retained.
    }
}
