package com.twinlore.app.journey.domain

import com.twinlore.app.journey.domain.model.AchievementType
import com.twinlore.app.journey.domain.model.AwardKind
import com.twinlore.app.journey.domain.model.JourneyFrame
import com.twinlore.app.journey.domain.model.JourneyMode
import com.twinlore.app.journey.domain.model.Medal
import com.twinlore.app.journey.domain.model.PlayerJourney
import com.twinlore.app.journey.domain.model.Unlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconBudgetTest {

    private val t0 = 1_760_000_000_000L
    private val day = 86_400_000L

    private fun unlock(id: Int, at: Long, pts: Int) = Unlock(
        id = id, title = "A$id", description = "", points = pts, badgeName = "b$id",
        type = AchievementType.STANDARD, earnedAtMillis = at, numAwarded = 10, gameId = 1,
    )

    private fun medal(kind: AwardKind, at: Long) = Medal(kind, at, 1, null, "G")

    private fun journey(unlocks: List<Unlock>, name: String = "me") = PlayerJourney(
        name = name, unlocks = unlocks, games = emptyMap(), medals = emptyList(),
    )

    private fun frame(unlocks: List<Unlock>, rivals: List<PlayerJourney> = emptyList()) = JourneyFrame(
        mode = JourneyMode.SPAN, main = journey(unlocks), rivals = rivals,
        spanFromMillis = 0, spanToMillis = 0,
    )

    @Test fun `everything fits under the budget`() {
        val medals = List(3) { medal(AwardKind.BEATEN, t0 + it) }
        val unlocks = List(10) { unlock(it, t0 + it, 5) }
        val s = IconBudget.select(medals, unlocks)
        assertEquals(medals.indices.toList(), s.medalIndices)
        assertEquals(unlocks.indices.toList(), s.unlockIndices)
    }

    @Test fun `unlocks are picked by descending points`() {
        val unlocks = listOf(
            unlock(0, t0, 1), unlock(1, t0 + 1, 50), unlock(2, t0 + 2, 25), unlock(3, t0 + 3, 5),
        )
        val s = IconBudget.select(emptyList(), unlocks, budget = 2)
        assertEquals(listOf(1, 2), s.unlockIndices)   // 50 then 25, reported in list order
    }

    @Test fun `equal points break the tie on the earlier unlock`() {
        val unlocks = listOf(
            unlock(0, t0 + 100, 10), unlock(1, t0 + 10, 10), unlock(2, t0 + 50, 10),
        )
        val s = IconBudget.select(emptyList(), unlocks, budget = 2)
        assertEquals(listOf(1, 2), s.unlockIndices)   // earliest two of the three tied unlocks
    }

    @Test fun `medals take the budget before unlocks do`() {
        val medals = List(4) { medal(AwardKind.BEATEN, t0 + it) }
        val unlocks = List(10) { unlock(it, t0 + it, it + 1) }
        val s = IconBudget.select(medals, unlocks, budget = 6)
        assertEquals(4, s.medalIndices.size)
        assertEquals(2, s.unlockIndices.size)
        assertEquals(listOf(8, 9), s.unlockIndices)   // the two highest scoring
    }

    /**
     * The budget does not rank awards, because it does not cut them. Every kind survives a cap
     * smaller than the medal list itself, in the order the caller gave them.
     */
    @Test fun `medals beyond the budget are kept whole, whatever their kind`() {
        val medals = listOf(
            medal(AwardKind.BEATEN, t0 + 900),          // 0
            medal(AwardKind.MASTERED, t0 + 100),        // 1
            medal(AwardKind.COMPLETED, t0 + 800),       // 2
            medal(AwardKind.MASTERED, t0 + 500),        // 3
            medal(AwardKind.COMPLETED, t0 + 200),       // 4
        )
        val s = IconBudget.select(medals, emptyList(), budget = 3)

        assertEquals(listOf(0, 1, 2, 3, 4), s.medalIndices)
        assertTrue(s.unlockIndices.isEmpty())
    }

    /**
     * No award is ever one of the ones cut. Sixty medals of all three kinds against a budget of
     * three: the budget is deliberately overrun rather than allowed to drop one, because an award
     * quietly missing from a chart is a fact about the journey gone with nothing on the stage
     * saying so. The cap is a cap on BADGES, and here there is nothing left for them.
     */
    @Test fun `every award kind survives a budget too small to hold them`() {
        val medals = AwardKind.values().flatMap { kind ->
            List(20) { medal(kind, t0 + kind.ordinal * 1000L + it) }
        }
        val unlocks = List(10) { unlock(it, t0 + it, 50) }
        val s = IconBudget.select(medals, unlocks, budget = 3)

        assertEquals("every award is kept", medals.size, s.medalIndices.size)
        AwardKind.values().forEach { kind ->
            assertEquals(
                "every ${'$'}kind is kept",
                20,
                s.medalIndices.count { medals[it].kind == kind },
            )
        }
        assertTrue("and the badges are what paid for it", s.unlockIndices.isEmpty())
        assertEquals("the selection stays ascending", s.medalIndices.sorted(), s.medalIndices)
    }

    /**
     * The budget applies to badges, so the medals come off the top of it rather than competing for
     * it: ten awards against a budget of sixteen leaves room for six badges and not for sixteen.
     */
    @Test fun `the budget left over after the awards is what the badges get`() {
        val medals = List(10) { medal(AwardKind.BEATEN, t0 + it) }
        val unlocks = List(30) { unlock(it, t0 + it, it + 1) }
        val s = IconBudget.select(medals, unlocks, budget = 16)

        assertEquals(10, s.medalIndices.size)
        assertEquals(6, s.unlockIndices.size)
        assertEquals("the six highest scoring", listOf(24, 25, 26, 27, 28, 29), s.unlockIndices)
    }

    @Test fun `medals alone can consume the whole budget`() {
        val medals = List(5) { medal(AwardKind.MASTERED, t0 + it) }
        val unlocks = List(10) { unlock(it, t0 + it, 50) }
        val s = IconBudget.select(medals, unlocks, budget = 5)
        assertEquals(5, s.medalIndices.size)
        assertTrue(s.unlockIndices.isEmpty())
    }

    @Test fun `the default budget is 200 icons`() {
        val unlocks = List(689) { unlock(it, t0 + it, (it % 25) + 1) }
        val medals = List(31) { medal(AwardKind.BEATEN, t0 + it) }
        val s = IconBudget.select(medals, unlocks)
        assertEquals(200, IconBudget.TOTAL_ICONS)
        assertEquals(31, s.medalIndices.size)
        assertEquals(169, s.unlockIndices.size)
        // Selected indices stay ascending so callers can index straight back into their lists.
        assertEquals(s.unlockIndices.sorted(), s.unlockIndices)
    }

    // --- budgetFor ---

    @Test fun `a short, sparse journey gets the default budget`() {
        val unlocks = List(10) { unlock(it, t0 + it * day, 5) }
        assertEquals(200, IconBudget.budgetFor(frame(unlocks)))
    }

    @Test fun `exactly 183 days still gets the default budget`() {
        val unlocks = listOf(unlock(1, t0, 5), unlock(2, t0 + 183 * day, 5))
        assertEquals(200, IconBudget.budgetFor(frame(unlocks)))
    }

    @Test fun `184 days crosses into the expanded budget`() {
        val unlocks = listOf(unlock(1, t0, 5), unlock(2, t0 + 184 * day, 5))
        assertEquals(400, IconBudget.budgetFor(frame(unlocks)))
    }

    @Test fun `exactly 300 main unlocks still gets the default budget`() {
        val unlocks = List(300) { unlock(it, t0 + it * 1000L, 5) }
        assertEquals(200, IconBudget.budgetFor(frame(unlocks)))
    }

    @Test fun `301 main unlocks crosses into the expanded budget regardless of span`() {
        val unlocks = List(301) { unlock(it, t0 + it * 1000L, 5) }   // milliseconds apart, span is nothing
        assertEquals(400, IconBudget.budgetFor(frame(unlocks)))
    }

    @Test fun `a rival's unlocks widen the span even when the main player is short and sparse`() {
        val main = List(3) { unlock(it, t0 + it * day, 5) }
        val rival = journey(listOf(unlock(90, t0 - 200 * day, 5)), name = "r")
        assertEquals(400, IconBudget.budgetFor(frame(main, rivals = listOf(rival))))
    }

    @Test fun `a single unlock has no span and gets the default budget`() {
        assertEquals(200, IconBudget.budgetFor(frame(listOf(unlock(1, t0, 5)))))
    }

    @Test fun `no unlocks at all gets the default budget`() {
        assertEquals(200, IconBudget.budgetFor(frame(emptyList())))
    }

    /**
     * UI feedback: the wide view's long empty stretches. A quiet region's unlocks must not lose
     * the whole budget to the dense clusters either side of it - every slice of the timeline with
     * anything in it keeps its best badge or two before any cluster gets its fiftieth.
     */
    @Test fun `a sparse stretch keeps its badges against denser clusters`() {
        val day = 86_400_000L
        // A dense high-point cluster at the start, three modest unlocks alone in the middle
        // months, and another dense cluster at the end.
        val cluster1 = (0 until 150).map { unlock(it, t0 + it * 60_000L, 50) }
        val middle = (0 until 3).map { unlock(1000 + it, t0 + (40 + it * 10) * day, 2) }
        val cluster2 = (0 until 150).map { unlock(2000 + it, t0 + 90 * day + it * 60_000L, 50) }
        val unlocks = cluster1 + middle + cluster2

        val picked = IconBudget.select(emptyList(), unlocks, budget = 100).unlockIndices

        middle.forEach { mid ->
            assertTrue(
                "the lone mid-gap unlock ${mid.id} must be on the stage",
                picked.any { unlocks[it].id == mid.id },
            )
        }
    }

    // --- the wide timeline's extra badges ---

    /**
     * UI feedback: a widened stage has two to four screenfuls of room, so it earns more badges
     * than the one-screenful compact chart drawn from the same frame. The scale rides the width
     * factor the layout was granted; the compact chart passes 1 and keeps today's budget, which
     * is what hides the extras when the player collapses to the dense view.
     */
    @Test fun `a widened stage earns a bigger badge budget`() {
        val unlocks = List(10) { unlock(it, t0 + it * day, 5) }
        val base = IconBudget.budgetFor(frame(unlocks))
        assertEquals(300, IconBudget.budgetFor(frame(unlocks), widthFactor = 1.5f))
        assertEquals(400, IconBudget.budgetFor(frame(unlocks), widthFactor = 2f))
        assertTrue("wider is never smaller", IconBudget.budgetFor(frame(unlocks), widthFactor = 4f) >= base)
    }

    /** The scale is capped: bitmap memory rides the wide budget, so it must stay bounded. */
    @Test fun `the widened budget never exceeds the absolute cap`() {
        val long = List(301) { unlock(it, t0 + it * 1000L, 5) }   // already the expanded 400 base
        assertEquals(IconBudget.WIDE_ICONS_CAP, IconBudget.budgetFor(frame(long), widthFactor = 4f))
    }

    /** The dense chart's selection stays a prefix of the wide chart's: the containment invariant. */
    @Test fun `the compact selection is a subset of the widened one`() {
        val unlocks = List(500) { unlock(it, t0 + it * 1000L, (it * 7) % 50) }
        val compact = IconBudget.select(emptyList(), unlocks, budget = 200)
        val wide = IconBudget.select(emptyList(), unlocks, budget = 400)
        assertTrue(
            "every compact badge must be on the wide stage",
            wide.unlockIndices.containsAll(compact.unlockIndices),
        )
    }

    // --- iconCountFor: what the chart will actually place ---

    @Test fun `iconCountFor counts every medal and badge under the budget`() {
        val main = journey(List(10) { unlock(it, t0 + it * day, 5) }).copy(
            medals = List(3) { medal(AwardKind.BEATEN, t0 + it) },
        )
        val f = JourneyFrame(
            mode = JourneyMode.SPAN, main = main, rivals = emptyList(),
            spanFromMillis = 0, spanToMillis = 0,
        )
        assertEquals(13, IconBudget.iconCountFor(f))
    }

    @Test fun `iconCountFor is capped by the budget`() {
        val unlocks = List(689) { unlock(it, t0 + it * 1000L, 5) }
        assertEquals(IconBudget.EXPANDED_ICONS, IconBudget.iconCountFor(frame(unlocks)))
    }

    /** Rivals' medals are drawn too, so they count towards how crowded the stage will be. */
    @Test fun `iconCountFor includes the rivals' medals`() {
        val rival = journey(List(2) { unlock(it, t0 + it * day, 5) }, name = "r")
            .copy(medals = List(4) { medal(AwardKind.MASTERED, t0 + it) })
        val f = JourneyFrame(
            mode = JourneyMode.SPAN, main = journey(List(5) { unlock(it, t0 + it * day, 5) }),
            rivals = listOf(rival), spanFromMillis = 0, spanToMillis = 0,
        )
        assertEquals(9, IconBudget.iconCountFor(f))
    }
}
