package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber

enum class AlertKind {
    /** At the habit's clock time. */
    ON_TIME,

    /** An earlier alert, some minutes before the clock time. */
    BEFORE,

    /** A habit without a clock time, reminded at a time of day. */
    REMINDER,

    /** The morning plan: today's list and the pace, when the user's day starts. Not for one goal. */
    MORNING,

    /** "Keep reminding": again, some minutes after the first reminder, while it isn't done. */
    AGAIN,

    /** Around the time of day the goal is usually ticked (see [UsualTimes]). */
    USUAL,

    /** Right after the goal that comes before it was done (see [Chains]). */
    NEXT,

    /** The weekly summary, on Sunday. Not for one goal. */
    WEEKLY,

    /** A focus timer ran out. */
    FOCUS_DONE,

    /** A project's deadline is near: the day before it, and on the day (see [Alerts.deadlines]). */
    DEADLINE,

    /** A bill, or regular money in, is due: some days before, as set on it (see [Alerts.bills]). */
    BILL,
}

/**
 * One notification due at [at], for [goalId] on the user's [day]. For a habit done several times
 * a day with a time for each, [number] is which one (1 for the first); otherwise 0.
 */
data class Alert(
    val goalId: Long,
    val day: LocalDate,
    val at: LocalDateTime,
    val kind: AlertKind,
    val minutesBefore: Int = 0,
    val number: Int = 0,
)

object Alerts {
    /**
     * Every notification from [from] (included) to [to] (not included). A habit counted per
     * week, month or year is reminded each day until its window is done; whether it is done is
     * checked when the alert fires.
     */
    fun between(
        goals: List<Goal>,
        from: LocalDateTime,
        to: LocalDateTime,
        dayEndHour: Int,
        defaultFinish: LocalTime? = null,
        usual: Map<Long, LocalTime> = emptyMap(),
    ): List<Alert> {
        val alerts = mutableListOf<Alert>()
        for (goal in goals) {
            // An "at most" goal has nothing to do, so nothing to remind of.
            if (goal.archived || !goal.isHabitLike || goal.atMost) continue
            val repeat = goal.repeat ?: continue
            val reminder = goal.reminder ?: Reminder()
            var day = from.date.minusDays(1)
            while (day <= to.date) {
                val firstOfDay = alerts.size
                if (repeat.isDueOn(day)) {
                    val at = Retimes.timeOn(goal, day)
                    val slots = Pace.slots(Retimes.on(goal, day), day, PaceSettings(dayEndHour, null)).filter { it.fixed }
                    if (slots.isNotEmpty()) {
                        // A time for each one, each with its own reminder.
                        for (slot in slots) {
                            if (reminder.onTime) alerts += Alert(goal.id, day, slot.at, AlertKind.ON_TIME, number = slot.number)
                            reminder.minutesBefore?.takeIf { it > 0 }?.let { before ->
                                alerts += Alert(goal.id, day, Pace.plusMinutes(slot.at, -before.toLong()), AlertKind.BEFORE, before, slot.number)
                            }
                        }
                    } else if (at != null) {
                        val time = Pace.on(day, at, dayEndHour)
                        if (reminder.onTime) alerts += Alert(goal.id, day, time, AlertKind.ON_TIME)
                        reminder.minutesBefore?.takeIf { it > 0 }?.let { before ->
                            alerts += Alert(goal.id, day, Pace.plusMinutes(time, -before.toLong()), AlertKind.BEFORE, before)
                        }
                    } else if (reminder.at != null) {
                        alerts += Alert(goal.id, day, Pace.on(day, reminder.at, dayEndHour), AlertKind.REMINDER)
                    } else {
                        // No time and no reminder: around when it is usually done, if that is known.
                        usual[goal.id]?.let { alerts += Alert(goal.id, day, Pace.on(day, it, dayEndHour), AlertKind.USUAL) }
                    }
                    reminder.againEvery?.takeIf { it > 0 }?.let { every ->
                        alerts += again(goal, day, alerts.subList(firstOfDay, alerts.size).toList(), every, dayEndHour, defaultFinish)
                    }
                }
                day = day.plusDays(1)
            }
        }
        return alerts.filter { it.at >= from && it.at < to }.sortedBy { it.at }
    }

