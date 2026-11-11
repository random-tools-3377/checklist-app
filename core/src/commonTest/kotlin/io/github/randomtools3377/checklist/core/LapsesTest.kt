package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LapsesTest {
    private val store = MemoryStore()

    // Wednesday 11 November 2026, 10:00.
    private val clock = TestClock(LocalDateTime(2026, 11, 11, 10, 0))
    private val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
    private val lapses = Lapses(store, book, Random(1))
    private val start = LocalDate(2026, 1, 1)

    private fun habit(points: Int = 10, allowance: Int? = null, per: Period? = null, repeat: Repeat = Repeat.everyDay(start), target: Int = 1) =
        store.addGoal(
            Goal(
                kind = GoalKind.HABIT, name = "Run", icon = "🏃", points = points, repeat = repeat, target = target,
                allowance = allowance, allowancePer = per,
            ),
        )

    private fun at(day: Int, hour: Int = 10) = clock.set(LocalDateTime(2026, 11, day, hour, 0))

    @Test
    fun aSkipWithinTheAllowanceIsFreeAndNeedsNoReason() = runTest {
        val run = habit(allowance = 1)
        assertEquals(SkipCheck.Allowed(used = 0, allowed = 1, overLimit = false, cost = 0), lapses.checkSkip(run.id))

        val result = lapses.skip(run.id, reason = null, note = null) as SkipResult.Skipped

        assertNull(result.penaltyId)
        assertEquals(0, book.balance())
        assertEquals(SkipCheck.AlreadyDone, lapses.checkSkip(run.id))
    }

    @Test
    fun overTheLimitASkipNeedsAReasonCostsHalfThePointsAndOwesAPenalty() = runTest {
        val run = habit(points = 11, allowance = 1)
        lapses.skip(run.id, null, null)
        at(12)

        assertEquals(SkipCheck.Allowed(used = 1, allowed = 1, overLimit = true, cost = 5), lapses.checkSkip(run.id))
        assertEquals(SkipResult.NeedsReason, lapses.skip(run.id, null, null))

        val result = lapses.skip(run.id, Reason.NO_TIME, "busy") as SkipResult.Skipped
        assertTrue(result.lapse.overLimit)
        assertEquals(-5, book.balance())
        assertEquals(result.penaltyId, lapses.openPenalty()?.id)
    }

    @Test
    fun theAllowanceResetsWhenANewWeekStarts() = runTest {
        val run = habit(allowance = 1)
        lapses.skip(run.id, null, null) // Wednesday
        at(16) // Monday
        assertEquals(SkipCheck.Allowed(0, 1, false, 0), lapses.checkSkip(run.id))
    }

    @Test
    fun aMonthlyAllowanceCountsTheWholeMonth() = runTest {
        val run = habit(allowance = 2, per = Period.MONTHLY)
        lapses.skip(run.id, null, null)
        at(20)
        lapses.skip(run.id, null, null)
        at(25)
        assertTrue((lapses.checkSkip(run.id) as SkipCheck.Allowed).overLimit)
    }

    @Test
    fun missesAreFoundWhenTheDayEndsAndOnlyFromTheFirstCheckOn() = runTest {
        val run = habit(allowance = 1)
        assertTrue(lapses.closeDays().isEmpty()) // first check: only notes where to start
        book.tickHabit(run.id) // Wednesday done
        at(14) // Saturday: Thursday and Friday were missed

        val found = lapses.closeDays()

        assertEquals(listOf(LocalDate(2026, 11, 12), LocalDate(2026, 11, 13)), found.map { it.day })
        assertEquals(listOf(false, true), found.map { it.overLimit })
        assertEquals(-5, book.balance() - 10)
        assertEquals(1, store.openPenalties().size)
        assertTrue(lapses.closeDays().isEmpty()) // nothing found twice
    }

    @Test
    fun aSkippedDayIsNotAMiss() = runTest {
        val run = habit(allowance = 3)
        lapses.closeDays()
        lapses.skip(run.id, null, null)
        at(12)
        assertTrue(lapses.closeDays().isEmpty())
    }

    @Test
    fun aTimesPerWeekHabitIsMissedOnceWhenItsWeekEndsShort() = runTest {
        val gym = habit(repeat = Repeat.timesPer(Period.WEEKLY, start), target = 3)
        lapses.closeDays()
        book.tickHabit(gym.id)
        at(13)
        lapses.skip(gym.id, null, null) // counts as one of the three
        at(17) // Tuesday of the next week

        val found = lapses.closeDays()

        assertEquals(listOf(LocalDate(2026, 11, 15)), found.map { it.day })
    }

    @Test
    fun aTimesPerWeekHabitStartedLateInTheWeekIsNotMissedThatWeek() = runTest {
        // Started on Saturday 14 November: that week is only partly its own, so falling short isn't a miss.
        at(14)
        val saturday = LocalDate(2026, 11, 14)
        val gym = habit(repeat = Repeat.timesPer(Period.WEEKLY, saturday), target = 3)
        lapses.closeDays()
        book.tickHabit(gym.id)
        at(16)
        assertTrue(lapses.closeDays().isEmpty())

        // The next full week is judged as usual.
        at(24)
        assertEquals(listOf(LocalDate(2026, 11, 22)), lapses.closeDays().map { it.day })

        // In the streaks, the short first week neither counts nor breaks; once done, it counts.
        val records = StreakCounter.records(LocalDate(2026, 11, 15), LocalDate(2026, 11, 15), store.goals, store.ticks, emptyList()) { null }
        assertEquals(0, records.single().due)
        val ticks = store.ticks + List(2) { Tick(goalId = gym.id, stepId = null, at = 0, day = saturday, period = Period.WEEKLY, periodStart = LocalDate(2026, 11, 9)) }
        val done = StreakCounter.records(LocalDate(2026, 11, 15), LocalDate(2026, 11, 15), store.goals, ticks, emptyList()) { null }
        assertEquals(1 to 1, done.single().let { it.due to it.done })
    }

    @Test
    fun aTimesPerWeekHabitEndingMidWeekIsNotMissedThatWeek() = runTest {
        val gym = habit(repeat = Repeat(RepeatKind.TIMES_PER, start, end = LocalDate(2026, 11, 11), per = Period.WEEKLY), target = 3)
        lapses.closeDays()
        at(17)
        assertTrue(lapses.closeDays().none { it.goalId == gym.id })
    }

    @Test
    fun aProjectPastItsDeadlineUnfinishedIsAMiss() = runTest {
        val project = store.addGoal(
            Goal(kind = GoalKind.PROJECT, name = "Shelf", icon = "🔨", points = 0, repeat = null, bonus = 20, deadline = LocalDate(2026, 11, 12), allowance = 0),
        )
        store.addStep(Step(goalId = project.id, name = "Buy wood", points = 5, position = 0))
        lapses.closeDays()
        at(13)

        val found = lapses.closeDays()

        assertEquals(listOf(project.id), found.map { it.goalId })
        assertEquals(-10, book.balance()) // half the bonus, over a limit of 0
    }

    @Test
    fun aMissNeedsAReasonAndCanBeExplained() = runTest {
        val run = habit()
        lapses.closeDays()
        at(12)
        val miss = lapses.closeDays().single()
        assertNull(miss.reason)

        lapses.explain(miss.id, Reason.ILL, "flu")

        assertEquals(Reason.ILL, store.lapse(miss.id)?.reason)
        assertEquals("flu", store.lapse(miss.id)?.note)
    }

    @Test
    fun iForgotToTickItTicksTheDayRefundsThePenaltyAndDropsThePenaltyTask() = runTest {
        val run = habit(points = 10, allowance = 0)
        lapses.closeDays()
        at(12)
        val miss = lapses.closeDays().single()
        assertEquals(-5, book.balance())

        assertTrue(lapses.forgotToTick(miss.id))

        assertEquals(10, book.balance()) // the tick's 10, and the 5 back
        assertTrue(store.ticks.any { it.goalId == run.id && it.day == LocalDate(2026, 11, 11) })
        assertTrue(store.openPenalties().isEmpty())
        assertTrue(store.lapse(miss.id)!!.forgotten)
        assertFalse(lapses.forgotToTick(miss.id))
    }

    @Test
    fun iForgotToTickItFinishesACountedGoalAndPaysItsBonus() = runTest {
        val water = store.addGoal(
            Goal(kind = GoalKind.HABIT, name = "Water", icon = "💧", points = 1, repeat = Repeat.everyDay(start), target = 8, bonus = 5, allowance = 1),
        )
        lapses.closeDays()
        repeat(6) { book.tickHabit(water.id) }
        at(12)
        val miss = lapses.closeDays().single()
        assertEquals(6, book.balance())

        assertTrue(lapses.forgotToTick(miss.id))

        val wednesday = LocalDate(2026, 11, 11)
        assertEquals(8, store.ticks.count { it.goalId == water.id && it.periodStart == wednesday })
        assertEquals(6 + 2 + 5, book.balance()) // the two ticks left, and the finishing bonus
        val record = StreakCounter.records(wednesday, wednesday, store.goals, store.ticks, store.lapses).single()
        assertEquals(1, record.done)
        assertEquals(0, record.misses)
    }

    @Test
    fun iForgotToTickItFinishesATimesPerWeekGoalCountingItsSkips() = runTest {
        val gym = habit(points = 10, allowance = 2, repeat = Repeat.timesPer(Period.WEEKLY, start), target = 4)
        lapses.closeDays()
        book.tickHabit(gym.id) // Wednesday
        at(13)
        lapses.skip(gym.id, null, null) // Friday: one of the four
        at(17)
        val miss = lapses.closeDays().single()

        assertTrue(lapses.forgotToTick(miss.id))

        val monday = LocalDate(2026, 11, 9)
        assertEquals(3, store.ticks.count { it.goalId == gym.id && it.periodStart == monday }) // two added
        assertEquals(30, book.balance())
        val sunday = LocalDate(2026, 11, 15)
        assertEquals(1, StreakCounter.records(sunday, sunday, store.goals, store.ticks, store.lapses).single().done)
    }

    @Test
    fun iForgotToTickItEntersWhatIsLeftOfAnAmount() = runTest {
        val read = store.addGoal(
            Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(start), amount = 20.0, unit = "pages", allowance = 1),
        )
        lapses.closeDays()
        book.logAmount(read.id, 12.0)
        at(12)
        val miss = lapses.closeDays().single()

        assertTrue(lapses.forgotToTick(miss.id))

        val wednesday = LocalDate(2026, 11, 11)
        val ticks = store.ticks.filter { it.goalId == read.id && it.periodStart == wednesday }
        assertEquals(listOf(12.0, 8.0), ticks.map { it.amount })
        assertEquals(4, book.balance())
    }

    @Test
    fun aPenaltyIsPaidByMakingUpAGoalOrARandomPenaltyTaskAndPaysNothing() = runTest {
        val run = habit(allowance = 0)
        lapses.skip(run.id, Reason.CHOSE_NOT, null)
        val penalty = lapses.openPenalty()!!
        assertNull(lapses.drawTask(penalty.id)) // no penalty tasks written yet

        store.addPenaltyTask("20 push-ups")
        store.addPenaltyTask("Clean the sink")
        val drawn = lapses.drawTask(penalty.id)!!
        assertEquals(PenaltyChoice.TASK, drawn.choice)
        assertTrue(drawn.label in listOf("20 push-ups", "Clean the sink"))

        val madeUp = lapses.makeUp(penalty.id, run.id)!!
        assertEquals("🏃 Run", madeUp.label)

        val before = book.balance()
        assertTrue(lapses.finishPenalty(penalty.id))
        assertEquals(before, book.balance())
        assertNull(lapses.openPenalty())
    }
}

