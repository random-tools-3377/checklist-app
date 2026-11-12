package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

/** A habit on a day's agenda. [time] is its clock time, if it has one. */
data class AgendaItem(val goal: Goal, val count: Int, val time: LocalTime?) {
    val done: Boolean get() = count >= goal.target
}

/**
 * One day in the calendar: how many single-day habits and dated to-dos were due and how many
 * got done. A skipped one that wasn't done is left out of both, as the main streak leaves it
 * out. [deadlines] is how many projects still open are due that day.
 */
data class DayCell(
    val date: LocalDate,
    val due: Int,
    val done: Int,
    val inMonth: Boolean,
    val isToday: Boolean,
    val deadlines: Int = 0,
    /** Before today: what wasn't done then was left undone. */
    val isPast: Boolean = false,
) {
    /** Good once everything due is done; bad for a past day with something left undone. */
    val tone: DayTone
        get() = when {
            due > 0 && done >= due -> DayTone.GOOD
            isPast && due > 0 -> DayTone.BAD
            else -> DayTone.PLAIN
        }

    /** What says it without colour: ✓ everything done, ◐ some of it, ✕ none of it on a past day. */
    val mark: String
        get() = when {
            due == 0 -> ""
            done >= due -> "✓"
            done > 0 -> "◐"
            isPast -> "✕"
            else -> ""
        }

    /**
     * The small count under the day's number: "2/3" done of due for today and past days, and
     * just how many are due, "3", for the days still to come. Empty with nothing due. With
     * [marks] (the colour-blind friendly look) its mark comes first, like "✓3/3".
     */
    fun label(marks: Boolean): String = when {
        due == 0 -> ""
        !isPast && !isToday && done == 0 -> "$due"
        else -> (if (marks) mark else "") + "$done/$due"
    }
}

/** How a day in the month went, for its colour: the app's good and bad colours, or neither. */
enum class DayTone { PLAIN, GOOD, BAD }

/** What a line on the calendar's agenda is. */
enum class LineKind { GOAL, TODO, DEADLINE, EVENT }

/** How a line stands: still open, done, skipped (never done), or late (open, with its time passed). */
enum class LineState { OPEN, DONE, SKIPPED, LATE }

/**
 * One line of a day's agenda on the calendar widget: a goal or a to-do due that day, a project's
 * deadline, or an event from the phone's calendar. A private goal behind the privacy lock is
 * [hidden]: it keeps its time and how it stands, but is only "🔒 A private goal".
 */
data class AgendaLine(
    val kind: LineKind,
    /** Null for a calendar event. */
    val goalId: Long?,
    val icon: String,
    val name: String,
    /** Its clock time, or an event's start. Null without one. */
    val time: LocalTime? = null,
    /** When a timed event ends. */
    val until: LocalTime? = null,
    val allDay: Boolean = false,
    val state: LineState = LineState.OPEN,
    val count: Int = 0,
    val target: Int = 1,
    /** "1 of 2", "2 of 3 this week", "3 steps left", "To-do"; null with nothing to add. */
    val detail: String? = null,
    /** Today's goals and to-dos can be ticked on the widget, and a tick taken back. */
    val canTick: Boolean = false,
    /** Ticked more than once: its button says "+1" until it is done. */
    val plusOne: Boolean = false,
    val color: Int? = null,
    val hidden: Boolean = false,
) {
    val title: String get() = "$icon $name"

    /** How it stands in words and marks, so colour never says it alone; null while simply open. */
    val stateWord: String?
        get() = when (state) {
            LineState.OPEN -> null
            LineState.DONE -> "✓ done"
            LineState.SKIPPED -> "– skipped"
            LineState.LATE -> "! late"
        }
}

/** One day of the agenda, under its [header]: "Today", "Tomorrow" or "Fri 14". */
data class AgendaDay(val date: LocalDate, val header: String, val lines: List<AgendaLine>) {
    /** Goals and to-dos that count for the day: not the skipped ones, deadlines or events. */
    private val counted: List<AgendaLine>
        get() = lines.filter { (it.kind == LineKind.GOAL || it.kind == LineKind.TODO) && it.state != LineState.SKIPPED }
    val due: Int get() = counted.size
    val done: Int get() = counted.count { it.state == LineState.DONE }
}

/** What the calendar widget shows: today's agenda, the days ahead, this week, and this month. */
object CalendarView {
    /** How many days the agenda of the next days covers, today included. */
    const val AGENDA_DAYS = 7

    /** A day's header on the agenda. */
    fun header(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> "${weekdayShort(date.dayOfWeek.isoDayNumber)} ${date.dayOfMonth}"
    }

