package io.github.randomtools3377.checklist.widget

import android.content.Context
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.calendar.PhoneCalendar
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.Planned
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.TodoRow
import io.github.randomtools3377.checklist.core.TodayPace
import io.github.randomtools3377.checklist.data.DayOrders
import io.github.randomtools3377.checklist.data.LoadedDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/**
 * Today's goals in the order Today shows them: the recommended order, or the user's own. For
 * the widgets, the quick settings tile and the morning plan, read straight from the database.
 * [planned] holds the habits in that order (see DayOrder); [todos] the to-dos still open today, and [doneTodos] the ones done today.
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
    /**
     * Today as Today shows it: the same order, pace and next goal (see DayOrder), read through
     * the one loader the tile and the morning plan use too. The phone's calendar is counted
     * where it is turned on and allowed.
     */
    suspend fun today(context: Context): LoadedDay {
        val app = context.applicationContext
        val dao = (app as ChecklistApplication).container.db.dao()
        return DayOrders.load(dao) { ids, from -> withContext(Dispatchers.IO) { PhoneCalendar.events(app, ids, from, 1) } }
    }

    suspend fun load(context: Context): DayPlan {
        val today = today(context)
        val view = today.view
        return DayPlan(
            today.day, today.now, today.order.planned, view.todos.filter { !it.done }, today.order.pace, today.goals, today.habitTicks,
            doneTodos = view.todos.filter { it.done },
        )
    }

    /**
     * Today's plan again whenever goals, ticks or settings change, while a widget is awake. A
     * widget reads its data once when it wakes; without this, a tick made on it would only show
     * the next time it woke.
     */
    fun <T> following(context: Context, load: suspend () -> T): Flow<T> =
        (context.applicationContext as ChecklistApplication).container.dataChanges.conflate().map { load() }
}
