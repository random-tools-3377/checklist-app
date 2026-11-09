package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/** Milestones, each earned once and kept. */
enum class Badge(val icon: String, val title: String, val how: String) {
    FIRST_TICK("✅", "First tick", "Tick a goal for the first time"),
    TICKS_100("💯", "100 ticks", "Tick goals 100 times"),
    TICKS_1000("🏅", "1,000 ticks", "Tick goals 1,000 times"),
    PERFECT_DAY("🌟", "Perfect day", "Do everything due in a day"),
    PERFECT_WEEK("🏆", "Perfect week", "Do everything due on every day of a week, Monday to Sunday"),
    STREAK_7("🔥", "A week in a row", "Keep the main streak going for 7 days"),
    STREAK_30("🔥", "A month in a row", "Keep the main streak going for 30 days"),
    STREAK_100("☄️", "100 days in a row", "Keep the main streak going for 100 days"),
    STREAK_365("👑", "A year in a row", "Keep the main streak going for 365 days"),
    POINTS_100("🪙", "100 points", "Earn 100 points in all"),
    POINTS_1000("💰", "1,000 points", "Earn 1,000 points in all"),
    POINTS_10000("💎", "10,000 points", "Earn 10,000 points in all"),
    FIRST_REWARD("🎁", "First reward", "Buy a reward in the shop"),
    FIRST_PROJECT("🚀", "Project done", "Finish a project"),
    WEEKLY_TARGET("🎯", "On target", "Reach your weekly points target"),
    WEEKLY_TARGET_4("🏹", "Four weeks on target", "Reach your weekly points target 4 weeks in a row"),
}

data class EarnedBadge(val badge: Badge, val on: LocalDate)

object Badges {
    private val EARNED = setOf(EntryKind.TICK, EntryKind.BONUS, EntryKind.REFUND, EntryKind.PERFECT_DAY)

    /**
     * Every badge the history shows, each with the day it was first earned. [records] are the
     * days from when tracking began to [today], in order.
     */
    fun earned(
        today: LocalDate,
        records: List<DayRecord>,
        streak: StreakSettings,
        goals: List<Goal>,
        steps: List<Step>,
        ticks: List<Tick>,
        entries: List<Entry>,
        weeklyTarget: Int,
    ): List<EarnedBadge> {
        val found = mutableListOf<EarnedBadge>()
        fun add(badge: Badge, on: LocalDate?) { if (on != null) found += EarnedBadge(badge, on) }

        val byTime = ticks.filter { it.day <= today }.sortedWith(compareBy({ it.at }, { it.id }))
        add(Badge.FIRST_TICK, byTime.getOrNull(0)?.day)
        add(Badge.TICKS_100, byTime.getOrNull(99)?.day)
        add(Badge.TICKS_1000, byTime.getOrNull(999)?.day)

        val days = records.filter { it.day <= today }.sortedBy { it.day }
        add(Badge.PERFECT_DAY, days.firstOrNull { perfect(it) }?.day)
        add(Badge.PERFECT_WEEK, perfectWeek(days, today))
        val streakDays = streakReached(days, today, streak)
        add(Badge.STREAK_7, streakDays[7])
        add(Badge.STREAK_30, streakDays[30])
        add(Badge.STREAK_100, streakDays[100])
        add(Badge.STREAK_365, streakDays[365])

        val earned = entries.filter { it.kind in EARNED && it.day <= today }.sortedWith(compareBy({ it.day }, { it.at }))
        fun pointsDay(points: Int): LocalDate? {
            var sum = 0
            return earned.firstOrNull { sum += it.amount; sum >= points }?.day
        }
        add(Badge.POINTS_100, pointsDay(100))
        add(Badge.POINTS_1000, pointsDay(1000))
        add(Badge.POINTS_10000, pointsDay(10000))
        add(Badge.FIRST_REWARD, entries.filter { it.kind == EntryKind.PURCHASE && it.day <= today }.minOfOrNull { it.day })
        add(Badge.FIRST_PROJECT, firstProject(goals, steps, ticks, today))

        val weeks = WeeklyTarget.reachedWeeks(entries, weeklyTarget, today)
        add(Badge.WEEKLY_TARGET, weeks.firstOrNull()?.second)
        var run = 0
        var previous: LocalDate? = null
        var fourth: LocalDate? = null
        for ((week, day) in weeks) {
            run = if (previous != null && previous.plusDays(7) == week) run + 1 else 1
            previous = week
            if (run == 4 && fourth == null) fourth = day
        }
        add(Badge.WEEKLY_TARGET_4, fourth)
        return found.sortedBy { it.badge.ordinal }
    }

    private fun perfect(record: DayRecord) = record.due > 0 && record.done >= record.due

    /** The Sunday of the first week, covered all through, with everything due done every day. */
    private fun perfectWeek(days: List<DayRecord>, today: LocalDate): LocalDate? =
        days.groupBy { Days.periodStart(it.day, Period.WEEKLY) }.entries.sortedBy { it.key }.firstOrNull { (monday, week) ->
            val sunday = monday.plusDays(6)
            sunday <= today && week.size == 7 && week.any { it.due > 0 } && week.all { it.due == 0 || perfect(it) }
        }?.let { it.key.plusDays(6) }

    /**
     * The day the main streak first reached each length, counted the way Today counts it: days
     * with nothing due are passed over, and today can't break it while it is still going.
     */
    private fun streakReached(days: List<DayRecord>, today: LocalDate, settings: StreakSettings): Map<Int, LocalDate> {
        val reached = HashMap<Int, LocalDate>()
        var length = 0
        for (record in days) {
            if (record.due == 0 && record.misses == 0 && record.skips == 0) continue
            val ok = StreakCounter.meetsTarget(record, settings)
            if (record.day == today && !ok) continue
            if (!ok) {
                length = 0
                continue
            }
            length++
            if (length !in reached) reached[length] = record.day
        }
        return reached
    }

    /** The day the first project had every step ticked. */
    private fun firstProject(goals: List<Goal>, steps: List<Step>, ticks: List<Tick>, today: LocalDate): LocalDate? {
        val stepTicks = ticks.filter { it.stepId != null && it.day <= today }.associateBy { it.stepId }
        return goals.filter { it.kind == GoalKind.PROJECT }.mapNotNull { project ->
            val own = steps.filter { it.goalId == project.id && !it.archived }
            if (own.isEmpty()) return@mapNotNull null
            val days = own.map { stepTicks[it.id]?.day ?: return@mapNotNull null }
            days.max()
        }.minOrNull()
    }

    /** Badges once earned stay, on the day they were first earned; new ones join them. */
    fun merge(kept: Map<Badge, LocalDate>, now: List<EarnedBadge>): Map<Badge, LocalDate> =
        kept + now.filter { it.badge !in kept }.associate { it.badge to it.on }

    /** Stored as "FIRST_TICK=2026-11-09,STREAK_7=2026-11-15". */
    fun encode(badges: Map<Badge, LocalDate>): String =
        badges.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.name}=${it.value}" }

    fun decode(text: String?): Map<Badge, LocalDate> = text.orEmpty().split(",").mapNotNull { part ->
        val (name, day) = part.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
        val badge = Badge.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
        val date = try { LocalDate.parse(day) } catch (e: IllegalArgumentException) { return@mapNotNull null }
        badge to date
    }.toMap()
}
