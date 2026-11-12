package io.github.randomtools3377.checklist.widget

import android.content.Context
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.Order
import io.github.randomtools3377.checklist.core.Pace
import io.github.randomtools3377.checklist.core.PaceSettings
import io.github.randomtools3377.checklist.core.Planned
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.TodayList
import io.github.randomtools3377.checklist.core.TodoRow
import io.github.randomtools3377.checklist.core.TodayPace
import io.github.randomtools3377.checklist.core.Usual
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.usualMinuteOfDay
import io.github.randomtools3377.checklist.data.LOCK_PRIVATE
import io.github.randomtools3377.checklist.data.ORDER_OWN
import io.github.randomtools3377.checklist.data.SETTING_DAY_END_HOUR
import io.github.randomtools3377.checklist.data.SETTING_DAY_START
import io.github.randomtools3377.checklist.data.SETTING_DEFAULT_FINISH
import io.github.randomtools3377.checklist.data.SETTING_TODAY_ORDER
import io.github.randomtools3377.checklist.data.nowLocal
import io.github.randomtools3377.checklist.data.parseDayEndHour
import io.github.randomtools3377.checklist.data.toCore
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.data.toLocalDateTime
import io.github.randomtools3377.checklist.data.toTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Today's goals in the order Today shows them: the recommended order, or the user's own. For
 * the widgets and the quick settings tile, read straight from the database. [planned] holds the
 * habits, done ones last; [todos] the to-dos still open today, and [doneTodos] the ones done today.
 */
data class DayPlan(
    val day: LocalDate,
    val now: LocalDateTime,
    val planned: List<Planned>,
    val todos: List<TodoRow>,
    val pace: TodayPace?,
    /** Goals shown on the home screen (private ones left out while locked), and recent ticks, for tomorrow's list. */
    val goals: List<Goal>,
    val ticks: List<Tick>,
    val doneTodos: List<TodoRow> = emptyList(),
)

object DayPlans {
    suspend fun load(context: Context): DayPlan {
        val dao = (context.applicationContext as ChecklistApplication).container.db.dao()
        val now = nowLocal()
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val day = Days.dayOf(now, dayEnd)
        // Private goals stay off the home screen while the privacy lock covers them.
        val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
        val goals = dao.activeGoals().map { it.toCore() }.filter { !(hidePrivate && it.private) }
        val since = minOf(Days.earliestOpenDay(day), day.minusDays(Usual.WINDOW_DAYS))
        val tickRows = dao.habitTicksSince(since.toEpochDay())
        val ticks = tickRows.map { it.toCore() }
        val lapses = dao.lapsesSince(since.toEpochDay()).map { it.toCore() }
        val steps = dao.allSteps().map { it.toCore() }
        val checks = dao.allChecks().map { it.toCore() }
        val view = TodayList.build(day, goals, steps, ticks, 0, lapses, checks)
        val settings = PaceSettings(
            dayEndHour = dayEnd,
            defaultFinish = dao.setting(SETTING_DEFAULT_FINISH)?.toIntOrNull()?.toTime(),
            dayStart = dao.setting(SETTING_DAY_START)?.toIntOrNull()?.toTime(),
        )
        val pace = Pace.today(now, day, view.dueToday, settings)
        val planned = if (dao.setting(SETTING_TODAY_ORDER) == ORDER_OWN) {
            // Those with nothing left to do, done or skipped, go last.
            val open = view.dueToday.filter { !it.settled }
            (open + view.dueToday.filter { it.settled }).map { row ->
                val at = row.goal.at?.let { Pace.on(day, it, dayEnd) } ?: Pace.nextSlot(row, day, settings)?.takeIf { it.fixed }?.at
                Planned(row, at?.takeIf { !row.settled }, fixed = at != null, late = !row.settled && at != null && at < now)
            }
        } else {
            // When each goal is usually ticked, learned from the last 4 weeks, as on Today.
            val usual = tickRows.filter { it.day >= day.minusDays(Usual.WINDOW_DAYS).toEpochDay() }
                .groupBy { it.goalId }
                .mapNotNull { (id, list) -> usualMinuteOfDay(list.map { it.at.toLocalDateTime().let { t -> t.hour * 60 + t.minute } })?.let { id to it } }
                .toMap()
            Order.recommend(now, day, view.dueToday, settings, usual, pace)
        }
        return DayPlan(day, now, planned, view.todos.filter { !it.done }, pace, goals, ticks, doneTodos = view.todos.filter { it.done })
    }

    /**
     * Today's plan again whenever goals, ticks or settings change, while a widget is awake. A
     * widget reads its data once when it wakes; without this, a tick made on it would only show
     * the next time it woke.
     */
    fun <T> following(context: Context, load: suspend () -> T): Flow<T> =
        (context.applicationContext as ChecklistApplication).container.dataChanges.conflate().map { load() }
}
