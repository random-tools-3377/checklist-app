package io.github.randomtools3377.checklist.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.AlertKind
import io.github.randomtools3377.checklist.core.SkipCheck
import io.github.randomtools3377.checklist.widget.CalendarWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.work(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** The alarm went off: find misses if the day ended, show what is due, set the next alarm, refresh the widget. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = work {
        runCatching { (context.applicationContext as ChecklistApplication).container.closeDays() }
        Reminders.fire(context)
        CalendarWidget.refresh(context)
    }
}

/** "Done" on a notification ticks the goal once. */
class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = work {
        val goalId = intent.getLongExtra(EXTRA_GOAL, -1)
        if (goalId >= 0) {
            (context.applicationContext as ChecklistApplication).container.book.tickHabit(goalId)
        }
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION, 0))
    }

    companion object {
        const val EXTRA_GOAL = "goal"
        const val EXTRA_NOTIFICATION = "notification"
    }
}

/** "Snooze" on a notification: it comes back after the snooze length set in Settings. */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = work {
        val goalId = intent.getLongExtra(DoneReceiver.EXTRA_GOAL, -1)
        val container = (context.applicationContext as ChecklistApplication).container
        if (goalId >= 0) {
            val minutes = container.db.dao().setting(io.github.randomtools3377.checklist.data.SETTING_SNOOZE)?.toIntOrNull()
                ?: io.github.randomtools3377.checklist.data.DEFAULT_SNOOZE_MINUTES
            Snoozes.add(context, Snooze(goalId, intent.getIntExtra(EXTRA_NUMBER, 0), System.currentTimeMillis() + minutes * 60_000L))
        }
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(DoneReceiver.EXTRA_NOTIFICATION, 0))
        Reminders.reschedule(context)
    }

    companion object {
        const val EXTRA_NUMBER = "number"
    }
}

/**
 * "Skip" on a notification, when it was within the allowance. If it would go over the limit by
 * now, the notification is replaced with one that opens the app to skip there, with a reason.
 */
class SkipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = work {
        val goalId = intent.getLongExtra(DoneReceiver.EXTRA_GOAL, -1)
        val notification = intent.getIntExtra(DoneReceiver.EXTRA_NOTIFICATION, 0)
        val container = (context.applicationContext as ChecklistApplication).container
        val check = container.lapses.checkSkip(goalId)
        if (check is SkipCheck.Allowed && !check.overLimit) {
            container.lapses.skip(goalId, reason = null, note = null)
            NotificationManagerCompat.from(context).cancel(notification)
        } else if (check is SkipCheck.Allowed) {
            val goal = container.repository.goal(goalId) ?: return@work
            Reminders.show(
                context, goal,
                Alert(goalId, container.book.today(), io.github.randomtools3377.checklist.data.nowLocal(), AlertKind.REMINDER),
                count = 0, hidden = false, skip = check,
            )
        } else {
            NotificationManagerCompat.from(context).cancel(notification)
        }
    }
}

/** After a restart, an update, or a change of clock or time zone, the alarm is set again. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = work {
        Reminders.reschedule(context)
        CalendarWidget.refresh(context)
    }
}
