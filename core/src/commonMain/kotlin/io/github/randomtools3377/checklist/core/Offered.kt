package io.github.randomtools3377.checklist.core

/**
 * What the app offers while extras are off: an extra's section on Today, and its numbers in
 * "Points and rules", show only once the extra is turned on or is already in use. Everything
 * else is always offered.
 */
object Offered {
    /** What Today shows even with its extra off: to-dos that exist, today's mood, a weekly target that is set. */
    data class InUse(val todos: Boolean = false, val moodToday: Boolean = false, val weeklyTarget: Boolean = false)

    /** Whether [section] can show on Today with the extras [on], so Appearance lists it. */
    fun section(section: TodaySection, on: Set<Feature>, inUse: InUse = InUse()): Boolean = when (section) {
        TodaySection.MOOD -> Feature.MOOD in on || inUse.moodToday
        TodaySection.CHARACTER -> Feature.CHARACTER in on
        TodaySection.QUESTS -> Feature.QUESTS in on
        TodaySection.RACE -> Feature.RACE in on
        TodaySection.BOSS -> Feature.BOSSES in on
        TodaySection.MONEY -> Feature.BUDGET in on
        TodaySection.TODOS -> Feature.TODOS in on || inUse.todos
        TodaySection.WEEKLY_TARGET -> inUse.weeklyTarget
        else -> true
    }

    /** Today's sections that can show, in the user's order. */
    fun sections(choices: List<SectionChoice>, on: Set<Feature>, inUse: InUse = InUse()): List<SectionChoice> =
        choices.filter { section(it.section, on, inUse) }

    /**
     * Moves [section] up (-1) or down (+1) past the next one of [listed], the sections Appearance
     * shows. The others, hidden there, keep their places.
     */
    fun move(choices: List<SectionChoice>, section: TodaySection, by: Int, listed: Collection<TodaySection>): List<SectionChoice> {
        val from = choices.indexOfFirst { it.section == section }
        if (from < 0 || by == 0) return choices
        val step = if (by < 0) -1 else 1
        var to = from + step
        while (to in choices.indices && choices[to].section !in listed) to += step
        if (to !in choices.indices) return choices
        return choices.toMutableList().also { list -> list.add(to, list.removeAt(from)) }
    }

    /**
     * The extra each number in "Points and rules" belongs to, by its key. A number not here, like
     * the penalty or the difficulty levels, is always shown.
     */
    val RULE_FEATURES: Map<String, Feature> = mapOf(
        "boostExtraPercent" to Feature.BOOST,
        "boostMinMisses" to Feature.BOOST,
        "streakBonus1Days" to Feature.STREAK_BONUS,
        "streakBonus1Percent" to Feature.STREAK_BONUS,
        "streakBonus2Days" to Feature.STREAK_BONUS,
        "streakBonus2Percent" to Feature.STREAK_BONUS,
        "streakBonus3Days" to Feature.STREAK_BONUS,
        "streakBonus3Percent" to Feature.STREAK_BONUS,
        "questReward" to Feature.QUESTS,
        "questBigReward" to Feature.QUESTS,
        "comboPerDay" to Feature.COMBOS,
        "comboMostDays" to Feature.COMBOS,
        "bossHit" to Feature.BOSSES,
        "bossWeakPercent" to Feature.BOSSES,
        "bossReward" to Feature.BOSSES,
        "bossHealthPercent" to Feature.BOSSES,
        "bossMinHealth" to Feature.BOSSES,
        "luckyOneIn" to Feature.LUCKY,
        "luckyChestMin" to Feature.LUCKY,
        "luckyChestMax" to Feature.LUCKY,
        "shieldEveryDays" to Feature.SHIELDS,
        "shieldsMost" to Feature.SHIELDS,
        "pathBonusPercent" to Feature.CHARACTER,
        "goodMoneyDay" to Feature.BUDGET_GAME,
        "noSpendDay" to Feature.BUDGET_GAME,
        "moneyMonthBonus" to Feature.BUDGET_GAME,
        "savedUp" to Feature.BUDGET_GAME,
        "repeatPriceRisePercent" to Feature.REPEAT_PRICE,
        "moneyDriftPercent" to Feature.SUGGESTIONS,
        "cheapDaysTenths" to Feature.BALANCE_CHECK,
        "farDays" to Feature.BALANCE_CHECK,
        "easySharePercent" to Feature.BALANCE_CHECK,
        "hardSharePercent" to Feature.BALANCE_CHECK,
        "payLessPercent" to Feature.BALANCE_CHECK,
        "payMorePercent" to Feature.BALANCE_CHECK,
        "effortEasyPoints" to Feature.SUGGESTIONS,
        "effortMediumPoints" to Feature.SUGGESTIONS,
        "effortHardPoints" to Feature.SUGGESTIONS,
        "effortVeryHardPoints" to Feature.SUGGESTIONS,
        "minutesPerPoint" to Feature.SUGGESTIONS,
        "rewardSmallDays" to Feature.SUGGESTIONS,
        "rewardMediumDays" to Feature.SUGGESTIONS,
        "rewardBigDays" to Feature.SUGGESTIONS,
        "rewardHugeDays" to Feature.SUGGESTIONS,
        "moodMinDays" to Feature.MOOD,
        "moodNotableTenths" to Feature.MOOD,
        "companionLateHours" to Feature.CHECKINS,
    )

    /**
     * The numbers "Points and rules" shows: those for extras that are [on], those that belong to
     * no extra, and any already changed from its usual value. The late nudges' numbers show
     * while nudges are on in Settings ([nudges]).
     */
    fun rules(fields: List<RuleField>, on: Set<Feature>, rules: Rules, usual: Rules = Rules(), nudges: Boolean = true): List<RuleField> =
        fields.filter { field ->
            val feature = RULE_FEATURES[field.key]
            val offered = if (field.group == Rules.NUDGES) nudges else feature == null || feature in on
            offered || field.get(rules) != field.get(usual)
        }
}