    /**
     * The morning plans from [from] (included) to [to] (not included): one each day at
     * [dayStart]. Whether there is anything to plan is checked when it fires.
     */
    fun mornings(from: LocalDateTime, to: LocalDateTime, dayStart: LocalTime, dayEndHour: Int): List<Alert> {
        val alerts = mutableListOf<Alert>()
        var day = from.date.minusDays(1)
        while (day <= to.date) {
            alerts += Alert(MORNING_GOAL, day, Pace.on(day, dayStart, dayEndHour), AlertKind.MORNING)
            day = day.plusDays(1)
        }
        return alerts.filter { it.at >= from && it.at < to }
    }

    /** Most times "keep reminding" repeats one reminder, so a forgotten goal doesn't buzz all night. */
    const val MAX_AGAIN = 12

    /**
     * "Keep reminding": after each of the day's reminders, again every [every] minutes until the
     * next one, or the goal's finish time, or the end of the day. Whether it's done by then is
     * checked when each one fires.
     */
    private fun again(goal: Goal, day: LocalDate, first: List<Alert>, every: Int, dayEndHour: Int, defaultFinish: LocalTime?): List<Alert> {
        val starts = first.filter { it.kind != AlertKind.BEFORE }.sortedBy { it.at }
        if (starts.isEmpty()) return emptyList()
        val dayEnd = LocalDateTime(day.plusDays(1), LocalTime(dayEndHour, 0))
        val finish = (goal.finishBy ?: defaultFinish)?.let { Pace.on(day, it, dayEndHour) }?.let { minOf(it, dayEnd) } ?: dayEnd
        val result = mutableListOf<Alert>()
        starts.forEachIndexed { i, start ->
            // Up to the next reminder, or for the last one up to and including the finish time.
            val next = starts.getOrNull(i + 1)?.at
            var at = Pace.plusMinutes(start.at, every.toLong())
            var n = 0
            while ((next == null || at < next) && at <= finish && n < MAX_AGAIN) {
                result += Alert(goal.id, day, at, AlertKind.AGAIN, number = start.number)
                at = Pace.plusMinutes(at, every.toLong())
                n++
            }
        }
        return result
    }

    /** The weekly summaries from [from] (included) to [to] (not included): Sundays at [at]. */
    fun weekly(from: LocalDateTime, to: LocalDateTime, at: LocalTime, dayEndHour: Int): List<Alert> {
        val alerts = mutableListOf<Alert>()
        var day = from.date.minusDays(1)
        while (day <= to.date) {
            if (day.dayOfWeek.isoDayNumber == 7) alerts += Alert(MORNING_GOAL, day, Pace.on(day, at, dayEndHour), AlertKind.WEEKLY)
            day = day.plusDays(1)
        }
        return alerts.filter { it.at >= from && it.at < to }
    }

    /**
     * Whether [goal] reminds of its deadline: a project with one, unless that was turned off in
     * its editor. A project keeps the choice where a habit keeps its reminder: [Reminder.onTime]
     * is the switch, and [Reminder.at] the time picked, if one was.
     */
    fun remindsOfDeadline(goal: Goal): Boolean =
        goal.kind == GoalKind.PROJECT && !goal.archived && goal.deadline != null && goal.reminder?.onTime != false

    /**
     * The deadline reminders from [from] (included) to [to] (not included): for each project
     * with a deadline, one on the day before and one on the day, at the time picked for it or,
     * without one, at [dayStart]; none while neither is set. Each alert's day is the day it comes
     * on. Whether the project is finished by then is checked when it fires.
     */
    fun deadlines(goals: List<Goal>, from: LocalDateTime, to: LocalDateTime, dayStart: LocalTime?, dayEndHour: Int): List<Alert> {
        val alerts = mutableListOf<Alert>()
        for (goal in goals) {
            if (!remindsOfDeadline(goal)) continue
            val deadline = goal.deadline ?: continue
            val time = goal.reminder?.at ?: dayStart ?: continue
            for (day in listOf(deadline.minusDays(1), deadline)) {
                alerts += Alert(goal.id, day, Pace.on(day, time, dayEndHour), AlertKind.DEADLINE)
            }
        }
        return alerts.filter { it.at >= from && it.at < to }.sortedBy { it.at }
    }

