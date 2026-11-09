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
import io.github.randomtools3377.checklist.core.SkipCheck
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.core.countsToward
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.core.MorningPlan
import io.github.randomtools3377.checklist.core.PaceSettings
import io.github.randomtools3377.checklist.core.TodayList
import io.github.randomtools3377.checklist.core.ordinal
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.SETTING_DAY_START
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
import io.github.randomtools3377.checklist.data.SETTING_MORNING_PLAN
import io.github.randomtools3377.checklist.data.toTime
import io.github.randomtools3377.checklist.ui.minutesText
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
        val from = Pace.plusMinutes(now, 1)
        val to = Pace.plusMinutes(now, LOOK_AHEAD_DAYS * 24 * 60L)
        val defaultFinish = dao.setting(SETTING_DEFAULT_FINISH)?.toIntOrNull()?.toTime()
        val nextAlert = (Alerts.between(goals, from, to, dayEnd, defaultFinish) + mornings(dao, from, to, dayEnd)).minOfOrNull { it.at }
        val dayTurns = LocalDateTime(Days.dayOf(now, dayEnd).plusDays(1), LocalTime(dayEnd, 0))
        val wake = listOfNotNull(nextAlert?.millis(), dayTurns.millis(), Snoozes.next(context)?.coerceAtLeast(System.currentTimeMillis() + 1_000)).min()
        setAlarm(context, wake)
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
        val defaultFinish = dao.setting(SETTING_DEFAULT_FINISH)?.toIntOrNull()?.toTime()
        // Snoozed reminders come back as "still to do".
        val snoozed = Snoozes.takeDue(context, until).map { snooze ->
            val at = snooze.at.local()
            Alert(snooze.goalId, Days.dayOf(at, dayEnd), at, AlertKind.AGAIN, number = snooze.number)
        }
        val due = (Alerts.between(goals, since.local(), until.local(), dayEnd, defaultFinish).filter { it.at.millis() > since } + snoozed)
            .sortedBy { it.at }
        if (mornings(dao, since.local(), until.local(), dayEnd).any { it.at.millis() > since }) {
            showMorningPlan(context, dayEnd)
        }
        if (due.isNotEmpty()) {
            val earliest = due.minOf { it.day }.startOfYear()
            val ticks = dao.habitTicksSince(earliest.toEpochDay()).map { it.toCore() }
            val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
            val container = (context.applicationContext as ChecklistApplication).container
            for (alert in due) {
                val goal = goals.find { it.id == alert.goalId } ?: continue
                val window = goal.repeat?.window(alert.day) ?: continue
                val count = ticks.count { it.countsToward(goal, window.start) }
                // One of several a day is reminded until that one is done.
                if (count >= (if (alert.number > 0) alert.number else goal.target)) continue
                val skips = dao.lapses(goal.id, window.start.toEpochDay(), window.end.toEpochDay())
                    .count { it.kind == "SKIP" && it.periodStart == window.start.toEpochDay() }
                if (window.period == io.github.randomtools3377.checklist.core.Period.DAILY && skips > 0) continue
                val skip = container.lapses.checkSkip(goal.id) as? SkipCheck.Allowed
                show(context, goal, alert, count, hidden = hidePrivate && goal.private, skip = skip)
            }
        }
        prefs.edit().putLong(LAST_FIRED, until).apply()
        reschedule(context)
    }

    /** The morning plans between [from] and [to], when the day start is set and the plan is on. */
    private suspend fun mornings(dao: io.github.randomtools3377.checklist.data.ChecklistDao, from: LocalDateTime, to: LocalDateTime, dayEnd: Int): List<Alert> {
        if (dao.setting(SETTING_MORNING_PLAN) == "0") return emptyList()
        val start = dao.setting(SETTING_DAY_START)?.toIntOrNull()?.toTime() ?: return emptyList()
        return Alerts.mornings(from, to, start, dayEnd)
    }

    /** The morning plan: what is due today, what to do first, and the pace. Nothing on a day with nothing due. */
    private suspend fun showMorningPlan(context: Context, dayEnd: Int) {
        if (!canNotify(context)) return
        val container = (context.applicationContext as ChecklistApplication).container
        val dao = container.db.dao()
        val now = nowLocal()
        val day = Days.dayOf(now, dayEnd)
        val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
        val goals = dao.activeGoals().map { it.toCore() }.filter { !(hidePrivate && it.private) }
        val since = Days.earliestOpenDay(day)
        val ticks = dao.habitTicksSince(since.toEpochDay()).map { it.toCore() } +
            dao.allTicks().filter { it.stepId != null }.map { it.toCore() }
        val steps = dao.allSteps().map { it.toCore() }
        val lapses = dao.lapsesSince(since.toEpochDay()).map { it.toCore() }
        val view = TodayList.build(day, goals, steps, ticks, 0, lapses)
        val settings = PaceSettings(
            dayEndHour = dayEnd,
            defaultFinish = dao.setting(SETTING_DEFAULT_FINISH)?.toIntOrNull()?.toTime(),
            dayStart = dao.setting(SETTING_DAY_START)?.toIntOrNull()?.toTime(),
        )
        val plan = MorningPlan.of(now, view, settings) ?: return
        val title = when (plan.goals) {
            0 -> "Today: nothing due, ${plan.alsoOpen} still open this week"
            1 -> "Today: 1 goal"
            else -> "Today: ${plan.goals} goals"
        }
        val text = buildList {
            plan.first?.let { first ->
                val goal = first.row.goal
                val at = first.time?.let { if (first.fixed) " at ${timeText(it.time)}" else "" } ?: ""
                add("First: ${goal.icon} ${goal.name}$at")
            }
            plan.pace?.let { pace ->
                pace.minutesEach?.let { add("about ${minutesText(it)} each") }
                add("done by ${timeText(pace.finishAt.time)}")
            }
            if (plan.goals > 0 && plan.alsoOpen > 0) add("${plan.alsoOpen} more open this week or month")
        }.joinToString(" · ")
        val open = PendingIntent.getActivity(
            context,
            MORNING_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(MORNING_ID, notification)
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }

    /** The morning plan's notification. Goals use 1000 and up. */
    private const val MORNING_ID = 900

    // Ticks from the start of the year cover every window an alert can fall in.
    private fun kotlinx.datetime.LocalDate.startOfYear() = Days.earliestOpenDay(this)

    fun notificationId(goalId: Long): Int = (goalId % 1_000_000).toInt() + 1000

    /** Shows a notification, or replaces it. [hidden] leaves the goal's name out (privacy lock). */
    fun show(context: Context, goal: Goal, alert: Alert, count: Int, hidden: Boolean, skip: SkipCheck.Allowed?) {
        if (!canNotify(context)) return
        val title = if (hidden) "🔒 A private goal" else "${goal.icon} ${goal.name}"
        val whenText = when (alert.kind) {
            AlertKind.ON_TIME -> "Now"
            AlertKind.BEFORE -> "In ${alert.minutesBefore} min, at ${timeText(alert.at.time)}"
            AlertKind.REMINDER -> "Reminder"
            AlertKind.MORNING -> "Your plan for today"
            AlertKind.AGAIN -> "Still to do"
        }
        val details = buildList {
            add(whenText)
            if (alert.number > 0) add("the ${ordinal(alert.number)} of ${goal.target}")
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
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (hidden) "Due now" else details)
            .setContentIntent(open)
            .addAction(0, if (goal.target > 1) "+1" else "Done", done)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (skip != null) {
            // A skip over the limit needs the warning, a reason and the penalty, so it opens the app.
            val skipIntent = if (skip.overLimit) {
                PendingIntent.getActivity(
                    context,
                    id + 500_000,
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_SKIP, goal.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                PendingIntent.getBroadcast(
                    context,
                    id + 500_000,
                    Intent(context, SkipReceiver::class.java)
                        .putExtra(DoneReceiver.EXTRA_GOAL, goal.id)
                        .putExtra(DoneReceiver.EXTRA_NOTIFICATION, id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            builder.addAction(0, "Skip", skipIntent)
        }
        val snooze = PendingIntent.getBroadcast(
            context,
            id + 1_000_000,
            Intent(context, SnoozeReceiver::class.java)
                .putExtra(DoneReceiver.EXTRA_GOAL, goal.id)
                .putExtra(DoneReceiver.EXTRA_NOTIFICATION, id)
                .putExtra(SnoozeReceiver.EXTRA_NUMBER, alert.number),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder.addAction(0, "Snooze", snooze)
        val notification = builder.build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }
}
