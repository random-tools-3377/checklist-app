package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A goal's own page: its calendar, runs, how often it got done, and what was finished in a stretch of the review. */
class GoalPageTest {
    private val start = LocalDate(2026, 10, 1)
    private val today = LocalDate(2026, 11, 12) // a Thursday
    private val read = Goal(id = 1, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(start))

    private var nextId = 1L
    private fun tick(day: LocalDate, goalId: Long = 1, stepId: Long? = null, note: String? = null, amount: Double? = null, seconds: Int? = null, period: Period? = Period.DAILY, periodStart: LocalDate = day) =
        Tick(id = nextId++, goalId = goalId, stepId = stepId, at = nextId, day = day, period = if (stepId == null) period else null, periodStart = periodStart, note = note, amount = amount, seconds = seconds)
    private fun lapse(kind: LapseKind, day: LocalDate, goalId: Long = 1, reason: Reason? = null, note: String? = null, forgotten: Boolean = false, period: Period? = Period.DAILY, periodStart: LocalDate = day) =
        Lapse(id = nextId++, goalId = goalId, kind = kind, day = day, period = period, periodStart = periodStart, at = nextId, overLimit = false, reason = reason, note = note, forgotten = forgotten, label = "x")
    private fun entry(kind: EntryKind, amount: Int, day: LocalDate, goalId: Long? = 1) = Entry(at = nextId++, day = day, kind = kind, amount = amount, goalId = goalId, label = "x")
    private fun page(goal: Goal, ticks: List<Tick>, lapses: List<Lapse> = emptyList(), entries: List<Entry> = emptyList(), steps: List<Step> = emptyList(), from: LocalDate? = start) =
        GoalPage.of(goal, today, steps, ticks, lapses, entries) { from }

    @Test
    fun theCalendarMarksDoneSkippedAndMissedDays() {
        val ticks = listOf(tick(today.minusDays(4)), tick(today.minusDays(1)))
        val lapses = listOf(
            lapse(LapseKind.MISS, today.minusDays(3), reason = Reason.NO_TIME),
            lapse(LapseKind.SKIP, today.minusDays(2)),
            // Said to be a forgotten tick: no longer a miss.
            lapse(LapseKind.MISS, today.minusDays(5), forgotten = true),
        )
        val view = page(read, ticks + tick(today.minusDays(5)), lapses)
        assertEquals(GoalDayMark.DONE, view.marks[today.minusDays(5)])
        assertEquals(GoalDayMark.DONE, view.marks[today.minusDays(4)])
        assertEquals(GoalDayMark.MISSED, view.marks[today.minusDays(3)])
        assertEquals(GoalDayMark.SKIPPED, view.marks[today.minusDays(2)])
        assertEquals(GoalDayMark.DONE, view.marks[today.minusDays(1)])
        // Today is still going: no mark yet.
        assertNull(view.marks[today])
        // Each mark has its own sign, so the days tell apart without colour.
        assertEquals(3, GoalDayMark.entries.map { it.mark }.toSet().size)
        // The newest skip or miss first, and the forgotten one left out.
        assertEquals(listOf(today.minusDays(2), today.minusDays(3)), view.lapses.map { it.day })
    }

    @Test
    fun aSkippedDayThatWasDoneAfterAllIsDone() {
        val day = today.minusDays(1)
        val view = page(read, listOf(tick(day)), listOf(lapse(LapseKind.SKIP, day)))
        assertEquals(GoalDayMark.DONE, view.marks[day])
        assertEquals(TimeState.DONE, view.times.single { it.day == day }.state)
    }

    @Test
    fun runsCountTimesDoneInARowAndASkipNeitherAddsNorEnds() {
        // Counting from eight days ago: done, done, missed, done, skipped, done, done, done, and today still open.
        val from = today.minusDays(8)
        val done = listOf(8, 7, 5, 3, 2, 1).map { tick(today.minusDays(it)) }
        val lapses = listOf(lapse(LapseKind.MISS, today.minusDays(6)), lapse(LapseKind.SKIP, today.minusDays(4)))
        val view = page(read, done, lapses, from = from)
        assertEquals(4, view.currentRun)
        assertEquals(4, view.bestRun)
        // Done today too: it counts straight away.
        assertEquals(5, page(read, done + tick(today), lapses, from = from).currentRun)
        // A day gone by with nothing recorded ends the run, like a miss.
        assertEquals(1, page(read, listOf(tick(today.minusDays(3)), tick(today.minusDays(1))), from = from).currentRun)
        assertEquals(1, page(read, listOf(tick(today.minusDays(3)), tick(today.minusDays(1))), from = from).bestRun)
    }

