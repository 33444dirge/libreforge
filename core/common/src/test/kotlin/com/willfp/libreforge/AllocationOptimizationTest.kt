package com.willfp.libreforge

import com.willfp.eco.util.NamespacedKeyUtils
import com.willfp.libreforge.effects.IdentifierFactory
import com.willfp.libreforge.effects.Identifiers
import com.willfp.libreforge.effects.Effect
import com.willfp.libreforge.slot.ItemHolderFinder
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
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.abs

class AllocationOptimizationTest {
    @AfterEach
    fun tearDown() = unmockkAll()

    private fun keyFactory() {
        mockkStatic(NamespacedKeyUtils::class)
        every { NamespacedKeyUtils.createEcoKey(any()) } answers { NamespacedKey("eco", firstArg<String>()) }
    }

    private fun expected(base: UUID, offset: Int) = Identifiers(
        UUID.nameUUIDFromBytes("$base$offset".toByteArray()),
        NamespacedKey("eco", "${abs(base.hashCode())}_$offset")
    )

    private fun cached(effect: Effect<*>, offset: Int): Identifiers =
        Effect::class.java.getDeclaredMethod("identifiersFor", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(effect, offset) as Identifiers

    @Test
    fun `cached and fallback offsets keep the exact previous identifiers`() {
        keyFactory()
        val bases = listOf(UUID(0, 0), UUID(-1, -1), UUID(0, Int.MIN_VALUE.toLong()), UUID.randomUUID())
        for (base in bases) {
            val factory = IdentifierFactory(base)
            for (offset in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 63, 64, 65, 1024, Int.MAX_VALUE)) {
                repeat(3) { assertEquals(expected(base, offset), factory.makeIdentifiers(offset)) }
            }
            assertNotSame(factory.makeIdentifiers(1), factory.makeIdentifiers(1))
            assertEquals(IdentifierFactory(base), factory)
            assertEquals(base.hashCode(), factory.hashCode())
            val child = factory.makeIdentifiers(2).makeFactory()
            assertEquals(expected(expected(base, 2).uuid, 3), child.makeIdentifiers(3))
        }
        val effect = object : Effect<Unit>("allocation_test") {}
        val base = UUID.nameUUIDFromBytes(effect.id.toByteArray())
        for (offset in listOf(Int.MIN_VALUE, -1, 0, 1, 2, 63, 64, 65, 1024, Int.MAX_VALUE)) {
            repeat(3) { assertEquals(expected(base, offset), cached(effect, offset)) }
        }
        assertSame(cached(effect, 1), cached(effect, 1))
        assertSame(cached(effect, 64), cached(effect, 64))
        assertNotSame(cached(effect, 65), cached(effect, 65))
    }

    @Test
    fun `parallel callers publish identical immutable identifiers without unbounded retention`() {
        keyFactory()
        val effect = object : Effect<Unit>("allocation_concurrency") {}
        val base = UUID.nameUUIDFromBytes(effect.id.toByteArray())
        val expected = (1..64).map { expected(base, it) }
        val executor = Executors.newFixedThreadPool(8)
        try {
            val jobs = (0 until 8).map { worker -> Callable {
                repeat(20_000) { i ->
                    val offset = (i + worker) % 64 + 1
                    assertEquals(expected[offset - 1], cached(effect, offset))
                }
            } }
            executor.invokeAll(jobs).forEach { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
        for (offset in 65..2048) assertEquals(expected(base, offset), cached(effect, offset))
        val field = Effect::class.java.getDeclaredField("identifiers").apply { isAccessible = true }
        val cache = field.get(effect) as AtomicReferenceArray<*>
        assertEquals(64, cache.length())
        assertEquals(64, (0 until cache.length()).count { cache.get(it) != null })
    }

    @Test
    fun `empty slots skip finders but real items preserve identity slot validity and ordering`() {
        // Registration is mocked only to keep this focused test independent of a running plugin.
        mockkStatic("com.willfp.libreforge.LibreforgeSpigotPluginKt")
        every { plugin } returns mockk<LibreforgeSpigotPlugin>(relaxed = true)
        mockkStatic("com.willfp.libreforge.HolderProviderKt")
        every { registerRefreshFunctionOwnedBy(any(), any()) } returns Unit
        val entity = mockk<LivingEntity>()
        val slot = mockk<SlotType>()
        fun item(material: Material) = mockk<ItemStack>(relaxed = true).also { every { it.type } returns material }
        val air = item(Material.AIR)
        val caveAir = item(Material.CAVE_AIR)
        val voidAir = item(Material.VOID_AIR)
        val stone = item(Material.STONE)
        val sword = item(Material.DIAMOND_SWORD)
        val holder = mockk<Holder>()
        val visited = mutableListOf<ItemStack>()
        var valid = true
        val finder = object : ItemHolderFinder<Holder>() {
            override fun find(item: ItemStack): List<Holder> {
                visited += item
                return listOf(holder)
            }
            override fun isValidInSlot(holder: Holder, slot: SlotType) = valid
        }
        every { slot.getItems(entity) } returns listOf(air, stone, caveAir, sword, voidAir, stone)
        val found = finder.findHolders(entity, slot)
        assertEquals(listOf(stone, sword, stone), visited)
        assertEquals(3, found.size)
        found.forEach { assertSame(holder, it.holder) }
        assertSame(stone, found[0].provider)
        assertSame(sword, found[1].provider)
        visited.clear()
        valid = false
        assertTrue(finder.findHolders(entity, slot).isEmpty())
        assertEquals(listOf(stone, sword, stone), visited)
        every { slot.getItems(entity) } returns listOf(air)
        assertTrue(finder.findHolders(entity, slot).isEmpty())
        every { slot.getItems(entity) } returns listOf(sword)
        valid = true
        assertSame(sword, finder.findHolders(entity, slot).single().provider)
    }
}
