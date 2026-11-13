package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.AlertKind
import io.github.randomtools3377.checklist.core.Amounts
import io.github.randomtools3377.checklist.core.AtStake
import io.github.randomtools3377.checklist.core.DayStanding
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.HabitRow
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.PrivateGoals
import io.github.randomtools3377.checklist.core.Race
import io.github.randomtools3377.checklist.core.Review
import io.github.randomtools3377.checklist.core.Rules
import io.github.randomtools3377.checklist.core.Shields
import io.github.randomtools3377.checklist.core.StreakCounter
import io.github.randomtools3377.checklist.core.StreakSettings
import io.github.randomtools3377.checklist.core.StreakTarget
import io.github.randomtools3377.checklist.core.TickAnswer
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.TodayList
import io.github.randomtools3377.checklist.core.WeeklySummary
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.datetime.LocalDate

/**
 * What is at stake today, read straight from the database for what sits outside the app's own
 * screens: goal reminders, the morning plan, the evening check and the note after a tick made on
 * a reminder or a widget (see [AtStake]). Made once for a batch of notifications.
 *
 * It only speaks about its own [day], the user's day when it was made: a reminder handed over
 * ahead for a later day (the iPhone's) can't know how that day will stand, so it gets no phrase.
 * A private goal behind the privacy lock gets none either, and while the review is behind the
 * lock no streak is mentioned.
 */
class StakeWriter internal constructor(
    val day: LocalDate,
    val standing: DayStanding,
    private val rows: Map<Long, HabitRow>,
    private val hidePrivate: Boolean,
) {
    /** Whether [goal] is a private goal behind the privacy lock, which is never named. */
    fun hidden(goal: Goal): Boolean = hidePrivate && goal.private

    /** The goal on Today's list, when it is one of today's goals. */
    fun row(goalId: Long): HabitRow? = rows[goalId]

    /** What [alert], a reminder for [goal], adds to its words; null when there is nothing true to add. */
    fun reminder(goal: Goal, alert: Alert): String? {
        if (alert.day != day || hidden(goal)) return null
        // Late nudges keep their own words.
        if (alert.kind == AlertKind.NUDGE || alert.kind == AlertKind.FOCUS_DONE) return null
        return rows[goal.id]?.let { AtStake.reminder(it, standing) }
    }

    /** What the morning plan adds on [on]. */
    fun morning(on: LocalDate = day): String? = if (on == day) AtStake.morning(standing) else null

    /** What the evening check adds on [on]. */
    fun evening(on: LocalDate = day): String? = if (on == day) AtStake.evening(standing) else null
}

/** How far back the streak is counted, as for the late nudges. */
private const val STAKE_STREAK_DAYS = 400

/**
 * A [StakeWriter] for the user's day at [now]. Counts and the streak are the real ones, as the
 * app's own Today counts them, private goals included: a count names nothing.
 */
suspend fun stakeWriter(dao: ChecklistDao, now: kotlinx.datetime.LocalDateTime = nowLocal()): StakeWriter {
    // Shields follow the user's own rules, also in a process woken by an alarm.
    Rules.current = Rules.decode(dao.setting(Rules.SETTING))
    val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
    val day = Days.dayOf(now, dayEnd)
    val goals = dao.activeCoreGoals()
    val since = minOf(day.minusDays(STAKE_STREAK_DAYS), Days.earliestOpenDay(day))
    val ticks = dao.habitTicksSince(since.toEpochDay()).map { it.toCore() }
    val lapses = dao.lapsesSince(since.toEpochDay()).map { it.toCore() }
    val defaults = StreakSettings()
    val settings = StreakSettings(
        target = dao.setting(SETTING_STREAK_TARGET)?.let { runCatching { StreakTarget.valueOf(it) }.getOrNull() } ?: defaults.target,
        value = dao.setting(SETTING_STREAK_VALUE)?.toIntOrNull() ?: defaults.value,
    )
    val records = StreakCounter.records(since, day, goals, ticks, lapses)
    val yesterday = day.minusDays(1)
    val shieldsFrom = if (Feature.SHIELDS.isOn(dao.setting(Feature.SHIELDS.key))) {
        dao.setting(Shields.FROM)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: yesterday
    } else {
        null
    }
    val streak = Shields.streaks(records.filter { it.day <= yesterday }, shieldsFrom, yesterday, settings).done
    val view = TodayList.build(day, goals, dao.allSteps().map { it.toCore() }, ticks, 0, lapses, dao.allChecks().map { it.toCore() })
    val standing = DayStanding(
        record = records.lastOrNull { it.day == day },
        settings = settings,
        streak = streak,
        left = view.dueToday.count { !it.settled },
        showStreak = dao.setting(LOCK_REVIEW) != "1",
    )
    return StakeWriter(day, standing, view.dueToday.associateBy { it.goal.id }, hidePrivate = dao.setting(LOCK_PRIVATE) == "1")
}