class StreaksTest {
    private val today = LocalDate(2026, 11, 11)

    private fun record(daysAgo: Int, due: Int, done: Int, misses: Int = 0, skips: Int = 0, overLimit: Int = 0) =
        DayRecord(today.minusDays(daysAgo), due, done, misses, skips, overLimit)

    @Test
    fun theMainStreakUsesTheUsersTarget() {
        val records = listOf(record(3, 10, 8), record(2, 10, 9), record(1, 10, 10), record(0, 10, 2))
        assertEquals(2, StreakCounter.count(records, today, StreakSettings(StreakTarget.PERCENT, 90)).done)
        assertEquals(3, StreakCounter.count(records, today, StreakSettings(StreakTarget.PERCENT, 80)).done)
        assertEquals(1, StreakCounter.count(records, today, StreakSettings(StreakTarget.COUNT, 10)).done)
    }

    @Test
    fun todayCountsOnceItQualifiesAndEmptyDaysAreNeutral() {
        val records = listOf(record(3, 2, 2), record(2, 0, 0), record(1, 2, 2), record(0, 2, 2))
        assertEquals(3, StreakCounter.count(records, today, StreakSettings()).perfect)
    }

    @Test
    fun eachStreakBreaksOnItsOwnThing() {
        val records = listOf(record(2, 3, 3), record(1, 3, 2, misses = 1, overLimit = 1), record(0, 3, 3, skips = 1))
        val streaks = StreakCounter.count(records, today, StreakSettings(StreakTarget.COUNT, 2))
        assertEquals(3, streaks.done)
        assertEquals(1, streaks.noMisses)
        assertEquals(0, streaks.noSkips) // today's skip has already happened
        assertEquals(1, streaks.withinLimit)
        assertEquals(1, streaks.perfect)
        assertFalse(streaks.allRunning)

        val clean = StreakCounter.count(records.take(2) + record(0, 3, 1), today, StreakSettings(StreakTarget.COUNT, 2))
        assertEquals(3, clean.noSkips) // today without skips counts, with the two days before it
    }

