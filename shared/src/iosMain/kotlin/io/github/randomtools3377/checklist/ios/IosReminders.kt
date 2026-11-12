@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.randomtools3377.checklist.ios

import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.AlertKind
import io.github.randomtools3377.checklist.core.Alerts
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Pace
import io.github.randomtools3377.checklist.core.UsualTimes
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
import io.github.randomtools3377.checklist.data.SharedParts
import io.github.randomtools3377.checklist.data.deadlineReminders
import io.github.randomtools3377.checklist.data.eveningChecks
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.nowMillis
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.data.toLocalDateTime
import io.github.randomtools3377.checklist.data.toTime
import io.github.randomtools3377.checklist.ui.timeText
import io.github.randomtools3377.checklist.reminders.FocusState
import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * Reminders on the iPhone: the next ones are handed to the iPhone ahead of time, since an app
 * can't wake itself up to show them. They are set again whenever goals, ticks or settings change,
 * so a goal ticked off has no reminder left waiting, and the evening check lists what is open now.
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

    /** Kept here: the iPhone holds its notification delegate only weakly. */
    private val whileOpen: UNUserNotificationCenterDelegateProtocol = ShowWhileOpen()

    /** Reminders show while the app is open too; the iPhone keeps them quiet then otherwise. */
    fun showWhileOpen() {
        center.delegate = whileOpen
    }

    fun ask(then: (Boolean) -> Unit) {
        center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
            allowed = granted
            then(granted)
        }
    }

    /**
     * Hands over the reminders of the next few days, and "Time's up" for a running [focus]
     * timer, in place of everything handed over before.
     */
    suspend fun reschedule(parts: SharedParts, focus: FocusState? = null) {
        val dao = parts.db.dao()
        val goals = dao.activeGoals().map { it.toCore() }
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val now = nowLocal()
        // From now: everything waiting is taken back below, so one due within the minute is
        // handed over again too.
        val from = now
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
        val stillToDo = alerts.filter { alert ->
            val goal = goals.find { it.id == alert.goalId } ?: return@filter false
            val window = goal.repeat?.window(alert.day) ?: return@filter false
            val lapses = dao.lapses(goal.id, window.start.toEpochDay(), window.end.toEpochDay()).map { it.toCore() }
            Alerts.stillToDo(goal, window, ticks, lapses, alert.number)
        }
        // The evening check for each of the next days, when it is on: what is still open on its
        // day as things stand. Everything is handed over again on every change, so its list stays
        // current, and a day with nothing open has none.
        val evenings = eveningChecks(dao, from, to)
        // Every day's first reminders before "keep reminding" repeats, so one day's repeats
        // don't take the places of the next days' reminders.
        // A project's deadline, the day before and on the day, while it isn't finished.
        val deadlines = deadlineReminders(dao, from, to).take((MOST - evenings.size).coerceAtLeast(0))
        val waiting = Alerts.keep(stillToDo, (MOST - evenings.size - deadlines.size).coerceAtLeast(0))

        center.removeAllPendingNotificationRequests()
        for (alert in waiting) {
            val goal = goals.find { it.id == alert.goalId } ?: continue
            val hidden = hidePrivate && goal.private
            // The text is set through its setters: Kotlin sees the read-only parent's properties.
            val content = UNMutableNotificationContent()
            content.setTitle(if (hidden) "🔒 A private goal" else "${goal.icon} ${goal.name}")
            content.setBody(if (hidden) Alerts.lockedWords(alert) else words(alert))
            content.setSound(UNNotificationSound.defaultSound)
            val request = UNNotificationRequest.requestWithIdentifier(
                "goal-${alert.goalId}-${alert.at}-${alert.kind}",
                content,
                UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components(alert.at), repeats = false),
            )
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
        for (evening in evenings) {
            val content = UNMutableNotificationContent()
            content.setTitle(evening.list.title)
            content.setBody(evening.list.lines().joinToString("\n"))
            content.setSound(UNNotificationSound.defaultSound)
            val request = UNNotificationRequest.requestWithIdentifier(
                "evening-${evening.at}",
                content,
                UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components(evening.at), repeats = false),
            )
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
        for (deadline in deadlines) {
            val content = UNMutableNotificationContent()
            content.setTitle(deadline.title)
            content.setBody(deadline.text)
            content.setSound(UNNotificationSound.defaultSound)
            val request = UNNotificationRequest.requestWithIdentifier(
                "deadline-${deadline.goalId}-${deadline.at}",
                content,
                UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components(deadline.at), repeats = false),
            )
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
        // A focus timer running: "Time's up" when it runs out, as the app can't wake itself then.
        focus?.takeIf { it.running }?.let { state ->
            val seconds = (state.endsAt - nowMillis()) / 1000.0
            if (seconds < 1.0) return@let
            val goal = if (state.isBreak) null else dao.goal(state.goalId)?.toCore()
            val content = UNMutableNotificationContent()
            content.setTitle(
                when {
                    state.isBreak -> "☕ Break"
                    goal == null || (hidePrivate && goal.private) -> "⏱ Focus"
                    else -> "⏱ ${goal.icon} ${goal.name}"
                },
            )
            content.setBody(if (state.isBreak) "Break's over" else "Time's up")
            content.setSound(UNNotificationSound.defaultSound)
            val request = UNNotificationRequest.requestWithIdentifier(
                "focus",
                content,
                UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false),
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
        AlertKind.DEADLINE -> "Due soon"
    }

    private fun components(at: LocalDateTime): NSDateComponents = NSDateComponents().apply {
        year = at.year.toLong()
        month = at.monthNumber.toLong()
        day = at.dayOfMonth.toLong()
        hour = at.hour.toLong()
        minute = at.minute.toLong()
    }
}

/** Lets a reminder show as a banner, with its sound, while the app is open. */
private class ShowWhileOpen : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound)
    }
}
