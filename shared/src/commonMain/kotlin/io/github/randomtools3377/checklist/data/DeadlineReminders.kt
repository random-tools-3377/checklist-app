package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.Alerts
import io.github.randomtools3377.checklist.core.DeadlineReminder
import io.github.randomtools3377.checklist.core.Goal
import kotlinx.datetime.LocalDateTime

/** The projects that remind of their deadline, as they are now. */
private suspend fun deadlineGoals(dao: ChecklistDao): List<Goal> =
    dao.activeGoals().map { it.toCore() }.filter { Alerts.remindsOfDeadline(it) }

/**
 * When deadline reminders come from [from] (included) to [to] (not included): the day before a
 * project's deadline and on the day, at the time picked in its editor or when the user's day
 * starts. Whether the project is finished by then is not looked at here (see [deadlineReminders]).
 */
suspend fun deadlineTimes(dao: ChecklistDao, from: LocalDateTime, to: LocalDateTime): List<Alert> = Alerts.deadlines(
    deadlineGoals(dao), from, to,
    dayStart = dao.setting(SETTING_DAY_START)?.toIntOrNull()?.toTime(),
    dayEndHour = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR)),
)

/**
 * The deadline reminders due from [from] (included) to [to] (not included), in words, as things
 * stand now: a finished project has none, and a private one isn't named while the privacy lock
 * covers private goals. Android and the computer ask for the minute that just came; the iPhone
 * asks for the days ahead when it hands its reminders over, and again on every change.
 */
suspend fun deadlineReminders(dao: ChecklistDao, from: LocalDateTime, to: LocalDateTime): List<DeadlineReminder> {
    val goals = deadlineGoals(dao)
    if (goals.isEmpty()) return emptyList()
    val ids = goals.map { it.id }.toSet()
    return Alerts.deadlineReminders(
        goals = goals,
        steps = dao.allSteps().map { it.toCore() }.filter { it.goalId in ids },
        stepTicks = goals.flatMap { dao.stepTicks(it.id) }.map { it.toCore() },
        from = from,
        to = to,
        dayStart = dao.setting(SETTING_DAY_START)?.toIntOrNull()?.toTime(),
        dayEndHour = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR)),
        hidePrivate = dao.setting(LOCK_PRIVATE) == "1",
    )
}