    @Test
    fun doneOfDueCountsASkipAsDueAndNeverDoneAndTodayOnceItIsDone() {
        val from = today.minusDays(9)
        // Ten days before today: 7 done, 1 skipped, 2 missed.
        val done = (1..7).map { tick(today.minusDays(it)) }
        val lapses = listOf(lapse(LapseKind.SKIP, today.minusDays(8)), lapse(LapseKind.MISS, today.minusDays(9)), lapse(LapseKind.MISS, today.minusDays(10)))
        val view = page(read, done, lapses, from = from)
        // Counted only from the day it was added: the miss before that isn't there.
        assertEquals(GoalRate(7, 9), view.last30)
        assertEquals(77, view.last30?.percent)
        assertEquals(GoalRate(7, 9), view.last90)
        assertEquals(GoalRate(8, 10), page(read, done + tick(today), lapses, from = from).last30)
        // Nothing due: no percentage to show.
        assertNull(GoalRate(0, 0).percent)
    }

    @Test
    fun theLast30DaysLeaveOutOlderOnes() {
        val done = (1..40).map { tick(today.minusDays(it)) }
        val view = page(read, done)
        assertEquals(GoalRate(29, 29), view.last30)
        assertEquals(GoalRate(40, 42), view.last90) // 1 and 2 October, before the first tick, weren't done
        assertEquals(40, view.bestRun)
        assertEquals(40, view.ticks)
    }

    @Test
    fun itsNumbersMatchTheReviewOverTheSameDays() {
        val from = today.minusDays(20)
        val done = listOf(20, 19, 17, 16, 15, 12, 9, 8, 7, 3, 2, 1).map { tick(today.minusDays(it)) }
        val lapses = listOf(lapse(LapseKind.MISS, today.minusDays(18)), lapse(LapseKind.SKIP, today.minusDays(14)), lapse(LapseKind.MISS, today.minusDays(13)))
        val view = page(read, done, lapses, from = from)
        // Up to yesterday, as today is still going.
        val review = Review.summarize(from, today.minusDays(1), today, listOf(read), done, lapses, emptyList(), emptyList()) { from }.goals.single()
        assertEquals(review.done, view.last30?.done)
        assertEquals(review.due, view.last30?.due)
        assertEquals(review.longestRun, view.bestRun)
    }

    @Test
    fun ticksPointsNotesAndAmountsAddUp() {
        val pages = Goal(id = 1, kind = GoalKind.HABIT, name = "Reading", icon = "📖", points = 5, repeat = Repeat.everyDay(start), amount = 20.0, unit = "pages")
        val ticks = listOf(
            tick(today.minusDays(2), amount = 12.0, seconds = 1500, note = "slow chapter"),
            tick(today.minusDays(2), amount = 8.0, seconds = 900),
            tick(today.minusDays(1), amount = 25.5, note = "couldn't stop"),
            tick(today.minusDays(1), goalId = 2), // another goal's
        )
        val entries = listOf(
            entry(EntryKind.TICK, 5, today.minusDays(2)), entry(EntryKind.BONUS, 2, today.minusDays(2)), entry(EntryKind.CHALLENGE, 50, today.minusDays(1)),
            entry(EntryKind.PENALTY, -2, today.minusDays(3)), entry(EntryKind.TICK, 9, today, goalId = 2),
        )
        val view = page(pages, ticks, entries = entries)
        assertEquals(3, view.ticks)
        assertEquals(57, view.points)
        assertEquals(45.5, view.amount)
        assertEquals(2400, view.seconds)
        assertEquals("45.5 pages", GoalPage.amountText(view.amount!!, pages.unit))
        assertEquals("40 min", GoalPage.timeText(view.seconds))
        assertEquals("9 h 40 min", GoalPage.timeText(9 * 3600 + 40 * 60 + 12))
        assertEquals("2 h", GoalPage.timeText(7200))
        assertEquals(listOf("couldn't stop", "slow chapter"), view.notes.map { it.note })
        // 12 + 8 reached the 20 pages that day.
        assertEquals(GoalDayMark.DONE, view.marks[today.minusDays(2)])
        // A goal that is simply ticked has no amount.
        assertNull(page(read, listOf(tick(today))).amount)
    }

