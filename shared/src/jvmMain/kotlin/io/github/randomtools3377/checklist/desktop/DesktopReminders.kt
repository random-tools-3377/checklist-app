package io.github.randomtools3377.checklist.desktop

import io.github.randomtools3377.checklist.core.AlertKind
import io.github.randomtools3377.checklist.core.Alerts
import io.github.randomtools3377.checklist.core.MoodReminder
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Pace
import io.github.randomtools3377.checklist.core.UsualTimes
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
import io.github.randomtools3377.checklist.data.SharedParts
import io.github.randomtools3377.checklist.data.activeCoreGoals
import io.github.randomtools3377.checklist.data.billReminders
import io.github.randomtools3377.checklist.data.deadlineReminders
import io.github.randomtools3377.checklist.data.eveningChecks
import io.github.randomtools3377.checklist.data.moodReminders
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.nowMillis
import io.github.randomtools3377.checklist.data.nextInChain
import io.github.randomtools3377.checklist.data.nudgePlan
import io.github.randomtools3377.checklist.data.nudgeWriter
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.stakeWriterOrNull
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
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon

/** One reminder to show: its words, with a locked private goal left unnamed. */
data class DueReminder(val goalId: Long, val title: String, val text: String)

/**
 * Reminders on a computer, while the app is running: with its window open, or closed with the
 * app carrying on in the notification area (see [DesktopPhone.hideWindow]). Nothing can be handed to the computer ahead of
 * time, so once a minute the app looks for the reminders due since it last looked, worked out
 * as the iPhone works out the ones it hands over (leaving out what is done or skipped), and shows
 * them from its icon in the notification area. The evening check, when it is on, shows the same
 * way at its time, and so does a project's deadline reminder. The icon's menu has "Open Checklist"
 * and "Quit". Nothing reminds once the app has quit, and nothing shows where the computer has no
 * notification area.
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

    /** Whether the icon is in the notification area, so the app can be reached with its window closed. */
    fun hasIcon(): Boolean = tray != null

    /** A message from the icon that isn't a reminder, like that the app is still running. */
    fun say(title: String, text: String) = show(title, text)

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
            // The evening check, at its time: what is still open today.
            evening(phone.parts, from, to)?.let { show(it.title, it.text) }
            // The mood reminder, at its time, unless today's mood is in: it only asks.
            mood(phone.parts, from, to)?.let { show(it.title, it.text) }
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
        if (state.isBreak) {
            show("☕ Break", "Break's over")
            return
        }
        val dao = phone.parts.db.dao()
        val goal = dao.goal(state.goalId)?.toCore()
        val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
        val title = if (goal == null || (goal.private && hidePrivate)) "⏱ Focus" else "⏱ ${goal.icon} ${goal.name}"
        show(title, "Time's up")
    }

    /** The goals after [goalId] in a habit chain, still to do today, as "Next up". */
    suspend fun showNext(goalId: Long) {
        val next = nextInChain(phone.parts.db.dao(), goalId)
        when (next.size) {
            0 -> {}
            1 -> show(next[0].title, "Next up")
            else -> show("Next up", next.joinToString("\n") { it.title })
        }
    }

    private fun showIcon() {
        if (!canShow()) return
        EventQueue.invokeLater {
            // Without its icon a hidden app couldn't be reached, so its window shows.
            runCatching<Unit> {
                val icon = TrayIcon(checklistIcon(64), "Checklist")
                icon.isImageAutoSize = true
                icon.addActionListener { phone.bringToFront() }
                icon.popupMenu = PopupMenu().apply {
                    add(MenuItem(OPEN).apply { addActionListener { phone.bringToFront() } })
                    add(MenuItem(QUIT).apply { addActionListener { phone.quit() } })
                }
                SystemTray.getSystemTray().add(icon)
                tray = icon
            }.onFailure { phone.windowShown.value = true }
        }
    }

    private fun show(title: String, text: String) {
        EventQueue.invokeLater {
            runCatching { tray?.displayMessage(title, text, TrayIcon.MessageType.NONE) }
        }
    }

    companion object {
        private const val CATCH_UP_MINUTES = 10L

        /** The icon's menu. */
        const val OPEN = "Open Checklist"
        const val QUIT = "Quit"

        /** Whether this computer has a notification area to show reminders from. */
        fun canShow(): Boolean = !GraphicsEnvironment.isHeadless() && runCatching { SystemTray.isSupported() }.getOrDefault(false)

        /**
         * The reminders due from [from] (included) to [to] (not included), as the iPhone finds
         * them, without those already done for their time or skipped.
         */
        suspend fun due(parts: SharedParts, from: LocalDateTime, to: LocalDateTime): List<DueReminder> {
            val dao = parts.db.dao()
            val goals = dao.activeCoreGoals()
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
            // Late nudges come with the rest, in the same words as on a phone.
            val alerts = Alerts.between(goals, from, to, dayEnd, defaultFinish, usual, nudgePlan(dao)).sortedBy { it.at }
            // A project's deadline, the day before and on the day, while it isn't finished.
            // And a bill, or regular money in, coming up: some days before it is due, as set on it.
            val deadlines = (deadlineReminders(dao, from, to) + billReminders(dao, from, to)).map { DueReminder(it.goalId, it.title, it.text) }
            if (alerts.isEmpty()) return deadlines
            val earliest = alerts.minOf { it.day }.minusDays(366)
            val ticks = dao.habitTicksSince(earliest.toEpochDay()).map { it.toCore() }
            val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
            val nudges = if (alerts.any { it.kind == AlertKind.NUDGE }) nudgeWriter(dao) else null
            // What is at stake today, said after a reminder's own words, as on a phone.
            val stakes = stakeWriterOrNull(dao)
            return alerts.mapNotNull { alert ->
                val goal = goals.find { it.id == alert.goalId } ?: return@mapNotNull null
                val window = goal.repeat?.window(alert.day) ?: return@mapNotNull null
                val lapses = dao.lapses(goal.id, window.start.toEpochDay(), window.end.toEpochDay()).map { it.toCore() }
                if (!Alerts.stillToDo(goal, window, ticks, lapses, alert.number)) return@mapNotNull null
                val hidden = hidePrivate && goal.private
                if (alert.kind == AlertKind.NUDGE && nudges != null) {
                    // A late nudge: the goal and how late it is, then the words whose turn it is.
                    val words = nudges.words(goal, alert, Alerts.timesDone(goal, window, ticks))
                    return@mapNotNull DueReminder(goal.id, words.title, words.text)
                }
                DueReminder(
                    goalId = goal.id,
                    title = if (hidden) "🔒 A private goal" else "${goal.icon} ${goal.name}",
                    // Led by the goal's own reason, never what it pays; a locked private goal only says when.
                    text = Alerts.goalWords(
                        goal, alert, Alerts.timesDone(goal, window, ticks), ::timeText,
                        stake = stakes?.reminder(goal, alert), hidden = hidden,
                    ),
                )
            }.distinctBy { it.goalId to it.text } + deadlines
        }

        /**
         * The evening check due from [from] (included) to [to] (not included), if anything is
         * still open on its day: how many, and the list, as every phone words it.
         */
        suspend fun evening(parts: SharedParts, from: LocalDateTime, to: LocalDateTime): DueReminder? =
            eveningChecks(parts.db.dao(), from, to).lastOrNull()?.let { due ->
                DueReminder(Alerts.MORNING_GOAL, due.list.title, due.list.lines(EVENING_LINES).joinToString("\n"))
            }

        /**
         * The mood reminder due from [from] (included) to [to] (not included), if it is on and
         * that day's mood isn't given yet. It only asks: no mood or note is ever in it.
         */
        suspend fun mood(parts: SharedParts, from: LocalDateTime, to: LocalDateTime): DueReminder? =
            moodReminders(parts.db.dao(), from, to).lastOrNull()?.let { DueReminder(Alerts.MORNING_GOAL, MoodReminder.TITLE, MoodReminder.TEXT_COMPUTER) }

        /** How many lines of the evening check fit a message from the notification area. */
        private const val EVENING_LINES = 4
    }
}
