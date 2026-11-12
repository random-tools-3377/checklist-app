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
    /** A goal due every single day, which the calendar widget can leave out of its lists. */
    val everyDay: Boolean = false,
    /** A to-do with a date of its own, rather than one waiting on today's list. */
    val dated: Boolean = false,
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
data class AgendaDay(
    val date: LocalDate,
    val header: String,
    val lines: List<AgendaLine>,
    /** Everything on the day, when [lines] leaves out what the user chose not to see (see [CalendarShows]). */
    val all: List<AgendaLine> = lines,
) {
    /** Goals and to-dos that count for the day, shown or not: not the skipped ones, deadlines or events. */
    private val counted: List<AgendaLine>
        get() = all.filter { (it.kind == LineKind.GOAL || it.kind == LineKind.TODO) && it.state != LineState.SKIPPED }
    val due: Int get() = counted.size
    val done: Int get() = counted.count { it.state == LineState.DONE }

    /** What to say when no line shows: nothing is on the day, or what is on it was chosen not to be seen. */
    val emptyNote: String get() = if (all.size > lines.size) "What's on this day is hidden" else "Nothing due today"
}

/** One thing written inside a day of the month: an event, a project's deadline or a to-do with a date. */
data class DayLabel(val kind: LineKind, val name: String) {
    /** What comes before its name: 🚩 for a deadline always, and with [marks] ▪ for an event and ○ for a to-do. */
    fun mark(marks: Boolean): String = when (kind) {
        LineKind.DEADLINE -> "🚩"
        LineKind.EVENT -> if (marks) "▪" else ""
        LineKind.TODO -> if (marks) "○" else ""
        LineKind.GOAL -> ""
    }
}

/**
 * One thing the calendar widget can show or leave out, chosen in Settings → Appearance. Each is a
 * setting of its own ([key], "1" or "0"); until it is set, [usual] says whether it shows.
 */
enum class CalendarShow(val key: String, val label: String, val hint: String, val usual: Boolean) {
    COUNTS("calendar_widget_counts", "Goals done each day", "A count under every day of the month, like 2/3.", false),
    EVENTS("calendar_widget_events", "Calendar events", "From your calendar, while it is turned on.", true),
    DEADLINES("calendar_widget_deadlines", "Project deadlines", "🚩 on the day a project is due.", true),
    TODOS("calendar_widget_todos", "To-dos with a date", "On the day you gave them.", true),
    DAILY_GOALS("calendar_widget_daily_goals", "Daily goals in the list", "Goals due every day. Off, the list keeps the goals for some days only.", true),
    ;

    fun isOn(value: String?): Boolean = when (value) {
        "1" -> true
        "0" -> false
        else -> usual
    }

    fun valueOf(on: Boolean): String = if (on) "1" else "0"
}

/** What the calendar widget shows, of the things in [CalendarShow]: the usual ones until the user chooses. */
data class CalendarShows(val on: Set<CalendarShow> = CalendarShow.entries.filter { it.usual }.toSet()) {
    val counts: Boolean get() = CalendarShow.COUNTS in on
    val events: Boolean get() = CalendarShow.EVENTS in on
    val deadlines: Boolean get() = CalendarShow.DEADLINES in on
    val todos: Boolean get() = CalendarShow.TODOS in on
    val dailyGoals: Boolean get() = CalendarShow.DAILY_GOALS in on

    /** How many lines of labels a day's cell has: the count's line is free for a second one while it is hidden. */
    val labelLines: Int get() = if (counts) 1 else 2

    private fun keeps(kind: LineKind): Boolean = when (kind) {
        LineKind.EVENT -> events
        LineKind.DEADLINE -> deadlines
        LineKind.TODO -> todos
        LineKind.GOAL -> true
    }

    /**
     * Whether [line] stays on the agenda. A to-do without a date and a goal for some days only
     * always stay: the switches are for to-dos with a date and for goals due every day.
     */
    fun keeps(line: AgendaLine): Boolean = when (line.kind) {
        LineKind.TODO -> todos || !line.dated
        LineKind.GOAL -> dailyGoals || !line.everyDay
        else -> keeps(line.kind)
    }

    /** [day] with only the lines that stay. What it counts as due and done stays the whole day's. */
    fun day(day: AgendaDay): AgendaDay = day.copy(lines = day.lines.filter { keeps(it) })

    /** The agenda with only the lines that stay. [today] is always there; a later day with nothing left is left out. */
    fun days(days: List<AgendaDay>, today: LocalDate): List<AgendaDay> =
        days.map { day(it) }.filter { it.date == today || it.lines.isNotEmpty() }

