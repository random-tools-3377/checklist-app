package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** How hard a goal is, which suggests the points it pays. */
@Serializable
enum class Effort(val label: String, val points: Int) {
    EASY("Easy", 2),
    MEDIUM("Medium", 5),
    HARD("Hard", 10),
    VERY_HARD("Very hard", 20),
}

/** How big a reward is, which suggests its price: so many days of the user's usual earnings. */
@Serializable
enum class RewardSize(val label: String, val days: Int) {
    SMALL("Small treat", 1),
    MEDIUM("Medium", 3),
    BIG("Big", 14),
    HUGE("Huge", 42),
}

/**
 * How easy rewards are to earn. It changes the points goals pay (ticks and bonuses) and how many
 * skips and misses are free. Penalties stay half a goal's own points, and prices don't change.
 */
enum class Difficulty(val label: String, val percent: Int, private val extraAllowance: Int) {
    VERY_EASY("Very easy", 200, 2),
    EASY("Easy", 150, 1),
    NORMAL("Normal", 100, 0),
    HARD("Hard", 75, -1),
    VERY_HARD("Very hard", 50, -1000),
    ;

    /** What [points] pay at this level: rounded down, but a goal that pays something pays at least 1. */
    fun pay(points: Int): Int = if (points <= 0) points else maxOf(1, points * percent / 100)

    /** Free skips and misses at this level, for a goal that allows [base]. */
    fun allowed(base: Int): Int = (base + extraAllowance).coerceAtLeast(0)
}

/**
 * Getting harder on a schedule: from [from] to [to] over [weeks] weeks, starting the week of
 * [start]. It moves a level at a time on Mondays, spread evenly over the weeks.
 */
data class Ramp(val from: Difficulty, val to: Difficulty, val weeks: Int, val start: LocalDate) {
    fun levelOn(day: LocalDate): Difficulty {
        val steps = to.ordinal - from.ordinal
        if (steps == 0 || weeks <= 0) return to
        val weeksIn = ((monday(day).toEpochDays() - monday(start).toEpochDays()) / 7).coerceAtLeast(0)
        if (weeksIn >= weeks) return to
        val moved = weeksIn * kotlin.math.abs(steps) / weeks
        return Difficulty.entries[from.ordinal + if (steps > 0) moved else -moved]
    }

    /** The next Monday the level changes after [day], or null once it has reached [to]. */
    fun nextChange(day: LocalDate): LocalDate? {
        val now = levelOn(day)
        if (now == to) return null
        var monday = monday(day).plusDays(7)
        repeat(weeks + 1) {
            if (levelOn(monday) != now) return monday
            monday = monday.plusDays(7)
        }
        return null
    }

    private fun monday(day: LocalDate) = day.minusDays(day.dayOfWeek.isoDayNumber - 1)

    companion object {
        /** Stored as "FROM|TO|weeks|start", like "EASY|NORMAL|6|2026-11-09". */
        fun decode(text: String?): Ramp? {
            val parts = text?.split("|") ?: return null
            if (parts.size != 4) return null
            val from = Difficulty.entries.firstOrNull { it.name == parts[0] } ?: return null
            val to = Difficulty.entries.firstOrNull { it.name == parts[1] } ?: return null
            val weeks = parts[2].toIntOrNull()?.takeIf { it in 1..52 } ?: return null
            val start = runCatching { LocalDate.parse(parts[3]) }.getOrNull() ?: return null
            return Ramp(from, to, weeks, start)
        }

        fun encode(ramp: Ramp): String = "${ramp.from.name}|${ramp.to.name}|${ramp.weeks}|${ramp.start}"
    }
}

/** The difficulty the user set: a level by hand, or a ramp, which wins while it is set. */
data class DifficultySetting(val level: Difficulty = Difficulty.NORMAL, val ramp: Ramp? = null) {
    fun on(day: LocalDate): Difficulty = ramp?.levelOn(day) ?: level

    companion object {
        /** The settings it is kept in: the level's name, and the ramp (see [Ramp.encode]). */
        const val LEVEL = "difficulty"
        const val RAMP = "difficulty_ramp"

        suspend fun of(store: Store) = decode(store.setting(LEVEL), store.setting(RAMP))

        fun decode(level: String?, ramp: String?) = DifficultySetting(
            level = Difficulty.entries.firstOrNull { it.name == level } ?: Difficulty.NORMAL,
            ramp = Ramp.decode(ramp),
        )
    }
}

/** Suggested points for goals and prices for rewards. */
object Suggest {
    /**
     * Points for each tick of a goal: by its difficulty, plus 1 for every 10 minutes of its
     * length, shared out over the times of a goal done more than once.
     */
    fun points(effort: Effort, minutes: Int? = null, target: Int = 1): Int {
        val whole = effort.points + (minutes ?: 0) / 10
        return maxOf(1, whole / target.coerceAtLeast(1))
    }

    /**
     * A reward's price from its size: so many days of [perDay], the user's usual earnings at
     * Normal. Null when there is nothing to go on. Prices from money are worked out by [MoneyPrices].
     */
    fun price(size: RewardSize?, perDay: Double?): Int? {
        if (size == null || perDay == null || perDay <= 0.0) return null
        return nice(perDay * size.days)
    }

    /** Rounds a price to something easy to read: to 5 from 20, to 10 from 200, to 50 from 1,000. */
    fun nice(value: Double): Int {
        val step = when {
            value >= 1000 -> 50
            value >= 200 -> 10
            value >= 20 -> 5
            else -> 1
        }
        return maxOf(1, (value / step).roundToInt() * step)
    }

    /**
     * What the user's goals would pay a day if all were done, at Normal: each goal's points for
     * every time it is due over the next [days] days (4 weeks unless said), spread over them. A
     * first guess at "your usual" before there is any history.
     */
    fun expectedPerDay(goals: List<Goal>, today: LocalDate, days: Int = 28): Double? {
        var total = 0
        for (goal in goals) {
            if (goal.archived || goal.kind != GoalKind.HABIT) continue
            val repeat = goal.repeat ?: continue
            for (d in 0 until days) {
                val day = today.plusDays(d)
                if (repeat.window(day)?.start == day) total += goal.points * goal.target + if (goal.target > 1) goal.bonus else 0
            }
        }
        return (total.toDouble() / days).takeIf { it > 0.0 }
    }
}
