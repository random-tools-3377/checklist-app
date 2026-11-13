package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/**
 * Milestones, each earned once and kept. Those in a series have a [tier]: bronze, silver, then
 * gold. The level badges show only with the character on.
 */
enum class Badge(val icon: String, val title: String, val how: String, val tier: Tier? = null) {
    FIRST_TICK("✅", "First tick", "Tick a goal for the first time"),
    TICKS_100("💯", "100 ticks", "Tick goals 100 times", Tier.BRONZE),
    TICKS_1000("🏅", "1,000 ticks", "Tick goals 1,000 times", Tier.SILVER),
    TICKS_5000("🎖️", "5,000 ticks", "Tick goals 5,000 times", Tier.GOLD),
    PERFECT_DAY("🌟", "Perfect day", "Do everything due in a day, with nothing skipped"),
    PERFECT_WEEK("🏆", "Perfect week", "Do everything due on every day of a week, Monday to Sunday, with nothing skipped"),
    BOUNCED_BACK("🌅", "Bounced back", "Reach your day's target again the day after one that fell short"),
    STREAK_7("🔥", "A week in a row", "Keep the main streak going for 7 days"),
    STREAK_30("🔥", "A month in a row", "Keep the main streak going for 30 days", Tier.BRONZE),
    STREAK_100("☄️", "100 days in a row", "Keep the main streak going for 100 days", Tier.SILVER),
    STREAK_365("👑", "A year in a row", "Keep the main streak going for 365 days", Tier.GOLD),
    POINTS_100("🪙", "100 points", "Earn 100 points in all", Tier.BRONZE),
    POINTS_1000("💰", "1,000 points", "Earn 1,000 points in all", Tier.SILVER),
    POINTS_10000("💎", "10,000 points", "Earn 10,000 points in all", Tier.GOLD),
    FIRST_REWARD("🎁", "First reward", "Buy a reward in the shop"),
    FIRST_PROJECT("🚀", "Project done", "Finish a project"),
    WEEKLY_TARGET("🎯", "On target", "Reach your weekly points target", Tier.BRONZE),
    WEEKLY_TARGET_4("🏹", "Four weeks on target", "Reach your weekly points target 4 weeks in a row", Tier.SILVER),
    WEEKLY_TARGET_12("🏵️", "Twelve weeks on target", "Reach your weekly points target 12 weeks in a row", Tier.GOLD),
    LEVEL_10("⭐", "Level 10", "Reach level 10 with your character", Tier.BRONZE),
    LEVEL_25("🌠", "Level 25", "Reach level 25 with your character", Tier.SILVER),
    LEVEL_50("🌌", "Level 50", "Reach level 50 with your character", Tier.GOLD),
    SAVED_UP("🐖", "Saved up", "Reach a saving goal in the Budget"),
    MONEY_STREAK_7("💵", "A good money week", "Keep a money streak going for 7 days", Tier.BRONZE),
    MONEY_STREAK_30("💸", "A good money month", "Keep a money streak going for 30 days", Tier.SILVER),
    MONEY_MONTH("🧾", "Within every limit", "Stay within every spending limit for a whole month"),
    MOOD_30("📔", "30 check-ins", "Answer the mood check-in on 30 days"),
    ;

    /** Shown only with the character on. */
    val forCharacter: Boolean get() = this == LEVEL_10 || this == LEVEL_25 || this == LEVEL_50

    /** The extras that must all be on for it to show, and to be earned; none for most. */
    val needs: Set<Feature>
        get() = when (this) {
            LEVEL_10, LEVEL_25, LEVEL_50 -> setOf(Feature.CHARACTER)
            SAVED_UP -> setOf(Feature.BUDGET)
            MONEY_STREAK_7, MONEY_STREAK_30, MONEY_MONTH -> setOf(Feature.BUDGET, Feature.BUDGET_GAME)
            MOOD_30 -> setOf(Feature.MOOD)
            else -> emptySet()
        }

    /** Whether it shows with the extras [on]. One earned and kept isn't listed while its extra is off. */
    fun shownWith(on: Set<Feature>): Boolean = on.containsAll(needs)

    companion object {
        /** The badges there are to earn with no extra but, perhaps, the character. */
        fun shown(character: Boolean): List<Badge> = shown(if (character) setOf(Feature.CHARACTER) else emptySet())

        /** The badges there are to earn with the extras [on]. */
        fun shown(on: Set<Feature>): List<Badge> = entries.filter { it.shownWith(on) }
    }
}

data class EarnedBadge(val badge: Badge, val on: LocalDate)

/** How far along a badge is: [have] of the [of] it needs, never more, with [text] like "73 of 100 ticks". */
data class BadgeProgress(val badge: Badge, val have: Long, val of: Long, val text: String) {
    /** From 0 to 1, for the bar. */
    val share: Float get() = if (of <= 0) 0f else (have.toFloat() / of).coerceIn(0f, 1f)
}

