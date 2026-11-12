package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The calendar widget's agenda of the next days and its month, and a project's deadline reminders. */
class CalendarAgendaTest {
    private val start = LocalDate(2026, 1, 1)
    private val wednesday = LocalDate(2026, 11, 11)
    private val noon = LocalDateTime(wednesday, LocalTime(12, 0))

    private fun habit(id: Long, name: String, at: LocalTime? = null, repeat: Repeat = Repeat.everyDay(start), target: Int = 1, private: Boolean = false) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = "⭐", points = 1, repeat = repeat, at = at, target = target, private = private, position = id.toInt())

    private fun project(id: Long, name: String, deadline: LocalDate?, reminder: Reminder? = null, private: Boolean = false) =
        Goal(id = id, kind = GoalKind.PROJECT, name = name, icon = "🎨", points = 0, repeat = null, deadline = deadline, reminder = reminder, private = private)

    private fun steps(goalId: Long, count: Int) = (1..count).map { Step(id = goalId * 100 + it, goalId = goalId, name = "Step $it", points = 1, position = it) }

    private fun tick(goalId: Long, day: LocalDate) = Tick(goalId = goalId, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day)

    private fun stepTick(step: Step, day: LocalDate) = Tick(goalId = step.goalId, stepId = step.id, at = 0, day = day, period = null, periodStart = day)

    private fun skip(goalId: Long, day: LocalDate) =
        Lapse(goalId = goalId, kind = LapseKind.SKIP, day = day, period = Period.DAILY, periodStart = day, at = 0, overLimit = false, label = "G$goalId")

    @Test
    fun daysAreGroupedUnderHeadersWithTodayFirst() {
        val water = habit(1, "Water")
        val gym = habit(2, "Gym", at = LocalTime(18, 0), repeat = Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(5)))
        val days = CalendarView.days(wednesday, noon, 7, listOf(water, gym), emptyList(), emptyList())
        assertEquals(listOf("Today", "Tomorrow", "Fri 13", "Sat 14", "Sun 15", "Mon 16", "Tue 17"), days.map { it.header })
        assertEquals(listOf("Water"), days[0].lines.map { it.name })
        // Friday: the timed one first.
        assertEquals(listOf("Gym", "Water"), days[2].lines.map { it.name })
        assertEquals(LocalTime(18, 0), days[2].lines[0].time)
        // Only today's lines can be ticked.
        assertTrue(days[0].lines.single().canTick)
        assertFalse(days[1].lines.single().canTick)
    }

    @Test
    fun aDayWithNothingOnItIsLeftOutButTodayStays() {
        val fridays = habit(1, "Gym", repeat = Repeat(RepeatKind.WEEKDAYS, start, weekdays = setOf(5)))
        val days = CalendarView.days(wednesday, noon, 7, listOf(fridays), emptyList(), emptyList())
        assertEquals(listOf("Today", "Fri 13"), days.map { it.header })
        assertTrue(days[0].lines.isEmpty())
    }

    @Test
    fun linesSayDoneSkippedAndLate() {
        val done = habit(1, "Done one")
        val skipped = habit(2, "Skipped one")
        val late = habit(3, "Late one", at = LocalTime(9, 0))
        val later = habit(4, "Later one", at = LocalTime(15, 0))
        val today = CalendarView.days(
            wednesday, noon, 1, listOf(done, skipped, late, later), emptyList(),
            ticks = listOf(tick(1, wednesday)), lapses = listOf(skip(2, wednesday)),
        ).single()
        val states = today.lines.associate { it.name to it.state }
        assertEquals(LineState.DONE, states["Done one"])
        assertEquals(LineState.SKIPPED, states["Skipped one"])
        assertEquals(LineState.LATE, states["Late one"])
        assertEquals(LineState.OPEN, states["Later one"])
        assertEquals("✓ done", today.lines.first { it.name == "Done one" }.stateWord)
        assertEquals("– skipped", today.lines.first { it.name == "Skipped one" }.stateWord)
        assertEquals("! late", today.lines.first { it.name == "Late one" }.stateWord)
        assertNull(today.lines.first { it.name == "Later one" }.stateWord)
        // A skipped one has nothing to tick, and is left out of what the day counts.
        assertFalse(today.lines.first { it.name == "Skipped one" }.canTick)
        assertEquals(3, today.due)
        assertEquals(1, today.done)
        // The same goal tomorrow is neither late nor done.
        val tomorrow = CalendarView.days(wednesday, noon, 2, listOf(late), emptyList(), emptyList())[1]
        assertEquals(LineState.OPEN, tomorrow.lines.single().state)
    }

    @Test
    fun aGoalDoneSeveralTimesCountsUpWithPlusOne() {
        val brush = habit(1, "Brush", target = 2)
        fun line(ticks: List<Tick>) = CalendarView.days(wednesday, noon, 1, listOf(brush), emptyList(), ticks).single().lines.single()
        assertEquals("0 of 2", line(emptyList()).detail)
        assertTrue(line(emptyList()).plusOne)
        assertEquals(1, line(listOf(tick(1, wednesday))).count)
        val done = line(listOf(tick(1, wednesday), tick(1, wednesday)))
        assertEquals(LineState.DONE, done.state)
        assertFalse(done.plusOne)
    }

    @Test
    fun eventsDeadlinesAndToDosShareTheList() {
        val friday = wednesday.plusDays(2)
        val walk = habit(1, "Walk", at = LocalTime(17, 0))
        val water = habit(2, "Water")
        val shed = project(3, "Paint the shed", deadline = friday)
        val dated = Goal(id = 4, kind = GoalKind.TODO, name = "Post the letter", icon = "✅", points = 2, repeat = Repeat(RepeatKind.ONCE, friday), position = 9)
        val undated = Goal(id = 5, kind = GoalKind.TODO, name = "Buy milk", icon = "✅", points = 2, repeat = null)
        val events = listOf(
            Busy(LocalDateTime(friday, LocalTime(0, 0)), LocalDateTime(friday.plusDays(1), LocalTime(0, 0)), "Holiday", allDay = true),
            Busy(LocalDateTime(friday, LocalTime(10, 0)), LocalDateTime(friday, LocalTime(11, 0)), "Dentist"),
        )
        val days = CalendarView.days(
            wednesday, noon, 7, listOf(walk, water, shed, dated, undated), steps(3, 3),
            ticks = listOf(stepTick(steps(3, 3)[0], wednesday)), events = events,
        )
        val fri = days.first { it.date == friday }
        assertEquals(listOf("Holiday", "Paint the shed", "Dentist", "Walk", "Water", "Post the letter"), fri.lines.map { it.name })
        assertEquals(
            listOf(LineKind.EVENT, LineKind.DEADLINE, LineKind.EVENT, LineKind.GOAL, LineKind.GOAL, LineKind.TODO),
            fri.lines.map { it.kind },
        )
        assertEquals("All day", fri.lines[0].detail)
        assertEquals("Deadline · 2 steps left", fri.lines[1].detail)
        assertEquals(LocalTime(10, 0) to LocalTime(11, 0), fri.lines[2].time to fri.lines[2].until)
        // Events and deadlines aren't counted as goals due.
        assertEquals(3, fri.due)
        // The to-do without a date waits on today's list only.
        assertEquals(listOf("Walk", "Water", "Buy milk"), days[0].lines.map { it.name })
        assertEquals("To-do", days[0].lines.last().detail)
        assertTrue(days.drop(1).none { day -> day.lines.any { it.name == "Buy milk" } })
    }

    @Test
    fun aFinishedProjectHasNoDeadlineLineOnLaterDays() {
        val friday = wednesday.plusDays(2)
        val shed = project(3, "Paint the shed", deadline = friday)
        val all = steps(3, 2)
        val days = CalendarView.days(wednesday, noon, 7, listOf(shed), all, ticks = all.map { stepTick(it, wednesday) })
        assertTrue(days.none { day -> day.lines.any { it.kind == LineKind.DEADLINE } })
        assertEquals(setOf(3L), CalendarView.finishedProjects(listOf(shed), all, all.map { stepTick(it, wednesday) }))
    }

    @Test
    fun goalsCountedPerWeekShowOnTodayWhileOpen() {
        val gym = habit(1, "Gym", repeat = Repeat.timesPer(Period.WEEKLY, start), target = 3)
        val days = CalendarView.days(wednesday, noon, 3, listOf(gym), emptyList(), emptyList())
        assertEquals(listOf("Today"), days.map { it.header })
        assertEquals("0 of 3 this week", days[0].lines.single().detail)
    }

    @Test
    fun aLateNightTimeSortsAfterTheEvening() {
        val night = habit(1, "Wind down", at = LocalTime(1, 0))
        val evening = habit(2, "Dinner", at = LocalTime(19, 0))
        val today = CalendarView.days(wednesday, noon, 1, listOf(night, evening), emptyList(), emptyList(), dayEndHour = 4).single()
        assertEquals(listOf("Dinner", "Wind down"), today.lines.map { it.name })
        // 1 am tonight hasn't come yet at noon.
        assertEquals(LineState.OPEN, today.lines[1].state)
    }

    @Test
    fun aLockedPrivateGoalIsOnlyAPrivateGoal() {
        val secret = habit(1, "Secret thing", at = LocalTime(9, 0), private = true).copy(color = 0xFF112233.toInt())
        val plan = project(2, "Secret plan", deadline = wednesday, private = true)
        val today = CalendarView.days(wednesday, noon, 1, listOf(secret, plan), steps(2, 2), emptyList(), hidden = setOf(1L, 2L)).single()
        assertEquals(listOf("🔒 A private goal", "🔒 A private goal"), today.lines.map { it.title })
        assertTrue(today.lines.all { it.hidden && !it.canTick && it.color == null })
        // The deadline still says it is one; nothing about its steps.
        assertEquals("Deadline", today.lines.first { it.kind == LineKind.DEADLINE }.detail)
        assertEquals(LocalTime(9, 0), today.lines.first { it.kind == LineKind.GOAL }.time)
        // Unlocked, they are named.
        val open = CalendarView.days(wednesday, noon, 1, listOf(secret, plan), steps(2, 2), emptyList()).single()
        assertEquals(setOf("Secret thing", "Secret plan"), open.lines.map { it.name }.toSet())
    }

    @Test
    fun theMonthMarksDeadlinesAndLeavesSkipsOut() {
        val a = habit(1, "A")
        val b = habit(2, "B")
        val shed = project(3, "Paint the shed", deadline = LocalDate(2026, 11, 20))
        val done = project(4, "Done already", deadline = LocalDate(2026, 11, 21))
        val monday = LocalDate(2026, 11, 9)
        val tuesday = LocalDate(2026, 11, 10)
        val month = CalendarView.month(
            wednesday, listOf(a, b, shed, done),
            ticks = listOf(tick(1, monday), tick(2, monday), tick(1, tuesday), tick(1, wednesday)),
            lapses = listOf(skip(2, tuesday)),
            finished = setOf(4L),
        )
        fun cell(day: LocalDate) = month.first { it.date == day }
        assertEquals(42, month.size)
        // Tuesday: B was skipped, so only A counts, and it was done.
        assertEquals(1 to 1, cell(tuesday).due to cell(tuesday).done)
        assertEquals(DayTone.GOOD, cell(tuesday).tone)
        assertEquals(1, cell(LocalDate(2026, 11, 20)).deadlines)
        assertEquals(0, cell(LocalDate(2026, 11, 21)).deadlines)
        assertTrue(cell(monday).isPast)
        assertFalse(cell(wednesday).isPast)
    }

    @Test
    fun aDaysCountSaysItInColourAndInMarks() {
        fun cell(due: Int, done: Int, past: Boolean = false, today: Boolean = false) =
            DayCell(wednesday, due, done, inMonth = true, isToday = today, isPast = past)
        // A past day: everything, some, nothing.
        assertEquals(DayTone.GOOD, cell(3, 3, past = true).tone)
        assertEquals("3/3", cell(3, 3, past = true).label(marks = false))
        assertEquals("✓3/3", cell(3, 3, past = true).label(marks = true))
        assertEquals(DayTone.BAD, cell(3, 1, past = true).tone)
        assertEquals("◐1/3", cell(3, 1, past = true).label(marks = true))
        assertEquals(DayTone.BAD, cell(3, 0, past = true).tone)
        assertEquals("✕0/3", cell(3, 0, past = true).label(marks = true))
        // Today isn't bad while it is still going.
        assertEquals(DayTone.PLAIN, cell(3, 1, today = true).tone)
        assertEquals("1/3", cell(3, 1, today = true).label(marks = false))
        assertEquals("0/3", cell(3, 0, today = true).label(marks = true))
        assertEquals(DayTone.GOOD, cell(2, 2, today = true).tone)
        // A day still to come says how many are due; nothing due says nothing.
        assertEquals("3", cell(3, 0).label(marks = true))
        assertEquals(DayTone.PLAIN, cell(3, 0).tone)
        assertEquals("", cell(0, 0, past = true).label(marks = true))
        assertEquals(DayTone.PLAIN, cell(0, 0, past = true).tone)
    }

    // Deadline reminders.

    private val friday = LocalDate(2026, 11, 13)
    private fun day(date: LocalDate) = LocalDateTime(date, LocalTime(0, 0))

    @Test
    fun aDeadlineRemindsTheDayBeforeAndOnTheDayWhenTheDayStarts() {
        val shed = project(1, "Paint the shed", deadline = friday)
        val alerts = Alerts.deadlines(listOf(shed), day(wednesday), day(friday.plusDays(2)), dayStart = LocalTime(7, 30), dayEndHour = 0)
        assertEquals(
            listOf(LocalDateTime(2026, 11, 12, 7, 30), LocalDateTime(2026, 11, 13, 7, 30)),
            alerts.map { it.at },
        )
        assertTrue(alerts.all { it.kind == AlertKind.DEADLINE && it.goalId == 1L })
        assertEquals("Due tomorrow, 3 steps left", Alerts.deadlineWords(shed, alerts[0], 3))
        assertEquals("Due today, 1 step left", Alerts.deadlineWords(shed, alerts[1], 1))
        assertEquals("Due today", Alerts.deadlineWords(shed, alerts[1], null))
        // Only inside the stretch asked for.
        assertEquals(1, Alerts.deadlines(listOf(shed), day(friday), day(friday.plusDays(1)), LocalTime(7, 30), 0).size)
    }

    @Test
    fun itsOwnTimeWinsAndWithoutAnyTimeThereIsNone() {
        val timed = project(1, "Timed", deadline = friday, reminder = Reminder(at = LocalTime(18, 0)))
        val plain = project(2, "Plain", deadline = friday)
        val from = day(wednesday)
        val to = day(friday.plusDays(1))
        assertEquals(
            listOf(1L to LocalDateTime(2026, 11, 12, 18, 0), 1L to LocalDateTime(2026, 11, 13, 18, 0)),
            Alerts.deadlines(listOf(timed, plain), from, to, dayStart = null, dayEndHour = 0).map { it.goalId to it.at },
        )
        // With the day's start set, the plain one comes then, and the timed one keeps its time.
        assertEquals(
            listOf(2L to LocalTime(8, 0), 1L to LocalTime(18, 0), 2L to LocalTime(8, 0), 1L to LocalTime(18, 0)),
            Alerts.deadlines(listOf(timed, plain), from, to, dayStart = LocalTime(8, 0), dayEndHour = 0).map { it.goalId to it.at.time },
        )
    }

    @Test
    fun itCanBeTurnedOffAndNeedsADeadline() {
        val off = project(1, "Off", deadline = friday, reminder = Reminder(onTime = false, at = LocalTime(9, 0)))
        val none = project(2, "No deadline", deadline = null)
        val removed = project(3, "Removed", deadline = friday).copy(archived = true)
        val habit = habit(4, "A habit")
        assertFalse(Alerts.remindsOfDeadline(off))
        assertTrue(Alerts.remindsOfDeadline(project(5, "On", deadline = friday)))
        assertTrue(Alerts.deadlines(listOf(off, none, removed, habit), day(wednesday), day(friday.plusDays(1)), LocalTime(8, 0), 0).isEmpty())
    }

    @Test
    fun nothingComesOnceTheProjectIsFinishedAndAPrivateOneIsNotNamed() {
        val shed = project(1, "Paint the shed", deadline = friday)
        val secret = project(2, "Secret plan", deadline = friday, private = true)
        val all = steps(1, 3) + steps(2, 2)
        val from = day(friday.minusDays(1))
        val to = day(friday)
        fun reminders(ticks: List<Tick>, hidePrivate: Boolean = true) =
            Alerts.deadlineReminders(listOf(shed, secret), all, ticks, from, to, LocalTime(8, 0), 0, hidePrivate)

        val first = reminders(emptyList())
        assertEquals(listOf("🎨 Paint the shed" to "Due tomorrow, 3 steps left", "🔒 A private goal" to "Due tomorrow"), first.map { it.title to it.text })
        assertEquals(friday.minusDays(1), first[0].day)
        // Unlocked, the private one is named like any other.
        assertEquals("🎨 Secret plan" to "Due tomorrow, 2 steps left", reminders(emptyList(), hidePrivate = false)[1].let { it.title to it.text })
        // One step done: one fewer left. All done: no reminder.
        assertEquals("Due tomorrow, 2 steps left", reminders(listOf(stepTick(all[0], wednesday)))[0].text)
        val finished = reminders(steps(1, 3).map { stepTick(it, wednesday) })
        assertEquals(listOf(2L), finished.map { it.goalId })
    }

    @Test
    fun aLateNightReminderBelongsToTheDayBefore() {
        val shed = project(1, "Paint the shed", deadline = friday, reminder = Reminder(at = LocalTime(1, 0)))
        val alerts = Alerts.deadlines(listOf(shed), day(wednesday), day(friday.plusDays(3)), null, dayEndHour = 4)
        // With the day ending at 4 am, Friday's 1 am is Saturday 1 am on the clock.
        assertEquals(listOf(LocalDateTime(2026, 11, 13, 1, 0), LocalDateTime(2026, 11, 14, 1, 0)), alerts.map { it.at })
        assertEquals("Due today", Alerts.deadlineWords(shed, alerts[1], null))
    }
}
