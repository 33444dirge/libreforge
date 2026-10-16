package com.willfp.libreforge

import com.willfp.libreforge.conditions.ConditionList
import com.willfp.libreforge.effects.Chain
import com.willfp.libreforge.effects.ChainElement
import com.willfp.libreforge.effects.EffectBlock
import com.willfp.libreforge.effects.EffectList
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.random.Random

class ActiveEffectSortTest {
    @Test
    fun `filter matches all condition outcomes with duplicate identities`() {
        mockkStatic("com.willfp.libreforge.LibreforgeSpigotPluginKt")
        every { plugin } returns mockk<LibreforgeSpigotPlugin>(relaxed = true)
        try {
            val dispatcher = mockk<Dispatcher<Any>>()
            var mask = 0
            var calls = 0
            val effects = List(8) { index ->
                val chain = mockk<Chain>()
                every { chain.weight } returns 0
                every { chain.isEmpty() } returns true
                every { chain.iterator() } answers { emptyList<ChainElement<*>>().iterator() }
                val conditions = mockk<ConditionList>()
                every { conditions.areMet(any(), any()) } answers { mask and (1 shl calls++) != 0 }
                EffectBlock(UUID(0, (index % 3).toLong()), mockk(), chain, emptySet(),
                    mockk(), conditions, mockk(), mockk(), false)
            }
            val holder = mockk<Holder>()
            val provided = object : ProvidedHolder {
                override val holder = holder
                override val provider = null
            }
            for (size in 0..8) {
                every { holder.effects } returns EffectList(effects.take(size))
                for (outcomes in 0 until (1 shl size)) {
                    mask = outcomes; calls = 0
                    val expected = holder.effects.filter { it.conditions.areMet(dispatcher, provided) }.toSet()
                    assertEquals(size, calls)
                    calls = 0
                    val actual = provided.getActiveEffects(dispatcher)
                    assertEquals(size, calls, "do not short-circuit duplicate condition calls")
                    assertEquals(expected, actual)
                    expected.zip(actual).forEach { (a, b) -> assertSame(a, b) }
                }
            }
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `stable ordering deduplication condition calls and result isolation match previous algorithm`() {
        mockkStatic("com.willfp.libreforge.LibreforgeSpigotPluginKt")
        every { plugin } returns mockk<LibreforgeSpigotPlugin>(relaxed = true)
        try {
            val dispatcher = mockk<Dispatcher<Any>>()
            val trace = mutableListOf<String>()
            var evaluation = 0
            fun conditions(label: String): ConditionList = mockk<ConditionList>().also {
                every { it.areMet(any(), any()) } answers {
                    trace += label
                    ++evaluation % 5 != 0
                }
            }
            fun block(id: Int, weight: Int): EffectBlock {
                val chain = mockk<Chain>()
                every { chain.weight } returns weight
                every { chain.isEmpty() } returns true
                every { chain.iterator() } answers { emptyList<ChainElement<*>>().iterator() }
                return EffectBlock(UUID(0, id.toLong()), mockk(), chain, emptySet(),
                    mockk(), conditions("block-$id"), mockk(), mockk(), false)
            }
            // Distinct objects with equal UUIDs exercise real EffectBlock equality.
            val pool = List(24) { block(it % 12, it % 5 - 2) }
            val random = Random(20261015)
            repeat(504) { case ->
                val count = if (case < 4) case else random.nextInt(0, 9)
                val holders = List(count) { index ->
                    val effects = when (case) {
                        1 -> listOf(pool[0])
                        2 -> listOf(pool[0], pool[12], pool[0])
                        else -> List(random.nextInt(0, 25)) { pool[random.nextInt(pool.size)] }
                    }
                    val holder = mockk<Holder>()
                    every { holder.effects } returns EffectList(effects)
                    every { holder.conditions } returns conditions("holder-$index")
                    object : ProvidedHolder {
                        override val holder = holder
                        override val provider = null
                    }
                }
                val inputBefore = holders.map { it.holder.effects.toList() }
                evaluation = 0; trace.clear()
                val expected = mutableListOf<ProvidedEffectBlock>()
                for (holder in holders) {
                    if (holder.holder.conditions.areMet(dispatcher, holder)) {
                        for (effect in holder.holder.effects.filter { it.conditions.areMet(dispatcher, holder) }.toSet()) {
                            expected += ProvidedEffectBlock(effect, holder)
                        }
                    }
                }
                val sorted = expected.sorted()
                val expectedTrace = trace.toList()
                evaluation = 0; trace.clear()
                val actual = holders.getProvidedActiveEffects(dispatcher)
                assertEquals(expectedTrace, trace, "condition order/count: case $case")
                assertEquals(sorted.size, actual.size)
                actual.zip(sorted).forEach { (a, b) ->
                    assertSame(b.effect, a.effect, "stable order and first duplicate identity")
                    assertSame(b.holder, a.holder)
                }
                assertEquals(inputBefore, holders.map { it.holder.effects.toList() })
                val saved = actual.toList()
                evaluation = 0
                holders.getProvidedActiveEffects(dispatcher)
                assertEquals(saved, actual, "later calls must not mutate earlier results")
            }
        } finally {
            unmockkAll()
        }
    }
}
