package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GapsTest {
    private val store = MemoryStore()
    private val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0)) // Monday
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
    private val lapses = Lapses(store, book)
    private val gaps = Gaps(store, book)
    private val start = LocalDate(2026, 1, 1)

    private fun at(day: Int) = clock.set(LocalDateTime(2026, 11, day, 10, 0))

    private fun habit(points: Int = 10, allowance: Int = 0) = store.addGoal(
        Goal(kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = points, repeat = Repeat.everyDay(start), allowance = allowance),
    )

    @Test
    fun twoDaysWithNothingTickedMakeAGapWithOneReasonAndOnePenaltyTask() = runTest {
        val run = habit()
        lapses.closeDays()
        book.tickHabit(run.id) // Monday done
        at(12) // Thursday: Tuesday and Wednesday had nothing ticked

        lapses.closeDays()
        val found = gaps.find()

        val gap = found.single()
        assertEquals(LocalDate(2026, 11, 10) to LocalDate(2026, 11, 11), gap.from to gap.to)
        assertEquals(GapKind.NOTHING_TICKED, gap.kind)
        // Both misses are inside it: their own penalty tasks are dropped for the gap's one.
        assertTrue(store.lapses.all { it.gapId == gap.id })
        assertEquals(listOf(gap.id), store.openPenalties().map { it.gapId })
        // Points still came off for each miss.
        assertEquals(10 - 5 - 5, book.balance())

        gaps.explain(gap.id, Reason.ILL, "flu")
        assertTrue(store.lapses.all { it.reason == Reason.ILL && it.note == "flu" })
        assertTrue(gaps.find().isEmpty()) // not flagged twice
    }

    @Test
    fun oneDayAloneIsNotAGapUnlessTheUserSetsItSo() = runTest {
        val run = habit()
        lapses.closeDays()
        book.tickHabit(run.id) // Monday
        at(11)
        book.tickHabit(run.id) // Wednesday; Tuesday had nothing
        at(12)
        lapses.closeDays()

        assertTrue(gaps.find().isEmpty())

        store.putSetting(Gaps.SETTING_DAYS, "1")
        val gap = gaps.find().single()
        assertEquals(LocalDate(2026, 11, 10) to LocalDate(2026, 11, 10), gap.from to gap.to)
    }

    @Test
    fun aBigPointLossWithinAWeekIsAGap() = runTest {
        store.putSetting(Gaps.SETTING_DAYS, "30") // only the point loss can trigger it here
        store.putSetting(Gaps.SETTING_POINTS, "40")
        val run = habit(points = 100)
        lapses.closeDays()
        book.tickHabit(run.id)
        at(10)
        book.tickHabit(run.id)
        at(12) // Tuesday ticked, Wednesday missed: half of 100 is lost
        lapses.closeDays()

        val gap = gaps.find().single()

        assertEquals(GapKind.POINT_LOSS, gap.kind)
        assertEquals(LocalDate(2026, 11, 11), gap.from)
    }
}

class ReviewTest {
    private val start = LocalDate(2026, 1, 1)
    private val monday = LocalDate(2026, 11, 9)

    private fun tick(goalId: Long, day: LocalDate) = Tick(goalId = goalId, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day)
    private fun entry(kind: EntryKind, amount: Int, day: LocalDate, goalId: Long? = null) =
        Entry(at = 0, day = day, kind = kind, amount = amount, goalId = goalId, label = "x")

    @Test
    fun aWeekSummedUp() {
        val run = Goal(id = 1, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 10, repeat = Repeat.everyDay(start))
        val read = Goal(id = 2, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(1, 3)))
        val days = (0..6).map { monday.plusDays(it) }
        val ticks = days.take(3).map { tick(1, it) } + days.drop(4).map { tick(1, it) } + listOf(tick(2, monday))
        val lapses = listOf(
            Lapse(goalId = 1, kind = LapseKind.MISS, day = days[3], period = Period.DAILY, periodStart = days[3], at = 0, overLimit = true, cost = 5, reason = Reason.NO_TIME, label = "Run"),
            Lapse(goalId = 2, kind = LapseKind.SKIP, day = days[2], period = Period.DAILY, periodStart = days[2], at = 0, overLimit = false, label = "Read"),
        )
        val entries = listOf(
            entry(EntryKind.TICK, 60, monday, goalId = 1),
            entry(EntryKind.PURCHASE, -20, days[1]),
            entry(EntryKind.RETURN, 5, days[2]),
            entry(EntryKind.PENALTY, -5, days[3], goalId = 1),
        )

        val summary = Review.summarize(monday, days.last(), today = days.last(), goals = listOf(run, read), ticks = ticks, lapses = lapses, entries = entries, gaps = emptyList())

        val runSummary = summary.goals.first { it.goal.id == 1L }
        assertEquals(listOf(7, 6, 0, 1), listOf(runSummary.due, runSummary.done, runSummary.skipped, runSummary.missed))
        assertEquals(3, runSummary.longestRun)
        assertEquals(3, runSummary.currentRun)
        val readSummary = summary.goals.first { it.goal.id == 2L }
        assertEquals(listOf(2, 2, 1, 0), listOf(readSummary.due, readSummary.done, readSummary.skipped, readSummary.missed))
        assertEquals(listOf("No time" to 1), summary.reasons)
        assertEquals(listOf(60, 15, 5), listOf(summary.earned, summary.spent, summary.lost))
        assertEquals("Run", summary.mostMissed?.goal?.name)
        assertEquals("Read", summary.mostSkipped?.goal?.name)
        assertEquals("Read", summary.mostConsistent?.goal?.name)
        assertEquals(4, summary.worstWeekday) // Thursday: the miss
        assertNull(Review.summarize(monday, monday, monday, emptyList(), emptyList(), emptyList(), emptyList(), emptyList()).mostTicked)
    }

    @Test
    fun rangesAreADayAWeekOrAMonth() {
        val wednesday = LocalDate(2026, 11, 11)
        assertEquals(monday to LocalDate(2026, 11, 15), Review.range(wednesday, Period.WEEKLY))
        assertEquals(LocalDate(2026, 11, 1) to LocalDate(2026, 11, 30), Review.range(wednesday, Period.MONTHLY))
        assertEquals(wednesday to wednesday, Review.range(wednesday, Period.DAILY))
    }
}