    /** The month's labels with only the kinds that stay. */
    fun labels(labels: Map<LocalDate, List<DayLabel>>): Map<LocalDate, List<DayLabel>> =
        labels.mapValues { (_, on) -> on.filter { keeps(it.kind) } }.filterValues { it.isNotEmpty() }

    companion object {
        /** From the settings as stored, by [CalendarShow.key]. */
        fun of(settings: Map<String, String?>): CalendarShows = CalendarShows(CalendarShow.entries.filter { it.isOn(settings[it.key]) }.toSet())
    }
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
        /** False to list [today] like any other day (a day picked in the month): only what is on that day itself. */
        firstIsToday: Boolean = true,
    ): List<AgendaDay> = (0 until count).mapNotNull { offset ->
        val day = today.plusDays(offset)
        val isToday = offset == 0 && firstIsToday
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
                everyDay = per == null && goal.repeat?.isEveryDay == true,
                dated = goal.kind == GoalKind.TODO,
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
            // An "at most" goal isn't something to do at a time, so it isn't on the agenda.
            .filter { !it.archived && it.isHabitLike && !it.atMost }
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

    /** The month starting on [first], six weeks from a Monday, with [today] marked: any month, not only today's. */
    fun monthOf(first: LocalDate, today: LocalDate, goals: List<Goal>, ticks: List<Tick>, lapses: List<Lapse> = emptyList(), finished: Set<Long> = emptySet()): List<DayCell> {
        val start = monthGridStart(first)
        return cells((0 until 42).map { start.plusDays(it) }, first.monthNumber, today, goals, ticks, lapses, finished)
    }

    /**
     * What to write inside each day of a month grid, like a calendar app does: the phone's
     * calendar events, project deadlines not yet finished and to-dos with a date, by day,
     * events first. Goals in [hidden] (private, behind the lock) are left out.
     */
    fun labels(
        from: LocalDate,
        to: LocalDate,
        goals: List<Goal>,
        events: List<Busy>,
        finished: Set<Long> = emptySet(),
        hidden: Set<Long> = emptySet(),
    ): Map<LocalDate, List<DayLabel>> {
        val labels = mutableMapOf<LocalDate, MutableList<DayLabel>>()
        fun add(day: LocalDate, kind: LineKind, name: String) {
            if (day >= from && day <= to) labels.getOrPut(day) { mutableListOf() } += DayLabel(kind, name)
        }
        events.sortedBy { it.start }.forEach { add(it.start.date, LineKind.EVENT, it.title) }
        for (goal in goals) {
            if (goal.archived || goal.id in hidden) continue
            if (goal.kind == GoalKind.PROJECT && goal.id !in finished) goal.deadline?.let { add(it, LineKind.DEADLINE, goal.name) }
            if (goal.kind == GoalKind.TODO) goal.repeat?.takeIf { it.kind == RepeatKind.ONCE }?.let { add(it.start, LineKind.TODO, goal.name) }
        }
        return labels
    }

    /** How many characters of a label fit a day's cell on one line, its mark and "+2" included. */
    const val LABEL_CHARS = 8

    /**
     * What a day's cell writes, on at most [lines] lines, each cut to fit on one: the first things
     * on the day, and when there are more than fit, the last line ends in how many more, like
     * "Dent +2". A deadline always starts with 🚩; with [marks] (the colour-blind friendly look)
     * an event starts with ▪ and a to-do with ○, so it isn't only colour that tells them apart.
     */
    fun cellLabels(on: List<DayLabel>, lines: Int, marks: Boolean, width: Int = LABEL_CHARS): List<DayLabel> {
        if (lines <= 0 || on.isEmpty()) return emptyList()
        val more = on.size - lines
        return on.take(lines).mapIndexed { index, label ->
            val mark = label.mark(marks)
            val tail = if (more > 0 && index == lines - 1) " +$more" else ""
            val room = (width - tail.length - (if (mark.isEmpty()) 0 else 1)).coerceAtLeast(1)
            DayLabel(label.kind, mark + cut(label.name.trim(), room) + tail)
        }
    }

    /** [text] cut to [room] characters, never through the middle of an emoji's pair. */
    private fun cut(text: String, room: Int): String {
        if (text.length <= room) return text
        val cut = text.take(room)
        return (if (cut.last().isHighSurrogate()) cut.dropLast(1) else cut).trimEnd()
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
