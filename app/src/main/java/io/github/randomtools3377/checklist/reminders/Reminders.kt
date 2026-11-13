package io.github.randomtools3377.checklist.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import io.github.randomtools3377.checklist.core.DeadlineReminder
import io.github.randomtools3377.checklist.core.EveningList
import io.github.randomtools3377.checklist.core.Chains
import io.github.randomtools3377.checklist.core.UsualTimes
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.MoodReminder
import io.github.randomtools3377.checklist.core.NudgeTone
import io.github.randomtools3377.checklist.core.NudgeWords
import io.github.randomtools3377.checklist.core.Nudges
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.Pace
import io.github.randomtools3377.checklist.core.SkipCheck
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.core.MorningPlan
import io.github.randomtools3377.checklist.core.WeeklySummary
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.SETTING_WEEKLY
import io.github.randomtools3377.checklist.core.PaceSettings
import io.github.randomtools3377.checklist.core.TodayList
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.SETTING_DAY_START
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
import io.github.randomtools3377.checklist.data.SETTING_MORNING_PLAN
import io.github.randomtools3377.checklist.data.SETTING_REMINDER_LIGHT
import io.github.randomtools3377.checklist.data.billReminders
import io.github.randomtools3377.checklist.data.billTimes
import io.github.randomtools3377.checklist.data.deadlineReminders
import io.github.randomtools3377.checklist.data.deadlineTimes
import io.github.randomtools3377.checklist.data.eveningChecks
import io.github.randomtools3377.checklist.data.eveningList
import io.github.randomtools3377.checklist.data.eveningTimes
import io.github.randomtools3377.checklist.data.moodReminders
import io.github.randomtools3377.checklist.data.nudgePlan
import io.github.randomtools3377.checklist.data.nudgeWriter
import io.github.randomtools3377.checklist.data.stakeWriterOrNull
import io.github.randomtools3377.checklist.data.weeklyWords
import io.github.randomtools3377.checklist.data.toTime
import io.github.randomtools3377.checklist.ui.minutesText
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.toLocalDateTime
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.activeCoreGoals
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.data.toDay
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
 * first. When it goes off, the notifications due are shown and the next alarm is set. The evening
 * check, when it is on, is one of them: one notification listing what is still open today. So is
 * a project's deadline reminder. The morning plan and the weekly summary have channels of their own,
 * and so do late nudges (see Nudges in the shared code).
 */
object Reminders {
    const val CHANNEL = "reminders"

    /** The morning plan and the weekly summary each have their own, to silence or change in the phone's settings. */
    const val CHANNEL_MORNING = "morning_plan"
    const val CHANNEL_WEEKLY = "weekly_summary"

    /** Late nudges have their own too, so they can be made quiet apart from the reminders. */
    const val CHANNEL_NUDGES = "late_nudges"

