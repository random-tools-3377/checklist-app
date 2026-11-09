package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toCore
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Ticking a "since last done" goal makes it due again that many days later. */
@RunWith(AndroidJUnit4::class)
class AfterDoneTest {
    class PlantsToday : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Water plants", icon = "🪴", points = 3, repeat = Repeat(RepeatKind.AFTER_DONE, nowLocal().date, interval = 4),
                    target = 1, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null,
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(PlantsToday()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun next() = runBlocking { container.db.dao().allGoals().single().toCore().repeat!!.start }

    @Test
    fun tickingMovesItOn() {
        val today = runBlocking { container.book.today() }
        app.waitFor { app.shownIn("today-list", "tick:Water plants") }
        app.tap("tick:Water plants")
        app.waitFor { next() == today.plusDays(4) }
        // It still shows today, done; tapping it again takes it back.
        app.waitFor { app.shownIn("today-list", "tick:Water plants") }
        app.tap("tick:Water plants")
        app.waitFor { next() == today }
    }
}