/** A [StakeWriter], or null if today couldn't be read: a reminder then keeps its plain words. */
suspend fun stakeWriterOrNull(dao: ChecklistDao): StakeWriter? = runCatching { stakeWriter(dao) }.getOrNull()

/** Whether the note after a tick made on a reminder or a widget is on: on unless turned off. */
fun tickAnswerOn(setting: String?): Boolean = setting != "0"

/**
 * The short note after [result], a tick of [goalId] made on a reminder or a widget, as things
 * stand after it: "Done: 💧 Water, +5" and "2 left today" or "That's everything for today".
 * Null when there is nothing to say: no tick was made, or one item of a routine was ticked, which
 * isn't the routine done and pays nothing. With [stakes] false ("Confirm a tick made outside the
 * app" is off) it is only the first part, for lighting the edges. A private goal behind the lock
 * isn't named and says nothing more.
 */
suspend fun tickAnswer(dao: ChecklistDao, goalId: Long, result: TickResult, stakes: Boolean = true): TickAnswer? {
    if (result !is TickResult.Ticked) return null
    val paid = result.points + result.bonus
    if (!result.finished && paid == 0) return null
    val goal = dao.goal(goalId)?.toCore() ?: return null
    val hidden = goal.private && dao.setting(LOCK_PRIVATE) == "1"
    val name = if (hidden) "🔒 ${PrivateGoals.NAME}" else "${goal.icon} ${goal.name}"
    val writer = if (stakes && !hidden) stakeWriterOrNull(dao) else null
    val row = writer?.row(goalId)
    val progress = row?.takeIf { !it.isRoutine && it.goal.target > 1 && !it.goal.atMost }?.let {
        if (it.goal.hasAmount) Amounts.progressText(it.progress, it.goal) else "${it.count} of ${it.goal.target}"
    }
    return AtStake.answer(
        name = name,
        paid = paid,
        finished = result.finished,
        countsToday = row != null && row.window.period == Period.DAILY,
        progress = progress,
        hidden = hidden,
        stakes = writer?.standing,
    )
}

/**
 * What the Sunday summary says about the week up to [day], or null with no goals at all. Behind
 * the review's lock it only says the review is ready. Otherwise the week in numbers (see
 * [WeeklySummary.describe]): against the week before only when more got done, and, while "Beat
 * last week" is on, that the week was won, with nothing said when it wasn't. A private goal is
 * never named or counted while the lock covers private goals.
 */
suspend fun weeklyWords(dao: ChecklistDao, day: LocalDate): String? {
    if (dao.activeGoals().isEmpty()) return null
    if (dao.setting(LOCK_REVIEW) == "1") return WEEKLY_LOCKED
    val (from, to) = Review.range(day, Period.WEEKLY)
    val lastFrom = from.minusDays(7)
    val since = Days.earliestOpenDay(lastFrom).minusDays(7)
    val goals = dao.allGoals().map { it.toCore() }
    val ticks = dao.habitTicksSince(since.toEpochDay()).map { it.toCore() }
    val lapses = dao.lapsesSince(since.toEpochDay()).map { it.toCore() }
    val entries = dao.entries(lastFrom.toEpochDay(), to.toEpochDay()).map { it.toCore() }
    val gaps = dao.allGaps().map { it.toCore() }
    // A notification can't be unlocked, so private goals stay out while they are locked.
    val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
    fun week(start: LocalDate) = WeeklySummary.of(
        Review.summarize(from = start, to = start.plusDays(6), today = day, goals = goals, ticks = ticks, lapses = lapses, entries = entries, gaps = gaps, hidePrivate = hidePrivate),
        hidePrivate,
    )
    // The week is won once it has more than the whole of last week, whatever is still to come.
    val race = if (Feature.RACE.isOn(dao.setting(Feature.RACE.key))) {
        val hidden = if (hidePrivate) PrivateGoals.ids(goals) else emptySet()
        Race.of(entries.filter { entry -> entry.goalId?.let { it in hidden } != true }, to, Long.MAX_VALUE).takeIf { it.lastWeek > 0 }
    } else {
        null
    }
    // Last week is only compared with once something was due in it.
    return week(from).describe(dao.setting(SETTING_WEEKLY_TARGET)?.toIntOrNull() ?: 0, race, before = week(lastFrom))
}

/** What the weekly summary says while the review is behind the privacy lock. */
const val WEEKLY_LOCKED = "Your weekly review is ready."
