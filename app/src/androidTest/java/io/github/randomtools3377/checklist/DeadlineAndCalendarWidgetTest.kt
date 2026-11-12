package io.github.randomtools3377.checklist

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.randomtools3377.checklist.core.LineKind
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.ProjectDraft
import io.github.randomtools3377.checklist.data.StepDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.reminders.Reminders
import io.github.randomtools3377.checklist.widget.CalendarWidget
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A project's deadline reminder (the day before, with how many steps are left, until it is
 * finished), the deadline on the calendar widget, and the morning plan's and weekly summary's
 * own notification channels.
 */
@RunWith(AndroidJUnit4::class)
class DeadlineAndCalendarWidgetTest {
    @get:Rule
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as ChecklistApplication
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val dao get() = app.container.db.dao()

    private fun forgetLastAlarm() =
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()

    private fun shown(goalId: Long) = manager.activeNotifications.firstOrNull { it.id == Reminders.notificationId(goalId) }?.notification

    private fun words(goalId: Long): Pair<String?, String?> {
        val extras = shown(goalId)?.extras
        return extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() to extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
    }

    /** Posting a notification reaches the system a moment later, so look for a few seconds. */
    private fun within(millis: Long = 5_000, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            if (condition()) return true
            Thread.sleep(100)
        }
        return condition()
    }

    /** A project due tomorrow that reminds this very minute. */
    private suspend fun addProject(name: String, private: Boolean = false): Long {
        val now = nowLocal()
        app.container.repository.saveProject(
            ProjectDraft(
                id = 0, name = name, icon = "🎨", steps = listOf(StepDraft(0, "Sand it", 2), StepDraft(0, "Paint it", 2)), bonus = 5,
                deadline = app.container.book.today().plusDays(1), finishBy = null, private = private,
                reminder = Reminder(at = LocalTime(now.hour, now.minute)),
            ),
        )
        return dao.activeGoals().single { it.name == name }.id
    }

    @Test
    fun aDeadlineRemindsTheDayBeforeUntilTheProjectIsFinished() = runBlocking {
        app.container.db.clearAllTables()
        manager.cancelAll()
        val shed = addProject("Paint the shed")
        val secret = addProject("Secret plan", private = true)
        app.container.repository.setSetting(LOCK_PRIVATE, "1")

        forgetLastAlarm()
        Reminders.fire(context)
        assertTrue("The deadline reminder should show", within { shown(shed) != null && shown(secret) != null })
        assertEquals("🎨 Paint the shed" to "Due tomorrow, 2 steps left", words(shed))
        // Behind the lock, a private project isn't named.
        assertEquals("🔒 A private goal" to "Due tomorrow", words(secret))
        // It comes on the goal reminders' channel.
        assertEquals(Reminders.CHANNEL, shown(shed)?.channelId)

        // Finished: nothing comes.
        dao.activeSteps(shed).forEach { app.container.book.tickStep(it.id) }
        manager.cancelAll()
        forgetLastAlarm()
        Reminders.fire(context)
        assertTrue("The other project still reminds", within { shown(secret) != null })
        assertFalse("A finished project shouldn't remind", within(1_500) { shown(shed) != null })
    }

    @Test
    fun aProjectTurnedOffDoesNotRemind() = runBlocking {
        app.container.db.clearAllTables()
        manager.cancelAll()
        val now = nowLocal()
        app.container.repository.saveProject(
            ProjectDraft(
                id = 0, name = "Quiet one", icon = "🎨", steps = listOf(StepDraft(0, "A step", 2)), bonus = 0,
                deadline = app.container.book.today().plusDays(1), finishBy = null,
                reminder = Reminder(onTime = false, at = LocalTime(now.hour, now.minute)),
            ),
        )
        val quiet = dao.activeGoals().single().id
        forgetLastAlarm()
        Reminders.fire(context)
        assertFalse(within(1_500) { shown(quiet) != null })
    }

    @Test
    fun theMorningPlanAndWeeklySummaryHaveTheirOwnQuieterChannels() {
        Reminders.createChannel(context)
        val morning = manager.getNotificationChannel(Reminders.CHANNEL_MORNING)
        val weekly = manager.getNotificationChannel(Reminders.CHANNEL_WEEKLY)
        assertEquals("Morning plan", morning.name.toString())
        assertEquals("Weekly summary", weekly.name.toString())
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, morning.importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, weekly.importance)
        // Goal reminders stay as they are.
        assertEquals(NotificationManager.IMPORTANCE_HIGH, manager.getNotificationChannel(Reminders.CHANNEL).importance)
    }

    @Test
    fun theCalendarWidgetShowsTheDeadlineAndHidesALockedPrivateOne() = runBlocking {
        app.container.db.clearAllTables()
        val shed = addProject("Paint the shed")
        addProject("Secret plan", private = true)
        val tomorrow = app.container.book.today().plusDays(1)

        val open = CalendarWidget.load(context)
        assertEquals("Today", open.days.first().header)
        val day = open.days.first { it.date == tomorrow }
        assertEquals("Tomorrow", day.header)
        assertEquals(setOf("Paint the shed", "Secret plan"), day.lines.filter { it.kind == LineKind.DEADLINE }.map { it.name }.toSet())
        assertEquals("Deadline · 2 steps left", day.lines.first { it.goalId == shed }.detail)
        assertEquals(2, open.month.first { it.date == tomorrow }.deadlines)

        app.container.repository.setSetting(LOCK_PRIVATE, "1")
        val locked = CalendarWidget.load(context).days.first { it.date == tomorrow }
        assertEquals(setOf("Paint the shed", "A private goal"), locked.lines.map { it.name }.toSet())
        assertTrue(locked.lines.none { it.canTick })
    }
}