    /**
     * What a deadline reminder says under the project's name: "Due tomorrow, 3 steps left".
     * [stepsLeft] is null for a private project behind the lock, which only says when.
     */
    fun deadlineWords(goal: Goal, alert: Alert, stepsLeft: Int?): String {
        val due = if (goal.deadline == alert.day) "Due today" else "Due tomorrow"
        return when {
            stepsLeft == null || stepsLeft <= 0 -> due
            stepsLeft == 1 -> "$due, 1 step left"
            else -> "$due, $stepsLeft steps left"
        }
    }

    /**
     * The deadline reminders to show from [from] (included) to [to] (not included), in words,
     * as things stand: a project whose steps are all ticked has none. [steps] and [stepTicks]
     * may hold other goals'. With [hidePrivate], a private project is only "A private goal".
     */
    fun deadlineReminders(
        goals: List<Goal>,
        steps: List<Step>,
        stepTicks: List<Tick>,
        from: LocalDateTime,
        to: LocalDateTime,
        dayStart: LocalTime?,
        dayEndHour: Int,
        hidePrivate: Boolean = false,
    ): List<DeadlineReminder> {
        val ticked = stepTicks.mapNotNull { it.stepId }.toSet()
        return deadlines(goals, from, to, dayStart, dayEndHour).mapNotNull { alert ->
            val goal = goals.find { it.id == alert.goalId } ?: return@mapNotNull null
            val own = steps.filter { it.goalId == goal.id && !it.archived }
            val left = own.count { it.id !in ticked }
            // Finished: nothing to remind of.
            if (own.isNotEmpty() && left == 0) return@mapNotNull null
            val hidden = hidePrivate && goal.private
            DeadlineReminder(
                goalId = goal.id,
                day = alert.day,
                at = alert.at,
                title = if (hidden) "🔒 ${PrivateGoals.NAME}" else "${goal.icon} ${goal.name}",
                text = deadlineWords(goal, alert, left.takeIf { !hidden }),
            )
        }
    }

    /** The time of day a bill's reminder comes when "My day starts at" isn't set. */
    val BILL_TIME = LocalTime(9, 0)

    /**
     * The bill reminders from [from] (included) to [to] (not included): for each bill with a
     * reminder, one for each time it falls due, [Bill.remindDays] days before, at [time]. An
     * alert's goal id is the bill's id, its day the day it comes on, and its minutes before the
     * days before.
     */
    fun bills(bills: List<Bill>, from: LocalDateTime, to: LocalDateTime, time: LocalTime = BILL_TIME): List<Alert> {
        val alerts = mutableListOf<Alert>()
        for (bill in bills) {
            val before = bill.remindDays?.coerceAtLeast(0) ?: continue
            if (bill.archived) continue
            for (due in Bills.due(bill, from.date.plusDays(before), to.date.plusDays(before))) {
                val day = due.minusDays(before)
                alerts += Alert(bill.id, day, LocalDateTime(day, time), AlertKind.BILL, minutesBefore = before)
            }
        }
        return alerts.filter { it.at >= from && it.at < to }.sortedBy { it.at }
    }

    /**
     * The bill reminders to show from [from] (included) to [to] (not included), in words: the
     * title is all there is to say ("Rent is due tomorrow, 650"). With [hidden], while the
     * budget is behind the privacy lock, each only says "A bill is due". [amount] writes cents
     * as the phone writes money. Their ids start at [BILL_IDS], apart from the goals'.
     */
    fun billReminders(
        bills: List<Bill>,
        from: LocalDateTime,
        to: LocalDateTime,
        time: LocalTime = BILL_TIME,
        hidden: Boolean = false,
        amount: (Long) -> String,
    ): List<DeadlineReminder> = bills(bills, from, to, time).mapNotNull { alert ->
        val bill = bills.find { it.id == alert.goalId } ?: return@mapNotNull null
        DeadlineReminder(
            goalId = BILL_IDS + bill.id,
            day = alert.day,
            at = alert.at,
            title = (if (hidden) "🔒 " else "${bill.icon} ") + Bills.reminderWords(bill, alert.minutesBefore, amount(bill.cents), hidden),
            text = if (hidden) "Open the Budget tab to see it." else if (bill.moneyIn) "It is counted as money in on its day, by itself." else "It is counted as spent on its day, by itself.",
        )
    }