    /** Marks a goal's notification as a late nudge, to take it away once its nudges are stopped. */
    private const val EXTRA_NUDGE = "late_nudge"
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
        // The two that aren't about one goal arrive without popping over the screen.
        val morning = NotificationChannelCompat.Builder(CHANNEL_MORNING, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Morning plan")
            .setDescription("Today's list and the pace, when your day starts")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(morning)
        val weekly = NotificationChannelCompat.Builder(CHANNEL_WEEKLY, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Weekly summary")
            .setDescription("How your week went, on Sunday")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(weekly)
        val nudges = NotificationChannelCompat.Builder(CHANNEL_NUDGES, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName("Late nudges")
            .setDescription("Friendly nudges when a goal is late, until it's done")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(nudges)
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

    /**
     * Sets the one alarm for the next notification or the end of the day. Takes away the
     * reminders of goals done or skipped since they were shown (in the app, on a widget or the tile).
     */
    suspend fun reschedule(context: Context) {
        val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
        val goals = dao.activeCoreGoals()
        runCatching { clearDone(context, dao, goals) }
        runCatching { refreshEvening(context) }
        runCatching { clearMoodReminder(context, dao) }
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val nowMillis = System.currentTimeMillis()
        val now = nowMillis.local()
        // From where the last alarm's notifications were shown up to (fire() saves it, at most
        // 30 seconds ahead), so one due in the coming minute keeps its alarm.
        val shownUntil = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(LAST_FIRED, 0L)
        val from = shownUntil.coerceIn(nowMillis, nowMillis + 30_000).local()
        val to = Pace.plusMinutes(now, LOOK_AHEAD_DAYS * 24 * 60L)
        val defaultFinish = dao.setting(SETTING_DEFAULT_FINISH)?.toIntOrNull()?.toTime()
        val nextAlert = (
            Alerts.between(goals, from, to, dayEnd, defaultFinish, usualTimes(dao), nudgePlan(dao)) + mornings(dao, from, to, dayEnd) +
                weeklies(dao, from, to, dayEnd) + deadlineTimes(dao, from, to) + billTimes(dao, from, to)
            ).minOfOrNull { it.at }
        // The evening check, when it is on.
        val nextEvening = eveningTimes(dao, from, to).minOfOrNull { it.at }
        // The mood reminder, when it is on, for the next day whose mood isn't in yet.
        val nextMood = moodReminders(dao, from, to).minOfOrNull { it.at }
        val dayTurns = LocalDateTime(Days.dayOf(now, dayEnd).plusDays(1), LocalTime(dayEnd, 0))
        val soon = System.currentTimeMillis() + 1_000
        val wake = listOfNotNull(
            nextAlert?.millis(),
            nextEvening?.millis(),
            nextMood?.millis(),
            dayTurns.millis(),
            Snoozes.next(context)?.coerceAtLeast(soon),
            Focus.alarmAt(context)?.coerceAtLeast(soon),
        ).min()
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
        val goals = dao.activeCoreGoals()
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val defaultFinish = dao.setting(SETTING_DEFAULT_FINISH)?.toIntOrNull()?.toTime()
        // Snoozed reminders come back as "still to do".
        val snoozed = Snoozes.takeDue(context, until).map { snooze ->
            val at = snooze.at.local()
            Alert(snooze.goalId, Days.dayOf(at, dayEnd), at, AlertKind.AGAIN, number = snooze.number)
        }
        val due = (Alerts.between(goals, since.local(), until.local(), dayEnd, defaultFinish, usualTimes(dao), nudgePlan(dao)).filter { it.at.millis() > since } + snoozed)
            .sortedBy { it.at }
        if (mornings(dao, since.local(), until.local(), dayEnd).any { it.at.millis() > since }) {
            showMorningPlan(context, dayEnd)
        }
        weeklies(dao, since.local(), until.local(), dayEnd).lastOrNull { it.at.millis() > since }?.let { showWeekly(context, it.day) }
        // The evening check: what is still open today. Nothing on a day when everything is done.
        eveningChecks(dao, since.local(), until.local()).lastOrNull { it.at.millis() > since }?.let { showEvening(context, it.list, dayEnd) }
        // The mood reminder: it only asks, and not once that day's mood is given.
        if (moodReminders(dao, since.local(), until.local()).any { it.at.millis() > since }) showMoodReminder(context, dayEnd)
        // A project's deadline, the day before and on the day, while it isn't finished.
        deadlineReminders(dao, since.local(), until.local()).filter { it.at.millis() > since }.forEach { showDeadline(context, it, dayEnd) }
        // A bill, or regular money in, coming up: some days before it is due, as set on it. It opens the Budget tab.
        billReminders(dao, since.local(), until.local()).filter { it.at.millis() > since }.forEach { showDeadline(context, it, dayEnd, open = "budget") }
        // A focus timer whose time is up (not one ending in the next few seconds): its goal is
        // ticked here, unless the app on screen took the timer first and ticked it there.
        Focus.takeEnded(context, nowMillis)?.let { focus ->
            val container = (context.applicationContext as ChecklistApplication).container
            if (focus.isBreak) {
                // A break after a focus session: only "Break's over". Nothing is ticked or counted.
                showBreakDone(context)
            } else {
                // The time really focused is kept, as it is when the app ends the timer itself.
                container.repository.recordFocus(focus, nowMillis)
                val result = container.book.tickHabit(focus.goalId)
                if (result is io.github.randomtools3377.checklist.core.TickResult.Ticked && result.finished) afterDone(context, focus.goalId)
                dao.goal(focus.goalId)?.toCore()?.let { goal ->
                    showFocusDone(context, goal, result, hidden = dao.setting(LOCK_PRIVATE) == "1" && goal.private)
                }
            }
        }
        if (due.isNotEmpty()) {
            val earliest = due.minOf { it.day }.startOfYear()
            val ticks = dao.habitTicksSince(earliest.toEpochDay()).map { it.toCore() }
            val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
            val light = dao.setting(SETTING_REMINDER_LIGHT) == "1"
            val container = (context.applicationContext as ChecklistApplication).container
            // Late nudges are worded with the goal's own reason and the streak as it is right now.
            val nudges = if (due.any { it.kind == AlertKind.NUDGE }) nudgeWriter(dao) else null
            // What is at stake today ("Last one for today"), said after a reminder's own words.
            val stakes = stakeWriterOrNull(dao)
            for (alert in due) {
                val goal = goals.find { it.id == alert.goalId } ?: continue
                val window = goal.repeat?.window(alert.day) ?: continue
                // Done or skipped as Today counts it; one of several a day until that one is done.
                val lapses = dao.lapses(goal.id, window.start.toEpochDay(), window.end.toEpochDay()).map { it.toCore() }
                if (!Alerts.stillToDo(goal, window, ticks, lapses, alert.number)) continue
                if (alert.kind == AlertKind.NUDGE && nudges != null) {
                    val count = Alerts.timesDone(goal, window, ticks)
                    showNudge(context, goal, alert, nudges.words(goal, alert, count), hidden = nudges.hidden(goal), dayEnd = dayEnd, light = light)
                    continue
                }
                val skip = container.lapses.checkSkip(goal.id) as? SkipCheck.Allowed
                show(
                    context, goal, alert, Alerts.timesDone(goal, window, ticks), hidden = hidePrivate && goal.private, skip = skip, dayEnd = dayEnd, light = light,
                    stake = stakes?.reminder(goal, alert),
                )
            }
        }
        prefs.edit().putLong(LAST_FIRED, until).apply()
        reschedule(context)
    }

    /**
     * When each goal is usually ticked, from the last 4 weeks, for reminders at the usual time;
     * empty while that extra is off.
     */
    private suspend fun usualTimes(dao: io.github.randomtools3377.checklist.data.ChecklistDao): Map<Long, LocalTime> {
        if (!Feature.USUAL_REMINDERS.isOn(dao.setting(Feature.USUAL_REMINDERS.key))) return emptyMap()
        val since = nowLocal().date.minusDays(28)
        return UsualTimes.of(
            dao.habitTicksSince(since.toEpochDay()).groupBy { it.goalId }.mapValues { (_, ticks) ->
                ticks.map { tick -> tick.at.toLocalDateTime().let { it.hour * 60 + it.minute } }
            },
        )
    }

    /**
     * A goal just done: the goals that come after it in a habit chain, still to do today, are
     * reminded straight away.
     */
    suspend fun afterDone(context: Context, goalId: Long) {
        val container = (context.applicationContext as ChecklistApplication).container
        val dao = container.db.dao()
        val goals = dao.activeCoreGoals()
        if (goals.none { it.after == goalId }) return
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val now = nowLocal()
        val day = Days.dayOf(now, dayEnd)
        val since = Days.earliestOpenDay(day)
        val ticks = dao.habitTicksSince(since.toEpochDay()).map { it.toCore() }
        val view = TodayList.build(day, goals, dao.allSteps().map { it.toCore() }, ticks, 0, dao.lapsesSince(since.toEpochDay()).map { it.toCore() })
        val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
        val light = dao.setting(SETTING_REMINDER_LIGHT) == "1"
        for (row in Chains.next(goalId, view)) {
            val skip = container.lapses.checkSkip(row.goal.id) as? SkipCheck.Allowed
            show(context, row.goal, Alert(row.goal.id, day, now, AlertKind.NEXT), row.count, hidden = hidePrivate && row.goal.private, skip = skip, dayEnd = dayEnd, light = light)
        }
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
        // What comes first is what Today, the Today widget and the tile show first.
        val today = io.github.randomtools3377.checklist.widget.DayPlans.today(context)
        val plan = MorningPlan.of(today.view, today.order) ?: return
        val title = when {
            plan.goals == 0 && plan.alsoOpen == 0 -> "Today: nothing due"
            plan.goals == 0 -> "Today: nothing due, ${plan.alsoOpen} still open this week"
            plan.goals == 1 -> "Today: 1 goal"
            else -> "Today: ${plan.goals} goals"
        }
        // The reward being saved for, in one short line; left out while the shop is behind the lock.
        val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
        val saving: String? = try {
            io.github.randomtools3377.checklist.data.SavingFors.morningLine(dao, today.day, dayEnd)
        } catch (e: Exception) {
            null
        }
        // The streak today's goals keep going ("Day 13 of your streak"); left out behind the review's lock.
        val stake: String? = stakeWriterOrNull(dao)?.morning(today.day)
        val text = buildList {
            stake?.let { add(it) }
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
            if (plan.todos > 0) add(if (plan.todos == 1) "1 to-do waiting" else "${plan.todos} to-dos waiting")
            saving?.let { add(it) }
        }.joinToString(" · ")
        val open = PendingIntent.getActivity(
            context,
            MORNING_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_MORNING)
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
    private const val WEEKLY_ID = 901

    /** The weekly summaries between [from] and [to], unless turned off. */
    private suspend fun weeklies(dao: io.github.randomtools3377.checklist.data.ChecklistDao, from: LocalDateTime, to: LocalDateTime, dayEnd: Int): List<Alert> {
        if (dao.setting(SETTING_WEEKLY) == "0") return emptyList()
        return Alerts.weekly(from, to, WeeklySummary.AT, dayEnd)
    }

    /** The weekly summary: how the week up to [day] went. Opens the review. Behind the lock, only that it's ready. */
    private suspend fun showWeekly(context: Context, day: kotlinx.datetime.LocalDate) {
        if (!canNotify(context)) return
        val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
        // The week in words, as worked out in the shared code: against the week before only when
        // it is up, and with the race's result only when the week was won.
        val text = weeklyWords(dao, day) ?: return
        val open = PendingIntent.getActivity(
            context,
            WEEKLY_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, "review")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_WEEKLY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Your week")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(WEEKLY_ID, notification)
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }

    /**
     * A project's deadline is tomorrow or today: its name, when it is due and how many steps are
     * left (a private one behind the lock says only when). Tapping it opens Today, where the
     * project is. It goes when its day ends at [dayEnd].
     */
    private fun showDeadline(context: Context, reminder: DeadlineReminder, dayEnd: Int, open: String = "today") {
        if (!canNotify(context)) return
        val endsIn = LocalDateTime(reminder.day.plusDays(1), LocalTime(dayEnd, 0)).millis() - System.currentTimeMillis()
        if (endsIn <= 0) return
        val id = notificationId(reminder.goalId)
        val opens = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, open)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminder.title)
            .setContentText(reminder.text)
            .setContentIntent(opens)
            .setAutoCancel(true)
            .setTimeoutAfter(endsIn)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }

    /** The mood reminder's notification. */
    private const val MOOD_ID = 906

    /**
     * The mood reminder: one notification that only asks how the user is feeling. No mood and no
     * note is ever in it. Tapping it opens Today with the check-in; it goes when the day ends at
     * [dayEnd], or once the mood is given (see [clearMoodReminder]).
     */
    private fun showMoodReminder(context: Context, dayEnd: Int) {
        if (!canNotify(context)) return
        val day = Days.dayOf(nowLocal(), dayEnd)
        val endsIn = LocalDateTime(day.plusDays(1), LocalTime(dayEnd, 0)).millis() - System.currentTimeMillis()
        if (endsIn <= 0) return
        val open = PendingIntent.getActivity(
            context,
            MOOD_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MoodReminder.OPEN)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(MoodReminder.TITLE)
            .setContentText(MoodReminder.TEXT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(endsIn)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(MOOD_ID, notification)
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }

    /** Takes the mood reminder away once today's mood is given, or the check-in is turned off. */
    private suspend fun clearMoodReminder(context: Context, dao: io.github.randomtools3377.checklist.data.ChecklistDao) {
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val given = dao.mood(Days.dayOf(nowLocal(), dayEnd).toEpochDay()) != null
        if (given || !Feature.MOOD.isOn(dao.setting(Feature.MOOD.key))) NotificationManagerCompat.from(context).cancel(MOOD_ID)
    }

    /** The evening check's notification (the timing notice has 902 and 903). */
    const val EVENING_ID = 905

    /** What the evening check that is showing says, to leave it alone while nothing has changed. */
    private const val EXTRA_EVENING_SAYS = "evening_says"

    private fun eveningSays(list: EveningList): String = list.title + "\n" + list.lines().joinToString("\n")

    /**
     * The evening check: one notification that lists what is still open on [list]'s day, with a
     * button that opens Today and one that ticks the first open goal. A locked private goal is
     * only "A private goal", and no button ticks it. It goes when that day ends at [dayEnd], and
     * shown again with a newer list it doesn't buzz again.
     */
    fun showEvening(context: Context, list: EveningList, dayEnd: Int) {
        if (!canNotify(context)) return
        val endsIn = LocalDateTime(list.day.plusDays(1), LocalTime(dayEnd, 0)).millis() - System.currentTimeMillis()
        if (endsIn <= 0) return
        val lines = list.lines()
        val open = PendingIntent.getActivity(
            context,
            EVENING_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, "today")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(list.title)
            .setContentText(lines.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(open)
            .addAction(0, "Open Today", open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setTimeoutAfter(endsIn)
            // Which day it is for and what it says, to bring it up to date (see refreshEvening).
            .addExtras(
                Bundle().apply {
                    putLong(DoneReceiver.EXTRA_DAY, list.day.toEpochDay())
                    putString(EXTRA_EVENING_SAYS, eveningSays(list))
                },
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        list.firstToTick?.let { item ->
            val done = PendingIntent.getBroadcast(
                context,
                EVENING_ID,
                Intent(context, DoneReceiver::class.java)
                    .putExtra(DoneReceiver.EXTRA_GOAL, item.goalId)
                    .putExtra(DoneReceiver.EXTRA_NOTIFICATION, EVENING_ID)
                    .putExtra(DoneReceiver.EXTRA_DAY, list.day.toEpochDay()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "${item.tick}: ${item.name}", done)
        }
        try {
            NotificationManagerCompat.from(context).notify(EVENING_ID, builder.build())
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }

    /**
     * Brings the evening check up to date while it is showing: what is still open now, or
     * nothing once everything is done or its day has ended. After a tick or a skip, from its own
     * button, in the app, on a widget or the tile.
     */
    suspend fun refreshEvening(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val shown = manager.activeNotifications.firstOrNull { it.id == EVENING_ID } ?: return
        val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val day = shown.notification.extras.getLong(DoneReceiver.EXTRA_DAY, Long.MIN_VALUE)
        val list = if (day == Days.dayOf(nowLocal(), dayEnd).toEpochDay()) eveningList(dao, day.toDay()) else null
        if (list == null) {
            manager.cancel(EVENING_ID)
        } else if (shown.notification.extras.getString(EXTRA_EVENING_SAYS) != eveningSays(list)) {
            showEvening(context, list, dayEnd)
        }
    }

    // Ticks from the start of the year cover every window an alert can fall in.
    private fun kotlinx.datetime.LocalDate.startOfYear() = Days.earliestOpenDay(this)

    fun notificationId(goalId: Long): Int = (goalId % 1_000_000).toInt() + 1000

    /** The break after a focus session ran out: says so. Tapping it opens the app. */
    private fun showBreakDone(context: Context) {
        if (!canNotify(context)) return
        val open = PendingIntent.getActivity(
            context,
            BREAK_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("☕ Break")
            .setContentText("Break's over")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(BREAK_ID, notification)
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }

    private const val BREAK_ID = 906

    /**
     * The focus timer ran out: says so, and that its goal was ticked. It has nothing to tap but
     * itself, which opens the app, and it takes the place of that goal's reminder.
     */
    private fun showFocusDone(context: Context, goal: Goal, result: io.github.randomtools3377.checklist.core.TickResult, hidden: Boolean) {
        if (!canNotify(context)) return
        val text = when (result) {
            is io.github.randomtools3377.checklist.core.TickResult.Ticked -> "Time's up: ticked, +${result.points + result.bonus}"
            io.github.randomtools3377.checklist.core.TickResult.AlreadyDone -> "Time's up: it was already done"
            else -> "Time's up"
        }
        val id = notificationId(goal.id)
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (hidden) "🔒 A private goal" else "${goal.icon} ${goal.name}")
            .setContentText(text)
            .setContentIntent(open)
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

    /**
     * Takes away the goal reminders showing whose goal is done or skipped for their day by now,
     * or no longer there. Ones shown before they carried their goal and day are left alone.
     */
    private suspend fun clearDone(context: Context, dao: io.github.randomtools3377.checklist.data.ChecklistDao, goals: List<Goal>) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val shown = manager.activeNotifications.filter { it.id >= 1000 && it.notification.extras.containsKey(DoneReceiver.EXTRA_DAY) }
        if (shown.isEmpty()) return
        val days = shown.map { it.notification.extras.getLong(DoneReceiver.EXTRA_DAY).toDay() }
        val ticks = dao.habitTicksSince(days.min().startOfYear().toEpochDay()).map { it.toCore() }
        val nudges = nudgePlan(dao)
        shown.forEachIndexed { i, notice ->
            val extras = notice.notification.extras
            val goal = goals.find { it.id == extras.getLong(DoneReceiver.EXTRA_GOAL, -1) }
            val window = goal?.repeat?.window(days[i])
            val toDo = goal != null && window != null && Alerts.stillToDo(
                goal, window, ticks,
                dao.lapses(goal.id, window.start.toEpochDay(), window.end.toEpochDay()).map { it.toCore() },
                extras.getInt(SnoozeReceiver.EXTRA_NUMBER, 0),
            )
            // A late nudge also goes once nudges are stopped: for today on the goal's card, or turned off.
            val stopped = goal != null && extras.getBoolean(EXTRA_NUDGE, false) &&
                (!Nudges.applies(goal, nudges) || nudges.stoppedOn(goal.id, days[i]))
            if (!toDo || stopped) manager.cancel(notice.id)
        }
    }

    /**
     * Shows a notification, or replaces it. Its words lead with the goal's own reason and never
     * say what it pays (see Alerts.goalWords). [hidden] leaves the goal's name and reason out
     * (privacy lock). It goes when the alert's day ends at [dayEnd], as its buttons act on the day
     * they're tapped. With [light] ("Light up the screen for reminders") it comes as a message
     * would (see EdgeLights). [stake] is what is at stake right now, said last ("Last one for
     * today"); never for a hidden goal.
     */
    fun show(
        context: Context, goal: Goal, alert: Alert, count: Int, hidden: Boolean, skip: SkipCheck.Allowed?, dayEnd: Int, light: Boolean = false,
        stake: String? = null,
    ) {
        if (!canNotify(context)) return
        val endsIn = LocalDateTime(alert.day.plusDays(1), LocalTime(dayEnd, 0)).millis() - System.currentTimeMillis()
        if (endsIn <= 0) return
        val title = if (hidden) "🔒 A private goal" else "${goal.icon} ${goal.name}"
        val details = Alerts.goalWords(goal, alert, count, ::timeText, stake = stake, hidden = hidden)

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
                .putExtra(DoneReceiver.EXTRA_NOTIFICATION, id)
                .putExtra(DoneReceiver.EXTRA_DAY, alert.day.toEpochDay()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (light) EdgeLights.createChannels(context)
        val builder = NotificationCompat.Builder(context, if (light) EdgeLights.REMINDER_CHANNEL else CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(details)
            // A reason can be longer than one line: it opens out in full.
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(open)
            .addAction(0, if (goal.quit) "Avoided" else if (goal.target > 1) "+1" else "Done", done)
            .setAutoCancel(true)
            .setTimeoutAfter(endsIn)
            // Which goal, day and time it is for, to take it away once that is done (see clearDone).
            .addExtras(
                Bundle().apply {
                    putLong(DoneReceiver.EXTRA_GOAL, goal.id)
                    putLong(DoneReceiver.EXTRA_DAY, alert.day.toEpochDay())
                    putInt(SnoozeReceiver.EXTRA_NUMBER, alert.number)
                },
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (light) EdgeLights.asMessage(builder)
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
                        .putExtra(DoneReceiver.EXTRA_NOTIFICATION, id)
                        .putExtra(DoneReceiver.EXTRA_DAY, alert.day.toEpochDay()),
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

    /**
     * Shows a late nudge in place of the goal's reminder or its earlier nudge: the goal and how
     * late it is, then [words], different each time. Its buttons are "Done" ("+1" for a goal
     * done several times), "Stop for today", which ends this goal's nudges until tomorrow, and on
     * a "Make it smaller" nudge for a goal with a length, "Focus", which opens the app with its
     * focus timer started. [hidden] names nothing (privacy lock), and then there is no "Focus".
     * It comes on its own channel, or with [light] like a message, as goal reminders do.
     */
    fun showNudge(context: Context, goal: Goal, alert: Alert, words: NudgeWords, hidden: Boolean, dayEnd: Int, light: Boolean = false) {
        if (!canNotify(context)) return
        val endsIn = LocalDateTime(alert.day.plusDays(1), LocalTime(dayEnd, 0)).millis() - System.currentTimeMillis()
        if (endsIn <= 0) return
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
                .putExtra(DoneReceiver.EXTRA_NOTIFICATION, id)
                .putExtra(DoneReceiver.EXTRA_DAY, alert.day.toEpochDay()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getBroadcast(
            context,
            id + 1_500_000,
            Intent(context, StopNudgesReceiver::class.java)
                .putExtra(DoneReceiver.EXTRA_GOAL, goal.id)
                .putExtra(DoneReceiver.EXTRA_NOTIFICATION, id)
                .putExtra(DoneReceiver.EXTRA_DAY, alert.day.toEpochDay()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (light) EdgeLights.createChannels(context)
        val builder = NotificationCompat.Builder(context, if (light) EdgeLights.REMINDER_CHANNEL else CHANNEL_NUDGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(words.title)
            .setContentText(words.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(words.text))
            .setContentIntent(open)
            .addAction(0, if (goal.target > 1) "+1" else "Done", done)
            .addAction(0, "Stop for today", stop)
            .setAutoCancel(true)
            .setTimeoutAfter(endsIn)
            // Which goal, day and time it is for, to take it away once that is done (see clearDone).
            .addExtras(
                Bundle().apply {
                    putLong(DoneReceiver.EXTRA_GOAL, goal.id)
                    putLong(DoneReceiver.EXTRA_DAY, alert.day.toEpochDay())
                    putInt(SnoozeReceiver.EXTRA_NUMBER, alert.number)
                    putBoolean(EXTRA_NUDGE, true)
                },
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        if (light) EdgeLights.asMessage(builder)
        val minutes = goal.minutes
        if (words.tone == NudgeTone.SMALLER && !hidden && minutes != null && minutes > 0 && !goal.hasAmount) {
            val focus = PendingIntent.getActivity(
                context,
                id + 2_000_000,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_FOCUS, goal.id)
                    .putExtra(MainActivity.EXTRA_FOCUS_MINUTES, minutes)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Focus", focus)
        }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Notifications were turned off in the meantime.
        }
    }
}