    /**
     * The agenda from [today] for [count] days, like a calendar app's: each day's events from
     * the phone's calendar ([events], empty while that is off), project deadlines, goals and
     * dated to-dos. In a day, all-day events come first, then deadlines, then everything with a
     * time in time order (a time before [dayEndHour] is the night after), then goals without one
     * in the user's order. Today also has the goals counted per week, month or year that are
     * still open, and the to-dos without a date. Today is always there; a later day with nothing
     * on it is left out. [ticks] need the step ticks of projects and to-dos as well.
     *
     * Goals in [hidden] (private ones behind the lock) are shown as "A private goal": no name,
     * icon, colour or detail, and nothing to tick.
     */
    fun days(
        today: LocalDate,
        now: LocalDateTime,
        count: Int,
        goals: List<Goal>,
        steps: List<Step>,
        ticks: List<Tick>,
        lapses: List<Lapse> = emptyList(),
        checks: List<Check> = emptyList(),
        events: List<Busy> = emptyList(),
        dayEndHour: Int = 0,
        hidden: Set<Long> = emptySet(),
    ): List<AgendaDay> = (0 until count).mapNotNull { offset ->
        val day = today.plusDays(offset)
        val isToday = offset == 0
        val view = TodayList.build(day, goals, steps, ticks, 0, lapses, checks)
        // Each line with where it sorts: its group, then its moment or its place in the list.
        val lines = mutableListOf<Triple<Int, LocalDateTime?, AgendaLine>>()

        BusyTime.on(events, day, dayEndHour).forEach { event ->
            val line = AgendaLine(
                kind = LineKind.EVENT, goalId = null, icon = "📅", name = event.title,
                time = event.start.time.takeIf { !event.allDay },
                until = event.end.time.takeIf { !event.allDay },
                allDay = event.allDay,
                detail = "All day".takeIf { event.allDay },
            )
            lines += if (event.allDay) Triple(0, null, line) else Triple(2, event.start, line)
        }

        view.projects.filter { it.goal.deadline == day }.forEach { project ->
            val line = AgendaLine(
                kind = LineKind.DEADLINE, goalId = project.goal.id, icon = project.goal.icon, name = project.goal.name,
                state = if (project.finished) LineState.DONE else LineState.OPEN,
                count = project.doneCount, target = project.steps.size,
                detail = when {
                    project.finished -> "Deadline"
                    project.left == 1 -> "Deadline · 1 step left"
                    project.left > 1 -> "Deadline · ${project.left} steps left"
                    else -> "Deadline"
                },
                color = project.goal.color,
            )
            lines += Triple(1, null, hide(line, hidden, "Deadline"))
        }

        fun habit(row: HabitRow, per: String?): AgendaLine {
            val goal = row.goal
            // A habit done several times a day shows its next set time, or its first.
            val times = goal.times.sorted()
            val time = if (per != null) null else goal.at ?: times.getOrNull(row.count) ?: times.firstOrNull()
            val state = when {
                row.done -> LineState.DONE
                row.settled -> LineState.SKIPPED
                isToday && time != null && Pace.on(day, time, dayEndHour) < now -> LineState.LATE
                else -> LineState.OPEN
            }
            val line = AgendaLine(
                kind = if (goal.kind == GoalKind.TODO) LineKind.TODO else LineKind.GOAL,
                goalId = goal.id, icon = goal.icon, name = goal.name, time = time, state = state,
                count = row.count, target = goal.target,
                detail = when {
                    goal.target > 1 && per != null -> "${row.count} of ${goal.target} this $per"
                    goal.target > 1 -> "${row.count} of ${goal.target}"
                    per != null -> "This $per"
                    else -> null
                },
                canTick = isToday && state != LineState.SKIPPED,
                plusOne = goal.target > 1 && !goal.hasAmount && !row.done,
                color = goal.color,
            )
            return hide(line, hidden, null)
        }

        view.dueToday.forEachIndexed { index, row ->
            val line = habit(row, null)
            lines += line.time?.let { Triple(2, Pace.on(day, it, dayEndHour), line) } ?: Triple(3 + index, null, line)
        }
        if (isToday) {
            val late = 3 + view.dueToday.size
            // Counted per week, month or year: on today's list while there is something left of them.
            listOf(view.thisWeek to "week", view.thisMonth to "month", view.thisYear to "year").forEach { (rows, per) ->
                rows.filter { !it.settled }.forEach { lines += Triple(late, null, habit(it, per)) }
            }
            view.todos.forEach { todo ->
                val line = AgendaLine(
                    kind = LineKind.TODO, goalId = todo.goal.id, icon = todo.goal.icon, name = todo.goal.name,
                    state = if (todo.done) LineState.DONE else LineState.OPEN,
                    count = if (todo.done) 1 else 0,
                    detail = "To-do", canTick = true, color = todo.goal.color,
                )
                lines += Triple(late + 1, null, hide(line, hidden, null))
            }
        }
        // A stable sort: lines in one group without a time keep the order they were added in.
        val sorted = lines.sortedWith(compareBy({ it.first }, { it.second })).map { it.third }
        if (sorted.isEmpty() && !isToday) null else AgendaDay(day, header(day, today), sorted)
    }

    /** [line] as it shows while its goal is behind the privacy lock: when and how it stands, nothing else. */
    private fun hide(line: AgendaLine, hidden: Set<Long>, detail: String?): AgendaLine =
        if (line.goalId == null || line.goalId !in hidden) line
        else line.copy(icon = "🔒", name = PrivateGoals.NAME, detail = detail, canTick = false, plusOne = false, color = null, hidden = true, count = 0, target = 1)

