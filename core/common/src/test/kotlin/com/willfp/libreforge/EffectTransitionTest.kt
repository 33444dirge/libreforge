package com.willfp.libreforge

import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.Chain
import com.willfp.libreforge.effects.ChainElement
import com.willfp.libreforge.effects.EffectBlock
import com.willfp.libreforge.effects.EffectList
import io.mockk.*
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

class EffectTransitionTest {
    private data class Call(val action: String, val effect: EffectBlock, val holder: ProvidedHolder, val reload: Boolean)

    @Test
    fun `empty transitions partial differences duplicates and stable reloads match old callbacks`() {
        mockkStatic("com.willfp.libreforge.LibreforgeSpigotPluginKt")
        every { plugin } returns mockk<LibreforgeSpigotPlugin>(relaxed = true)
        mockkStatic(Bukkit::class)
        every { Bukkit.getPluginManager() } returns mockk<PluginManager>(relaxed = true)
        val dispatcher = mockk<Dispatcher<Any>>()
        every { dispatcher.uuid } returns UUID.randomUUID()
        var supplied: List<ProvidedHolder> = emptyList()
        val provider = object : HolderProvider {
            override fun provide(dispatcher: Dispatcher<*>) = supplied
        }
        val trace = mutableListOf<Call>()
        var expectedPublished: List<ProvidedEffectBlock> = emptyList()
        fun assertIdentities(expected: List<ProvidedEffectBlock>, actual: List<ProvidedEffectBlock>) {
            assertEquals(expected.size, actual.size)
            expected.zip(actual).forEach { (a, b) -> assertSame(a.effect, b.effect); assertSame(a.holder, b.holder) }
        }
        fun block(index: Int): EffectBlock {
            val element = mockk<ChainElement<Any>>()
            every { element.supportsDelay } returns true
            val chain = mockk<Chain>()
            every { chain.weight } returns index % 3 - 1
            every { chain.isEmpty() } returns false
            every { chain.iterator() } answers { listOf(element).iterator() }
            val effect = EffectBlock(UUID(0, (index % 4).toLong()), mockk(), chain, emptySet(),
                mockk(), ConditionList(emptyList()), mockk(), mockk(), false)
            every { element.enable(any(), any(), any()) } answers {
                assertIdentities(expectedPublished, dispatcher.providedActiveEffects)
                trace += Call("enable", effect, secondArg(), thirdArg())
            }
            every { element.disable(any(), any(), any()) } answers {
                assertIdentities(expectedPublished, dispatcher.providedActiveEffects)
                trace += Call("disable", effect, secondArg(), thirdArg())
            }
            return effect
        }
        val effects = List(12, ::block)
        var holderPass = true
        val holderConditions = mockk<ConditionList>()
        every { holderConditions.areMet(any(), any()) } answers { holderPass }
        val random = Random(20261015)
        val cases = listOf(emptyList(), listOf(0, 1), emptyList(), listOf(0, 0, 4),
            listOf(0, 0, 4), listOf(0, 4), listOf(2, 3), emptyList()) +
            List(500) { if (it % 3 == 0) emptyList() else List(random.nextInt(1, 20)) { random.nextInt(12) } }
        registerHolderProvider(provider)
        try {
            var before: List<ProvidedEffectBlock> = emptyList()
            for ((index, ids) in cases.withIndex()) {
                // Holders can stay present while conditions disable every effect,
                // then become valid again without a new provider identity.
                holderPass = index % 5 != 3
                supplied = ids.map { id -> SimpleProvidedHolder(SimpleHolder(
                    NamespacedKey("test", "holder_${id % 2}"), EffectList(listOf(effects[id])), holderConditions)) }
                dispatcher.forceCalculateHolders()
                val after = supplied.getProvidedActiveEffects(dispatcher)
                // Independent oracle of memoryfix-7, including multiset occurrence choice.
                fun difference(a: List<ProvidedEffectBlock>, b: List<ProvidedEffectBlock>): List<ProvidedEffectBlock> {
                    val counts = HashMap<ProvidedEffectBlock, Int>(b.size)
                    b.forEach { counts[it] = (counts[it] ?: 0) + 1 }
                    return a.filter { val n = counts[it] ?: 0; if (n > 0) { counts[it] = n - 1; false } else true }
                }
                val expected = mutableListOf<Call>()
                fun append(action: String, blocks: List<ProvidedEffectBlock>, reload: Boolean) {
                    blocks.forEach { expected += Call(action, it.effect, it.holder, reload) }
                }
                if (before == after) {
                    append("disable", after, true); append("enable", after, true)
                } else {
                    val added = difference(after, before).sorted()
                    val removed = difference(before, after).sorted()
                    append("disable", removed, false); append("enable", added, false)
                    val reload = difference(after, added).sorted()
                    append("disable", reload, true); append("enable", reload, true)
                }
                expectedPublished = after
                trace.clear()
                dispatcher.updateEffects()
                assertEquals(expected.size, trace.size, "step $index")
                expected.zip(trace).forEach { (a, b) ->
                    assertEquals(a.action, b.action); assertEquals(a.reload, b.reload)
                    assertSame(a.effect, b.effect); assertSame(a.holder, b.holder)
                }
                assertIdentities(after, dispatcher.providedActiveEffects)
                before = after
            }
            supplied = emptyList(); dispatcher.forceCalculateHolders(); expectedPublished = emptyList()
            dispatcher.updateEffects()
            assertTrue(dispatcher.providedActiveEffects.isEmpty())
        } finally {
            unregisterHolderProvider(provider)
            dispatcher.purgePreviousHolders(); dispatcher.updateHolders()
            // MockK keeps recorded invocation graphs beyond unmockkAll; release this
            // large differential fixture before the next test class in the same JVM.
            clearAllMocks()
            unmockkAll()
        }
    }
}
