package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Categories
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.TodayList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** To-dos with a category, and steps and checklist items put in another order, on the shared database code. */
class TodoCategoryAndStepOrderTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())
    private val today = LocalDate(2026, 11, 11)

    @AfterTest
    fun close() = db.close()

    private suspend fun view() = TodayList.build(
        today, dao.allGoals().map { it.toCore() }, dao.allSteps().map { it.toCore() }, dao.allTicks().map { it.toCore() }, balance = 0,
        checks = dao.allChecks().map { it.toCore() },
    )

    @Test
    fun aToDoTakesACategory() = runTest {
        val id = repository.saveTodo(TodoDraft(id = 0, name = "Buy milk", category = "  Errands "))
        assertEquals("Errands", dao.goal(id)!!.category)
        // A category used only on a to-do is one of Today's chips, and the to-do shows under it.
        val todos = view().todos
        assertEquals(listOf("Errands"), Categories.of(todos.map { it.goal }))
        assertTrue(Categories.shows("Errands", todos.single().goal))
    }

    @Test
    fun aToDosCategoryJoinsOneAlreadyInUseAndCanBeTakenOff() = runTest {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = "Tidy", icon = "🧹", points = 2, repeat = Repeat(RepeatKind.EVERY_N_DAYS, today), target = 1, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null, category = "Home",
            ),
            today,
        )
        val id = repository.saveTodo(TodoDraft(id = 0, name = "Fix the tap", category = "home"))
        assertEquals("Home", dao.goal(id)!!.category)
        // Changing its date keeps the category it is saved with; an empty box takes it off.
        repository.saveTodo(TodoDraft(id = id, name = "Fix the tap", date = today, category = "Home"))
        assertEquals("Home", dao.goal(id)!!.category)
        repository.saveTodo(TodoDraft(id = id, name = "Fix the tap", date = today, category = ""))
        assertNull(dao.goal(id)!!.category)
    }

    @Test
    fun aToDoWithoutACategoryHasNone() = runTest {
        val id = repository.saveTodo(TodoDraft(id = 0, name = "Call back"))
        assertNull(dao.goal(id)!!.category)
    }

    @Test
    fun aTickedStepKeepsItsTickWhenMoved() = runTest {
        repository.saveProject(
            ProjectDraft(
                id = 0, name = "Paint the shed", icon = "🎨",
                steps = listOf(StepDraft(0, "Sand", 3), StepDraft(0, "Prime", 3), StepDraft(0, "Paint", 3)),
                bonus = 0, deadline = null, finishBy = null,
            ),
        )
        val goalId = dao.allGoals().single().id
        val steps = dao.activeSteps(goalId)
        assertEquals(listOf("Sand", "Prime", "Paint"), steps.map { it.name })
        val sand = steps[0]
        dao.insertTick(Tick(goalId = goalId, stepId = sand.id, at = 0, day = today, period = Period.DAILY, periodStart = today).toEntity())

        // The editor saves the steps in their new order, each with the id it had.
        val moved = listOf(steps[1], steps[2], steps[0]).map { StepDraft(it.id, it.name, it.points) }
        repository.saveProject(
            ProjectDraft(id = goalId, name = "Paint the shed", icon = "🎨", steps = moved, bonus = 0, deadline = null, finishBy = null),
        )

        val rows = view().projects.single().steps
        assertEquals(listOf("Prime", "Paint", "Sand"), rows.map { it.step.name })
        assertEquals(listOf(false, false, true), rows.map { it.done })
        assertEquals(3, dao.activeSteps(goalId).size)
    }

    @Test
    fun aToDosStepsAndARoutinesItemsKeepTheOrderTheyAreSavedIn() = runTest {
        val todo = repository.saveTodo(TodoDraft(id = 0, name = "Pack", steps = listOf(StepDraft(0, "Socks", 0), StepDraft(0, "Charger", 0))))
        val todoSteps = dao.activeSteps(todo)
        repository.saveTodo(TodoDraft(id = todo, name = "Pack", steps = todoSteps.reversed().map { StepDraft(it.id, it.name, 0) }))
        assertEquals(listOf("Charger", "Socks"), dao.activeSteps(todo).map { it.name })
        assertEquals(todoSteps.map { it.id }.toSet(), dao.activeSteps(todo).map { it.id }.toSet())

        val routine = HabitDraft(
            id = 0, name = "Morning", icon = "☀️", points = 5, repeat = Repeat(RepeatKind.EVERY_N_DAYS, today), target = 1, bonus = 0,
            minutes = null, at = null, finishBy = null, finishDay = null,
            items = listOf(StepDraft(0, "Water", 0), StepDraft(0, "Stretch", 0), StepDraft(0, "Make the bed", 0)),
        )
        repository.saveHabit(routine, today)
        val routineId = dao.allGoals().first { it.name == "Morning" }.id
        val items = dao.activeSteps(routineId)
        repository.saveHabit(routine.copy(id = routineId, items = listOf(items[2], items[0], items[1]).map { StepDraft(it.id, it.name, 0) }), today)
        val row = view().dueToday.single { it.goal.id == routineId }
        assertEquals(listOf("Make the bed", "Water", "Stretch"), row.items.map { it.step.name })
    }
}