    @Test
    fun aGoalCountedPerWeekShowsEachDayItWasTickedAndCountsOnSunday() {
        val weekly = Goal(id = 1, kind = GoalKind.HABIT, name = "Swim", icon = "🏊", points = 5, repeat = Repeat.timesPer(Period.WEEKLY, start), target = 2)
        val lastMonday = LocalDate(2026, 11, 2)
        val ticks = listOf(
            tick(lastMonday, period = Period.WEEKLY, periodStart = lastMonday),
            tick(lastMonday.plusDays(3), period = Period.WEEKLY, periodStart = lastMonday),
            // The week before: once of twice, and missed when it ended.
            tick(lastMonday.minusDays(5), period = Period.WEEKLY, periodStart = lastMonday.minusDays(7)),
        )
        val lapses = listOf(lapse(LapseKind.MISS, lastMonday.minusDays(1), period = Period.WEEKLY, periodStart = lastMonday.minusDays(7)))
        val view = page(weekly, ticks, lapses, from = lastMonday.minusDays(7))
        assertEquals(GoalDayMark.DONE, view.marks[lastMonday])
        assertEquals(GoalDayMark.DONE, view.marks[lastMonday.plusDays(3)])
        assertEquals(GoalDayMark.DONE, view.marks[lastMonday.minusDays(5)])
        assertEquals(GoalDayMark.MISSED, view.marks[lastMonday.minusDays(1)])
        // Two weeks ended: one done, one missed. This week is still going.
        assertEquals(listOf(TimeState.MISSED, TimeState.DONE), view.times.map { it.state })
        assertEquals(GoalRate(1, 2), view.last30)
        assertEquals(1, view.currentRun)
    }

    @Test
    fun aProjectShowsItsStepsAndWhenEachWasDone() {
        val shed = Goal(id = 3, kind = GoalKind.PROJECT, name = "Paint the shed", icon = "🎨", points = 0, repeat = null, bonus = 20, deadline = today.minusDays(1))
        val steps = listOf(Step(id = 11, goalId = 3, name = "Sand", points = 5, position = 0), Step(id = 12, goalId = 3, name = "Paint", points = 5, position = 1), Step(id = 13, goalId = 3, name = "Old", points = 5, position = 2, archived = true))
        val first = tick(today.minusDays(6), goalId = 3, stepId = 11)
        val deadline = lapse(LapseKind.MISS, today.minusDays(1), goalId = 3, period = null)
        val view = page(shed, listOf(first), listOf(deadline), listOf(entry(EntryKind.TICK, 5, today.minusDays(6), goalId = 3)), steps, from = null)
        assertEquals(listOf("Sand" to today.minusDays(6), "Paint" to null), view.steps.map { it.step.name to it.day })
        assertNull(view.finishedOn)
        assertNull(view.currentRun)
        assertNull(view.last30)
        assertEquals(1, view.ticks)
        assertEquals(GoalDayMark.DONE, view.marks[today.minusDays(6)])
        assertEquals(GoalDayMark.MISSED, view.marks[today.minusDays(1)])
        // The calendar goes back to the first day with anything on it.
        assertEquals(today.minusDays(6), view.firstDay)

        val finished = page(shed, listOf(first, tick(today, goalId = 3, stepId = 12)), steps = steps)
        assertEquals(today, finished.finishedOn)
    }

