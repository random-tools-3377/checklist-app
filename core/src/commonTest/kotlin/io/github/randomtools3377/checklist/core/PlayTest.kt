package io.github.randomtools3377.checklist.core

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayTest {
    private val monday = LocalDate(2026, 11, 9)
    private fun goal(id: Long, name: String, icon: String = "⭐", points: Int = 5) =
        Goal(id = id, kind = GoalKind.HABIT, name = name, icon = icon, points = points, repeat = Repeat.everyDay(monday))
    private val goals = listOf(goal(1, "Morning run", "🏃"), goal(2, "Gym", "🏋️"), goal(3, "Read", "📖"), goal(4, "Drink water", "💧"))

    @Test
    fun theDaysQuestsAreTheSameAllDayAndDifferOtherDays() {
        val today = Quests.of(monday, goals)
        assertEquals(3, today.size)
        assertEquals(today, Quests.of(monday, goals))
        assertEquals(listOf(0, 1, 2), today.map { it.id })
        assertEquals(3, today.map { it.kind }.distinct().size)
        val week = (0..6).map { Quests.of(monday.plusDays(it), goals).map { q -> q.kind } }
        assertTrue(week.distinct().size > 1, "quests change from day to day")
        assertTrue(Quests.of(monday, emptyList()).isEmpty())
    }

    private fun row(goal: Goal, done: Boolean) = HabitRow(goal, if (done) 1 else 0, Window(monday, monday, Period.DAILY))
    private fun tick(goalId: Long, at: Long) = Tick(goalId = goalId, stepId = null, at = at, day = monday, period = Period.DAILY, periodStart = monday)
    private val timeOf: (Long) -> LocalTime = { minutes -> LocalTime((minutes / 60).toInt(), (minutes % 60).toInt()) }

    @Test
    fun questsCountWhatWasDone() {
        val early = Quest(0, QuestKind.EARLY, "Tick 2 goals before noon", 2, 5, by = LocalTime(12, 0))
        val ticks = listOf(tick(1, 8 * 60L), tick(2, 13 * 60L))
        val rows = listOf(row(goals[0], true), row(goals[1], true), row(goals[2], false))
        assertEquals(QuestProgress(early, 1, false), Quests.progress(early, rows, ticks, timeOf, 10))
        assertTrue(Quests.progress(early, rows, ticks + tick(3, 11 * 60L), timeOf, 15).complete)

        val area = Quest(1, QuestKind.AREA, "Do all 2 Fitness goals", 2, 5, area = SkillArea.FITNESS)
        assertTrue(Quests.progress(area, rows, ticks, timeOf, 10).complete)

        val allBy = Quest(2, QuestKind.ALL_BY, "Finish everything due by 8 pm", 2, 10, by = LocalTime(20, 0))
        val allDone = listOf(row(goals[0], true), row(goals[1], true))
        assertTrue(Quests.progress(allBy, allDone, ticks, timeOf, 10).complete)
        assertFalse(Quests.progress(allBy, allDone, ticks + tick(2, 21 * 60L), timeOf, 10).complete, "the last one after 8 pm")

        val points = Quest(0, QuestKind.POINTS, "Earn 20 points from goals", 20, 5)
        assertEquals(QuestProgress(points, 20, true), Quests.progress(points, rows, ticks, timeOf, 25))
    }

    @Test
    fun paidQuestsAreRememberedForTheirDayOnly() {
        val text = Quests.encodePaid(monday, setOf(2, 0))
        assertEquals("2026-11-09:0,2", text)
        assertEquals(setOf(0, 2), Quests.paidOn(text, monday))
        assertTrue(Quests.paidOn(text, monday.plusDays(1)).isEmpty())
        assertTrue(Quests.paidOn(null, monday).isEmpty())
    }

    @Test
    fun aComboGrowsWithEachPerfectDayAndEndsOnAMiss() = runTest {
        val store = MemoryStore()
        val clock = TestClock(LocalDateTime(2026, 11, 9, 10, 0))
        val book = Book(store, clock) { kotlinx.datetime.TimeZone.UTC }
        val combos = Combos(store, book)
        store.putSetting(Feature.COMBOS.key, Feature.COMBOS.onValue)
        val read = store.addGoal(goal(0, "Read").copy(id = 0))
        assertTrue(combos.pay().isEmpty()) // starts looking from today

        book.tickHabit(read.id) // Monday perfect
        clock.set(LocalDateTime(2026, 11, 10, 10, 0))
        book.tickHabit(read.id) // Tuesday perfect
        clock.set(LocalDateTime(2026, 11, 11, 10, 0))
        assertEquals(listOf("Combo ×1" to 2, "Combo ×2" to 4), combos.pay().map { it.label to it.amount })
        assertTrue(combos.pay().isEmpty())

        // Wednesday left undone: the combo ends, and Thursday starts again.
        clock.set(LocalDateTime(2026, 11, 12, 10, 0))
        book.tickHabit(read.id)
        clock.set(LocalDateTime(2026, 11, 13, 10, 0))
        assertEquals(listOf("Combo ×1" to 2), combos.pay().map { it.label to it.amount })
        assertEquals(20, Combos.pay(25), "it stops growing at 10")
    }

    @Test
    fun theRaceIsAgainstLastWeekByTheSameTime() {
        val hour = 60L * 60 * 1000
        val week = 7 * 24 * hour
        val wednesday = monday.plusDays(2)
        val now = 1_000_000 * hour
        val entries = listOf(
            Entry(at = now - 2 * hour, day = wednesday, kind = EntryKind.TICK, amount = 30, label = "x"),
            Entry(at = now - 30 * hour, day = monday.plusDays(1), kind = EntryKind.BONUS, amount = 10, label = "x"),
            Entry(at = now - 1 * hour, day = wednesday, kind = EntryKind.PURCHASE, amount = -50, label = "x"),
            // Last week: one before the same moment, one after it.
            Entry(at = now - week - 3 * hour, day = wednesday.minusDays(7), kind = EntryKind.TICK, amount = 25, label = "x"),
            Entry(at = now - week + 3 * hour, day = wednesday.minusDays(7), kind = EntryKind.TICK, amount = 100, label = "x"),
        )
        val standing = Race.of(entries, wednesday, now)
        assertEquals(RaceStanding(40, 25), standing)
        assertEquals(15, standing.ahead)
    }
}
