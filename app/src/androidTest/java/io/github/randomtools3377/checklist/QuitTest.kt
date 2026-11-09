package io.github.randomtools3377.checklist

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.randomtools3377.checklist.core.Challenge
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** "Avoided" ticks a goal to quit, and "I slipped" records today's miss straight away. */
@RunWith(AndroidJUnit4::class)
class QuitTest {
    class NoSugar : ExternalResource() {
        override fun before() = runBlocking {
            val c = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            c.db.clearAllTables()
            val today = nowLocal().date
            c.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "No sugar", icon = "🍬", points = 4, repeat = Repeat.everyDay(today), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null, quit = true, challenge = Challenge(30, 50, today),
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(NoSugar()).around(app)

    private val container get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
    private fun balance() = runBlocking { container.book.balance() }

    @Test
    fun avoidedThenSlipped() {
        app.waitFor { app.shownIn("today-list", "tick:No sugar") }
        app.tap("tick:No sugar")
        app.waitFor { balance() == 4 }
        app.waitFor { app.shownIn("today-list", "slip:No sugar") }
        app.tap("slip:No sugar")
        // The tick is taken back and the miss is there, waiting for its reason.
        app.waitFor { balance() <= 0 }
        app.waitFor { runBlocking { container.db.dao().allLapses() }.size == 1 }
    }
}
