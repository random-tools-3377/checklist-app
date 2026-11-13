package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/**
 * What was paid when the day ended, kept for the morning card on Today. [day] is the new day
 * (the one the card shows on); the perfect day and the combo are yesterday's.
 */
data class OvernightPaid(
    val day: LocalDate,
    val perfect: Int = 0,
    /** The combo's length in days, and what it paid. */
    val comboDays: Int = 0,
    val comboPoints: Int = 0,
    val interest: Int = 0,
) {
    val isEmpty: Boolean get() = perfect == 0 && comboPoints == 0 && interest == 0
}

/**
 * The morning card: what yesterday came to, and what was paid for it overnight. It shows the
 * first time Today opens on a new day and closes with one tap.
 */
object Overnight {
    /** What was paid when the last day ended, as "2026-11-12|10|3|6|3" (see [encode]). */
    const val SETTING = "overnight_paid"

    /** The day whose morning card was closed. */
    const val SEEN = "overnight_seen"

    /**
     * What [entries], just paid as the day turned to [today], add to [kept]. The perfect day and
     * the combo count only when they are yesterday's; interest is the week's. What was kept for
     * an earlier day is dropped.
     */
    fun paid(today: LocalDate, entries: List<Entry>, kept: OvernightPaid? = null): OvernightPaid {
        val yesterday = today.minusDays(1)
        val before = kept?.takeIf { it.day == today } ?: OvernightPaid(today)
        val combo = entries.lastOrNull { it.kind == EntryKind.PERFECT_DAY && it.day == yesterday && it.label.startsWith(Combos.LABEL) }
        val perfect = entries.filter { it.kind == EntryKind.PERFECT_DAY && it.day == yesterday && !it.label.startsWith(Combos.LABEL) }.sumOf { it.amount }
        return before.copy(
            perfect = before.perfect + perfect,
            comboDays = combo?.label?.removePrefix(Combos.LABEL)?.toIntOrNull() ?: before.comboDays,
            comboPoints = before.comboPoints + (combo?.amount ?: 0),
            interest = before.interest + entries.filter { it.kind == EntryKind.INTEREST }.sumOf { it.amount },
        )
    }

    fun encode(paid: OvernightPaid): String = listOf(paid.day, paid.perfect, paid.comboDays, paid.comboPoints, paid.interest).joinToString("|")

    fun decode(text: String?): OvernightPaid? {
        val parts = text?.split("|")?.takeIf { it.size == 5 } ?: return null
        val day = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return null
        val numbers = parts.drop(1).map { it.toIntOrNull() ?: return null }
        return OvernightPaid(day, numbers[0], numbers[1], numbers[2], numbers[3])
    }

    /**
     * The card's words on [today], or null when there is nothing to say: nothing was due
     * yesterday and nothing was paid. [yesterday] is yesterday's record, [paid] what was kept
     * (ignored unless it is today's), [streak] the main streak now.
     *
     * "Yesterday: 5 of 5 done · perfect day +10 · combo of 3 +6 · interest +3 · streak now 13",
     * or after a thin day "Yesterday: 2 of 5. Today is a fresh start."
     */
    fun line(today: LocalDate, yesterday: DayRecord?, paid: OvernightPaid?, settings: StreakSettings, streak: Int): String? {
        val mine = paid?.takeIf { it.day == today && !it.isEmpty }
        val record = yesterday?.takeIf { it.day == today.minusDays(1) && it.due > 0 }
        if (record == null && mine == null) return null
        val extras = buildList {
            if (mine != null) {
                if (mine.perfect > 0) add("perfect day +${mine.perfect}")
                if (mine.comboPoints > 0) add("combo of ${mine.comboDays} +${mine.comboPoints}")
                if (mine.interest > 0) add("interest +${mine.interest}")
            }
        }
        if (record == null) return "Overnight: " + extras.joinToString(" · ")
        val counted = StreakCounter.meetsTarget(record, settings)
        if (!counted) {
            // A thin day: no tally of what went wrong, just the count and a fresh start.
            val start = "Yesterday: ${record.done} of ${record.due}. Today is a fresh start."
            return if (extras.isEmpty()) start else start + " Overnight: " + extras.joinToString(" · ")
        }
        return (listOf("Yesterday: ${record.done} of ${record.due} done") + extras + listOfNotNull("streak now $streak".takeIf { streak > 0 })).joinToString(" · ")
    }
}
