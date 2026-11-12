package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.AgendaDay
import io.github.randomtools3377.checklist.core.Busy
import io.github.randomtools3377.checklist.core.CalendarView
import io.github.randomtools3377.checklist.core.DayCell
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.PrivateGoals
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * What the calendar widget shows, read from the database as it stands: the agenda of the next
 * days ([days], today first and always there), and the month around today, six weeks from a Monday.
 */
data class CalendarBoard(
    val today: LocalDate,
    val days: List<AgendaDay>,
    val month: List<DayCell>,
    /** Whether "+" adds a to-do (with To-dos on Today turned on) rather than a goal. */
    val addsTodo: Boolean = false,
)

/**
 * The calendar widget's data at [now]. [events] are the phone's calendar events for the days
 * shown, read by the phone when the calendar is turned on and allowed; empty otherwise. Private
 * goals are only "A private goal" while the privacy lock covers them.
 */
suspend fun calendarBoard(dao: ChecklistDao, now: LocalDateTime = nowLocal(), events: List<Busy> = emptyList()): CalendarBoard {
    val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
    val today = Days.dayOf(now, dayEnd)
    val goals = dao.activeGoals().map { it.toCore() }
    val hidden = if (dao.setting(LOCK_PRIVATE) == "1") PrivateGoals.ids(goals) else emptySet()
    val since = minOf(CalendarView.monthGridStart(today), Days.earliestOpenDay(today))
    // Steps ticked, for projects (how many are left by the deadline) and to-dos with steps.
    val withSteps = goals.filter { it.kind == GoalKind.PROJECT || it.kind == GoalKind.TODO }
    val ticks = (dao.habitTicksSince(since.toEpochDay()) + withSteps.flatMap { dao.stepTicks(it.id) }).map { it.toCore() }
    val lapses = dao.lapsesSince(since.toEpochDay()).map { it.toCore() }
    val steps = dao.allSteps().map { it.toCore() }
    return CalendarBoard(
        today = today,
        days = CalendarView.days(
            today, now, CalendarView.AGENDA_DAYS, goals, steps, ticks, lapses,
            checks = dao.allChecks().map { it.toCore() },
            events = events,
            dayEndHour = dayEnd,
            hidden = hidden,
        ),
        month = CalendarView.month(today, goals, ticks, lapses, CalendarView.finishedProjects(goals, steps, ticks)),
        addsTodo = Feature.TODOS.isOn(dao.setting(Feature.TODOS.key)),
    )
}

/**
 * Whether the phone's calendar is turned on in Settings, and from which calendars (all of them
 * when empty), for what reads it outside the app's screens.
 */
suspend fun calendarChoice(dao: ChecklistDao): Pair<Boolean, Set<Long>> =
    (dao.setting(SETTING_CALENDAR) == "1") to dao.setting(SETTING_CALENDAR_IDS).orEmpty().split(",").mapNotNull { it.toLongOrNull() }.toSet()
