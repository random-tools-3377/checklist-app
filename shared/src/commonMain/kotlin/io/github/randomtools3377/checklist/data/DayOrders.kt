package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Busy
import io.github.randomtools3377.checklist.core.DayOrder
import io.github.randomtools3377.checklist.core.DayOrdered
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.PaceSettings
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.TodayList
import io.github.randomtools3377.checklist.core.TodayView
import io.github.randomtools3377.checklist.core.Usual
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.usualMinuteOfDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** Today read straight from the database, with its goals in the order Today shows them. */
data class LoadedDay(
    val day: LocalDate,
    val now: LocalDateTime,
    val settings: PaceSettings,
    /** Active goals, private ones left out while the privacy lock covers them. */
    val goals: List<Goal>,
    /** Habit ticks over the look-back (no project steps). */
    val habitTicks: List<Tick>,
    val view: TodayView,
    /** The order, the pace and what's next, as on Today (see [DayOrder]). */
    val order: DayOrdered,
)

/**
 * Today's order for everything outside the app's own screens: the widgets, the quick settings
 * tile and the morning plan. It reads what Today reads and asks the same [DayOrder], so they all
 * show the same goal first.
 */
object DayOrders {
    /** How far back ticks are read, as Today does, so the usual speed of goals with an amount is the same. */
    const val LOOK_BACK_DAYS = 400

    /** The minute of the day each goal is usually ticked at, from its ticks over the last 4 weeks before [day]. */
    fun usualMinutes(habitTicks: List<TickEntity>, day: LocalDate): Map<Long, Int> {
        val from = day.minusDays(Usual.WINDOW_DAYS).toEpochDay()
        return habitTicks.filter { it.day >= from }
            .groupBy { it.goalId }
            .mapNotNull { (id, list) -> usualMinuteOfDay(list.map { it.at.toLocalDateTime().let { t -> t.hour * 60 + t.minute } })?.let { id to it } }
            .toMap()
    }

    /**
     * @param calendar reads the phone's calendar from a day on, for the calendars picked (all
     *   when empty). Only asked while the calendar is turned on in Settings; it gives nothing
     *   where the phone has no calendar or it isn't allowed.
     */
    suspend fun load(
        dao: ChecklistDao,
        now: LocalDateTime = nowLocal(),
        calendar: suspend (ids: Set<Long>, from: LocalDate) -> List<Busy> = { _, _ -> emptyList() },
    ): LoadedDay {
        val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
        val day = Days.dayOf(now, dayEnd)
        // Private goals stay off the home screen and out of notifications while the privacy lock covers them.
        val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
        val goals = dao.activeGoals().map { it.toCore() }.filter { !(hidePrivate && it.private) }
        val since = minOf(Days.earliestOpenDay(day), day.minusDays(LOOK_BACK_DAYS))
        val tickRows = dao.habitTicksSince(since.toEpochDay())
        val habitTicks = tickRows.map { it.toCore() }
        // Step ticks too, so projects and to-dos show the steps already done.
        val stepTicks = dao.allTicks().filter { it.stepId != null }.map { it.toCore() }
        val lapses = dao.lapsesSince(since.toEpochDay()).map { it.toCore() }
        val steps = dao.allSteps().map { it.toCore() }
        val checks = dao.allChecks().map { it.toCore() }
        val view = TodayList.build(day, goals, steps, habitTicks + stepTicks, 0, lapses, checks)
        val settings = PaceSettings(
            dayEndHour = dayEnd,
            defaultFinish = dao.setting(SETTING_DEFAULT_FINISH)?.toIntOrNull()?.toTime(),
            dayStart = dao.setting(SETTING_DAY_START)?.toIntOrNull()?.toTime(),
        )
        val busy = if (dao.setting(SETTING_CALENDAR) == "1") {
            val ids = dao.setting(SETTING_CALENDAR_IDS).orEmpty().split(",").mapNotNull { it.toLongOrNull() }.toSet()
            runCatching { calendar(ids, day) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val order = DayOrder.of(
            now, day, view.dueToday, settings,
            own = dao.setting(SETTING_TODAY_ORDER) == ORDER_OWN,
            usualMinute = usualMinutes(tickRows, day),
            busy = busy,
            ticks = habitTicks,
        )
        return LoadedDay(day, now, settings, goals, habitTicks, view.copy(dueToday = order.rows), order)
    }
}
