package io.github.randomtools3377.checklist.core

/**
 * Words for the numbers set in "Points and rules", so screens say what is actually set rather
 * than the app's starting values.
 */
object RuleWords {
    /** A percent as a multiplier: 200 is "2", 150 is "1.5", 175 is "1.75". */
    fun times(percent: Int): String {
        val whole = percent / 100
        val rest = (percent % 100).toString().padStart(2, '0').trimEnd('0')
        return if (rest.isEmpty()) "$whole" else "$whole.$rest"
    }

    /** A share of the time: "half the time" at 50, "95% of the time" otherwise. */
    fun ofTheTime(percent: Int): String = if (percent == 50) "half the time" else "$percent% of the time"

    /** What an item a boss is weak to does, like "hits 2 times as hard". */
    fun weakHit(rules: Rules = Rules.current): String = "hits ${times(rules.bossWeakPercent)} times as hard"

    /** The balance check with nothing to flag: every goal done often enough, none nearly always. */
    fun inBalance(rules: Rules = Rules.current): String =
        "All in balance: over the last 4 weeks, every goal was done more than ${ofTheTime(rules.hardSharePercent)}, " +
            "and none ${ofTheTime(rules.easySharePercent)} or more."
}

/**
 * What a feature does, in a line, with the numbers set in "Points and rules" in place of the
 * starting ones.
 */
fun Feature.aboutNow(rules: Rules = Rules.current): String = when (this) {
    Feature.BOOST -> "Each week, the goal missed most lately pays ${rules.boostExtraPercent}% more."
    Feature.STREAK_BONUS ->
        "Goals pay ${rules.streakBonus1Percent}% more from a ${rules.streakBonus1Days}-day main streak, " +
            "${rules.streakBonus2Percent}% from ${rules.streakBonus2Days} and ${rules.streakBonus3Percent}% from ${rules.streakBonus3Days}."
    Feature.REPEAT_PRICE -> "A reward costs ${rules.repeatPriceRisePercent}% more for each time it was already bought that week."
    Feature.SHIELDS ->
        "Every ${rules.shieldEveryDays} days of main streak earns a shield (hold ${rules.shieldsMost} at most). " +
            "A day that would break the main streak uses one up and the streak goes on."
    else -> about
}
