package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** How often a bill comes round: every so many weeks or months. */
@Serializable
enum class BillEvery(val one: String, val many: String) { WEEKS("week", "weeks"), MONTHS("month", "months") }

/**
 * A bill or subscription: [cents] every [count] weeks or months from [first], in a category, until
 * [until] if it ends. Each time it falls due it counts as spent that day, by itself.
 */
@Serializable
data class Bill(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val cents: Long,
    val categoryId: Long? = null,
    val every: BillEvery = BillEvery.MONTHS,
    val count: Int = 1,
    val first: LocalDate,
    val until: LocalDate? = null,
    val archived: Boolean = false,
    /** The last day it was counted up to; null before it ever was. */
    val countedTo: LocalDate? = null,
) {
    /** "every month", "every 2 weeks". */
    val howOften: String get() = if (count <= 1) "every ${every.one}" else "every $count ${every.many}"
}

/** A saving goal: [targetCents] to save up, with money put in and taken out as [MoneyEntry]s. */
@Serializable
data class SavingGoal(
    val id: Long = 0,
    val name: String,
    val icon: String,
    val targetCents: Long,
    val position: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = 0,
)

object Bills {
    /** The [n]th time (from 0) [bill] falls due: by weeks from its first day, or by months on the same date (the last day in a shorter month). */
    fun nth(bill: Bill, n: Int): LocalDate = when (bill.every) {
        BillEvery.WEEKS -> bill.first.plusDays(n * 7 * bill.count.coerceAtLeast(1))
        BillEvery.MONTHS -> {
            val months = bill.first.year * 12 + (bill.first.monthNumber - 1) + n * bill.count.coerceAtLeast(1)
            val first = LocalDate(months / 12, months % 12 + 1, 1)
            val last = Pay.monthDays(first).last().dayOfMonth
            LocalDate(first.year, first.month, minOf(bill.first.dayOfMonth, last))
        }
    }

    /** The days [bill] falls due from [from] to [to], both included, up to its end. */
    fun due(bill: Bill, from: LocalDate, to: LocalDate): List<LocalDate> {
        val end = bill.until?.let { minOf(it, to) } ?: to
        if (end < from || end < bill.first) return emptyList()
        // Skip ahead roughly, then step.
        val step = when (bill.every) {
            BillEvery.WEEKS -> 7 * bill.count.coerceAtLeast(1)
            BillEvery.MONTHS -> 28 * bill.count.coerceAtLeast(1)
        }
        var n = maxOf(0, (from.toEpochDays() - bill.first.toEpochDays()) / step - 1)
        val days = mutableListOf<LocalDate>()
        while (true) {
            val day = nth(bill, n)
            if (day > end) break
            if (day >= from) days += day
            n++
        }
        return days
    }

    /**
     * The days [bill] should be counted as spent by [today] and hasn't been: from the day after
     * [Bill.countedTo], or from [startedFrom] (when the budget started) for one never counted, so
     * a bill added with an old first date isn't counted far back.
     */
    fun toCount(bill: Bill, today: LocalDate, startedFrom: LocalDate): List<LocalDate> {
        if (bill.archived) return emptyList()
        val from = bill.countedTo?.plusDays(1) ?: maxOf(bill.first, startedFrom)
        return due(bill, from, today)
    }

    /** The next day [bill] falls due on or after [day], or null once it has ended. */
    fun next(bill: Bill, day: LocalDate): LocalDate? = if (bill.archived) null else due(bill, day, day.plusDays(400)).firstOrNull()

    /** What [bill] costs over a usual month: so much a week becomes 52 weeks over 12 months. */
    fun monthly(bill: Bill): Double = when (bill.every) {
        BillEvery.WEEKS -> bill.cents / 100.0 * 52 / 12 / bill.count.coerceAtLeast(1)
        BillEvery.MONTHS -> bill.cents / 100.0 / bill.count.coerceAtLeast(1)
    }
}

/** A saving goal with what is saved in it so far. */
data class SavingProgress(val goal: SavingGoal, val savedCents: Long) {
    val leftCents: Long get() = (goal.targetCents - savedCents).coerceAtLeast(0)
    val reached: Boolean get() = goal.targetCents > 0 && savedCents >= goal.targetCents
    val share: Float get() = if (goal.targetCents <= 0) 0f else (savedCents.toFloat() / goal.targetCents).coerceIn(0f, 1f)

    companion object {
        /** Each goal not removed, in order, with what has been put in less what was taken out. */
        fun of(goals: List<SavingGoal>, entries: List<MoneyEntry>): List<SavingProgress> {
            val saved = entries.filter { it.savingId != null }.groupBy { it.savingId }.mapValues { (_, list) ->
                list.sumOf { if (it.kind == MoneyKind.SAVED) it.cents else if (it.kind == MoneyKind.TAKEN_OUT) -it.cents else 0 }
            }
            return goals.filter { !it.archived }.sortedBy { it.position }.map { SavingProgress(it, saved[it.id] ?: 0) }
        }
    }
}
