package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.NudgeFacts
import io.github.randomtools3377.checklist.core.NudgePlan
import io.github.randomtools3377.checklist.core.NudgeTone
import io.github.randomtools3377.checklist.core.NudgeWords
import io.github.randomtools3377.checklist.core.Nudges
import io.github.randomtools3377.checklist.core.Rules
import io.github.randomtools3377.checklist.core.Shields
import io.github.randomtools3377.checklist.core.StreakCounter
import io.github.randomtools3377.checklist.core.StreakSettings
import io.github.randomtools3377.checklist.core.StreakTarget
import io.github.randomtools3377.checklist.core.minusDays
import kotlinx.datetime.LocalDate

/**
 * Late nudges as they are set up now, read straight from the database: the Settings switch, the
 * numbers from "Points and rules", and the goals stopped for today. Every phone and the computer
 * hand it to [io.github.randomtools3377.checklist.core.Alerts.between].
 */
suspend fun nudgePlan(dao: ChecklistDao): NudgePlan =
    Nudges.plan(dao.setting(Nudges.SETTING), dao.setting(Nudges.STOPPED), Rules.decode(dao.setting(Rules.SETTING)))

/**
 * "Stop for today": no more nudges for [goalId] on [day], the user's day. Kept in one setting
 * that holds a single day, so older days clear themselves.
 */
suspend fun stopNudges(dao: ChecklistDao, goalId: Long, day: LocalDate) {
    dao.putSetting(SettingEntity(Nudges.STOPPED, Nudges.stop(dao.setting(Nudges.STOPPED), goalId, day)))
}

/**
 * Writes late nudges in words, with what is really there as things stand: the goal's own reason
 * and the main streak. Never what a tick pays or how close a reward is: nudges are about the task
 * and why it is good (decided after 0.71.0). Made once for a batch of nudges (see [nudgeWriter]).
 */
class NudgeWriter internal constructor(
    private val tones: List<NudgeTone>,
    /** The main streak up to the end of yesterday; 0 while the review is behind the privacy lock. */
    private val streak: Int,
    private val hidePrivate: Boolean,
    /** The user's day the streak was read on. */
    private val day: LocalDate,
) {
    /** Whether [goal] is a private goal behind the privacy lock, which a nudge never names. */
    fun hidden(goal: Goal): Boolean = hidePrivate && goal.private

    /** What [alert], a late nudge for [goal], says. [count] is how many of its times are done. */
    fun words(goal: Goal, alert: Alert, count: Int = 0): NudgeWords {
        // A nudge written ahead for a later day (the iPhone's) can't know that day's streak yet,
        // so it only names the goal and its reason: every number has to be a real one.
        val now = alert.day == day
        val facts = NudgeFacts(streak = if (now) streak else 0, count = count)
        return Nudges.words(goal, alert.day, alert.nth, alert.late, facts, tones, locked = hidden(goal))
    }
}

/**
 * A [NudgeWriter] for now. The streak is the main streak up to the end of yesterday, the one
 * today's goals keep going; private goals don't count toward it while they are locked, as on the
 * widgets, and while the review is behind the privacy lock no streak is mentioned at all.
 */
suspend fun nudgeWriter(dao: ChecklistDao): NudgeWriter {
    // The streak's shields follow the user's own rules, also in a process woken by an alarm.
    Rules.current = Rules.decode(dao.setting(Rules.SETTING))
    val dayEnd = parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR))
    val day = Days.dayOf(nowLocal(), dayEnd)
    val hidePrivate = dao.setting(LOCK_PRIVATE) == "1"
    val streak = if (dao.setting(LOCK_REVIEW) == "1") 0 else runCatching { mainStreak(dao, day.minusDays(1), hidePrivate) }.getOrDefault(0)
    return NudgeWriter(
        tones = Nudges.tones(dao.setting(Nudges.TONES)),
        streak = streak,
        hidePrivate = hidePrivate,
        day = day,
    )
}

/** How far back the streak is counted for a nudge. */
private const val NUDGE_STREAK_DAYS = 400

/** The main streak on [day], with streak shields while they are on, as Today counts it. */
private suspend fun mainStreak(dao: ChecklistDao, day: LocalDate, hidePrivate: Boolean): Int {
    val goals = dao.activeCoreGoals().filter { !(hidePrivate && it.private) }
    val since = minOf(day.minusDays(NUDGE_STREAK_DAYS), Days.earliestOpenDay(day))
    val ticks = dao.habitTicksSince(since.toEpochDay()).map { it.toCore() }
    val lapses = dao.lapsesSince(since.toEpochDay()).map { it.toCore() }
    val defaults = StreakSettings()
    val settings = StreakSettings(
        target = dao.setting(SETTING_STREAK_TARGET)?.let { runCatching { StreakTarget.valueOf(it) }.getOrNull() } ?: defaults.target,
        value = dao.setting(SETTING_STREAK_VALUE)?.toIntOrNull() ?: defaults.value,
    )
    val records = StreakCounter.records(since, day, goals, ticks, lapses)
    val shieldsFrom = if (Feature.SHIELDS.isOn(dao.setting(Feature.SHIELDS.key))) {
        dao.setting(Shields.FROM)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: day
    } else {
        null
    }
    return Shields.streaks(records, shieldsFrom, day, settings).done
}
