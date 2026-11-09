package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChainsTest {
    private val monday = LocalDate(2026, 11, 9)

    @Test
    fun theNextGoalInAChainIsTheOneStillToDo() {
        val coffee = Goal(id = 1, kind = GoalKind.HABIT, name = "Coffee", icon = "☕", points = 1, repeat = Repeat.everyDay(monday))
        val journal = Goal(id = 2, kind = GoalKind.HABIT, name = "Journal", icon = "📓", points = 2, repeat = Repeat.everyDay(monday), after = 1)
        val walk = Goal(id = 3, kind = GoalKind.HABIT, name = "Walk", icon = "🚶", points = 2, repeat = Repeat.everyDay(monday), after = 2)
        val goals = listOf(coffee, journal, walk)
        val view = TodayList.build(monday, goals, emptyList(), emptyList(), 0)
        assertEquals(listOf("Journal"), Chains.next(1, view).map { it.goal.name })
        val done = Tick(goalId = 2, stepId = null, at = 0, day = monday, period = Period.DAILY, periodStart = monday)
        assertTrue(Chains.next(1, TodayList.build(monday, goals, emptyList(), listOf(done), 0)).isEmpty())
        // Walk can come after either; Coffee after neither, since both lead back to it.
        assertEquals(listOf("Coffee", "Journal"), Chains.choices(walk, goals).map { it.name })
        assertEquals(emptyList(), Chains.choices(coffee, goals).map { it.name })
    }

    @Test
    fun aGoalWithNoTimeIsRemindedAtItsUsualTime() {
        val read = Goal(id = 1, kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 1, repeat = Repeat.everyDay(monday))
        val usual = UsualTimes.of(mapOf(1L to listOf(21 * 60, 21 * 60 + 30, 20 * 60 + 45, 21 * 60 + 10, 22 * 60), 2L to listOf(9 * 60)))
        assertEquals(mapOf(1L to LocalTime(21, 10)), usual) // the middle one; 2 has too few to trust
        val alerts = Alerts.between(listOf(read), LocalDateTime(2026, 11, 9, 0, 0), LocalDateTime(2026, 11, 9, 23, 0), 0, usual = usual)
        assertEquals(listOf(LocalDateTime(2026, 11, 9, 21, 10)), alerts.map { it.at })
        assertEquals(AlertKind.USUAL, alerts.single().kind)
        // A goal with its own reminder keeps that instead.
        val reminded = read.copy(reminder = Reminder(at = LocalTime(8, 0)))
        assertEquals(AlertKind.REMINDER, Alerts.between(listOf(reminded), LocalDateTime(2026, 11, 9, 0, 0), LocalDateTime(2026, 11, 9, 23, 0), 0, usual = usual).single().kind)
    }
}