    @Test
    fun aDayWhoseOnlyDueThingWasMissedBreaksTheMainStreak() {
        // Two days kept, then a day with nothing else due and a project's deadline missed.
        val records = listOf(record(3, 2, 2), record(2, 2, 2), record(1, 0, 0, misses = 1), record(0, 2, 0))
        assertEquals(0, StreakCounter.count(records, today, StreakSettings(StreakTarget.PERCENT, 90)).done)
        assertEquals(0, StreakCounter.count(records, today, StreakSettings(StreakTarget.COUNT, 1)).done)
        assertEquals(0, Shields.of(records, LocalDate(2026, 1, 1), today, StreakSettings()).main)
        // A day with only a skip on it still counts, as a skipped goal counts as done.
        val skipped = listOf(record(2, 2, 2), record(1, 0, 0, skips = 1))
        assertEquals(2, StreakCounter.count(skipped, today, StreakSettings()).done)
    }

    @Test
    fun aGoalCountsInTheRecordsOnlyFromTheDayItWasAdded() {
        val start = LocalDate(2026, 1, 1)
        val old = Goal(id = 1, kind = GoalKind.HABIT, name = "Old", icon = "⭐", points = 1, repeat = Repeat.everyDay(start))
        // A copy keeping the old start, added on 11 November, with nothing ticked before then.
        val copy = old.copy(id = 2, name = "Old (copy)")
        val added = LocalDate(2026, 11, 11)
        val ticks = (1..11).map { d -> LocalDate(2026, 11, d) }.map { day -> Tick(goalId = 1, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day) }
        val records = StreakCounter.records(LocalDate(2026, 11, 1), added, listOf(old, copy), ticks, emptyList()) { goal -> if (goal.id == 2L) added else null }
        assertEquals(List(10) { 1 to 1 } + listOf(2 to 1), records.map { it.due to it.done })
        assertEquals(10, StreakCounter.count(records, added, StreakSettings()).perfect)

        // The day it was added comes from when it was made, in the phone's time zone.
        val made = copy.copy(createdAt = kotlinx.datetime.LocalDateTime(2026, 11, 11, 23, 30).toInstant(kotlinx.datetime.TimeZone.UTC).toEpochMilliseconds())
        assertEquals(added, made.addedOn(kotlinx.datetime.TimeZone.UTC))
        assertEquals(LocalDate(2026, 11, 12), made.addedOn(kotlinx.datetime.UtcOffset(hours = 9).asTimeZone()))
        assertNull(old.addedOn(kotlinx.datetime.TimeZone.UTC))
    }

    @Test
    fun recordsCountSkipsAsDoneAndTimesPerOnTheirLastDay() {
        val start = LocalDate(2026, 1, 1)
        val daily = Goal(id = 1, kind = GoalKind.HABIT, name = "D", icon = "⭐", points = 1, repeat = Repeat.everyDay(start))
        val weekly = Goal(id = 2, kind = GoalKind.HABIT, name = "W", icon = "⭐", points = 1, repeat = Repeat.timesPer(Period.WEEKLY, start))
        val sunday = LocalDate(2026, 11, 15)
        val skip = Lapse(goalId = 1, kind = LapseKind.SKIP, day = sunday, period = Period.DAILY, periodStart = sunday, at = 0, overLimit = false, label = "D")
        val records = StreakCounter.records(LocalDate(2026, 11, 14), sunday, listOf(daily, weekly), emptyList(), listOf(skip))
        assertEquals(listOf(1 to 0, 2 to 1), records.map { it.due to it.done })
        assertEquals(1, records.last().skips)
    }
}
