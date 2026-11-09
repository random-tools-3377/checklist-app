package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber

/**
 * Savings interest: at the end of each week (Sunday), a positive balance grows by a small
 * percentage, rounded down, paid on the Monday after. It counts from the first week the app
 * looked, never further back.
 */
class Interest(private val store: Store, private val book: Book) {
    companion object {
        /** The percentage, as a whole number; "0" turns it off. */
        const val PERCENT = "interest_percent"
        const val DEFAULT_PERCENT = 2

        /** The last Sunday interest was worked out for. */
        const val THROUGH = "interest_through"

        /** The percentage while the feature is on; off (0) until the user turns it on. */
        suspend fun percent(store: Store): Int = store.setting(PERCENT)?.toIntOrNull()?.coerceIn(0, 100) ?: 0
    }

    /** Pays the interest of every week that ended since the last time. Returns what was paid. */
    suspend fun pay(): List<Entry> = store.transaction {
        val today = book.today()
        val lastSunday = today.minusDays(today.dayOfWeek.isoDayNumber)
        val through = store.setting(THROUGH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (through == null) {
            store.putSetting(THROUGH, lastSunday.toString())
            return@transaction emptyList()
        }
        val percent = percent(store)
        val paid = mutableListOf<Entry>()
        var sunday = through.plusDays(7)
        while (sunday <= lastSunday) {
            val balance = store.balanceOn(sunday)
            val amount = if (percent > 0 && balance > 0) balance * percent / 100 else 0
            if (amount > 0) {
                val entry = Entry(at = book.millis(), day = sunday.plusDays(1), kind = EntryKind.INTEREST, amount = amount, label = "$percent% on $balance")
                paid += entry.copy(id = store.addEntry(entry))
            }
            sunday = sunday.plusDays(7)
        }
        if (lastSunday > through) store.putSetting(THROUGH, lastSunday.toString())
        paid
    }
}
