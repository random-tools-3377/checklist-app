package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The user's own miss reasons, notes on ticks, and fixing when a tick was done. */
class ReasonsAndNotesTest {
    private val day = LocalDate(2026, 11, 11)
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 11, 20, 0))
    private val book = Book(store, clock) { TimeZone.UTC }
    private val lapses = Lapses(store, book)
    private val walk = store.addGoal(
        Goal(kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 4, repeat = Repeat.everyDay(day.minusDays(3)), allowance = 0),
    )

    @Test
    fun anOwnReasonKeepsItsWords() = runTest {
        val skipped = lapses.skip(walk.id, Reason.OTHER, note = null, reasonText = "Raining hard") as SkipResult.Skipped
        val lapse = store.lapse(skipped.lapse.id)!!
        assertEquals("Raining hard", lapse.reasonText)
        assertEquals("Raining hard", reasonLabel(lapse.reason, lapse.reasonText))
        // A built-in reason keeps no words.
        lapses.explain(lapse.id, Reason.ILL, note = null, reasonText = "ignored")
        assertNull(store.lapse(lapse.id)!!.reasonText)
        assertEquals("Ill", reasonLabel(Reason.ILL, null))
    }

    @Test
    fun theReviewCountsOwnReasonsByTheirWords() {
        val lapse = Lapse(goalId = walk.id, kind = LapseKind.MISS, day = day, period = Period.DAILY, periodStart = day, at = 0, overLimit = false, cost = 0, reason = Reason.OTHER, label = "Walk", reasonText = "Raining hard")
        val summary = Review.summarize(day, day, day, listOf(walk), emptyList(), listOf(lapse, lapse.copy(id = 9)), emptyList(), emptyList())
        assertEquals(listOf("Raining hard" to 2), summary.reasons)
    }

    @Test
    fun aTicksTimeAndNoteCanBeFixed() = runTest {
        book.tickHabit(walk.id)
        val tick = store.ticks.single()
        val earlier = LocalDateTime(2026, 11, 11, 7, 30).toInstant(TimeZone.UTC).toEpochMilliseconds()
        book.editTick(tick.id, earlier, "  ran 5 km  ")
        val fixed = store.ticks.single()
        assertEquals(earlier, fixed.at)
        assertEquals("ran 5 km", fixed.note)
        assertEquals(listOf(earlier), store.entries.map { it.at })
        assertEquals(4, book.balance())
        // An empty note takes it away.
        book.editTick(tick.id, null, " ")
        assertNull(store.ticks.single().note)
    }
}