    @Test
    fun theMonthStartsOnTheRightWeekday() {
        // 1 November 2026 is a Sunday: six blanks before it.
        val cells = GoalPage.monthCells(LocalDate(2026, 11, 12))
        assertEquals(6, cells.takeWhile { it == null }.size)
        assertEquals(30, cells.filterNotNull().size)
        assertEquals(LocalDate(2026, 11, 1), cells.filterNotNull().first())
    }

    @Test
    fun theReviewListsWhatWasFinishedInTheStretch() {
        val from = LocalDate(2026, 11, 1)
        val to = LocalDate(2026, 11, 30)
        val shed = Goal(id = 3, kind = GoalKind.PROJECT, name = "Paint the shed", icon = "🎨", points = 0, repeat = null)
        val fence = Goal(id = 4, kind = GoalKind.PROJECT, name = "Fix the fence", icon = "🔨", points = 0, repeat = null, deadline = LocalDate(2026, 11, 5))
        val old = Goal(id = 5, kind = GoalKind.PROJECT, name = "Tidy the loft", icon = "📦", points = 0, repeat = null)
        val milk = Goal(id = 6, kind = GoalKind.TODO, name = "Buy milk", icon = "🥛", points = 2, repeat = null)
        val secret = Goal(id = 7, kind = GoalKind.TODO, name = "Hidden", icon = "🤫", points = 2, repeat = null, private = true)
        val reading = Goal(id = 8, kind = GoalKind.HABIT, name = "Reading", icon = "📖", points = 5, repeat = Repeat.everyDay(start), amount = 20.0, unit = "pages")
        val steps = listOf(
            Step(id = 31, goalId = 3, name = "Sand", points = 5, position = 0), Step(id = 32, goalId = 3, name = "Paint", points = 5, position = 1),
            Step(id = 41, goalId = 4, name = "Posts", points = 5, position = 0), Step(id = 51, goalId = 5, name = "Boxes", points = 5, position = 0),
        )
        val ticks = listOf(
            tick(LocalDate(2026, 10, 28), goalId = 3, stepId = 31), tick(LocalDate(2026, 11, 3), goalId = 3, stepId = 32),
            tick(LocalDate(2026, 10, 20), goalId = 5, stepId = 51), // achieved last month
            tick(LocalDate(2026, 11, 4), goalId = 6, period = null), tick(LocalDate(2026, 11, 4), goalId = 7, period = null),
            tick(LocalDate(2026, 11, 2), goalId = 8, amount = 300.0, seconds = 9 * 3600), tick(LocalDate(2026, 11, 9), goalId = 8, amount = 12.0, seconds = 40 * 60),
            tick(LocalDate(2026, 10, 30), goalId = 8, amount = 99.0), // before the stretch
        )
        val lapses = listOf(lapse(LapseKind.MISS, LocalDate(2026, 11, 5), goalId = 4, period = null))
        val goals = listOf(shed, fence, old, milk, secret, reading)

        val done = StretchDoneList.of(from, to, goals, steps, ticks, lapses)
        assertEquals(listOf("Paint the shed", "Fix the fence"), done.projects.map { it.goal.name })
        assertEquals(LocalDate(2026, 11, 3), done.projects[0].finishedOn)
        assertNull(done.projects[0].missedDeadline)
        // A missed deadline is shown against its project.
        assertNull(done.projects[1].finishedOn)
        assertEquals(LocalDate(2026, 11, 5), done.projects[1].missedDeadline)
        assertEquals(setOf("Buy milk", "Hidden"), done.todos.map { it.goal.name }.toSet())
        assertEquals("Reading: 312 pages, 9 h 40 min", done.amounts.single().text)

        // Private goals are left out while they are locked.
        assertEquals(listOf("Buy milk"), StretchDoneList.of(from, to, goals, steps, ticks, lapses, hidePrivate = true).todos.map { it.goal.name })
        // Nothing timed: only how much.
        assertEquals("Reading: 99 pages", StretchDoneList.of(LocalDate(2026, 10, 1), LocalDate(2026, 10, 31), listOf(reading), steps, ticks, lapses).amounts.single().text)
        assertTrue(StretchDoneList.of(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30), goals, steps, ticks, lapses).isEmpty)
    }
}
