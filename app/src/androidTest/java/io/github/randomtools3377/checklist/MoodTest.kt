package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** The mood check-in on Today saves how the user feels, once, and then goes away. */
@RunWith(AndroidJUnit4::class)
class MoodTest {
    class OneGoal : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(OneGoal()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun shown(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aMoodIsSavedOnce() {
        app.waitFor { shown("mood:4") }
        app.tap("mood:4")
        app.waitFor { runBlocking { container.db.dao().allMoods() }.singleOrNull()?.score == 4 }
        app.waitFor { !shown("mood-checkin") }
    }
}
