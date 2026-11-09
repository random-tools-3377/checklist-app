package io.github.randomtools3377.checklist

import android.Manifest
import android.app.NotificationManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** An amount done more than once, and the notification that shows while a goal is timed. */
@RunWith(AndroidJUnit4::class)
class TimingAndTimesTest {
    class WalksAndReading : ExternalResource() {
        override fun before() = runBlocking {
            val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container
            container.db.clearAllTables()
            val today = nowLocal().date
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Walk", icon = "🚶", points = 3, repeat = Repeat(RepeatKind.TIMES_PER, today, per = Period.WEEKLY),
                    target = 3, bonus = 0, minutes = null, at = null, finishBy = null, finishDay = null, amount = 1.0, unit = "mile",
                ),
            )
            container.repository.saveHabit(
                HabitDraft(
                    id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(today), target = 1, bonus = 0,
                    minutes = null, at = null, finishBy = null, finishDay = null, amount = 20.0, unit = "pages",
                ),
            )
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(WalksAndReading()).around(app)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as ChecklistApplication).container
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    private fun textOf(tag: String): String = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
        .joinToString(" ") { node -> node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.joinToString(" ") { it.text } }

    @Test
    fun eachWholeMileIsOneOfTheWeeksTimes() {
        app.waitFor { app.shownIn("today-list", "amount:Walk") }
        app.waitFor { textOf("amount:Walk") == "0 of 3 times · 0 of 1 mile on the next" }
        app.tap("add-amount:Walk")
        app.waitFor { !gone("field:amount") }
        app.onNodeWithTag("field:amount").performTextInput("1.5")
        app.onNodeWithTag("save-amount").performClick()
        app.waitFor { textOf("amount:Walk") == "1 of 3 times · 0.5 of 1 mile on the next" }
        assertEquals(3, runBlocking { container.book.balance() })
    }

    @Test
    fun aNotificationShowsWhileTiming() {
        app.waitFor { app.shownIn("today-list", "start:Read") }
        app.tap("start:Read")
        app.waitFor(10_000) { manager.activeNotifications.any { it.id == 902 } }
        app.waitFor { app.shownIn("today-list", "stop:Read") }
        app.tap("stop:Read")
        app.waitFor { !gone("field:amount") }
        app.onNodeWithTag("field:amount").performTextInput("4")
        app.onNodeWithTag("save-amount").performClick()
        app.waitFor(10_000) { manager.activeNotifications.none { it.id == 902 } }
        app.waitFor { textOf("amount:Read").startsWith("4 of 20 pages") }
    }
}