    /** Where bill reminders' ids start: far above any goal's, and still a notification id of their own on Android. */
    const val BILL_IDS = 900_000L

    /** The goal id a morning plan carries: it is about the whole day, not one goal. */
    const val MORNING_GOAL = 0L

    /**
     * Whether an alert for [goal] in [window] is still to do, by the rule Today uses: times done,
     * so an amount counts once the whole of it is entered, and skips, which cover a single-day
     * goal's day or one of a "times per" goal's times (a skipped goal isn't done, but there is
     * nothing left of it to remind about). [ticks] and [lapses] may hold other goals'
     * and windows'. For one of several a day with its own time ([number] above 0), it is to do
     * until that one is done.
     */
    fun stillToDo(goal: Goal, window: Window, ticks: List<Tick>, lapses: List<Lapse>, number: Int = 0): Boolean {
        // An "at most" goal is never something to do: staying under its limit needs no reminder.
        if (goal.atMost) return false
        val times = timesDone(goal, window, ticks)
        if (number > 0 && times >= number) return false
        val skips = lapses.count { it.goalId == goal.id && it.kind == LapseKind.SKIP && it.periodStart == window.start && !it.forgotten }
        return !HabitRow(goal, times, window, skips).settled
    }

    /**
     * At most [most] of [alerts], for a phone that keeps only so many waiting: every first
     * reminder, earliest first, before any "keep reminding" repeat, so one day's repeats never
     * crowd out the next days' reminders. In time order.
     */
    fun keep(alerts: List<Alert>, most: Int): List<Alert> {
        val (repeats, firsts) = alerts.sortedBy { it.at }.partition { it.kind == AlertKind.AGAIN }
        val kept = firsts.take(most)
        return (kept + repeats.take(most - kept.size)).sortedBy { it.at }
    }

    /** What a reminder for a locked private goal says: only when, nothing about the goal. */
    fun lockedWords(alert: Alert): String = when (alert.kind) {
        AlertKind.BEFORE -> "In ${alert.minutesBefore} min"
        AlertKind.FOCUS_DONE -> "Time's up"
        else -> "Due now"
    }

    /** How many of [goal]'s times are done in [window], for a reminder's "1 of 2". */
    fun timesDone(goal: Goal, window: Window, ticks: List<Tick>): Int =
        goal.timesDone(ticks.filter { it.countsToward(goal, window.start) })
}

/** A deadline reminder ready to show at [at], on the user's [day]: the same words on every phone and the computer. */
data class DeadlineReminder(val goalId: Long, val day: LocalDate, val at: LocalDateTime, val title: String, val text: String)

/** What the morning plan says: what is due today, the first thing to do, and the pace. */
data class MorningPlan(
    /** Habits due today, and still to do. */
    val goals: Int,
    /** Of them, what to do first in the recommended order, with its time if it has one. */
    val first: Planned?,
    /** "Times per" habits and projects still open. */
    val alsoOpen: Int,
    val pace: TodayPace?,
    /** To-dos without a date still waiting. */
    val todos: Int = 0,
) {
    companion object {
        /**
         * The plan for [view] at [now], or null when there's nothing to plan today. What comes
         * first is what Today shows first (see [DayOrder]): in the user's [own] order or the
         * recommended one, with their usual times, the calendar's [busy] time and [ticks] for
         * how long goals with an amount take.
         */
        fun of(
            now: LocalDateTime,
            view: TodayView,
            settings: PaceSettings,
            usual: Map<Long, Int> = emptyMap(),
            own: Boolean = false,
            busy: List<Busy> = emptyList(),
            ticks: List<Tick> = emptyList(),
        ): MorningPlan? = of(view, DayOrder.of(now, view.day, view.dueToday, settings, own, usual, busy, ticks))

        /** The plan for [view] with today's goals already in [order]. */
        fun of(view: TodayView, order: DayOrdered): MorningPlan? {
            val left = view.dueToday.filter { !it.settled }
            val open = (view.thisWeek + view.thisMonth + view.thisYear).count { !it.settled } + view.projects.count { !it.finished }
            val todos = view.todos.count { !it.done }
            if (left.isEmpty() && open == 0 && todos == 0) return null
            val pace = order.pace
            val first = order.next
            return MorningPlan(left.size, first, open, pace, todos)
        }
    }
}