    /**
     * The habits due on [day], timed ones first in time order, then the rest in the user's
     * order. "Times per" habits are included while their window is open.
     */
    fun agenda(day: LocalDate, goals: List<Goal>, ticks: List<Tick>): List<AgendaItem> {
        val items = goals
            .filter { !it.archived && it.isHabitLike }
            .mapNotNull { goal ->
                val window = goal.repeat?.window(day) ?: return@mapNotNull null
                val count = goal.timesDone(ticks.filter { it.countsToward(goal, window.start) })
                // A habit done several times a day shows its next set time, or its first.
                val times = goal.times.sorted()
                AgendaItem(goal, count, Retimes.on(goal, day).at ?: times.getOrNull(count) ?: times.firstOrNull())
            }
        // To-dos without a date wait at the end, open or done today.
        val todos = goals
            .filter { !it.archived && it.kind == GoalKind.TODO && it.repeat == null }
            .sortedWith(compareBy({ it.position }, { it.id }))
            .map { goal -> AgendaItem(goal, if (Todos.doneOn(goal, day, ticks)) 1 else 0, null) }
        return items.sortedWith(compareBy({ it.time == null }, { it.time }, { it.goal.position }, { it.goal.id })) + todos
    }

    /** Single-day habits due on [day], and how many of them were done. */
    private fun counts(day: LocalDate, goals: List<Goal>, ticksByDay: Map<LocalDate, List<Tick>>, skipped: Set<Pair<Long, LocalDate>>): Pair<Int, Int> {
        var due = 0
        var done = 0
        val ticks = ticksByDay[day].orEmpty()
        for (goal in goals) {
            if (goal.archived || !goal.isHabitLike) continue
            val repeat = goal.repeat ?: continue
            if (repeat.kind == RepeatKind.TIMES_PER || !repeat.isDueOn(day)) continue
            val isDone = goal.timesDone(ticks.filter { it.countsToward(goal, day) }) >= goal.target
            // A skip is never done, and never held against the day.
            if (!isDone && (goal.id to day) in skipped) continue
            due++
            if (isDone) done++
        }
        return due to done
    }

    private fun cells(
        days: List<LocalDate>,
        month: Int?,
        today: LocalDate,
        goals: List<Goal>,
        ticks: List<Tick>,
        lapses: List<Lapse>,
        finished: Set<Long>,
    ): List<DayCell> {
        val ticksByDay = ticks.filter { it.stepId == null && it.period == Period.DAILY }.groupBy { it.periodStart }
        val skipped = lapses.filter { it.kind == LapseKind.SKIP && it.period == Period.DAILY && !it.forgotten }.map { it.goalId to it.periodStart }.toSet()
        val deadlines = goals.filter { it.kind == GoalKind.PROJECT && !it.archived && it.id !in finished }.mapNotNull { it.deadline }.groupingBy { it }.eachCount()
        return days.map { day ->
            val (due, done) = counts(day, goals, ticksByDay, skipped)
            DayCell(
                day, due, done,
                inMonth = month == null || day.monthNumber == month,
                isToday = day == today,
                deadlines = deadlines[day] ?: 0,
                isPast = day < today,
            )
        }
    }

    /** Monday to Sunday of the week containing [today]. */
    fun week(today: LocalDate, goals: List<Goal>, ticks: List<Tick>, lapses: List<Lapse> = emptyList(), finished: Set<Long> = emptySet()): List<DayCell> {
        val monday = today.minusDays(today.dayOfWeek.isoDayNumber - 1)
        return cells((0 until 7).map { monday.plusDays(it) }, null, today, goals, ticks, lapses, finished)
    }

    /**
     * Six weeks from the Monday on or before the 1st of [today]'s month. Skips in [lapses] are
     * left out of their day's count, and each day says how many project deadlines fall on it,
     * the projects in [finished] aside.
     */
    fun month(today: LocalDate, goals: List<Goal>, ticks: List<Tick>, lapses: List<Lapse> = emptyList(), finished: Set<Long> = emptySet()): List<DayCell> {
        val first = LocalDate(today.year, today.monthNumber, 1)
        val start = first.minusDays(first.dayOfWeek.isoDayNumber - 1)
        return cells((0 until 42).map { start.plusDays(it) }, today.monthNumber, today, goals, ticks, lapses, finished)
    }

    /** The projects in [goals] whose steps are all ticked, by [ticks]: their deadline no longer shows. */
    fun finishedProjects(goals: List<Goal>, steps: List<Step>, ticks: List<Tick>): Set<Long> {
        val ticked = ticks.mapNotNull { it.stepId }.toSet()
        return goals.filter { it.kind == GoalKind.PROJECT }.filter { goal ->
            val own = steps.filter { it.goalId == goal.id && !it.archived }
            own.isNotEmpty() && own.all { it.id in ticked }
        }.map { it.id }.toSet()
    }

    /** The first day the month grid shows, so the widget knows how far back to load ticks. */
    fun monthGridStart(today: LocalDate): LocalDate {
        val first = LocalDate(today.year, today.monthNumber, 1)
        return first.minusDays(first.dayOfWeek.isoDayNumber - 1)
    }
}
