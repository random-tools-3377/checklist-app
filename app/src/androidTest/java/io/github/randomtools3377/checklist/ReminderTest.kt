package io.github.randomtools3377.checklist

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.data.HabitDraft
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.reminders.Reminders
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A habit with a clock time notifies at that time, and stays quiet once it is done. */
@RunWith(AndroidJUnit4::class)
class ReminderTest {
    @get:Rule
    val notifications: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as ChecklistApplication
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun forgetLastAlarm() =
        context.getSharedPreferences("reminders", Context.MODE_PRIVATE).edit().clear().commit()

    private fun showing(goalId: Long) = manager.activeNotifications.any { it.id == Reminders.notificationId(goalId) }

    /** Posting a notification reaches the system a moment later, so look for a few seconds. */
    private fun showsWithin(goalId: Long, millis: Long = 5_000): Boolean {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            if (showing(goalId)) return true
            Thread.sleep(100)
        }
        return showing(goalId)
    }

    @Test
    fun aHabitNotifiesAtItsTimeUntilItIsDone() = runBlocking {
        app.container.db.clearAllTables()
        manager.cancelAll()
        val now = nowLocal()
        app.container.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Stretch", icon = "🧘", points = 5, repeat = Repeat.everyDay(now.date), target = 1, bonus = 0,
                minutes = null, at = LocalTime(now.hour, now.minute), finishBy = null, finishDay = null,
            ),
        )
        val goalId = app.container.db.dao().activeGoals().single().id

        forgetLastAlarm()
        Reminders.fire(context)
        assertTrue("The reminder should show at its time", showsWithin(goalId))

        app.container.book.tickHabit(goalId)
        manager.cancelAll()
        forgetLastAlarm()
        Reminders.fire(context)
        assertFalse("A done habit shouldn't remind again", showsWithin(goalId, millis = 1_500))
    }
}
