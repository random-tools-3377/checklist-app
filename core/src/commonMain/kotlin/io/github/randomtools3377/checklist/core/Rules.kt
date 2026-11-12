package io.github.randomtools3377.checklist.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Every number that decides points, prices, penalties and the game's rewards, which the user
 * can change. The defaults are the app's usual values. Changes apply from then on: what was
 * already paid or taken stays as it was.
 *
 * The rules in use are kept in [current], read from the setting [SETTING] by [load]. Code that
 * pays or takes points loads them first (see [Book]), so a process woken by a notification uses
 * the user's rules too.
 */
@Serializable
data class Rules(
    // Penalties
    /** Over the limit, a skip or miss costs this percent of the goal's points (a project's finishing bonus), rounded down. */
    val penaltyPercent: Int = 50,

    // Difficulty: what goals pay at each level, in percent, and the free skips and misses it adds.
    val veryEasyPayPercent: Int = 200,
    val easyPayPercent: Int = 150,
    val normalPayPercent: Int = 100,
    val hardPayPercent: Int = 75,
    val veryHardPayPercent: Int = 50,
    val veryEasyExtraSkips: Int = 2,
    val easyExtraSkips: Int = 1,
    val normalExtraSkips: Int = 0,
    val hardExtraSkips: Int = -1,
    /** 1: at Very hard no skip or miss is free at all, whatever the goal allows. 0: [veryHardExtraSkips] applies. */
    val veryHardNoFreeSkips: Int = 1,
    val veryHardExtraSkips: Int = -1,

    // Boosts and bonuses
    /** The boosted goal pays this percent more. */
    val boostExtraPercent: Int = 50,
    /** Misses over the 4 weeks before for a goal to be boosted. */
    val boostMinMisses: Int = 2,
    /** The streak bonus: from a main streak of so many days, ticks pay so many percent more. */
    val streakBonus1Days: Int = 7,
    val streakBonus1Percent: Int = 10,
    val streakBonus2Days: Int = 30,
    val streakBonus2Percent: Int = 20,
    val streakBonus3Days: Int = 100,
    val streakBonus3Percent: Int = 30,

    // Quests and combos
    val questReward: Int = 5,
    /** The "everything due by 8 pm" quest. */
    val questBigReward: Int = 10,
    val comboPerDay: Int = 2,
    val comboMostDays: Int = 10,

    // Bosses and lucky ticks
    val bossHit: Int = 10,
    /** What an item a boss is weak to does, in percent of [bossHit]. */
    val bossWeakPercent: Int = 200,
    val bossReward: Int = 25,
    /** A boss's health: what this percent of the week's ticks would deal with plain items. */
    val bossHealthPercent: Int = 70,
    val bossMinHealth: Int = 50,
    /** One tick in this many is lucky. */
    val luckyOneIn: Int = 10,
    val luckyChestMin: Int = 5,
    val luckyChestMax: Int = 15,

    // Streak shields. (Bets, with their stakes and win, are no longer in the app: their old
    // numbers in the stored text are left out when it is read.)
    val shieldEveryDays: Int = 30,
    val shieldsMost: Int = 2,

    // Paths
    val pathBonusPercent: Int = 10,

    // Budget game
    val goodMoneyDay: Int = 3,
    val noSpendDay: Int = 5,
    val moneyMonthBonus: Int = 25,
    val savedUp: Int = 20,

    // Prices
    val repeatPriceRisePercent: Int = 25,
    val moneyDriftPercent: Int = 10,

    // Balance check
    /** Under this many tenths of a day of earning, a price is too cheap. */
    val cheapDaysTenths: Int = 5,
    val farDays: Int = 60,
    val easySharePercent: Int = 95,
    val hardSharePercent: Int = 50,
    val payLessPercent: Int = 75,
    val payMorePercent: Int = 150,

    // Suggestions
    val effortEasyPoints: Int = 2,
    val effortMediumPoints: Int = 5,
    val effortHardPoints: Int = 10,
    val effortVeryHardPoints: Int = 20,
    /** One more suggested point for every so many minutes of a goal's length; 0 for none. */
    val minutesPerPoint: Int = 10,
    val rewardSmallDays: Int = 1,
    val rewardMediumDays: Int = 3,
    val rewardBigDays: Int = 14,
    val rewardHugeDays: Int = 42,

    // Mood and your goals (see MoodInsights)
    /** Days with a mood needed each way (done and not done, noted and not) before a goal or thing is compared. */
    val moodMinDays: Int = 3,
    /** A difference in mood counts from this many tenths of a point (5 is half a point). */
    val moodNotableTenths: Int = 5,
) {
    /** Every value brought into its range (see [FIELDS]), and a lucky chest's least no more than its most. */
    fun sane(): Rules {
        var rules = this
        for (field in FIELDS) {
            val value = field.get(rules)
            if (value < field.min || value > field.max) rules = field.set(rules, value)
        }
        if (rules.luckyChestMax < rules.luckyChestMin) rules = rules.copy(luckyChestMax = rules.luckyChestMin)
        return rules
    }

    companion object {
        /** The setting the user's rules are kept in, as JSON holding only what differs from the defaults. */
        const val SETTING = "rules"

        /** The rules in use. */
        @kotlin.concurrent.Volatile
        var current: Rules = Rules()

        private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        /** The rules from [text]: a field missing keeps its default; text that can't be read gives the defaults. */
        fun decode(text: String?): Rules {
            if (text.isNullOrBlank()) return Rules()
            return runCatching { json.decodeFromString(serializer(), text) }.getOrNull()?.sane() ?: Rules()
        }

        fun encode(rules: Rules): String = json.encodeToString(serializer(), rules)

        /** What a number outside [field]'s range is told, like "From 0 to 1000." */
        fun rangeText(field: RuleField): String = "From ${field.min} to ${field.max}."

        /** Reads the user's rules from [store] and puts them in use. */
        suspend fun load(store: Store): Rules = decode(store.setting(SETTING)).also { current = it }

        private fun field(
            key: String,
            group: String,
            label: String,
            unit: String,
            min: Int,
            max: Int,
            get: (Rules) -> Int,
            set: (Rules, Int) -> Rules,
        ) = RuleField(key, group, label, unit, get, { rules, value -> set(rules, value.coerceIn(min, max)) }, min, max)

        private const val PENALTIES = "Penalties"
        private const val DIFFICULTY = "Difficulty"
        private const val BOOSTS = "Boosts and bonuses"
        private const val QUESTS = "Quests and combos"
        private const val BOSSES = "Bosses and lucky ticks"
        private const val SHIELDS = "Streak shields"
        private const val PATHS = "Paths"
        private const val BUDGET = "Budget game"
        private const val PRICES = "Prices"
        private const val BALANCE = "Balance check"
        private const val SUGGESTIONS = "Suggestions"
        private const val MOOD = "Mood and your goals"

        private const val PERCENT = "percent"
        private const val POINTS = "points"
        private const val DAYS = "days"
        private const val MOST = 999_999

        /** Every rule, in order and grouped, for the settings page. Each key is the field's name. */
        val FIELDS: List<RuleField> = listOf(
            field("penaltyPercent", PENALTIES, "Over the limit, a skip or miss costs this much of what the goal pays", PERCENT, 0, 1000, { it.penaltyPercent }) { r, v -> r.copy(penaltyPercent = v) },

            field("veryEasyPayPercent", DIFFICULTY, "Very easy: goals pay", PERCENT, 0, 1000, { it.veryEasyPayPercent }) { r, v -> r.copy(veryEasyPayPercent = v) },
            field("easyPayPercent", DIFFICULTY, "Easy: goals pay", PERCENT, 0, 1000, { it.easyPayPercent }) { r, v -> r.copy(easyPayPercent = v) },
            field("normalPayPercent", DIFFICULTY, "Normal: goals pay", PERCENT, 0, 1000, { it.normalPayPercent }) { r, v -> r.copy(normalPayPercent = v) },
            field("hardPayPercent", DIFFICULTY, "Hard: goals pay", PERCENT, 0, 1000, { it.hardPayPercent }) { r, v -> r.copy(hardPayPercent = v) },
            field("veryHardPayPercent", DIFFICULTY, "Very hard: goals pay", PERCENT, 0, 1000, { it.veryHardPayPercent }) { r, v -> r.copy(veryHardPayPercent = v) },
            field("veryEasyExtraSkips", DIFFICULTY, "Very easy: extra free skips or misses (below 0 for fewer)", "", -10, 10, { it.veryEasyExtraSkips }) { r, v -> r.copy(veryEasyExtraSkips = v) },
            field("easyExtraSkips", DIFFICULTY, "Easy: extra free skips or misses (below 0 for fewer)", "", -10, 10, { it.easyExtraSkips }) { r, v -> r.copy(easyExtraSkips = v) },
            field("normalExtraSkips", DIFFICULTY, "Normal: extra free skips or misses (below 0 for fewer)", "", -10, 10, { it.normalExtraSkips }) { r, v -> r.copy(normalExtraSkips = v) },
            field("hardExtraSkips", DIFFICULTY, "Hard: extra free skips or misses (below 0 for fewer)", "", -10, 10, { it.hardExtraSkips }) { r, v -> r.copy(hardExtraSkips = v) },
            field("veryHardNoFreeSkips", DIFFICULTY, "Very hard: no skip or miss is free at all (1 for yes, 0 for no)", "", 0, 1, { it.veryHardNoFreeSkips }) { r, v -> r.copy(veryHardNoFreeSkips = v) },
            field("veryHardExtraSkips", DIFFICULTY, "Very hard: extra free skips or misses, when the line above is 0", "", -10, 10, { it.veryHardExtraSkips }) { r, v -> r.copy(veryHardExtraSkips = v) },

            field("boostExtraPercent", BOOSTS, "The boosted goal pays this much more", PERCENT, 0, 1000, { it.boostExtraPercent }) { r, v -> r.copy(boostExtraPercent = v) },
            field("boostMinMisses", BOOSTS, "Misses in the last 4 weeks before a goal can be boosted", "misses", 1, 100, { it.boostMinMisses }) { r, v -> r.copy(boostMinMisses = v) },
            field("streakBonus1Days", BOOSTS, "Streak bonus starts at a streak of", DAYS, 1, 3650, { it.streakBonus1Days }) { r, v -> r.copy(streakBonus1Days = v) },
            field("streakBonus1Percent", BOOSTS, "From then, ticks pay this much more", PERCENT, 0, 1000, { it.streakBonus1Percent }) { r, v -> r.copy(streakBonus1Percent = v) },
            field("streakBonus2Days", BOOSTS, "Bigger streak bonus at a streak of", DAYS, 1, 3650, { it.streakBonus2Days }) { r, v -> r.copy(streakBonus2Days = v) },
            field("streakBonus2Percent", BOOSTS, "From then, ticks pay this much more", PERCENT, 0, 1000, { it.streakBonus2Percent }) { r, v -> r.copy(streakBonus2Percent = v) },
            field("streakBonus3Days", BOOSTS, "Biggest streak bonus at a streak of", DAYS, 1, 3650, { it.streakBonus3Days }) { r, v -> r.copy(streakBonus3Days = v) },
            field("streakBonus3Percent", BOOSTS, "From then, ticks pay this much more", PERCENT, 0, 1000, { it.streakBonus3Percent }) { r, v -> r.copy(streakBonus3Percent = v) },

            field("questReward", QUESTS, "A quest pays", POINTS, 0, MOST, { it.questReward }) { r, v -> r.copy(questReward = v) },
            field("questBigReward", QUESTS, "The \"everything by 8 pm\" quest pays", POINTS, 0, MOST, { it.questBigReward }) { r, v -> r.copy(questBigReward = v) },
            field("comboPerDay", QUESTS, "Each day of a combo pays this much more", POINTS, 0, MOST, { it.comboPerDay }) { r, v -> r.copy(comboPerDay = v) },
            field("comboMostDays", QUESTS, "A combo stops growing after", DAYS, 0, 3650, { it.comboMostDays }) { r, v -> r.copy(comboMostDays = v) },

            field("bossHit", BOSSES, "Each item does this much damage to a boss", "", 1, MOST, { it.bossHit }) { r, v -> r.copy(bossHit = v) },
            field("bossWeakPercent", BOSSES, "Items a boss is weak to do this much of that", PERCENT, 0, 1000, { it.bossWeakPercent }) { r, v -> r.copy(bossWeakPercent = v) },
            field("bossReward", BOSSES, "Beating a boss pays", POINTS, 0, MOST, { it.bossReward }) { r, v -> r.copy(bossReward = v) },
            field("bossHealthPercent", BOSSES, "A boss is beaten by doing this much of the week's goals", PERCENT, 1, 1000, { it.bossHealthPercent }) { r, v -> r.copy(bossHealthPercent = v) },
            field("bossMinHealth", BOSSES, "A boss has at least this much health", "", 1, MOST, { it.bossMinHealth }) { r, v -> r.copy(bossMinHealth = v) },
            field("luckyOneIn", BOSSES, "One tick in this many is lucky", "", 1, 1000, { it.luckyOneIn }) { r, v -> r.copy(luckyOneIn = v) },
            field("luckyChestMin", BOSSES, "A lucky chest holds at least", POINTS, 0, MOST, { it.luckyChestMin }) { r, v -> r.copy(luckyChestMin = v) },
            field("luckyChestMax", BOSSES, "A lucky chest holds at most", POINTS, 0, MOST, { it.luckyChestMax }) { r, v -> r.copy(luckyChestMax = v) },

            field("shieldEveryDays", SHIELDS, "A streak shield is earned every", DAYS, 1, 3650, { it.shieldEveryDays }) { r, v -> r.copy(shieldEveryDays = v) },
            field("shieldsMost", SHIELDS, "Streak shields held at most", "shields", 0, 100, { it.shieldsMost }) { r, v -> r.copy(shieldsMost = v) },

            field("pathBonusPercent", PATHS, "A path's skill gets this much more experience", PERCENT, 0, 1000, { it.pathBonusPercent }) { r, v -> r.copy(pathBonusPercent = v) },

            field("goodMoneyDay", BUDGET, "A day within its share pays", POINTS, 0, MOST, { it.goodMoneyDay }) { r, v -> r.copy(goodMoneyDay = v) },
            field("noSpendDay", BUDGET, "A day with nothing spent pays", POINTS, 0, MOST, { it.noSpendDay }) { r, v -> r.copy(noSpendDay = v) },
            field("moneyMonthBonus", BUDGET, "A month within every limit pays", POINTS, 0, MOST, { it.moneyMonthBonus }) { r, v -> r.copy(moneyMonthBonus = v) },
            field("savedUp", BUDGET, "Reaching a saving goal pays", POINTS, 0, MOST, { it.savedUp }) { r, v -> r.copy(savedUp = v) },

            field("repeatPriceRisePercent", PRICES, "A reward bought again the same week costs this much more each time", PERCENT, 0, 1000, { it.repeatPriceRisePercent }) { r, v -> r.copy(repeatPriceRisePercent = v) },
            field("moneyDriftPercent", PRICES, "Prices from money change once what goals pay has moved by", PERCENT, 0, 1000, { it.moneyDriftPercent }) { r, v -> r.copy(moneyDriftPercent = v) },

            field("cheapDaysTenths", BALANCE, "Too cheap: earned in under this many tenths of a day", "tenths of a day", 0, 10_000, { it.cheapDaysTenths }) { r, v -> r.copy(cheapDaysTenths = v) },
            field("farDays", BALANCE, "Out of reach: takes more than this many days to earn", DAYS, 1, 3650, { it.farDays }) { r, v -> r.copy(farDays = v) },
            field("easySharePercent", BALANCE, "Nearly always done: done at least this often", PERCENT, 0, 100, { it.easySharePercent }) { r, v -> r.copy(easySharePercent = v) },
            field("hardSharePercent", BALANCE, "Often missed: done this often or less", PERCENT, 0, 100, { it.hardSharePercent }) { r, v -> r.copy(hardSharePercent = v) },
            field("payLessPercent", BALANCE, "A goal nearly always done could pay this much of its points", PERCENT, 1, 100, { it.payLessPercent }) { r, v -> r.copy(payLessPercent = v) },
            field("payMorePercent", BALANCE, "A goal often missed could pay this much of its points", PERCENT, 100, 1000, { it.payMorePercent }) { r, v -> r.copy(payMorePercent = v) },

            field("effortEasyPoints", SUGGESTIONS, "Suggested for an easy goal", POINTS, 0, MOST, { it.effortEasyPoints }) { r, v -> r.copy(effortEasyPoints = v) },
            field("effortMediumPoints", SUGGESTIONS, "Suggested for a medium goal", POINTS, 0, MOST, { it.effortMediumPoints }) { r, v -> r.copy(effortMediumPoints = v) },
            field("effortHardPoints", SUGGESTIONS, "Suggested for a hard goal", POINTS, 0, MOST, { it.effortHardPoints }) { r, v -> r.copy(effortHardPoints = v) },
            field("effortVeryHardPoints", SUGGESTIONS, "Suggested for a very hard goal", POINTS, 0, MOST, { it.effortVeryHardPoints }) { r, v -> r.copy(effortVeryHardPoints = v) },
            field("minutesPerPoint", SUGGESTIONS, "One more suggested point for every so many minutes a goal takes (0 for none)", "minutes", 0, 1440, { it.minutesPerPoint }) { r, v -> r.copy(minutesPerPoint = v) },
            field("rewardSmallDays", SUGGESTIONS, "A small treat costs this many days of earning", DAYS, 1, 3650, { it.rewardSmallDays }) { r, v -> r.copy(rewardSmallDays = v) },
            field("rewardMediumDays", SUGGESTIONS, "A medium reward costs this many days of earning", DAYS, 1, 3650, { it.rewardMediumDays }) { r, v -> r.copy(rewardMediumDays = v) },
            field("rewardBigDays", SUGGESTIONS, "A big reward costs this many days of earning", DAYS, 1, 3650, { it.rewardBigDays }) { r, v -> r.copy(rewardBigDays = v) },
            field("rewardHugeDays", SUGGESTIONS, "A huge reward costs this many days of earning", DAYS, 1, 3650, { it.rewardHugeDays }) { r, v -> r.copy(rewardHugeDays = v) },

            field("moodMinDays", MOOD, "Days with a mood needed with a goal done or a thing noted, and without, before it is compared", DAYS, 1, 90, { it.moodMinDays }) { r, v -> r.copy(moodMinDays = v) },
            field("moodNotableTenths", MOOD, "A goal or thing goes with a better or lower mood from a difference of this many tenths (out of 5)", "tenths", 1, 40, { it.moodNotableTenths }) { r, v -> r.copy(moodNotableTenths = v) },
        )
    }
}

/**
 * One rule for the settings page: its [key] (the field's name), the [group] it is shown in, a
 * short [label], the [unit] it is in (empty for none), and its range. [set] keeps the value in
 * its range.
 */
data class RuleField(
    val key: String,
    val group: String,
    val label: String,
    val unit: String,
    val get: (Rules) -> Int,
    val set: (Rules, Int) -> Rules,
    val min: Int,
    val max: Int,
)
