package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Chains
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.TodayList
import kotlinx.datetime.LocalDateTime

/** A goal that comes next in a habit chain: its name and icon, or "A private goal" while locked. */
data class NextUp(val goalId: Long, val title: String)

/**
 * The goals that come right after [goalId] in a habit chain and are still to do today, for the
 * "Next up" reminder on the iPhone and the computer (Android's own reminders work it out the same
 * way). A private goal isn't named while the privacy lock covers private goals.
 */
suspend fun nextInChain(dao: ChecklistDao, goalId: Long, now: LocalDateTime = nowLocal()): List<NextUp> {
    val goals = dao.activeCoreGoals()
    if (goals.none { it.after == goalId }) return emptyList()
    val day = Days.dayOf(now, parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR)))
    val since = Days.earliestOpenDay(day)
    val ticks = dao.habitTicksSince(since.toEpochDay()).map { it.toCore() }
    val view = TodayList.build(day, goals, dao.allSteps().map { it.toCore() }, ticks, 0, dao.lapsesSince(since.toEpochDay()).map { it.toCore() })
    val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
    return Chains.next(goalId, view).map { row ->
        NextUp(row.goal.id, if (hidePrivate && row.goal.private) "🔒 A private goal" else "${row.goal.icon} ${row.goal.name}")
    }
}
