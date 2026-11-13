package io.github.randomtools3377.checklist

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.AlertKind
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.activeCoreGoals
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.reminders.Reminders
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

private val container
    get() = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as ChecklistApplication).container

/** One habit, Stretch: 20 minutes at 9 pm, paying 5. */
private fun saveStretch(why: String? = null, private: Boolean = false) = runBlocking {
    container.db.clearAllTables()
    container.repository.saveHabit(
        HabitDraft(
            id = 0, name = "Stretch", icon = "🧘", points = 5, repeat = Repeat.everyDay(nowLocal().date), target = 1, bonus = 0,
            minutes = 20, at = LocalTime(21, 0), finishBy = null, finishDay = null, why = why, private = private,
        ),
    )
}

/** "Why it's good" is typed in the habit's editor and kept in the user's own words. */
@RunWith(AndroidJUnit4::class)
class WhyItsGoodEditorTest {
    class AHabit : ExternalResource() {
        override fun before() {
            saveStretch()
        }
    }

    private val app = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(AHabit()).around(app)

    private fun gone(tag: String) = app.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()

    @Test
    fun theHabitEditorKeepsWhyItsGood() {
        app.onNodeWithTag("nav:goals").performClick()
        app.editGoal("Stretch")
        app.waitFor { !gone("field:why") }
        app.onNodeWithTag("field:why").performTextInput("so my back stops aching")
        app.onNodeWithTag("save").performClick()
        app.waitFor { runBlocking { container.db.dao().allGoals() }.single().why == "so my back stops aching" }
    }
}

/** A goal's reminder leads with its reason and never says what it pays; a locked private goal's says neither. */
@RunWith(AndroidJUnit4::class)
class WhyItsGoodReminderTest {
    @get:Rule
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun clear() = manager.cancelAll()

    private fun shown(goalId: Long) = manager.activeNotifications.firstOrNull { it.id == Reminders.notificationId(goalId) }?.notification

    /** The reminder as shown: its title and text, once it has reached the system. */
    private fun remind(hidden: Boolean): Pair<String?, String?> {
        val goal = runBlocking { container.db.dao().activeCoreGoals() }.single()
        val now = nowLocal()
        Reminders.show(context, goal, Alert(goal.id, now.date, now, AlertKind.ON_TIME), count = 0, hidden = hidden, skip = null, dayEnd = 4)
        val end = System.currentTimeMillis() + 5_000
        while (shown(goal.id) == null && System.currentTimeMillis() < end) Thread.sleep(100)
        val extras = shown(goal.id)?.extras
        return extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() to extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    @Test
    fun theReminderLeadsWithTheReasonAndNotThePoints() {
        saveStretch(why = "so my back stops aching")
        val (title, text) = remind(hidden = false)
        assertEquals("🧘 Stretch", title)
        assertEquals("so my back stops aching · Now · 20 min", text)
        assertFalse(text!!.contains("+5"))
    }

    @Test
    fun aGoalWithNoReasonReadsAsBeforeWithoutThePoints() {
        saveStretch()
        assertEquals("🧘 Stretch" to "Now · 20 min", remind(hidden = false))
    }

    @Test
    fun aLockedPrivateGoalNamesNothing() {
        saveStretch(why = "so my back stops aching", private = true)
        val (title, text) = remind(hidden = true)
        assertEquals("🔒 A private goal", title)
        assertEquals("Due now", text)
        assertTrue(listOf("Stretch", "back", "20 min").none { title!!.contains(it) || text!!.contains(it) })
    }
}
