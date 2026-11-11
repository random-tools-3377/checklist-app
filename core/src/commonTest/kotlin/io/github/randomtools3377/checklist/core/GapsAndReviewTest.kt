package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
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

    private fun weekdays() = store.addGoal(
        Goal(kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 2, repeat = Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(1, 2, 3, 4, 5))),
    )

    private fun gym() = store.addGoal(
        Goal(kind = GoalKind.HABIT, name = "Gym", icon = "🏋️", points = 5, repeat = Repeat.timesPer(Period.WEEKLY, start), target = 3),
    )

    @Test
    fun aTimesPerWeekGoalAlreadyDoneLeavesAQuietWeekendOutOfGaps() = runTest {
        val walk = weekdays()
        val gym = gym()
        lapses.closeDays()
        for (day in 9..13) {
            at(day)
            book.tickHabit(walk.id)
            if (day % 2 == 1) book.tickHabit(gym.id) // Monday, Wednesday and Friday
        }
        at(16) // Monday: nothing ticked at the weekend, and nothing was due then
        lapses.closeDays()

        assertTrue(gaps.find().isEmpty())
    }

    @Test
    fun aTimesPerWeekGoalDoneLateInItsWeekIsNoGapButWeeksWithNothingAre() = runTest {
        val gym = gym()
        lapses.closeDays()
        for (day in 13..15) {
            at(day)
            book.tickHabit(gym.id) // Friday to Sunday
        }
        at(16)
        lapses.closeDays()
        assertTrue(gaps.find().isEmpty())

        // Two whole weeks with nothing ticked: each ends with its times not done.
        at(30)
        lapses.closeDays()
        val gap = gaps.find().single()
        assertEquals(LocalDate(2026, 11, 22) to LocalDate(2026, 11, 29), gap.from to gap.to)
    }

    @Test
    fun afterMonthsAwayOneGapCoversEveryMissFound() = runTest {
        val run = habit()
        val plants = store.addGoal(
            Goal(kind = GoalKind.HABIT, name = "Water plants", icon = "🪴", points = 3, repeat = Repeat(RepeatKind.AFTER_DONE, LocalDate(2026, 11, 20), interval = 5)),
        )
        val letter = store.addGoal(Goal(kind = GoalKind.TODO, name = "Post the letter", icon = "✉️", points = 2, repeat = null))
        lapses.closeDays()
        book.tickHabit(run.id) // Monday 9 November
        book.tickHabit(letter.id) // a to-do with no date, done that day
        clock.set(LocalDateTime(2027, 2, 7, 10, 0)) // the app opened again 90 days later

        val found = lapses.closeDays()
        val gap = gaps.find().single()

        // Misses are looked for as far back as gaps are, so the one gap covers them all.
        assertEquals(Gaps.LOOK_BACK_DAYS + 1, found.count { it.goalId == run.id })
        assertEquals(LocalDate(2026, 12, 8) to LocalDate(2027, 2, 6), gap.from to gap.to)
        assertTrue(store.lapses.all { it.gapId == gap.id })
        assertEquals(listOf(gap.id), store.openPenalties().map { it.gapId })
        // The days before are still gone through, with no misses: the "since last done" goal, due
        // then, waits on Today, late since its day, and the to-do takes the day it was done.
        assertTrue(found.none { it.goalId == plants.id })
        val waiting = store.goals.first { it.id == plants.id }.repeat!!
        assertEquals(LocalDate(2027, 2, 7) to LocalDate(2026, 11, 20), waiting.start to waiting.lateSince)
        assertEquals(Repeat(RepeatKind.ONCE, LocalDate(2026, 11, 9)), store.goals.first { it.id == letter.id }.repeat)
    }

    @Test
    fun aPenaltyGivenBackIsNoPointLoss() = runTest {
        store.putSetting(Gaps.SETTING_DAYS, "30") // only the point loss can trigger it here
        val run = habit(points = 60) // 30 off for each miss
        lapses.closeDays()
        book.tickHabit(run.id)
        at(11)
        val tuesday = lapses.closeDays().single()
        assertTrue(lapses.forgotToTick(tuesday.id)) // it was done after all: the 30 comes back
        at(12) // Wednesday missed: 30 off
        lapses.closeDays()

        assertTrue(gaps.find().isEmpty())
    }

    @Test
    fun afterAGapIsAnsweredOneMoreQuietDayIsNoNewGap() = runTest {
        val run = habit()
        lapses.closeDays()
        book.tickHabit(run.id) // Monday
        at(12)
        lapses.closeDays()
        val first = gaps.find().single()
        gaps.explain(first.id, Reason.ILL, null)

        at(13) // Thursday was quiet too: one day, below the two a gap needs
        lapses.closeDays()
        assertTrue(gaps.find().isEmpty())

        at(14) // and Friday: now it is two days
        lapses.closeDays()
        val second = gaps.find().single()
        assertEquals(LocalDate(2026, 11, 12) to LocalDate(2026, 11, 13), second.from to second.to)
    }

    @Test
    fun afterAGapIsAnsweredASmallLossIsNoNewGap() = runTest {
        store.putSetting(Gaps.SETTING_DAYS, "30") // only the point loss can trigger it here
        val run = habit(points = 50) // 25 off for each miss
        val stretch = store.addGoal(
            Goal(kind = GoalKind.HABIT, name = "Stretch", icon = "🧘", points = 4, repeat = Repeat.everyDay(start), allowance = 0), // 2 off
        )
        lapses.closeDays()
        book.tickHabit(run.id)
        book.tickHabit(stretch.id)
        at(12) // Tuesday and Wednesday missed: 54 off
        lapses.closeDays()
        val first = gaps.find().single()
        assertEquals(GapKind.POINT_LOSS, first.kind)
        gaps.explain(first.id, Reason.NO_TIME, null)
        book.tickHabit(run.id) // Thursday: only the stretch is missed, 2 off
        at(13)
        lapses.closeDays()

        assertTrue(gaps.find().isEmpty())
    }

    @Test
    fun afterAGapIsAnsweredABigLossOfItsOwnIsANewGap() = runTest {
        store.putSetting(Gaps.SETTING_DAYS, "30") // only the point loss can trigger it here
        val run = habit(points = 50) // 25 off for each miss
        lapses.closeDays()
        book.tickHabit(run.id) // Monday
        at(12) // Tuesday and Wednesday missed: 50 off
        lapses.closeDays()
        gaps.explain(gaps.find().single().id, Reason.ILL, null)
        for (day in 12..17) {
            at(day)
            lapses.closeDays()
            assertTrue(gaps.find().isEmpty())
            if (day != 14 && day != 17) book.tickHabit(run.id) // Saturday and Tuesday missed: 50 off in 4 days
        }
        at(18)
        lapses.closeDays()

        val second = gaps.find().single()
        assertEquals(GapKind.POINT_LOSS, second.kind)
        assertEquals(LocalDate(2026, 11, 14) to LocalDate(2026, 11, 17), second.from to second.to)
    }

    @Test
    fun aGoalBroughtBackDoesNotMakeAGapOfTheDaysBeforeIt() = runTest {
        // A weekday goal, done Monday to Friday; nothing is due at the weekend.
        val work = store.addGoal(
            Goal(kind = GoalKind.HABIT, name = "Study", icon = "📚", points = 5, repeat = Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(1, 2, 3, 4, 5))),
        )
        lapses.closeDays()
        for (d in 9..13) {
            at(d)
            book.tickHabit(work.id)
        }
        at(16)
        lapses.closeDays()
        // On Monday a daily goal with an old start is brought back: the weekend before isn't a gap.
        val noon = LocalDateTime(2026, 11, 16, 12, 0).toInstant(kotlinx.datetime.TimeZone.currentSystemDefault()).toEpochMilliseconds()
        store.addGoal(Goal(kind = GoalKind.HABIT, name = "Stretch", icon = "🧘", points = 3, repeat = Repeat.everyDay(start), createdAt = noon))
        assertTrue(gaps.find().isEmpty())
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
    fun aGoalAddedMidWeekCountsInTheReviewFromThatDay() {
        val run = Goal(id = 1, kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = 10, repeat = Repeat.everyDay(start))
        val days = (0..6).map { monday.plusDays(it) }
        val thursday = days[3]
        val ticks = days.drop(3).map { tick(1, it) }
        val summary = Review.summarize(monday, days.last(), today = days.last(), goals = listOf(run), ticks = ticks, lapses = emptyList(), entries = emptyList(), gaps = emptyList()) { thursday }
        val runSummary = summary.goals.single()
        assertEquals(listOf(4, 4), listOf(runSummary.due, runSummary.done))
        assertEquals(listOf(0, 0, 0, 1, 1, 1, 1), summary.days.map { it.due })
    }

    @Test
    fun rangesAreADayAWeekOrAMonth() {
        val wednesday = LocalDate(2026, 11, 11)
        assertEquals(monday to LocalDate(2026, 11, 15), Review.range(wednesday, Period.WEEKLY))
        assertEquals(LocalDate(2026, 11, 1) to LocalDate(2026, 11, 30), Review.range(wednesday, Period.MONTHLY))
        assertEquals(wednesday to wednesday, Review.range(wednesday, Period.DAILY))
    }
}
