package com.willfp.libreforge

import com.willfp.eco.core.config.interfaces.Config
import com.willfp.libreforge.effects.impl.EffectDamageArmor
import com.willfp.libreforge.effects.impl.EffectDamageMainhand
import com.willfp.libreforge.effects.templates.DamageItemEffect
import com.willfp.libreforge.filters.impl.FilterItemDurabilityAbove
import com.willfp.libreforge.filters.impl.FilterItemDurabilityAbovePercent
import com.willfp.libreforge.filters.impl.FilterItemDurabilityBelow
import com.willfp.libreforge.filters.impl.FilterItemDurabilityBelowPercent
import com.willfp.libreforge.triggers.TriggerData
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.EntityEquipment
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Tests actual damage helpers, effect event gates and durability filters without a server. */
class DurabilityComponentsTest {
    private val pluginManager = mockk<PluginManager>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(Bukkit::class)
        mockkStatic("com.willfp.libreforge.SeparatorAmbivalentConfigKt")
        every { Bukkit.getPluginManager() } returns pluginManager
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    private fun damage(fixture: Fixture, armor: Boolean, amount: Int = 1) {
        if (armor) {
            EffectDamageArmor.javaClass.getDeclaredMethod(
                "applyDamage", ItemStack::class.java, Int::class.javaPrimitiveType, Player::class.java
            ).apply { isAccessible = true }.invoke(EffectDamageArmor, fixture.item, amount, null)
        } else {
            assertTrue(fixture.item.applyDamage(amount, null))
        }
    }

    @Test
    fun `custom weapons survive both the audited and API vanilla boundaries`() {
        for (armor in listOf(false, true)) {
            for (initialDamage in listOf(2030, Material.NETHERITE_SWORD.maxDurability - 1)) {
                val fixture = Fixture(Material.NETHERITE_SWORD, 2438, initialDamage)
                damage(fixture, armor)
                assertEquals(Material.NETHERITE_SWORD, fixture.type)
                assertEquals(initialDamage + 1, fixture.damage)
                assertEquals(2438, fixture.meta.maxDamage)
            }
        }
    }

    @Test
    fun `all custom armor pieces survive their vanilla boundary`() {
        val materials = listOf(Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS)
        for (armor in listOf(false, true)) {
            for (material in materials) {
                val fixture = Fixture(material, 710, material.maxDurability - 1)
                damage(fixture, armor)
                assertEquals(material, fixture.type)
                assertEquals(material.maxDurability.toInt(), fixture.damage)
                assertEquals(710, fixture.meta.maxDamage)
            }
        }
    }

    @Test
    fun `break thresholds and damage caps use custom limits in both helpers`() {
        for (armor in listOf(false, true)) {
            for ((material, maximum) in listOf(Material.NETHERITE_SWORD to 2438,
                Material.NETHERITE_HELMET to 710, Material.NETHERITE_SWORD to 100)) {
                for (amount in listOf(1, 5)) {
                    val fixture = Fixture(material, maximum, maximum - 1)
                    damage(fixture, armor, amount)
                    assertEquals(Material.AIR, fixture.type)
                    assertEquals(maximum, fixture.damage)
                    verify(exactly = 0) { fixture.meta.setMaxDamage(any()) }
                }
            }
        }
    }

    @Test
    fun `items without components retain vanilla break thresholds`() {
        for (armor in listOf(false, true)) {
            val material = Material.NETHERITE_SWORD
            val before = Fixture(material, null, material.maxDurability - 2)
            damage(before, armor)
            assertEquals(material, before.type)
            assertEquals(material.maxDurability - 1, before.damage)
            val atLimit = Fixture(material, null, material.maxDurability - 1)
            damage(atLimit, armor)
            assertEquals(Material.AIR, atLimit.type)
            assertEquals(material.maxDurability.toInt(), atLimit.damage)
            verify(exactly = 0) { atLimit.meta.maxDamage }
        }
    }

    @Test
    fun `a component supplies durability even when the base material has none`() {
        for (armor in listOf(false, true)) {
            val fixture = Fixture(Material.STICK, 100, 10)
            damage(fixture, armor)
            assertEquals(Material.STICK, fixture.type)
            assertEquals(11, fixture.damage)
        }
    }

    @Test
    fun `non damageable metadata remains untouched`() {
        for (armor in listOf(false, true)) {
            val fixture = Fixture(Material.STONE, null, 0)
            every { fixture.item.itemMeta } returns mockk<ItemMeta>()
            if (armor) damage(fixture, true) else assertFalse(fixture.item.applyDamage(1, null))
            assertEquals(Material.STONE, fixture.type)
            assertEquals(0, fixture.damage)
        }
    }

    private fun trigger(fixture: Fixture, armor: Boolean, slots: List<String> = emptyList()) {
        every { fixture.config.getIntFromExpression("damage", fixture.data) } returns 1
        every { fixture.config.getStrings("slots") } returns slots
        val effect = if (armor) EffectDamageArmor else EffectDamageMainhand
        val owner = if (armor) EffectDamageArmor.javaClass else DamageItemEffect::class.java
        owner.getDeclaredMethod("onTrigger", Config::class.java, TriggerData::class.java,
            NoCompileData::class.java).apply { isAccessible = true }
            .invoke(effect, fixture.config, fixture.data, NoCompileData)
    }

