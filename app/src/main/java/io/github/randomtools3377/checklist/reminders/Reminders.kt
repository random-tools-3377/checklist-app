package io.github.randomtools3377.checklist.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.MainActivity
import io.github.randomtools3377.checklist.R
import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.AlertKind
import io.github.randomtools3377.checklist.core.Alerts
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.Pace
import io.github.randomtools3377.checklist.core.countsToward
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.ui.timeText
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Notifications at set times, scheduled on the phone like a calendar's. One alarm is kept: at the
 * next notification, or at the end of the user's day (so the widget turns over), whichever is
 * first. When it goes off, the notifications due are shown and the next alarm is set.
 */
object Reminders {
    const val CHANNEL = "reminders"
    private const val PREFS = "reminders"
    private const val LAST_FIRED = "last_fired"
    private const val LOOK_AHEAD_DAYS = 2

    private fun LocalDateTime.millis(): Long = toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    private fun Long.local(): LocalDateTime = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.currentSystemDefault())

    fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Reminders")
            .setDescription("Goals at their set times")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** False when the phone only lets reminders arrive roughly on time. */
    fun exactAlarmsAllowed(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    }

    /** Sets the one alarm for the next notification or the end of the day. */
    suspend fun reschedule(context: Context) {
        val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
        val goals = dao.activeGoals().map { it.toCore() }
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val now = nowLocal()
        val nextAlert = Alerts.between(goals, Pace.plusMinutes(now, 1), Pace.plusMinutes(now, LOOK_AHEAD_DAYS * 24 * 60L), dayEnd)
            .firstOrNull()?.at
        val dayTurns = LocalDateTime(Days.dayOf(now, dayEnd).plusDays(1), LocalTime(dayEnd, 0))
        val wake = listOfNotNull(nextAlert, dayTurns).min()
        setAlarm(context, wake.millis())
    }

    private fun setAlarm(context: Context, atMillis: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (exactAlarmsAllowed(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    /** Shows the notifications that came due since the last alarm, then sets the next one. */
    suspend fun fire(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val nowMillis = System.currentTimeMillis()
        // Never look back more than a few hours: a phone that was off doesn't need old reminders.
        val since = prefs.getLong(LAST_FIRED, nowMillis - 60_000).coerceIn(nowMillis - 6 * 3_600_000L, nowMillis)
        val until = nowMillis + 30_000

        val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
        val goals = dao.activeGoals().map { it.toCore() }
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val due = Alerts.between(goals, since.local(), until.local(), dayEnd).filter { it.at.millis() > since }
        if (due.isNotEmpty()) {
            val earliest = due.minOf { it.day }.startOfYear()
            val ticks = dao.habitTicksSince(earliest.toEpochDay()).map { it.toCore() }
            for (alert in due) {
                val goal = goals.find { it.id == alert.goalId } ?: continue
                val window = goal.repeat?.window(alert.day) ?: continue
                val count = ticks.count { it.countsToward(goal, window.start) }
                if (count >= goal.target) continue
                show(context, goal, alert, count)
            }
        }
        prefs.edit().putLong(LAST_FIRED, until).apply()
        reschedule(context)
    }

    // Ticks from the start of the year cover every window an alert can fall in.
    private fun kotlinx.datetime.LocalDate.startOfYear() = Days.earliestOpenDay(this)

    fun notificationId(goalId: Long): Int = (goalId % 1_000_000).toInt() + 1000

    private fun show(context: Context, goal: Goal, alert: Alert, count: Int) {
        if (!canNotify(context)) return
        val title = "${goal.icon} ${goal.name}"
        val whenText = when (alert.kind) {
            AlertKind.ON_TIME -> "Now"
            AlertKind.BEFORE -> "In ${alert.minutesBefore} min, at ${timeText(alert.at.time)}"
            AlertKind.REMINDER -> "Reminder"
        }
        val details = buildList {
            add(whenText)
            goal.minutes?.let { add("$it min") }
            add("+${goal.points}")
            if (goal.target > 1) add("$count of ${goal.target}")
        }.joinToString(" · ")

        val id = notificationId(goal.id)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val done = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, DoneReceiver::class.java)
                .putExtra(DoneReceiver.EXTRA_GOAL, goal.id)
                .putExtra(DoneReceiver.EXTRA_NOTIFICATION, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(details)
            .setContentIntent(open)
            .addAction(0, "Done", done)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }
}
