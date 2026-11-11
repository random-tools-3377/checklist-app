@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.randomtools3377.checklist.ios

import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.AlertKind
import io.github.randomtools3377.checklist.core.Alerts
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Pace
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.UsualTimes
import io.github.randomtools3377.checklist.core.countsToward
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
import io.github.randomtools3377.checklist.data.SharedParts
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.data.toLocalDateTime
import io.github.randomtools3377.checklist.data.toTime
import io.github.randomtools3377.checklist.ui.timeText
import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Reminders on the iPhone: the next ones are handed to the iPhone ahead of time, since an app
 * can't wake itself up to show them. They are set again whenever goals, ticks or settings change,
 * so a goal ticked off has no reminder left waiting.
 */
internal object IosReminders {
    /** How far ahead, and how many: the iPhone keeps at most 64 waiting for an app. */
    private const val LOOK_AHEAD_DAYS = 3
    private const val MOST = 60

    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    /** Whether the user lets the app notify, as last seen. */
    var allowed: Boolean = false
        private set

    fun refreshAllowed(then: (Boolean) -> Unit = {}) {
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            allowed = status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional
            then(allowed)
        }
    }

    fun ask(then: (Boolean) -> Unit) {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            allowed = granted
            then(granted)
        }
    }

    suspend fun reschedule(parts: SharedParts) {
        val dao = parts.db.dao()
        val goals = dao.activeGoals().map { it.toCore() }
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val now = nowLocal()
        val from = Pace.plusMinutes(now, 1)
        val to = Pace.plusMinutes(now, LOOK_AHEAD_DAYS * 24 * 60L)
        val defaultFinish = dao.setting(SETTING_DEFAULT_FINISH)?.toIntOrNull()?.toTime()
        val usual = if (Feature.USUAL_REMINDERS.isOn(dao.setting(Feature.USUAL_REMINDERS.key))) {
            UsualTimes.of(
                dao.habitTicksSince(now.date.minusDays(28).toEpochDay()).groupBy { it.goalId }.mapValues { (_, ticks) ->
                    ticks.map { tick -> tick.at.toLocalDateTime().let { it.hour * 60 + it.minute } }
                },
            )
        } else {
            emptyMap()
        }
        val alerts = Alerts.between(goals, from, to, dayEnd, defaultFinish, usual).sortedBy { it.at }
        // Leave out what is already done for its time.
        val earliest = alerts.minOfOrNull { it.day }?.minusDays(366)
        val ticks = earliest?.let { dao.habitTicksSince(it.toEpochDay()).map { tick -> tick.toCore() } }.orEmpty()
        val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
        val waiting = alerts.filter { alert ->
            val goal = goals.find { it.id == alert.goalId } ?: return@filter false
            val window = goal.repeat?.window(alert.day) ?: return@filter false
            val count = ticks.count { it.countsToward(goal, window.start) }
            val skipped = window.period == Period.DAILY && dao.lapses(goal.id, window.start.toEpochDay(), window.end.toEpochDay())
                .any { it.kind == "SKIP" && it.periodStart == window.start.toEpochDay() }
            count < (if (alert.number > 0) alert.number else goal.target) && !skipped
        }.take(MOST)

        center.removeAllPendingNotificationRequests()
        for (alert in waiting) {
            val goal = goals.find { it.id == alert.goalId } ?: continue
            val hidden = hidePrivate && goal.private
            val content = UNMutableNotificationContent()
            content.title = if (hidden) "🔒 A private goal" else "${goal.icon} ${goal.name}"
            content.body = if (hidden) "Due now" else words(alert)
            content.sound = UNNotificationSound.defaultSound
            val request = UNNotificationRequest.requestWithIdentifier(
                "goal-${alert.goalId}-${alert.at}-${alert.kind}",
                content,
                UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components(alert.at), repeats = false),
            )
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
    }

    private fun words(alert: Alert): String = when (alert.kind) {
        AlertKind.ON_TIME -> "Now"
        AlertKind.BEFORE -> "In ${alert.minutesBefore} min, at ${timeText(alert.at.time)}"
        AlertKind.REMINDER -> "Reminder"
        AlertKind.MORNING -> "Your plan for today"
        AlertKind.AGAIN -> "Still to do"
        AlertKind.WEEKLY -> "Your week"
        AlertKind.FOCUS_DONE -> "Time's up"
        AlertKind.USUAL -> "About now, as usual"
        AlertKind.NEXT -> "Next up"
    }

    private fun components(at: LocalDateTime): NSDateComponents = NSDateComponents().apply {
        year = at.year.toLong()
        month = at.monthNumber.toLong()
        day = at.dayOfMonth.toLong()
        hour = at.hour.toLong()
        minute = at.minute.toLong()
    }
}
