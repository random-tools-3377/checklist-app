package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalTime

/**
 * What a tick is about to change, as things stood just before it. Everything is from before the
 * tick, so the phrase can be worked out the moment the tick is made.
 */
data class TickMoment(
    /** The tick finished the goal for its day, week, month or year. */
    val finished: Boolean,
    /** What the tick paid, its finishing bonus included. */
    val paid: Int,
    /** The goal is one of the day's goals, on Today's list. */
    val onTodaysList: Boolean = false,
    /** Goals on Today's list that still had something to do, this one included. */
    val leftBefore: Int = 0,
    /** Whether the goal counts for today in the streaks: its day, week, month or year ends today. */
    val countsToday: Boolean = false,
    /** Today's record before the tick, or null when it isn't known. */
    val record: DayRecord? = null,
    val streak: StreakSettings = StreakSettings(),
    /** The main streak before the tick. */
    val streakBefore: Int = 0,
    /** The quests still open before the tick, while quests are on, each with how far along it was. */
    val quests: List<QuestProgress> = emptyList(),
    /** The goal's skill area, for the quest of one area. */
    val area: SkillArea? = null,
    val goalId: Long = 0,
    /** The goal was skipped today before this tick, which takes the skip back. */
    val skippedBefore: Boolean = false,
    /** The goal hadn't been ticked today before this tick. */
    val firstTickToday: Boolean = true,
    /** The time of the tick, for quests with a time. */
    val time: LocalTime? = null,
    val dayEndHour: Int = 0,
    /** The week against its target before the tick, when a target is set. */
    val week: WeekProgress? = null,
    /** The race before the tick, while it is on. */
    val race: RaceStanding? = null,
    /** The goal's run of times done in a row with this tick, and the best run before it. */
    val run: Int? = null,
    val bestRunBefore: Int? = null,
    /** How many times the goal has been done in all, this one included. */
    val timesDone: Int? = null,
)

/**
 * The one thing a tick changed that is most worth saying, for the bar at the bottom: "2 left
 * today", "That was the last one", "Today now counts for your streak: day 13". Words only: it
 * is not another celebration. The rarest news wins, so the same line doesn't come every time.
 */
object TickNews {
    /** Times done that are worth a word. */
    val MILESTONES = listOf(10, 25, 50, 100, 250, 500, 1000, 2500, 5000)

    /** A run of times done in a row is a personal best worth a word from here on. */
    const val BEST_FROM = 3

    /**
     * What the tick changed, or null when there is nothing to add. [goalName] is used only in
     * the milestone line; pass null for a goal that mustn't be named (private, while locked).
     */
    fun phrase(m: TickMoment, goalName: String?): String? {
        val record = m.record
        val after = when {
            record == null || !m.finished || !m.countsToday -> record
            // A skipped goal was left out of the day: ticked, it is due again, and done.
            m.skippedBefore -> record.copy(due = record.due + 1, done = record.done + 1, skips = (record.skips - 1).coerceAtLeast(0))
            else -> record.copy(done = record.done + 1)
        }
        // The streak: today didn't reach the target before, and does now.
        if (record != null && after != null && record.due > 0 && !StreakCounter.meetsTarget(record, m.streak) && StreakCounter.meetsTarget(after, m.streak)) {
            return "Today now counts for your streak: day ${m.streakBefore + 1}"
        }
        // A skipped goal wasn't among those left, so ticking it leaves as many as before.
        val leftAfter = if (m.finished && m.onTodaysList && !m.skippedBefore) m.leftBefore - 1 else m.leftBefore
        if (m.finished && m.onTodaysList && leftAfter == 0) {
            return if (after?.perfect == true) "That was the last one: a perfect day so far" else "That was the last one"
        }
        if (m.finished && m.run != null && m.bestRunBefore != null && m.run >= BEST_FROM && m.run > m.bestRunBefore) {
            return "New best: ${m.run} in a row"
        }
        if (m.finished && m.timesDone != null && m.timesDone in MILESTONES) {
            return if (goalName != null) "That's ${m.timesDone} times for $goalName" else "That's ${m.timesDone} times"
        }
        questsDone(m, leftAfter).takeIf { it.isNotEmpty() }?.let { done ->
            return (if (done.size == 1) "Quest done" else "${done.size} quests done") + " +${done.sumOf { it.reward }}"
        }
        m.week?.let { week ->
            if (!week.reached && week.target > 0 && week.earned + m.paid >= week.target) return "Weekly target reached"
        }
        m.race?.let { race ->
            if (race.ahead <= 0 && race.ahead + m.paid > 0) return "You're ahead of last week now"
        }
        if (m.onTodaysList && leftAfter > 0) return "$leftAfter left today"
        return null
    }

    /** The open quests this tick finishes. */
    internal fun questsDone(m: TickMoment, leftAfter: Int): List<Quest> = m.quests.filter { !it.complete }.mapNotNull { progress ->
        val quest = progress.quest
        val inTime = quest.by == null || (m.time != null && Days.secondOfDay(m.time, m.dayEndHour) < Days.secondOfDay(quest.by, m.dayEndHour))
        val done = when (quest.kind) {
            QuestKind.GOAL -> m.finished && quest.goalId == m.goalId
            QuestKind.AREA -> m.finished && m.onTodaysList && quest.area == m.area && progress.done + 1 >= quest.target
            QuestKind.EARLY -> m.onTodaysList && m.firstTickToday && inTime && progress.done + 1 >= quest.target
            QuestKind.ALL_BY -> m.finished && m.onTodaysList && leftAfter == 0 && inTime
            QuestKind.POINTS -> m.paid > 0 && progress.done + m.paid >= quest.target
        }
        quest.takeIf { done }
    }

    /** What the Undo bar says after a tick: "💧 Water · +5 · 2 left today". */
    fun line(name: String, paid: Int, extra: String?, phrase: String?): String =
        listOfNotNull(if (paid > 0) "$name · +$paid" else "$name ticked", extra, phrase).joinToString(" · ")

    /**
     * What went back when a tick is undone: "Water taken back · −5", and, when the tick had
     * finished one of today's goals, how many are left again.
     */
    fun undone(name: String, paid: Int, m: TickMoment? = null): String = listOfNotNull(
        "$name taken back",
        if (paid > 0) "−$paid" else null,
        m?.takeIf { it.finished && it.onTodaysList && !it.skippedBefore && it.leftBefore > 0 }?.let { "${it.leftBefore} left today" },
    ).joinToString(" · ")
}
