package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/**
 * What the app says right after spending is typed in: where that leaves the user. With a monthly
 * limit on the category, what is left of it; with the budget game on, how today stands against
 * its line, which is a line to stay under and never an amount to spend. Over either, it stays
 * kind. Without both it only says what was saved.
 */
object SpendingNote {
    /**
     * The line for [entry], just saved. [category] is what it was for; [categoryCents] is what
     * its month has spent in that category, this entry included. [lineCents] is today's line in
     * the budget game, or null while the game is off or has nothing to go by, and [todayCents]
     * what today has spent, bills aside (see [MoneyGame.spent]). [money] writes cents out.
     */
    fun after(
        entry: MoneyEntry,
        today: LocalDate,
        category: MoneyCategory?,
        categoryCents: Long,
        lineCents: Long?,
        todayCents: Long,
        money: (Long) -> String,
    ): String {
        val name = category?.name?.takeIf { it.isNotBlank() }
        // Paid from a saving goal: set aside earlier, so no part of the month's limits or today's line.
        if (entry.paidFromSavings) return "${money(entry.cents)} paid from savings" + (name?.let { " · $it" } ?: "")
        val parts = mutableListOf<String>()
        val limit = category?.limitCents
        if (name != null && limit != null && Budget.monthOf(entry.day) == Budget.monthOf(today)) {
            parts += if (categoryCents <= limit) {
                "$name: ${money(limit - categoryCents)} left of ${money(limit)} this month"
            } else {
                "$name: ${money(categoryCents - limit)} over its ${money(limit)} this month"
            }
        }
        if (lineCents != null && entry.day == today && entry.billId == null) {
            parts += when {
                todayCents < lineCents -> "Still under today's line by ${money(lineCents - todayCents)}"
                todayCents == lineCents -> "Right on today's line"
                else -> "Over today's line by ${money(todayCents - lineCents)}; tomorrow is a new day"
            }
        }
        if (parts.isEmpty()) return "Spent ${money(entry.cents)}" + (name?.let { " on $it" } ?: "")
        return parts.joinToString(" · ")
    }
}
