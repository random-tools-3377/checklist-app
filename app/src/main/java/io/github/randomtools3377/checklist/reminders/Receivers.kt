package io.github.randomtools3377.checklist.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import io.github.randomtools3377.checklist.ChecklistApplication
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

/** The alarm went off: show what is due, set the next alarm, and refresh the widget. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = work {
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

/** After a restart, an update, or a change of clock or time zone, the alarm is set again. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = work {
        Reminders.reschedule(context)
        CalendarWidget.refresh(context)
    }
}
