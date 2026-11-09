package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Habits done several times a day: a time for each one, or spread over the day; and the morning plan. */
class RepetitionsTest {
    private val day = LocalDate(2026, 11, 11)
    private val eightAm = LocalDateTime(2026, 11, 11, 8, 0)
    private val settings = PaceSettings(dayEndHour = 0, defaultFinish = LocalTime(21, 0), dayStart = LocalTime(8, 0))

    private fun goal(id: Long = 1, target: Int = 2, times: List<LocalTime> = emptyList(), finishBy: LocalTime? = null, minutes: Int? = null) = Goal(
        id = id, kind = GoalKind.HABIT, name = "G$id", icon = "💊", points = 2, repeat = Repeat.everyDay(day.minusDays(5)),
        target = target, times = times, finishBy = finishBy, minutes = minutes,
    )

    private fun row(goal: Goal, count: Int = 0) = HabitRow(goal, count, Window(day, day, Period.DAILY))

    @Test
    fun withoutSetTimesTheyAreSpreadFromTheDayStartToTheFinish() {
        // 8 am to 9 pm is 13 hours: the first by 2:30 pm, the second by 9 pm.
        val slots = Pace.slots(goal(), day, settings)
        assertEquals(listOf(LocalDateTime(2026, 11, 11, 14, 30), LocalDateTime(2026, 11, 11, 21, 0)), slots.map { it.at })
        assertTrue(slots.none { it.fixed })
        assertEquals(LocalDateTime(2026, 11, 11, 21, 0), Pace.nextSlot(row(goal(), count = 1), day, settings)!!.at)
    }

    @Test
    fun aGoalsOwnFinishTimeIsUsed() {
        val slots = Pace.slots(goal(target = 3, finishBy = LocalTime(14, 0)), day, settings)
        assertEquals(listOf(LocalTime(10, 0), LocalTime(12, 0), LocalTime(14, 0)), slots.map { it.at.time })
    }

    @Test
    fun nothingIsSpreadUntilTheDayStartIsSet() {
        assertTrue(Pace.slots(goal(), day, settings.copy(dayStart = null)).isEmpty())
        assertTrue(Pace.slots(goal(), day, settings.copy(defaultFinish = null)).isEmpty())
        assertTrue(Pace.slots(goal(target = 1), day, settings).isEmpty())
    }

    @Test
    fun setTimesComeInOrderThroughTheDay() {
        val g = goal(times = listOf(LocalTime(21, 0), LocalTime(9, 0)))
        val slots = Pace.slots(g, day, settings)
        assertEquals(listOf(1 to LocalTime(9, 0), 2 to LocalTime(21, 0)), slots.map { it.number to it.at.time })
        assertTrue(slots.all { it.fixed })
        assertNull(Pace.nextSlot(row(g, count = 2), day, settings))
    }

    @Test
    fun aSetTimeAfterMidnightBelongsToTheNightAfter() {
        val late = settings.copy(dayEndHour = 4)
        val slots = Pace.slots(goal(times = listOf(LocalTime(1, 0), LocalTime(20, 0))), day, late)
        assertEquals(listOf(LocalDateTime(2026, 11, 11, 20, 0), LocalDateTime(2026, 11, 12, 1, 0)), slots.map { it.at })
    }

    @Test
    fun theirPaceGoesOneRepetitionAtATime() {
        // Two left, due by 9 am and 9 pm. From 8 am the first has only an hour.
        val pace = Pace.today(eightAm, day, listOf(row(goal(times = listOf(LocalTime(9, 0), LocalTime(21, 0))))), settings)!!
        assertEquals(60, pace.minutesEach)
        assertEquals(LocalDateTime(2026, 11, 11, 21, 0), pace.finishAt)
    }

    @Test
    fun theRecommendedOrderPutsASetTimeAtThatTime() {
        val medicine = goal(id = 1, times = listOf(LocalTime(9, 0), LocalTime(21, 0)))
        val walk = goal(id = 2, target = 1, finishBy = LocalTime(12, 0))
        val noon = LocalDateTime(2026, 11, 11, 12, 30)
        val rows = listOf(row(medicine, count = 1), row(walk))
        val plan = Order.recommend(noon, day, rows, settings, emptyMap(), Pace.today(noon, day, rows, settings))
        val first = plan.first { it.row.goal.id == 1L }
        assertTrue(first.fixed)
        assertEquals(LocalDateTime(2026, 11, 11, 21, 0), first.time)
        // The walk was due by noon: late, so it comes before the 9 pm one.
        assertEquals(2L, plan.first().row.goal.id)
    }

    @Test
    fun eachSetTimeHasItsOwnReminder() {
        val g = goal(times = listOf(LocalTime(9, 0), LocalTime(21, 0))).copy(reminder = Reminder(onTime = true, minutesBefore = 10))
        val alerts = Alerts.between(listOf(g), LocalDateTime(2026, 11, 11, 0, 0), LocalDateTime(2026, 11, 12, 0, 0), dayEndHour = 0)
        assertEquals(
            listOf(
                Triple(LocalTime(8, 50), AlertKind.BEFORE, 1),
                Triple(LocalTime(9, 0), AlertKind.ON_TIME, 1),
                Triple(LocalTime(20, 50), AlertKind.BEFORE, 2),
                Triple(LocalTime(21, 0), AlertKind.ON_TIME, 2),
            ),
            alerts.map { Triple(it.at.time, it.kind, it.number) },
        )
    }

    @Test
    fun spreadTimesDontRemind() {
        val alerts = Alerts.between(listOf(goal()), LocalDateTime(2026, 11, 11, 0, 0), LocalDateTime(2026, 11, 12, 0, 0), dayEndHour = 0)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun theMorningPlanComesEachDayWhenTheDayStarts() {
        val mornings = Alerts.mornings(LocalDateTime(2026, 11, 11, 9, 0), LocalDateTime(2026, 11, 13, 9, 0), LocalTime(8, 0), dayEndHour = 0)
        assertEquals(listOf(LocalDateTime(2026, 11, 12, 8, 0), LocalDateTime(2026, 11, 13, 8, 0)), mornings.map { it.at })
        assertTrue(mornings.all { it.kind == AlertKind.MORNING && it.goalId == Alerts.MORNING_GOAL })
    }

    @Test
    fun theMorningPlanSaysWhatIsDueAndWhatToDoFirst() {
        val medicine = goal(id = 1, times = listOf(LocalTime(9, 0), LocalTime(21, 0)))
        val walk = goal(id = 2, target = 1)
        val view = TodayList.build(day, listOf(medicine, walk), emptyList(), emptyList(), balance = 0)
        val plan = MorningPlan.of(eightAm, view, settings)!!
        assertEquals(2, plan.goals)
        assertEquals(1L, plan.first!!.row.goal.id)
        assertEquals(LocalTime(9, 0), plan.first!!.time!!.time)
        assertEquals(0, plan.alsoOpen)
    }

    @Test
    fun aDayWithNothingDueHasNoMorningPlan() {
        val view = TodayList.build(day, emptyList(), emptyList(), emptyList(), balance = 0)
        assertNull(MorningPlan.of(eightAm, view, settings))
    }

    @Test
    fun setTimesTravelInTheBackup() {
        val g = goal(times = listOf(LocalTime(9, 0), LocalTime(21, 0)))
        val backup = Backup(
            exportedAt = 0, goals = listOf(g), steps = emptyList(), ticks = emptyList(), entries = emptyList(),
            rewards = emptyList(), purchases = emptyList(), settings = emptyMap(),
        )
        assertEquals(g, Backup.decode(Backup.encode(backup)).goals.single())
    }
}
