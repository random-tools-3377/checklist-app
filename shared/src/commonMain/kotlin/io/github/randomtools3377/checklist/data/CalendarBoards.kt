package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.AgendaDay
import io.github.randomtools3377.checklist.core.Busy
import io.github.randomtools3377.checklist.core.CalendarShow
import io.github.randomtools3377.checklist.core.CalendarShows
import io.github.randomtools3377.checklist.core.CalendarView
import io.github.randomtools3377.checklist.core.DayCell
import io.github.randomtools3377.checklist.core.DayLabel
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.PrivateGoals
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
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
    /** The first day of the month shown: today's, unless the user turned to another. */
    val monthFirst: LocalDate = LocalDate(today.year, today.monthNumber, 1),
    /** The day picked in the month, with its agenda; null while it is today. Nothing on it can be ticked. */
    val picked: AgendaDay? = null,
    /** What to write inside each day of the month: events, deadlines and dated to-dos, those the user hid left out. */
    val labels: Map<LocalDate, List<DayLabel>> = emptyMap(),
    /** What the user chose to see on the widget (Settings → Appearance); [days], [picked] and [labels] already follow it. */
    val shows: CalendarShows = CalendarShows(),
)

/**
 * The calendar widget's data at [now]. [events] are the phone's calendar events for the days
 * shown, read by the phone when the calendar is turned on and allowed; empty otherwise. Private
 * goals are only "A private goal" while the privacy lock covers them. What the user turned off
 * under "Calendar widget shows" is left out of the agenda and the month's labels.
 */
suspend fun calendarBoard(
    dao: ChecklistDao,
    now: LocalDateTime = nowLocal(),
    events: List<Busy> = emptyList(),
    /** The first day of the month to show; today's month with null. */
    month: LocalDate? = null,
    /** A day picked in the month, to show its agenda; today with null. */
    picked: LocalDate? = null,
): CalendarBoard {
    val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
    val today = Days.dayOf(now, dayEnd)
    val shows = calendarShows(dao)
    val goals = dao.activeGoals().map { it.toCore() }
    val hidden = if (dao.setting(LOCK_PRIVATE) == "1") PrivateGoals.ids(goals) else emptySet()
    val monthFirst = month?.let { LocalDate(it.year, it.monthNumber, 1) } ?: LocalDate(today.year, today.monthNumber, 1)
    val gridStart = CalendarView.monthGridStart(monthFirst)
    val since = minOf(gridStart, CalendarView.monthGridStart(today), Days.earliestOpenDay(today))
    // Steps ticked, for projects (how many are left by the deadline) and to-dos with steps.
    val withSteps = goals.filter { it.kind == GoalKind.PROJECT || it.kind == GoalKind.TODO }
    val ticks = (dao.habitTicksSince(since.toEpochDay()) + withSteps.flatMap { dao.stepTicks(it.id) }).map { it.toCore() }
    val lapses = dao.lapsesSince(since.toEpochDay()).map { it.toCore() }
    val steps = dao.allSteps().map { it.toCore() }
    val checks = dao.allChecks().map { it.toCore() }
    val finished = CalendarView.finishedProjects(goals, steps, ticks)
    // A picked day's agenda: only what is on that day, and nothing on it is late or can be ticked, since it isn't today.
    val pickedDay = picked?.takeIf { it != today }?.let { day ->
        val lines = CalendarView.days(
            day, LocalDateTime(day, kotlinx.datetime.LocalTime(0, 0)), 1, goals, steps, ticks, lapses,
            checks = checks, events = events, dayEndHour = dayEnd, hidden = hidden, firstIsToday = false,
        ).firstOrNull()?.lines.orEmpty().map { it.copy(canTick = false) }
        shows.day(AgendaDay(day, "${io.github.randomtools3377.checklist.core.weekdayShort(day.dayOfWeek.isoDayNumber)} ${day.dayOfMonth}", lines))
    }
    return CalendarBoard(
        today = today,
        days = shows.days(
            CalendarView.days(
                today, now, CalendarView.AGENDA_DAYS, goals, steps, ticks, lapses,
                checks = checks,
                events = events,
                dayEndHour = dayEnd,
                hidden = hidden,
            ),
            today,
        ),
        month = CalendarView.monthOf(monthFirst, today, goals, ticks, lapses, finished),
        monthFirst = monthFirst,
        picked = pickedDay,
        labels = shows.labels(CalendarView.labels(gridStart, gridStart.plusDays(41), goals, events, finished, hidden)),
        shows = shows,
        addsTodo = Feature.TODOS.isOn(dao.setting(Feature.TODOS.key)),
    )
}

/** What the calendar widget shows, as chosen under "Calendar widget shows" in Settings → Appearance. */
suspend fun calendarShows(dao: ChecklistDao): CalendarShows =
    CalendarShows(CalendarShow.entries.filter { it.isOn(dao.setting(it.key)) }.toSet())

/**
 * Whether the phone's calendar is turned on in Settings, and from which calendars (all of them
 * when empty), for what reads it outside the app's screens.
 */
suspend fun calendarChoice(dao: ChecklistDao): Pair<Boolean, Set<Long>> =
    (dao.setting(SETTING_CALENDAR) == "1") to dao.setting(SETTING_CALENDAR_IDS).orEmpty().split(",").mapNotNull { it.toLongOrNull() }.toSet()
