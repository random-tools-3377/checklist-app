package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** A challenge: [days] days due in a row, done, from [from], paying [bonus] at the end. */
@Serializable
data class Challenge(val days: Int, val bonus: Int, val from: LocalDate)

/** How far a challenge has got: days done in a row since [startedOn], and whether it is finished. */
data class ChallengeProgress(val done: Int, val days: Int, val startedOn: LocalDate, val finished: Boolean)

/**
 * Challenges, like 30 days in a row with a bonus at the end. A day it was due and missed starts
 * it again from day 1; a skipped day neither counts nor starts it again. Finishing pays the bonus
 * once, and the goal carries on as a habit.
 */
object Challenges {
    /** Where [goal]'s challenge has got by [today], from its ticks, misses and bonus entries. */
    fun progress(goal: Goal, ticks: List<Tick>, lapses: List<Lapse>, bonuses: List<Entry>, today: LocalDate): ChallengeProgress? {
        val challenge = goal.challenge ?: return null
        val repeat = goal.repeat ?: return null
        val mine = lapses.filter { it.goalId == goal.id && !it.forgotten && it.day >= challenge.from }
        val lastMiss = mine.filter { it.kind == LapseKind.MISS }.maxOfOrNull { it.day }
        val start = if (lastMiss == null) challenge.from else maxOf(challenge.from, lastMiss.plusDays(1))
        val finishedAt = bonuses.filter { it.goalId == goal.id && it.kind == EntryKind.CHALLENGE && it.day >= start }.minOfOrNull { it.day }
        var done = 0
        var day = start
        while (day <= today && done < challenge.days) {
            val window = repeat.window(day)
            if (window != null && window.end == day) {
                if (goal.timesDone(ticks.filter { it.countsToward(goal, window.start) }) >= goal.target) done++
            }
            day = day.plusDays(1)
        }
        return ChallengeProgress(done, challenge.days, start, finished = finishedAt != null || done >= challenge.days)
    }
}

/** Goals to quit: how many days in a row it has been avoided. */
object Quit {
    /** Days in a row ticked as avoided, counting back from today if it is ticked, else from yesterday. */
    fun daysWithout(goal: Goal, ticks: List<Tick>, today: LocalDate): Int {
        val repeat = goal.repeat ?: return 0
        fun avoided(day: LocalDate): Boolean {
            val window = repeat.window(day) ?: return true // not due: neither counts nor breaks it
            return goal.timesDone(ticks.filter { it.countsToward(goal, window.start) }) >= goal.target
        }
        var day = if (repeat.window(today) != null && avoided(today)) today else today.minusDays(1)
        var count = 0
        while (day >= repeat.start && count < 3660) {
            if (repeat.window(day) != null) {
                if (!avoided(day)) break
                count++
            }
            day = day.minusDays(1)
        }
        return count
    }
}