object Badges {
    private val EARNED = setOf(EntryKind.TICK, EntryKind.BONUS, EntryKind.REFUND, EntryKind.PERFECT_DAY, EntryKind.CHALLENGE)

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
        /** Whether the character is on, for the level badges. */
        character: Boolean = false,
        /** The extras turned on, for the badges of the Budget, the budget game and the mood check-in. */
        on: Set<Feature> = emptySet(),
        moods: List<Mood> = emptyList(),
        money: List<MoneyEntry> = emptyList(),
        savingGoals: List<SavingGoal> = emptyList(),
        /** The day tracking began, for the comeback badge; with none it isn't looked for. */
        trackingSince: LocalDate? = null,
    ): List<EarnedBadge> {
        val found = mutableListOf<EarnedBadge>()
        fun add(badge: Badge, on: LocalDate?) { if (on != null) found += EarnedBadge(badge, on) }

        val byTime = ticks.filter { it.day <= today && !it.isUse }.sortedWith(compareBy({ it.at }, { it.id }))
        add(Badge.FIRST_TICK, byTime.getOrNull(0)?.day)
        add(Badge.TICKS_100, byTime.getOrNull(99)?.day)
        add(Badge.TICKS_1000, byTime.getOrNull(999)?.day)
        add(Badge.TICKS_5000, byTime.getOrNull(4999)?.day)

        val days = records.filter { it.day <= today }.sortedBy { it.day }
        add(Badge.PERFECT_DAY, days.firstOrNull { it.perfect }?.day)
        add(Badge.PERFECT_WEEK, perfectWeek(days, today))
        add(Badge.BOUNCED_BACK, Comebacks.days(days, today, streak, trackingSince).firstOrNull())
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
        if (character) {
            // Experience: what goals have paid, as for the character's level.
            val experience = entries.filter { it.kind in Experience.KINDS && it.goalId != null && it.day <= today }.sortedWith(compareBy({ it.day }, { it.at }))
            fun levelDay(level: Int): LocalDate? {
                val needed = Levels.needed(level, Levels.CHARACTER_STEP)
                var sum = 0L
                return experience.firstOrNull { sum += it.amount; sum >= needed }?.day
            }
            add(Badge.LEVEL_10, levelDay(10))
            add(Badge.LEVEL_25, levelDay(25))
            add(Badge.LEVEL_50, levelDay(50))
        }
        add(Badge.FIRST_REWARD, entries.filter { it.kind == EntryKind.PURCHASE && it.day <= today }.minOfOrNull { it.day })
        add(Badge.FIRST_PROJECT, firstProject(goals, steps, ticks, today))

        val weeks = WeeklyTarget.reachedWeeks(entries, weeklyTarget, today)
        add(Badge.WEEKLY_TARGET, weeks.firstOrNull()?.second)
        var run = 0
        var previous: LocalDate? = null
        var fourth: LocalDate? = null
        var twelfth: LocalDate? = null
        for ((week, day) in weeks) {
            run = if (previous != null && previous.plusDays(7) == week) run + 1 else 1
            previous = week
            if (run == 4 && fourth == null) fourth = day
            if (run == 12 && twelfth == null) twelfth = day
        }
        add(Badge.WEEKLY_TARGET_4, fourth)
        add(Badge.WEEKLY_TARGET_12, twelfth)

        if (Badge.SAVED_UP.shownWith(on)) add(Badge.SAVED_UP, savedUp(savingGoals, money, today))
        if (Badge.MONEY_MONTH.shownWith(on)) {
            val moneyStreak = moneyStreakReached(entries, today)
            add(Badge.MONEY_STREAK_7, moneyStreak[7])
            add(Badge.MONEY_STREAK_30, moneyStreak[30])
            add(Badge.MONEY_MONTH, entries.filter { it.kind == EntryKind.BONUS && it.label == MoneyGame.LABEL_MONTH && it.day <= today }.minOfOrNull { it.day })
        }
        if (Badge.MOOD_30.shownWith(on)) {
            add(Badge.MOOD_30, moods.map { it.day }.filter { it <= today }.distinct().sorted().getOrNull(MOOD_DAYS - 1))
        }
        return found.sortedBy { it.badge.ordinal }
    }

    /**
     * How far along each badge with something to count is, from the same history as [earned]:
     * ticks, the main streak running now, points earned, this week's target and weeks on target
     * in a row, the character's level, the project nearest done, the saving goal nearest its
     * target, the money streak and mood check-ins. A badge earned by one thing happening (a
     * first tick, a perfect day) has no entry. What is counted never passes what is needed.
     */
    fun progress(
        today: LocalDate,
        records: List<DayRecord>,
        streak: StreakSettings,
        goals: List<Goal>,
        steps: List<Step>,
        ticks: List<Tick>,
        entries: List<Entry>,
        weeklyTarget: Int,
        on: Set<Feature> = emptySet(),
        moods: List<Mood> = emptyList(),
        money: List<MoneyEntry> = emptyList(),
        savingGoals: List<SavingGoal> = emptyList(),
    ): Map<Badge, BadgeProgress> {
        val found = LinkedHashMap<Badge, BadgeProgress>()
        fun put(badge: Badge, have: Long, of: Long, text: (String, String) -> String) {
            if (!badge.shownWith(on) || of <= 0) return
            val counted = have.coerceIn(0, of)
            found[badge] = BadgeProgress(badge, counted, of, text(grouped(counted), grouped(of)))
        }

        val tickCount = ticks.count { it.day <= today && !it.isUse }.toLong()
        for ((badge, of) in listOf(Badge.TICKS_100 to 100L, Badge.TICKS_1000 to 1000L, Badge.TICKS_5000 to 5000L)) {
            put(badge, tickCount, of) { have, all -> "$have of $all ticks" }
        }

        val days = records.filter { it.day <= today }.sortedBy { it.day }
        val running = StreakCounter.count(days, today, streak).done.toLong()
        for ((badge, of) in listOf(Badge.STREAK_7 to 7L, Badge.STREAK_30 to 30L, Badge.STREAK_100 to 100L, Badge.STREAK_365 to 365L)) {
            put(badge, running, of) { have, all -> "Streak $have of $all days" }
        }

        val points = entries.filter { it.kind in EARNED && it.day <= today }.sumOf { it.amount.toLong() }
        for ((badge, of) in listOf(Badge.POINTS_100 to 100L, Badge.POINTS_1000 to 1000L, Badge.POINTS_10000 to 10000L)) {
            put(badge, points, of) { have, all -> "$have of $all points earned" }
        }

        if (Feature.CHARACTER in on) {
            val experience = entries.filter { it.kind in Experience.KINDS && it.goalId != null && it.day <= today }.sumOf { it.amount.toLong() }
            val level = Levels.of(experience.coerceAtLeast(0), Levels.CHARACTER_STEP).level.toLong()
            for ((badge, of) in listOf(Badge.LEVEL_10 to 10L, Badge.LEVEL_25 to 25L, Badge.LEVEL_50 to 50L)) {
                // Levels start at 1, so the bar counts the levels climbed.
                put(badge, level - 1, of - 1) { _, _ -> "Level ${minOf(level, of)} of $of" }
            }
        }

        // The project nearest done, by its steps ticked.
        val ticked = ticks.filter { it.stepId != null && it.day <= today }.mapNotNull { it.stepId }.toSet()
        goals.filter { it.kind == GoalKind.PROJECT && !it.archived }.mapNotNull { project ->
            val own = steps.filter { it.goalId == project.id && !it.archived }
            if (own.isEmpty()) null else own.count { it.id in ticked } to own.size
        }.maxWithOrNull(compareBy({ it.first.toDouble() / it.second }, { it.first }))?.let { (done, all) ->
            put(Badge.FIRST_PROJECT, done.toLong(), all.toLong()) { have, of -> "$have of $of steps of your nearest project" }
        }

        if (weeklyTarget > 0) {
            val week = WeeklyTarget.progress(entries, today, weeklyTarget)
            put(Badge.WEEKLY_TARGET, week.earned.toLong(), weeklyTarget.toLong()) { have, all -> "$have of $all points this week" }
            // Weeks on target in a row, up to this week or, while it is still going, the last one.
            val weeks = WeeklyTarget.reachedWeeks(entries, weeklyTarget, today).map { it.first }
            var run = 0L
            var expected = weeks.lastOrNull()?.takeIf { it >= week.weekStart.minusDays(7) }
            for (monday in weeks.asReversed()) {
                if (monday != expected) break
                run++
                expected = monday.minusDays(7)
            }
            put(Badge.WEEKLY_TARGET_4, run, 4) { have, all -> "$have of $all weeks in a row" }
            put(Badge.WEEKLY_TARGET_12, run, 12) { have, all -> "$have of $all weeks in a row" }
        }

        // The saving goal nearest its target, as a share: money is the screen's to write out.
        SavingProgress.of(savingGoals.filter { it.targetCents > 0 }, money.filter { it.day <= today }).maxByOrNull { it.share }?.let { nearest ->
            put(Badge.SAVED_UP, (nearest.share * 100).toLong(), 100) { have, _ -> "$have% of the way with ${nearest.goal.name}" }
        }
        if (Badge.MONEY_STREAK_7.shownWith(on)) {
            // Good money days in a row, as the game paid them; they are paid the day after, so yesterday still counts as running.
            val good = entries.filter {
                it.kind == EntryKind.BONUS && it.day <= today && (it.label == MoneyGame.LABEL_NO_SPEND || it.label == MoneyGame.LABEL_WITHIN)
            }.map { it.day }.distinct().sortedDescending()
            var run = 0L
            var expected = good.firstOrNull()?.takeIf { it >= today.minusDays(1) }
            for (day in good) {
                if (day != expected) break
                run++
                expected = day.minusDays(1)
            }
            put(Badge.MONEY_STREAK_7, run, 7) { have, all -> "Money streak $have of $all days" }
            put(Badge.MONEY_STREAK_30, run, 30) { have, all -> "Money streak $have of $all days" }
        }
        val moodDays = moods.map { it.day }.filter { it <= today }.distinct().size.toLong()
        put(Badge.MOOD_30, moodDays, MOOD_DAYS.toLong()) { have, all -> "$have of $all check-ins" }
        return found
    }

    /**
     * The badge to show first as "Next up": of [shown], the one still to earn that is furthest
     * along, the earlier one in the list when two are level. None while nothing has been started.
     */
    fun nextUp(shown: List<Badge>, earned: Set<Badge>, progress: Map<Badge, BadgeProgress>): Badge? =
        shown.filter { it !in earned }.mapNotNull { progress[it] }.filter { it.have > 0 && it.have < it.of }
            .sortedWith(compareByDescending<BadgeProgress> { it.share }.thenBy { it.badge.ordinal }).firstOrNull()?.badge

    /** 1234 as "1,234". */
    private fun grouped(number: Long): String = number.toString().reversed().chunked(3).joinToString(",").reversed()

    /** Days with a mood check-in, in all, for the mood badge. */
    const val MOOD_DAYS = 30

    /**
     * The first day a saving goal held all it was saving up for, from the money put in and taken
     * out. A goal removed since still counts: it was reached.
     */
    private fun savedUp(goals: List<SavingGoal>, money: List<MoneyEntry>, today: LocalDate): LocalDate? {
        val targets = goals.filter { it.targetCents > 0 }.associate { it.id to it.targetCents }
        val saved = HashMap<Long, Long>()
        for (entry in money.filter { it.savingId != null && it.day <= today }.sortedWith(compareBy({ it.day }, { it.at }, { it.id }))) {
            val id = entry.savingId!!
            val target = targets[id] ?: continue
            val now = (saved[id] ?: 0L) + when (entry.kind) {
                MoneyKind.SAVED -> entry.cents
                MoneyKind.TAKEN_OUT -> -entry.cents
                else -> 0L
            }
            saved[id] = now
            if (now >= target) return entry.day
        }
        return null
    }

    /**
     * The day the money streak first reached each length: good money days in a row, read off
     * what the budget game paid for each day in the points history. A day it paid nothing for
     * (over its share, or the game was off) starts the count again.
     */
    private fun moneyStreakReached(entries: List<Entry>, today: LocalDate): Map<Int, LocalDate> {
        val good = entries.filter {
            it.kind == EntryKind.BONUS && it.day <= today && (it.label == MoneyGame.LABEL_NO_SPEND || it.label == MoneyGame.LABEL_WITHIN)
        }.map { it.day }.distinct().sorted()
        val reached = HashMap<Int, LocalDate>()
        var length = 0
        var previous: LocalDate? = null
        for (day in good) {
            length = if (previous != null && previous.plusDays(1) == day) length + 1 else 1
            previous = day
            if (length !in reached) reached[length] = day
        }
        return reached
    }

    /**
     * The Sunday of the first week, covered all through, with every day that had something due a
     * perfect day: everything done, nothing skipped.
     */
    private fun perfectWeek(days: List<DayRecord>, today: LocalDate): LocalDate? =
        days.groupBy { Days.periodStart(it.day, Period.WEEKLY) }.entries.sortedBy { it.key }.firstOrNull { (monday, week) ->
            val sunday = monday.plusDays(6)
            sunday <= today && week.size == 7 && week.any { it.due > 0 } && week.all { it.perfect || (it.due == 0 && it.skips == 0) }
        }?.let { it.key.plusDays(6) }

    /**
     * The day the main streak first reached each length, counted the way Today counts it: days
     * with nothing due are passed over, and today can't break it while it is still going.
     */
    private fun streakReached(days: List<DayRecord>, today: LocalDate, settings: StreakSettings): Map<Int, LocalDate> {
        val reached = HashMap<Int, LocalDate>()
        var length = 0
        for (record in days) {
            val ok = StreakCounter.mainDay(record, settings) ?: continue
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
