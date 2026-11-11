package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.AlertKind
import io.github.randomtools3377.checklist.core.Alerts
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Pace
import io.github.randomtools3377.checklist.core.UsualTimes
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
import io.github.randomtools3377.checklist.data.SharedParts
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.nowMillis
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.data.toLocalDateTime
import io.github.randomtools3377.checklist.data.toTime
import io.github.randomtools3377.checklist.ui.timeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.SystemTray
import java.awt.TrayIcon

/** One reminder to show: its words, with a locked private goal left unnamed. */
data class DueReminder(val goalId: Long, val title: String, val text: String)

/**
 * Reminders on a computer, while the app is open. Nothing can be handed to the computer ahead of
 * time, so once a minute the app looks for the reminders due since it last looked, worked out
 * as the iPhone works out the ones it hands over (leaving out what is done or skipped), and shows
 * them from its icon in the notification area. Nothing reminds while the app is closed, and
 * nothing shows where the computer has no notification area.
 */
class DesktopReminders(private val phone: DesktopPhone) {
    @Volatile
    private var tray: TrayIcon? = null

    /** The end of the last minute looked at: reminders before it were shown already. */
    private var lookedUntil: LocalDateTime? = null

    /** When the focus timer last shown as done ran out. */
    private var focusShown: Long? = null

    fun start(scope: CoroutineScope): Job = scope.launch {
        showIcon()
        while (isActive) {
            runCatching { look() }
            val minute = 60_000L - nowMillis() % 60_000L
            val focusLeft = phone.focus()?.takeIf { it.running }?.left(nowMillis())?.plus(500)
            delay(minOf(minute + 500, focusLeft ?: Long.MAX_VALUE).coerceAtLeast(500))
        }
    }

    /** Takes the icon away from the notification area, when the app closes. */
    fun stop() {
        tray?.let { icon -> runCatching { SystemTray.getSystemTray().remove(icon) } }
        tray = null
    }

    internal suspend fun look(now: LocalDateTime = nowLocal()) {
        val minute = LocalDateTime(now.date, LocalTime(now.hour, now.minute))
        val to = Pace.plusMinutes(minute, 1)
        // After the computer slept, only the last few minutes' reminders are still worth showing.
        val earliest = Pace.plusMinutes(minute, -CATCH_UP_MINUTES)
        val from = lookedUntil?.let { maxOf(it, earliest) } ?: minute
        lookedUntil = to
        if (from < to) {
            val due = due(phone.parts, from, to)
            when (due.size) {
                0 -> {}
                1 -> show(due[0].title, due[0].text)
                else -> show(
                    "${due.size} reminders",
                    due.take(4).joinToString("\n") { it.title } + if (due.size > 4) "\nand ${due.size - 4} more" else "",
                )
            }
        }
        focusDone()
    }

    /** The focus timer ran out since it was last looked at. */
    private suspend fun focusDone() {
        val state = phone.focus()?.takeIf { it.running } ?: return
        val now = nowMillis()
        if (state.endsAt > now || focusShown == state.endsAt) return
        focusShown = state.endsAt
        if (now - state.endsAt > CATCH_UP_MINUTES * 60_000L) return
        val dao = phone.parts.db.dao()
        val goal = dao.goal(state.goalId)?.toCore()
        val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
        val title = if (goal == null || (goal.private && hidePrivate)) "⏱ Focus" else "⏱ ${goal.icon} ${goal.name}"
        show(title, "Time's up")
    }

    private fun showIcon() {
        if (!canShow()) return
        EventQueue.invokeLater {
            runCatching {
                val icon = TrayIcon(checklistIcon(64), "Checklist")
                icon.isImageAutoSize = true
                icon.addActionListener { phone.bringToFront() }
                SystemTray.getSystemTray().add(icon)
                tray = icon
            }
        }
    }

    private fun show(title: String, text: String) {
        EventQueue.invokeLater {
            runCatching { tray?.displayMessage(title, text, TrayIcon.MessageType.NONE) }
        }
    }

    companion object {
        private const val CATCH_UP_MINUTES = 10L

        /** Whether this computer has a notification area to show reminders from. */
        fun canShow(): Boolean = !GraphicsEnvironment.isHeadless() && runCatching { SystemTray.isSupported() }.getOrDefault(false)

        /**
         * The reminders due from [from] (included) to [to] (not included), as the iPhone finds
         * them, without those already done for their time or skipped.
         */
        suspend fun due(parts: SharedParts, from: LocalDateTime, to: LocalDateTime): List<DueReminder> {
            val dao = parts.db.dao()
            val goals = dao.activeGoals().map { it.toCore() }
            val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
            val defaultFinish = dao.setting(SETTING_DEFAULT_FINISH)?.toIntOrNull()?.toTime()
            val usual = if (Feature.USUAL_REMINDERS.isOn(dao.setting(Feature.USUAL_REMINDERS.key))) {
                UsualTimes.of(
                    dao.habitTicksSince(to.date.minusDays(28).toEpochDay()).groupBy { it.goalId }.mapValues { (_, ticks) ->
                        ticks.map { tick -> tick.at.toLocalDateTime().let { it.hour * 60 + it.minute } }
                    },
                )
            } else {
                emptyMap()
            }
            val alerts = Alerts.between(goals, from, to, dayEnd, defaultFinish, usual).sortedBy { it.at }
            if (alerts.isEmpty()) return emptyList()
            val earliest = alerts.minOf { it.day }.minusDays(366)
            val ticks = dao.habitTicksSince(earliest.toEpochDay()).map { it.toCore() }
            val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
            return alerts.mapNotNull { alert ->
                val goal = goals.find { it.id == alert.goalId } ?: return@mapNotNull null
                val window = goal.repeat?.window(alert.day) ?: return@mapNotNull null
                val lapses = dao.lapses(goal.id, window.start.toEpochDay(), window.end.toEpochDay()).map { it.toCore() }
                if (!Alerts.stillToDo(goal, window, ticks, lapses, alert.number)) return@mapNotNull null
                val hidden = hidePrivate && goal.private
                DueReminder(
                    goalId = goal.id,
                    title = if (hidden) "🔒 A private goal" else "${goal.icon} ${goal.name}",
                    text = if (hidden) Alerts.lockedWords(alert) else words(alert),
                )
            }.distinctBy { it.goalId to it.text }
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
    }
}
