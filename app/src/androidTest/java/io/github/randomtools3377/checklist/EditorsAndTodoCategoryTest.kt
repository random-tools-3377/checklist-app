package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.ProjectDraft
import io.github.randomtools3377.checklist.data.RewardDraft
import io.github.randomtools3377.checklist.data.StepDraft
import io.github.randomtools3377.checklist.data.TodoDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * Editors ask before throwing changes away, "Make a copy" saves first, steps are reordered with
 * arrows, and to-dos take a category and open from where they are seen.
 */
@RunWith(AndroidJUnit4::class)
class EditorsAndTodoCategoryTest {
    class AGoalAProjectAndAReward : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            // To-dos are an extra, off until turned on.
            c.repository.setSetting(Feature.TODOS.key, Feature.TODOS.onValue)
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
            c.repository.saveProject(
                ProjectDraft(
                    id = 0, name = "Paint the shed", icon = "🎨",
                    steps = listOf(StepDraft(0, "Sand", 3), StepDraft(0, "Prime", 3)),
                    bonus = 0, deadline = null, finishBy = null,
                ),
            )
            c.repository.saveReward(RewardDraft(id = 0, name = "Coffee", icon = "☕", price = 20))
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AGoalAProjectAndAReward()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun gone(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    private fun goals() = runBlocking { container.db.dao().allGoals() }
    /** The system's back, once the screen has taken in what was just typed. */
    private fun systemBack() {
        app.waitForIdle()
        app.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    @Test
    fun leavingAHabitWithChangesAsksFirst() {
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { !gone("goal:Read") }
        app.editGoal("Read")
        app.waitFor { !gone("field:name") }

        // Nothing changed: back just leaves.
        app.onNodeWithTag("editor-back").performClick()
        app.waitFor { gone("field:name") && !gone("goal:Read") }
        assertTrue(gone("discard-question"))

        // With a change, the back arrow asks. "Keep editing" stays, with what was typed.
        app.editGoal("Read")
        app.waitFor { !gone("field:name") }
        app.onNodeWithTag("field:name").performTextReplacement("Read a lot")
        app.onNodeWithTag("editor-back").performClick()
        app.waitFor { !gone("discard") }
        app.onNodeWithTag("keep-editing").performClick()
        app.waitFor { gone("discard") }
        assertTrue(!gone("field:name"))

        // The system's back asks too, and "Discard" leaves without saving.
        systemBack()
        app.waitFor { !gone("discard") }
        app.onNodeWithTag("discard").performClick()
        app.waitFor { gone("field:name") && !gone("goal:Read") }
        assertTrue(goals().none { it.name == "Read a lot" })
    }

    @Test
    fun leavingARewardWithChangesAsksFirst() {
        app.onNodeWithTag("nav:shop").performClick()
        app.waitFor { !gone("reward:Coffee") }
        app.tap("reward:Coffee")
        app.waitFor { !gone("field:price") }
        app.onNodeWithTag("field:price").performTextReplacement("25")
        systemBack()
        app.waitFor { !gone("discard") }
        app.onNodeWithTag("discard").performClick()
        app.waitFor { gone("field:price") && !gone("reward:Coffee") }
        assertEquals(20, runBlocking { container.db.dao().allRewards() }.single().price)
    }

    @Test
    fun makingACopySavesTheChangesFirst() {
        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { !gone("goal:Read") }
        app.editGoal("Read")
        app.waitFor { !gone("duplicate") }
        app.onNodeWithTag("field:name").performTextReplacement("Read daily")
        app.tap("duplicate")
        app.waitFor { goals().map { it.name }.toSet() == setOf("Read daily", "Read daily (copy)", "Paint the shed") }
        // The copy is open, with nothing to ask about.
        app.waitFor { app.onAllNodesWithText("Read daily (copy)").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("editor-back").performClick()
        app.waitFor { !gone("goal:Read daily (copy)") }
        assertTrue(gone("discard-question"))
    }

    @Test
    fun aTickedStepKeepsItsTickWhenMovedInTheEditor() {
        val dao = container.db.dao()
        val project = goals().first { it.name == "Paint the shed" }
        val today = runBlocking { container.book.today() }
        val sand = runBlocking { dao.activeSteps(project.id) }.first { it.name == "Sand" }
        runBlocking {
            dao.insertTick(Tick(goalId = project.id, stepId = sand.id, at = 0, day = today, period = Period.DAILY, periodStart = today).toEntity())
        }

        app.onNodeWithTag("nav:goals").performClick()
        app.waitFor { !gone("goal:Paint the shed") }
        app.editGoal("Paint the shed")
        app.waitFor { !gone("step-up-1") }
        app.tap("step-up-1")
        app.onNodeWithTag("save").performClick()

        app.waitFor { runBlocking { dao.activeSteps(project.id) }.map { it.name } == listOf("Prime", "Sand") }
        // The same step, so its tick is still its own.
        assertEquals(sand.id, runBlocking { dao.activeSteps(project.id) }.last().id)
        assertEquals(listOf(sand.id), runBlocking { dao.allTicks() }.mapNotNull { it.stepId })
    }

    @Test
    fun aToDoAddedUnderACategoryChipTakesThatCategory() {
        runBlocking { container.repository.saveTodo(TodoDraft(id = 0, name = "Post the parcel", category = "Errands")) }
        // A category used only on to-dos has its chip on Today.
        app.waitFor { app.shownIn("today-list", "today-category:Errands") }
        app.tap("today-category:Errands")
        app.waitFor { app.shownIn("today-list", "todo-field") }
        app.onNodeWithTag("todo-field").performTextInput("Buy stamps")
        app.tap("todo-add")
        app.waitFor { goals().firstOrNull { it.name == "Buy stamps" }?.category == "Errands" }
        // So it stays on screen, with the chip still selected.
        app.waitFor { app.shownIn("today-list", "todo:Buy stamps") }

        // The to-do editor has the Category box.
        app.tap("todo:Buy stamps")
        app.waitFor { !gone("field:category") }
        app.onNodeWithTag("field:category").performTextReplacement("Post")
        app.onNodeWithTag("save").performClick()
        app.waitFor { goals().firstOrNull { it.name == "Buy stamps" }?.category == "Post" }
    }

    @Test
    fun aToDoWithADateOpensFromTodayAndThePlanner() {
        val today = runBlocking { container.book.today() }
        runBlocking { container.repository.saveTodo(TodoDraft(id = 0, name = "Renew the pass", date = today)) }

        app.waitFor { app.shownIn("today-list", "change-date:Renew the pass") }
        app.tap("change-date:Renew the pass")
        app.waitFor { !gone("field:date") }
        app.onNodeWithTag("editor-back").performClick()
        app.waitFor { gone("field:date") }

        // A tap on its name opens it too.
        app.waitFor { app.shownIn("today-list", "open-todo:Renew the pass") }
        app.tap("open-todo:Renew the pass")
        app.waitFor { !gone("field:date") }
        app.onNodeWithTag("editor-back").performClick()
        app.waitFor { gone("field:date") }

        app.onNodeWithTag("nav:plan").performClick()
        app.waitFor { app.shownIn("plan", "plan-open-todo:Renew the pass") }
        app.tap("plan-open-todo:Renew the pass")
        app.waitFor { !gone("field:date") }
    }
}
