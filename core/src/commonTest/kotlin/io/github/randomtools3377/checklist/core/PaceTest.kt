package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PaceTest {
    private val day = LocalDate(2026, 11, 11)
    private val twoPm = LocalDateTime(2026, 11, 11, 14, 0)
    private val tenPm = PaceSettings(dayEndHour = 0, defaultFinish = LocalTime(22, 0))

    private fun row(id: Long, target: Int = 1, count: Int = 0, minutes: Int? = null, at: LocalTime? = null, finishBy: LocalTime? = null) = HabitRow(
        Goal(
            id = id, kind = GoalKind.HABIT, name = "G$id", icon = "⭐", points = 1, repeat = Repeat.everyDay(day),
            target = target, minutes = minutes, at = at, finishBy = finishBy,
        ),
        count,
        Window(day, day, Period.DAILY),
    )

    @Test
    fun untimedGoalsShareTheTimeLeftEvenly() {
        // 2pm to 10pm is 480 minutes; gym takes 60; three others share 420.
        val pace = Pace.today(twoPm, day, listOf(row(1, minutes = 60), row(2), row(3), row(4)), tenPm)!!
        assertEquals(3, pace.untimedLeft)
        assertEquals(140, pace.minutesEach)
        assertEquals(60, pace.reservedMinutes)
        assertEquals(LocalDateTime(2026, 11, 11, 16, 20), pace.nextBy)
        assertEquals(0, pace.shortBy)
    }

    @Test
    fun countedGoalsShareTimeTickByTick() {
        // 8 glasses with 3 done: 5 ticks left in 480 minutes.
        val pace = Pace.today(twoPm, day, listOf(row(1, target = 8, count = 3)), tenPm)!!
        assertEquals(96, pace.minutesEach)
    }

    @Test
    fun anEarlierFinishByTightensThePace() {
        // One must be done by 3pm: it gets at most 60 minutes, so that is the pace.
        val pace = Pace.today(twoPm, day, listOf(row(1, finishBy = LocalTime(15, 0)), row(2)), tenPm)!!
        assertEquals(60, pace.minutesEach)
    }

    @Test
    fun notEnoughTimeIsReported() {
        val pace = Pace.today(twoPm, day, listOf(row(1, minutes = 600)), tenPm)!!
        assertEquals(120, pace.shortBy)
        assertNull(pace.minutesEach)
    }

    @Test
    fun noFinishTimeMeansNoPace() {
        val noDefault = PaceSettings(dayEndHour = 0, defaultFinish = null)
        assertNull(Pace.today(twoPm, day, listOf(row(1)), noDefault))
        val pace = Pace.today(twoPm, day, listOf(row(1), row(2, finishBy = LocalTime(20, 0))), noDefault)!!
        assertEquals(1, pace.unpaced)
    }

    @Test
    fun aFinishTimeAfterMidnightBelongsToTheNightWhenTheDayEndsLater() {
        val settings = PaceSettings(dayEndHour = 4, defaultFinish = LocalTime(1, 0))
        val pace = Pace.today(twoPm, day, listOf(row(1)), settings)!!
        assertEquals(LocalDateTime(2026, 11, 12, 1, 0), pace.finishAt)
        assertEquals(660, pace.minutesEach)
    }

    @Test
    fun timesPerAndProjectsArePacedOnlyWithAFinishDate() {
        val week = Window(LocalDate(2026, 11, 9), LocalDate(2026, 11, 15), Period.WEEKLY)
        val gym = Goal(id = 1, kind = GoalKind.HABIT, name = "Gym", icon = "⭐", points = 1, repeat = Repeat.timesPer(Period.WEEKLY, day), target = 3)
        assertNull(Pace.forHabit(HabitRow(gym, 1, week), day))

        // Finish by Saturday: Wednesday to Saturday is 4 days for 2 more sessions.
        val bySaturday = Pace.forHabit(HabitRow(gym.copy(finishDay = 6), 1, week), day)!!
        assertEquals(LongPace(left = 2, daysLeft = 4, finish = LocalDate(2026, 11, 14)), bySaturday)
        assertEquals("2 left · about one every 2 days", bySaturday.describe())

        val project = Goal(id = 2, kind = GoalKind.PROJECT, name = "P", icon = "⭐", points = 0, repeat = null)
        val steps = (1..6).map { StepRow(Step(id = it.toLong(), goalId = 2, name = "S$it", points = 1, position = it), done = false) }
        assertNull(Pace.forProject(ProjectRow(project, steps, null), day))
        val withDeadline = Pace.forProject(ProjectRow(project.copy(deadline = LocalDate(2026, 11, 13)), steps, null), day)!!
        assertEquals("6 left · about 2 a day", withDeadline.describe())
    }

    @Test
    fun aPassedFinishDateIsSaidPlainly() {
        assertEquals("1 left · the finish date has passed", LongPace(1, 0, day).describe())
    }
}
