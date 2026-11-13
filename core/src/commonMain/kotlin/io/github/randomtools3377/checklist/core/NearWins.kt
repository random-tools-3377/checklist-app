package io.github.randomtools3377.checklist.core

import kotlin.math.ceil

/** What the streak card says about today, beside the count. */
data class StreakToday(
    /** "Today counts: day 13", "2 more and today counts: day 13", or "A new run starts today". */
    val line: String?,
    /** The best the main streak has stood at, when it is more than it stands at now. */
    val best: Int?,
)

/**
 * The closest win on Today, as one short line, and what the streak card says about today. It is
 * always an offer ("1 more for a perfect day"), never what is missing, never a count of free
 * skips, and it never suggests a skip.
 */
object NearWins {
    /** A badge for ticks is mentioned from this many ticks away. */
    const val BADGE_WITHIN = 5

    /** The streak's line says how many more only from this few. */
    const val STREAK_WITHIN = 3

    /** The weekly target is mentioned once this share of it is earned. */
    const val TARGET_FROM_PERCENT = 80

    private val TICK_BADGES = listOf(100 to Badge.TICKS_100, 1000 to Badge.TICKS_1000, 5000 to Badge.TICKS_5000)

    /** How many more of today's goals the main streak's target needs; 0 once today counts or nothing is due. */
    fun neededForStreak(record: DayRecord?, settings: StreakSettings): Int {
        if (record == null || record.due == 0 || StreakCounter.meetsTarget(record, settings)) return 0
        val target = if (settings.target == StreakTarget.COUNT) {
            minOf(settings.value, record.due)
        } else {
            ceil(settings.value * record.due / 100.0).toInt()
        }
        return (target - record.done).coerceAtLeast(1)
    }

    /** "2 more keeps your 12-day streak", or without a streak running, "2 more and today counts for your streak". */
    fun streakOffer(needed: Int, streak: Int): String =
        if (streak > 0) "$needed more keeps your $streak-day streak" else "$needed more and today counts for your streak"

    /** "2 more for a perfect day", with what it [pays] when that is something. */
    fun perfectOffer(left: Int, pays: Int = 0): String = "$left more for a perfect day" + if (pays > 0) " (+$pays)" else ""

    /**
     * The closest win, nearest first. [record] is today's so far, [streak] the main streak now,
     * [perfectBonus] the perfect-day bonus (0 when off), [combo] the combo through yesterday
     * while combos are on (null when off), [ticks] every tick so far for the tick badges (null
     * to leave them out), and [earnedBadges] the badges already earned.
     */
    fun line(
        record: DayRecord?,
        settings: StreakSettings,
        streak: Int,
        perfectBonus: Int = 0,
        combo: Int? = null,
        ticks: Int? = null,
        earnedBadges: Set<Badge> = emptySet(),
        week: WeekProgress? = null,
    ): String? {
        class Win(val away: Int, val text: String)
        val wins = mutableListOf<Win>()
        val needed = neededForStreak(record, settings)
        if (record != null && needed > 0) {
            wins += Win(needed, streakOffer(needed, streak))
        }
        // A perfect day: only while nothing was skipped today, since a day with a skip is never one.
        if (record != null && record.due > 0 && record.skips == 0 && record.done < record.due) {
            val left = record.due - record.done
            val pays = perfectBonus + (combo?.let { Combos.pay(it + 1) } ?: 0)
            wins += Win(left, perfectOffer(left, pays))
        }
        if (ticks != null) {
            TICK_BADGES.firstOrNull { (count, badge) -> badge !in earnedBadges && ticks < count }?.let { (count, badge) ->
                val away = count - ticks
                if (away <= BADGE_WITHIN) wins += Win(away, (if (away == 1) "1 tick" else "$away ticks") + " from the ${badge.title} badge")
            }
        }
        if (week != null && !week.reached && week.target > 0 && week.earned * 100 >= week.target * TARGET_FROM_PERCENT) {
            // Points aren't ticks: it comes after anything a tick or two away.
            wins += Win(Int.MAX_VALUE, "${week.left} more ${PointsName.current.word(week.left)} reaches your weekly target")
        }
        // The nearest first; of two equally near, the perfect day says more than the streak.
        return wins.sortedWith(compareBy<Win> { it.away }.thenByDescending { it.text.contains("perfect day") }).firstOrNull()?.text
    }

    /**
     * What the streak card says about today. [streak] is the main streak now (today counted once
     * it qualifies), [best] the best it has ever stood at, [brokeYesterday] whether the last day
     * that counted before today broke a run.
     */
    fun streakToday(record: DayRecord?, settings: StreakSettings, streak: Int, best: Int, brokeYesterday: Boolean): StreakToday {
        val counts = record != null && record.due > 0 && StreakCounter.meetsTarget(record, settings)
        val needed = neededForStreak(record, settings)
        val line = when {
            counts -> "Today counts: day $streak"
            needed in 1..STREAK_WITHIN && streak > 0 -> "$needed more and today counts: day ${streak + 1}"
            streak == 0 && brokeYesterday && record != null && record.due > 0 -> "A new run starts today"
            else -> null
        }
        return StreakToday(line, best.takeIf { it > streak })
    }
}
