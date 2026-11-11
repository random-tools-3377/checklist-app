package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Challenge
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Lapse
import io.github.randomtools3377.checklist.core.LapseKind
import io.github.randomtools3377.checklist.core.Move
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.TodayList
import io.github.randomtools3377.checklist.core.Todos
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Copying, bringing back, reordering and editing goals, on the shared database code. */
class GoalsDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())

    // Wednesday 11 November 2026.
    private val today = LocalDate(2026, 11, 11)
    private val monday = LocalDate(2026, 11, 9)

    @AfterTest
    fun close() = db.close()

    private suspend fun goal(id: Long) = dao.goal(id)!!.toCore()

    private suspend fun insert(goal: Goal): Long = dao.insertGoal(goal.toEntity())

    private fun draft(id: Long, repeat: Repeat, target: Int = 1, challenge: Challenge? = null) = HabitDraft(
        id = id, name = "Gym", icon = "🏋️", points = 4, repeat = repeat, target = target, bonus = 0,
        minutes = null, at = null, finishBy = null, finishDay = null, challenge = challenge,
    )

    private fun tick(goalId: Long, day: LocalDate, period: Period, periodStart: LocalDate) =
        Tick(goalId = goalId, stepId = null, at = 0, day = day, period = period, periodStart = periodStart).toEntity()

    @Test
    fun aCopyStartsTodayAsANewGoal() = runTest {
        val id = insert(
            Goal(
                kind = GoalKind.HABIT, name = "Stretch", icon = "🧘", points = 3, createdAt = 1,
                repeat = Repeat(
                    RepeatKind.EVERY_N_DAYS, LocalDate(2026, 1, 1), end = LocalDate(2026, 10, 31), interval = 2,
                    moves = listOf(Move(LocalDate(2026, 10, 1), LocalDate(2026, 10, 2))),
                ),
                challenge = Challenge(days = 30, bonus = 50, from = LocalDate(2026, 10, 1)),
            ),
        )
        val copy = goal(repository.duplicateGoal(id, today)!!)
        assertEquals("Stretch (copy)", copy.name)
        assertEquals(today, copy.repeat!!.start)
        assertEquals(2, copy.repeat!!.interval)
        assertNull(copy.repeat!!.end) // the old end date had passed
        assertTrue(copy.repeat!!.moves.isEmpty())
        assertNull(copy.challenge)
        assertTrue(copy.createdAt > 1)
        // The original is as it was.
        assertEquals(LocalDate(2026, 1, 1), goal(id).repeat!!.start)

        // Every 3rd day from 1 January falls on 12 November, not today: the copy keeps those days.
        val third = insert(Goal(kind = GoalKind.HABIT, name = "Plants", icon = "🪴", points = 3, repeat = Repeat(RepeatKind.EVERY_N_DAYS, LocalDate(2026, 1, 1), interval = 3)))
        val thirdCopy = goal(repository.duplicateGoal(third, today)!!).repeat!!
        assertEquals(LocalDate(2026, 11, 12), thirdCopy.start)
        assertEquals((0..9).map { today.plusDays(it) }.filter { goal(third).repeat!!.isDueOn(it) }, (0..9).map { today.plusDays(it) }.filter { thirdCopy.isDueOn(it) })
    }

    @Test
    fun aGoalBroughtBackCountsFromNowButUndoPutsItBackAsItWas() = runTest {
        val id = insert(Goal(kind = GoalKind.HABIT, name = "Stretch", icon = "🧘", points = 3, createdAt = 1, archived = true, repeat = Repeat.everyDay(LocalDate(2026, 1, 1))))
        repository.restoreGoal(id, today, freshStart = false)
        assertEquals(1, goal(id).createdAt)
        repository.removeGoal(id)
        repository.restoreGoal(id, today)
        assertTrue(!goal(id).archived && goal(id).createdAt > 1)

        // A "since last done" goal whose day passed while it was away is due today.
        val plants = insert(
            Goal(kind = GoalKind.HABIT, name = "Water plants", icon = "🪴", points = 3, archived = true, repeat = Repeat(RepeatKind.AFTER_DONE, LocalDate(2026, 10, 1), interval = 3)),
        )
        repository.restoreGoal(plants, today)
        assertEquals(today, goal(plants).repeat!!.start)
    }

    @Test
    fun switchingToSingleDaysKeepsTodaysTick() = runTest {
        repository.saveHabit(draft(0, Repeat.timesPer(Period.WEEKLY, monday), target = 3), today)
        val id = dao.activeGoals().single().id
        dao.insertTicks(listOf(monday, monday.plusDays(1), today).map { tick(id, it, Period.WEEKLY, monday) })
        dao.insertLapse(
            Lapse(goalId = id, kind = LapseKind.SKIP, day = today, period = Period.WEEKLY, periodStart = monday, at = 0, overLimit = false, label = "Gym").toEntity(),
        )

        // Now Monday, Wednesday and Friday: today's tick counts toward today, and so does the skip.
        repository.saveHabit(draft(id, Repeat(RepeatKind.WEEKDAYS, monday, weekdays = setOf(1, 3, 5))), today)
        val ticks = dao.allTicks().map { it.toCore() }
        assertEquals(listOf(Period.WEEKLY to monday, Period.WEEKLY to monday, Period.DAILY to today), ticks.sortedBy { it.day }.map { it.period to it.periodStart })
        assertEquals(Period.DAILY to today, dao.allLapses().single().let { it.period?.let { p -> Period.valueOf(p) } to it.periodStart.toDay() })
        val row = TodayList.build(today, listOf(goal(id)), emptyList(), ticks, 0).dueToday.single()
        assertEquals(1, row.count)
        assertTrue(row.done)
    }

    @Test
    fun switchingToTimesAWeekKeepsTheWeeksTicks() = runTest {
        repository.saveHabit(draft(0, Repeat.everyDay(monday)), today)
        val id = dao.activeGoals().single().id
        dao.insertTicks(listOf(monday, monday.plusDays(1)).map { tick(id, it, Period.DAILY, it) })

        repository.saveHabit(draft(id, Repeat.timesPer(Period.WEEKLY, monday), target = 3), today)
        val ticks = dao.allTicks().map { it.toCore() }
        assertTrue(ticks.all { it.period == Period.WEEKLY && it.periodStart == monday })
        assertEquals(2, TodayList.build(today, listOf(goal(id)), emptyList(), ticks, 0).thisWeek.single().count)
    }

    @Test
    fun switchingToSinceLastDoneKeepsTodaysTick() = runTest {
        repository.saveHabit(draft(0, Repeat.timesPer(Period.WEEKLY, monday), target = 1), today)
        val id = dao.activeGoals().single().id
        dao.insertTicks(listOf(tick(id, today, Period.WEEKLY, monday)))

        // Now "3 days after it's done", next due today: today's tick counts, so it isn't due again.
        repository.saveHabit(draft(id, Repeat(RepeatKind.AFTER_DONE, today, interval = 3)), today)
        val ticks = dao.allTicks().map { it.toCore() }
        assertEquals(listOf(Period.DAILY to today), ticks.map { it.period to it.periodStart })
        assertTrue(TodayList.build(today, listOf(goal(id)), emptyList(), ticks, 0).dueToday.single().done)
    }

    @Test
    fun aGoalCountedPerWeekKeepsNoChallenge() = runTest {
        repository.saveHabit(draft(0, Repeat.timesPer(Period.WEEKLY, monday), target = 3, challenge = Challenge(30, 50, today)), today)
        assertNull(goal(dao.activeGoals().single().id).challenge)
        repository.saveHabit(draft(0, Repeat.everyDay(monday), challenge = Challenge(30, 50, today)), today)
        assertEquals(30, dao.activeGoals().mapNotNull { it.toCore().challenge }.single().days)
    }

    @Test
    fun aGoalMovesPastOnesTheListDoesNotShow() = runTest {
        // To-dos A (done last week, so not shown), B, C (also past) and D: the list shows B and D.
        val a = insert(Todos.of("A", date = LocalDate(2026, 11, 2), position = 0))
        val b = insert(Todos.of("B", position = 1))
        val c = insert(Todos.of("C", date = LocalDate(2026, 11, 3), position = 2))
        val d = insert(Todos.of("D", position = 3))

        // D up, next to B as shown: it goes before B, not just before the hidden C.
        repository.moveGoalTo(d, b)
        assertEquals(listOf(a, d, b, c), dao.activeGoals().map { it.id })
        // And back down below B.
        repository.moveGoalTo(d, b)
        assertEquals(listOf(a, b, d, c), dao.activeGoals().map { it.id })
        // One place among them all still works.
        repository.moveGoal(c, -1)
        assertEquals(listOf(a, b, c, d), dao.activeGoals().map { it.id })
    }
}
