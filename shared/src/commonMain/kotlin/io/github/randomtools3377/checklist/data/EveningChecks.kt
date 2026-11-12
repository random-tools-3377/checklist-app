package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.EveningCheck
import io.github.randomtools3377.checklist.core.EveningList
import io.github.randomtools3377.checklist.core.EveningTime
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.TodayList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

/** An evening check to show at [at]: what is still open on its day. */
data class DueEvening(val at: LocalDateTime, val list: EveningList)

/**
 * When the evening check comes from [from] (included) to [to] (not included): once a day at the
 * time picked in Settings. None while it is off.
 */
suspend fun eveningTimes(dao: ChecklistDao, from: LocalDateTime, to: LocalDateTime): List<EveningTime> {
    val at = EveningCheck.decode(dao.setting(EveningCheck.SETTING)) ?: return emptyList()
    return EveningCheck.between(from, to, at, parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR)))
}

/**
 * What is still open on [day] as things stand now, for the evening check, or null when nothing
 * is. A private goal isn't named while the privacy lock covers private goals: a notification
 * can't be unlocked.
 */
suspend fun eveningList(dao: ChecklistDao, day: LocalDate): EveningList? {
    val goals = dao.activeCoreGoals()
    val since = Days.earliestOpenDay(day)
    // Steps only matter for a project whose deadline is that day.
    val deadlines = goals.filter { it.kind == GoalKind.PROJECT && it.deadline == day }
    val ticks = (dao.habitTicksSince(since.toEpochDay()) + deadlines.flatMap { dao.stepTicks(it.id) }).map { it.toCore() }
    val lapses = dao.lapsesSince(since.toEpochDay()).map { it.toCore() }
    val view = TodayList.build(day, goals, dao.allSteps().map { it.toCore() }, ticks, 0, lapses)
    return EveningCheck.of(view, lapses, hidePrivate = dao.setting(LOCK_PRIVATE) == "1")
}

/**
 * The evening checks due from [from] (included) to [to] (not included), each with what is still
 * open on its day as things stand now. A day with nothing open has none. Android and the computer
 * ask for the minute that just came; the iPhone asks for the days ahead when it hands its
 * reminders over, and again on every change, so the list it holds stays current.
 */
suspend fun eveningChecks(dao: ChecklistDao, from: LocalDateTime, to: LocalDateTime): List<DueEvening> =
    eveningTimes(dao, from, to).mapNotNull { time -> eveningList(dao, time.day)?.let { DueEvening(time.at, it) } }
