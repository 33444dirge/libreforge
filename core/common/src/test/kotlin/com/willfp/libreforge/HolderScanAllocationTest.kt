package com.willfp.libreforge

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.event.Event
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.util.UUID
import kotlin.random.Random

@Suppress("DEPRECATION")
class HolderScanAllocationTest {
    @Test
    fun `scan preserves original membership events duplicate identity order and replacement payload`() {
        mockkStatic("com.willfp.libreforge.LibreforgeSpigotPluginKt")
        every { plugin } returns mockk<LibreforgeSpigotPlugin>(relaxed = true)
        mockkStatic(Bukkit::class)
        val manager = mockk<PluginManager>()
        every { Bukkit.getPluginManager() } returns manager
        val events = mutableListOf<Event>()
        every { manager.callEvent(any()) } answers { events += firstArg<Event>() }
        val dispatcher = mockk<Dispatcher<Any>>()
        every { dispatcher.uuid } returns UUID.randomUUID()
        var supplied: List<ProvidedHolder> = emptyList()
        var scans = 0
        val provider = object : HolderProvider {
            override fun provide(dispatcher: Dispatcher<*>) = supplied.also { scans++ }
        }
        fun holder(id: Int): ProvidedHolder {
            val value = mockk<Holder>()
            every { value.id } returns NamespacedKey("test", "holder_$id")
            return object : ProvidedHolder {
                override val holder = value
                override val provider = Any()
            }
        }
        val random = Random(20261015)
        val cases = listOf(emptyList(), emptyList(), listOf(2, 1, 2), listOf(2, 1, 2),
            listOf(3, 2, 3), emptyList(), listOf(1), emptyList()) +
            List(500) { List(random.nextInt(0, 25)) { random.nextInt(0, 12) } }
        registerHolderProvider(provider)
        try {
            var old: Collection<ProvidedHolder> = emptyList()
            for ((index, ids) in cases.withIndex()) {
                supplied = ids.map(::holder)
                // The exact previous algorithm is the oracle, including duplicate-ID behavior.
                val newByID = supplied.associateBy { it.holder.id }
                val oldByID = old.associateBy { it.holder.id }
                val added = newByID.keys - oldByID.keys
                val removed = oldByID.keys - newByID.keys
                events.clear()
                val result = dispatcher.forceCalculateHolders()
                assertEquals(index + 1, scans, "must still call provider on every forced scan")
                assertEquals(supplied.size, result.size)
                result.zip(supplied).forEach { (a, b) -> assertSame(b, a) }
                assertSame(result, dispatcher.holders, "cache must publish the fresh payload even when IDs are unchanged")
                assertEquals(added.size + removed.size + if (added.isNotEmpty() || removed.isNotEmpty()) 1 else 0, events.size)
                var cursor = 0
                for (id in added) {
                    val event = events[cursor++] as HolderEnableEvent
                    assertSame(newByID[id], event.holder)
                    assertSame(result, event.newHolders)
                }
                for (id in removed) {
                    val event = events[cursor++] as HolderDisableEvent
                    assertSame(oldByID[id], event.holder)
                    assertSame(old, event.previousHolders)
                }
                if (cursor < events.size) assertSame(result, (events[cursor] as HolderProvideEvent).holders)
                old = result
            }
        } finally {
            unregisterHolderProvider(provider)
            dispatcher.purgePreviousHolders()
            dispatcher.updateHolders()
            unmockkAll()
        }
    }
}
