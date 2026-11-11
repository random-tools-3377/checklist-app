package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The review over a stretch: days still to come, removed goals, "since last done" goals and every kind of points. */
class ReviewStretchTest {
    private val start = LocalDate(2026, 1, 1)
    private val monday = LocalDate(2026, 11, 9)
    private val week = (0..6).map { monday.plusDays(it) }

    private fun tick(goalId: Long, day: LocalDate) = Tick(goalId = goalId, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day)
    private fun entry(kind: EntryKind, amount: Int, day: LocalDate, goalId: Long? = null) =
        Entry(at = 0, day = day, kind = kind, amount = amount, goalId = goalId, label = "x")
    private fun miss(goalId: Long, day: LocalDate) =
        Lapse(goalId = goalId, kind = LapseKind.MISS, day = day, period = Period.DAILY, periodStart = day, at = 0, overLimit = false, label = "x")
    private fun habit(id: Long, category: String? = null) =
        Goal(id = id, kind = GoalKind.HABIT, name = "Habit $id", icon = "⭐", points = 5, repeat = Repeat.everyDay(start), category = category, position = id.toInt())

    private fun summarize(today: LocalDate, goals: List<Goal>, ticks: List<Tick>, lapses: List<Lapse> = emptyList(), entries: List<Entry> = emptyList()) =
        Review.summarize(monday, week.last(), today, goals, ticks, lapses, entries, emptyList())

    @Test
    fun daysStillToComeAreNotCountedYet() {
        val wednesday = week[2]
        val daily = habit(1)
        // Done Monday and Wednesday, missed Tuesday; Thursday to Sunday haven't come.
        val summary = summarize(wednesday, listOf(daily), listOf(tick(1, week[0]), tick(1, wednesday)), listOf(miss(1, week[1])))

        val goal = summary.goals.single()
        assertEquals(listOf(3, 2, 1), listOf(goal.due, goal.done, goal.missed))
        assertEquals(2, summary.worstWeekday) // Tuesday, not a day still to come
        assertEquals(1, summary.bestWeekday)
        // Every day keeps its place on the calendar, but the days to come have nothing due yet.
        assertEquals(7, summary.days.size)
        assertEquals(listOf(1, 1, 1, 0, 0, 0, 0), summary.days.map { it.due })
    }

    @Test
    fun theSameDayIsNeverBothTheBestAndTheHardest() {
        val daily = habit(1)
        val oneDay = Review.summarize(week[1], week[1], week[1], listOf(daily), listOf(tick(1, week[1])), emptyList(), emptyList(), emptyList())
        assertNull(oneDay.bestWeekday)
        assertNull(oneDay.worstWeekday)
        // Every day done: no best or hardest day either.
        val allDone = summarize(week.last(), listOf(daily), week.map { tick(1, it) })
        assertNull(allDone.bestWeekday)
        assertNull(allDone.worstWeekday)
    }

    @Test
    fun aRemovedGoalIsNotDueButItsPointsKeepTheirCategory() {
        val run = habit(1, "Health")
        val swim = habit(2, "Health").copy(archived = true)
        val read = habit(3)
        val ticks = week.map { tick(1, it) } + listOf(tick(2, monday))
        val entries = listOf(entry(EntryKind.TICK, 5, monday, 1), entry(EntryKind.TICK, 5, monday, 2))

        val summary = summarize(week.last(), listOf(run, swim, read), ticks, entries = entries)

        assertEquals(listOf(1L, 3L), summary.goals.map { it.goal.id })
        val health = summary.categories.first { it.name == "Health" }
        assertEquals(listOf(7, 7, 10), listOf(health.due, health.done, health.points))
        assertEquals(0, summary.categories.first { it.name == null }.points)
        // Each day, only the two goals still kept were due.
        assertEquals(listOf(2, 1), listOf(summary.days.first().due, summary.days.first().done))
        // The weekly summary, given every goal, says the same as the review.
        assertEquals(14 to 7, WeeklySummary.of(summary).let { it.due to it.done })
    }

    @Test
    fun aSinceLastDoneGoalCountsTheDaysItWasDueBefore() = runTest {
        val store = MemoryStore()
        val clock = TestClock(LocalDateTime(2026, 11, 2, 10, 0)) // Monday 2 November
        val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
        val lapses = Lapses(store, book)
        fun on(day: Int) = clock.set(LocalDateTime(2026, 11, day, 10, 0))
        val plants = store.addGoal(
            Goal(kind = GoalKind.HABIT, name = "Water plants", icon = "🪴", points = 3, repeat = Repeat(RepeatKind.AFTER_DONE, LocalDate(2026, 11, 2), interval = 3)),
        )
        lapses.closeDays()
        book.tickHabit(plants.id) // Monday 2nd: next on Thursday 5th
        on(5)
        lapses.closeDays()
        book.tickHabit(plants.id) // Thursday 5th: next on Sunday 8th
        on(10)
        lapses.closeDays() // Sunday 8th missed; it waits on Today since

        val from = LocalDate(2026, 11, 2)
        val sunday = LocalDate(2026, 11, 8)
        val summary = Review.summarize(from, sunday, LocalDate(2026, 11, 10), store.goals, store.ticks, store.lapses, store.entries, emptyList())

        val goal = summary.goals.single()
        assertEquals(listOf(3, 2, 1, 2), listOf(goal.due, goal.done, goal.missed, goal.longestRun))
        assertEquals(listOf(1, 0, 0, 1, 0, 0, 1), summary.days.map { it.due })
        assertEquals(listOf(1, 0, 0, 1, 0, 0, 0), summary.days.map { it.done })
    }

    @Test
    fun interestAndBetsAreSummedUpAndAReturnCanOutweighWhatWasBought() {
        val entries = listOf(
            entry(EntryKind.TICK, 10, monday, 1),
            entry(EntryKind.INTEREST, 12, monday),
            entry(EntryKind.BET, -20, week[1], 1),
            entry(EntryKind.BET, 40, week[6], 1),
            entry(EntryKind.RETURN, 50, week[2]), // bought the week before
        )

        val summary = summarize(week.last(), listOf(habit(1)), emptyList(), entries = entries)

        assertEquals(10, summary.earned) // the same as the weekly target counts
        assertEquals(12, summary.interest)
        assertEquals(20, summary.bets)
        assertEquals(-50, summary.spent)
        // Together they make up the change in the balance.
        assertEquals(entries.sumOf { it.amount }, summary.earned - summary.spent - summary.lost + summary.interest + summary.bets)
    }
}
