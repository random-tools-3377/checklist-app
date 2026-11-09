package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Difficulty
import io.github.randomtools3377.checklist.core.DifficultySetting
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Easy in Settings makes a 5-point goal pay 7. */
@RunWith(AndroidJUnit4::class)
class DifficultyTest {
    class ReadEveryDay : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(ReadEveryDay()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

    @Test
    fun easyPaysMore() {
        app.waitFor { app.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        app.onNodeWithTag("settings").performClick()
        app.waitFor { app.onAllNodesWithTag("difficulty:EASY").fetchSemanticsNodes().isNotEmpty() }
        app.tap("difficulty:EASY")
        app.waitFor { runBlocking { container.db.dao().setting(DifficultySetting.LEVEL) } == Difficulty.EASY.name }
        app.onNodeWithContentDescription("Back").performClick()

        app.waitFor { app.shownIn("today-list", "tick:Read") }
        app.tap("tick:Read")
        app.waitFor { runBlocking { container.book.balance() } == 7 }
    }
}
