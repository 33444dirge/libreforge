package com.willfp.libreforge.triggers.impl

import com.willfp.eco.util.BlockUtils
import com.willfp.libreforge.LibreforgeSpigotPlugin
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import io.papermc.paper.threadedregions.scheduler.RegionScheduler
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.function.Consumer

class BlockDropSnapshotTest {
    private val region = mockk<RegionScheduler>()
    private val owner = mockk<LibreforgeSpigotPlugin>(relaxed = true)
    private val world = mockk<World>()
    private val block = mockk<Block>()
    private val player = mockk<Player>()
    private val location = Location(world, -17.0, 65.0, 33.0)
    private val expirations = mutableListOf<Consumer<ScheduledTask>>()
    private val snapshots: MutableMap<*, *>
        get() = TriggerBlockItemDrop.javaClass.getDeclaredField("pendingSources").let {
            it.isAccessible = true
            it.get(TriggerBlockItemDrop) as MutableMap<*, *>
        }

    @BeforeEach
    fun setUp() {
        mockkStatic(Bukkit::class, BlockUtils::class)
        Class.forName("com.willfp.libreforge.LibreforgeSpigotPluginKt").getDeclaredField("plugin").apply {
            isAccessible = true
            set(null, owner)
        }
        every { Bukkit.getRegionScheduler() } returns region
        every { world.uid } returns UUID.randomUUID()
        every { block.world } returns world
        every { block.x } returns -17
        every { block.y } returns 65
        every { block.z } returns 33
        every { block.location } returns location
        every { block.type } returns Material.STONE
        every { player.uniqueId } returns UUID.randomUUID()
        every { BlockUtils.isPlayerPlaced(block) } returns false
        every { region.runDelayed(owner, location, any(), 1L) } answers {
            expirations += arg<Consumer<ScheduledTask>>(2)
            mockk()
        }
        snapshots.clear()
    }

    @AfterEach
    fun tearDown() {
        snapshots.clear()
        unmockkAll()
    }

    private fun capture() = TriggerBlockItemDrop.captureSource(BlockBreakEvent(block, player))

    @Test
    fun `expiry is bound to original block region and cannot run on a global tick`() {
        capture()
        verify(exactly = 1) { region.runDelayed(owner, location, any(), 1L) }
        verify(exactly = 0) { Bukkit.getGlobalRegionScheduler() }
        verify(exactly = 0) { owner.scheduler }
        // No region tick has elapsed: the source survives for the current drop event.
        assertEquals(1, snapshots.size)
        expirations.single().accept(mockk())
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun `late cancelled or no-drop breaks expire and do not leak sources`() {
        val event = BlockBreakEvent(block, player)
        TriggerBlockItemDrop.captureSource(event)
        event.isCancelled = true
        expirations.single().accept(mockk())
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun `old expiry does not remove newer break at identical coordinates`() {
        capture()
        capture()
        assertEquals(1, snapshots.size)
        expirations.first().accept(mockk())
        assertEquals(1, snapshots.size)
        expirations.last().accept(mockk())
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun `drop event consumes source even when creative mode rejects effects`() {
        capture()
        every { player.gameMode } returns GameMode.CREATIVE
        TriggerBlockItemDrop.handle(BlockDropItemEvent(block, mockk<BlockState>(), player, mutableListOf()))
        assertTrue(snapshots.isEmpty())
        expirations.single().accept(mockk())
        assertTrue(snapshots.isEmpty())
    }

    @Test
    fun `snapshot retains both classifications after tracker changes`() {
        for (placed in listOf(false, true)) {
            every { BlockUtils.isPlayerPlaced(block) } returns placed
            capture()
            every { BlockUtils.isPlayerPlaced(block) } returns !placed
            val source = snapshots.values.single()!!
            val placedField = source.javaClass.getDeclaredField("placed").apply { isAccessible = true }
            assertEquals(placed, placedField.getBoolean(source))
            expirations.last().accept(mockk())
            assertFalse(snapshots.isNotEmpty())
        }
    }
}
