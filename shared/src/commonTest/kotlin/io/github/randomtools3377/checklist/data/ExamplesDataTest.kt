package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.ui.FirstGoal
import io.github.randomtools3377.checklist.ui.FirstRun
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The tour's examples, added and taken away again with Undo, and the setup's first goals. */
class ExamplesDataTest {
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

    @AfterTest
    fun close() = db.close()

    @Test
    fun theExamplesAreWhatTheTourSaysTheyAre() = runTest {
        val added = repository.addExamples(today)
        val goals = dao.allGoals()
        assertEquals(EXAMPLE_GOALS + EXAMPLE_TODOS, goals.size)
        assertEquals(EXAMPLE_TODOS, goals.count { it.kind == "TODO" })
        assertEquals(EXAMPLE_REWARDS, dao.allRewards().size)
        assertEquals(goals.map { it.id }.toSet(), added.goals.toSet())
        assertEquals(dao.allRewards().map { it.id }.toSet(), added.rewards.toSet())
        assertEquals("done", dao.setting(SETTING_GUIDE))
    }

    @Test
    fun undoTakesTheExamplesAwayAndLeavesTheUsersOwn() = runTest {
        repository.saveHabit(FirstRun.draft(FirstGoal("🧘", "Stretch"), today))
        repository.saveReward(RewardDraft(id = 0, name = "A walk in the park", icon = "🌳", price = 20))
        val added = repository.addExamples(today)
        assertTrue(dao.allSteps().isNotEmpty())

        repository.removeExamples(added)
        assertEquals(listOf("Stretch"), dao.allGoals().map { it.name })
        assertEquals(listOf("A walk in the park"), dao.allRewards().map { it.name })
        // The routine's checklist and the project's steps go with them.
        assertTrue(dao.allSteps().isEmpty())
        // Nothing waits among the removed goals either.
        assertTrue(dao.allGoals().none { it.archived })
    }

    @Test
    fun anExampleAlreadyTickedIsRemovedTheUsualWay() = runTest {
        val added = repository.addExamples(today)
        val read = dao.allGoals().first { it.name == "Read" }
        dao.insertTick(Tick(goalId = read.id, stepId = null, at = 0, day = today, period = Period.DAILY, periodStart = today).toEntity())

        repository.removeExamples(added)
        val left = dao.allGoals()
        assertEquals(listOf("Read"), left.map { it.name })
        assertTrue(left.single().archived)
    }

    @Test
    fun aFirstGoalIsADailyHabit() = runTest {
        repository.saveHabit(FirstRun.draft(FirstRun.GOALS.first(), today))
        val goal = dao.allGoals().single().toCore()
        assertEquals(FirstRun.GOALS.first().name, goal.name)
        assertEquals(1, goal.target)
        assertTrue(goal.repeat!!.window(today) != null)
        assertTrue(goal.repeat!!.window(LocalDate(2026, 11, 12)) != null)
    }
}