    @Test
    fun `cancelled weapon and armor damage events prevent damage and breakage`() {
        every { pluginManager.callEvent(any()) } answers {
            (firstArg<Event>() as? PlayerItemDamageEvent)?.isCancelled = true
        }
        for (armor in listOf(false, true)) {
            for (slots in listOf(emptyList(), listOf("HEAD"))) {
                val fixture = Fixture(Material.NETHERITE_HELMET, 710, 709)
                trigger(fixture, armor, slots)
                assertEquals(Material.NETHERITE_HELMET, fixture.type)
                assertEquals(709, fixture.damage)
                verify(exactly = 0) { fixture.item.setItemMeta(any()) }
            }
        }
        verify(exactly = 0) { pluginManager.callEvent(ofType<PlayerItemBreakEvent>()) }
    }

    @Test
    fun `event supplied damage is honored on weapon and both armor slot paths`() {
        every { pluginManager.callEvent(any()) } answers {
            (firstArg<Event>() as? PlayerItemDamageEvent)?.damage = 2
        }
        for (armor in listOf(false, true)) {
            for (slots in listOf(emptyList(), listOf("HEAD"))) {
                val fixture = Fixture(Material.NETHERITE_HELMET, 710, 500)
                trigger(fixture, armor, slots)
                assertEquals(Material.NETHERITE_HELMET, fixture.type)
                assertEquals(502, fixture.damage)
            }
        }
        verify(exactly = 0) { pluginManager.callEvent(ofType<PlayerItemBreakEvent>()) }
    }

    @Test
    fun `unbreakable items keep existing weapon and all armor slot protection`() {
        for (armor in listOf(false, true)) {
            val fixture = Fixture(Material.NETHERITE_HELMET, 710, 709)
            every { fixture.meta.isUnbreakable } returns true
            trigger(fixture, armor)
            assertEquals(Material.NETHERITE_HELMET, fixture.type)
            assertEquals(709, fixture.damage)
        }
        verify(exactly = 0) { pluginManager.callEvent(any()) }
    }

    private fun filter(filter: Any, fixture: Fixture, value: Number): Boolean {
        val valueType = if (value is Int) Int::class.javaPrimitiveType else Double::class.javaPrimitiveType
        return filter.javaClass.getDeclaredMethod("isMet", TriggerData::class.java, valueType,
            NoCompileData::class.java).apply { isAccessible = true }
            .invoke(filter, fixture.data, value, NoCompileData) as Boolean
    }

    @Test
    fun `absolute durability filters use remaining component durability`() {
        val fixture = Fixture(Material.NETHERITE_SWORD, 2438, 2031)
        assertTrue(filter(FilterItemDurabilityAbove, fixture, 407))
        assertFalse(filter(FilterItemDurabilityAbove, fixture, 408))
        assertTrue(filter(FilterItemDurabilityBelow, fixture, 407))
        assertFalse(filter(FilterItemDurabilityBelow, fixture, 406))
    }

    @Test
    fun `percentage filters use components and retain fractional durability`() {
        val fixture = Fixture(Material.NETHERITE_SWORD, 2438, 2031)
        assertTrue(filter(FilterItemDurabilityAbovePercent, fixture, 16.0))
        assertFalse(filter(FilterItemDurabilityAbovePercent, fixture, 17.0))
        assertTrue(filter(FilterItemDurabilityBelowPercent, fixture, 17.0))
        assertFalse(filter(FilterItemDurabilityBelowPercent, fixture, 16.0))
        val lowLimit = Fixture(Material.NETHERITE_SWORD, 100, 75)
        assertTrue(filter(FilterItemDurabilityAbovePercent, lowLimit, 25.0))
        assertTrue(filter(FilterItemDurabilityBelowPercent, lowLimit, 25.0))
        assertFalse(filter(FilterItemDurabilityAbovePercent, lowLimit, 26.0))
        assertFalse(filter(FilterItemDurabilityBelowPercent, lowLimit, 24.0))
    }

    @Test
    fun `filters fall back to vanilla values when components are absent`() {
        val fixture = Fixture(Material.NETHERITE_SWORD, null, 1000)
        val remaining = fixture.type.maxDurability - fixture.damage
        assertTrue(filter(FilterItemDurabilityAbove, fixture, remaining))
        assertTrue(filter(FilterItemDurabilityBelow, fixture, remaining))
        assertTrue(filter(FilterItemDurabilityAbovePercent, fixture, 50.0))
        assertFalse(filter(FilterItemDurabilityBelowPercent, fixture, 50.0))
    }

    @Test
    fun `percentage filters do not divide by zero for non durable materials`() {
        val fixture = Fixture(Material.STICK, null, 0)
        assertTrue(filter(FilterItemDurabilityAbovePercent, fixture, 50.0))
        assertTrue(filter(FilterItemDurabilityBelowPercent, fixture, 50.0))
    }

    private class Fixture(material: Material, maximum: Int?, initialDamage: Int) {
        val item = mockk<ItemStack>(relaxed = true)
        val meta = mockk<Damageable>(relaxed = true)
        val player = mockk<Player>(relaxed = true)
        val equipment = mockk<EntityEquipment>()
        val config = mockk<Config>()
        val data = mockk<TriggerData>()
        var type = material
        var damage = initialDamage

        init {
            every { item.type } answers { type }
            every { item.type = any() } answers { type = firstArg() }
            every { item.itemMeta } returns meta
            every { meta.hasMaxDamage() } returns (maximum != null)
            if (maximum != null) every { meta.maxDamage } returns maximum
            every { meta.damage } answers { damage }
            every { meta.damage = any() } answers { damage = firstArg() }
            every { meta.isUnbreakable } returns false
            every { player.equipment } returns equipment
            every { equipment.itemInMainHand } returns item
            every { equipment.armorContents } returns arrayOf(item)
            every { equipment.getItem(any<EquipmentSlot>()) } returns item
            every { data.victim } returns player
            every { data.foundItem } returns item
        }
    }
}
