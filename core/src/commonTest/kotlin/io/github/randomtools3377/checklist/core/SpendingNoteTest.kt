package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SpendingNoteTest {
    private val today = LocalDate(2026, 11, 12)
    private val food = MoneyCategory(id = 1, name = "Food", icon = "🍎", position = 0, limitCents = 20_000)
    private val fun_ = MoneyCategory(id = 2, name = "Fun", icon = "🎉", position = 1)
    private fun money(cents: Long) = "£" + if (cents % 100 == 0L) "${cents / 100}" else "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    private fun spent(cents: Long, category: MoneyCategory?, day: LocalDate = today) =
        MoneyEntry(id = 9, kind = MoneyKind.SPENT, cents = cents, categoryId = category?.id, day = day)

    @Test
    fun aCategoryWithALimitSaysWhatIsLeftOfIt() {
        assertEquals("Food: £42 left of £200 this month", SpendingNote.after(spent(1_200, food), today, food, 15_800, null, 1_200, ::money))
        assertEquals("Food: £0 left of £200 this month", SpendingNote.after(spent(1_200, food), today, food, 20_000, null, 1_200, ::money))
        assertEquals("Food: £12.50 over its £200 this month", SpendingNote.after(spent(1_200, food), today, food, 21_250, null, 1_200, ::money))
        // Dated in another month: "this month" wouldn't be true.
        assertEquals("Spent £12 on Food", SpendingNote.after(spent(1_200, food, LocalDate(2026, 10, 30)), today, food, 1_200, null, 0, ::money))
    }

    @Test
    fun withTheBudgetGameItSaysHowTodayStandsAgainstItsLine() {
        assertEquals("Still under today's line by £6", SpendingNote.after(spent(900, fun_), today, fun_, 900, 1_500, 900, ::money))
        assertEquals("Right on today's line", SpendingNote.after(spent(900, fun_), today, fun_, 900, 900, 900, ::money))
        assertEquals("Over today's line by £4; tomorrow is a new day", SpendingNote.after(spent(900, fun_), today, fun_, 900, 1_500, 1_900, ::money))
        // Dated yesterday: today's line isn't what it touched.
        assertEquals("Spent £9 on Fun", SpendingNote.after(spent(900, fun_, today.minusDays(1)), today, fun_, 900, 1_500, 0, ::money))
        // Both a limit and the line.
        assertEquals(
            "Food: £42 left of £200 this month · Still under today's line by £3",
            SpendingNote.after(spent(1_200, food), today, food, 15_800, 1_500, 1_200, ::money),
        )
    }

    @Test
    fun withNeitherItOnlySaysWhatWasSaved() {
        assertEquals("Spent £9 on Fun", SpendingNote.after(spent(900, fun_), today, fun_, 900, null, 900, ::money))
        assertEquals("Spent £9", SpendingNote.after(spent(900, null), today, null, 0, null, 900, ::money))
        val fromSavings = MoneyEntry(kind = MoneyKind.TAKEN_OUT, cents = 900, categoryId = 1, day = today, savingId = 3, spentFromSavings = true)
        assertEquals("£9 paid from savings · Food", SpendingNote.after(fromSavings, today, food, 15_800, 1_500, 0, ::money))
    }

    @Test
    fun usualAmountsAreTheOnesEnteredMostOftenThenMostRecently() {
        var id = 1L
        fun tick(amount: Double?) = Tick(id = id, goalId = 1, stepId = null, at = id++, day = today, period = Period.DAILY, periodStart = today, amount = amount)
        assertEquals(emptyList(), Amounts.usual(listOf(tick(null))))
        // Oldest first: 20 twice, 10 three times, 5 once, 7.5 once and newest.
        val ticks = listOf(tick(20.0), tick(10.0), tick(20.0), tick(10.0), tick(5.0), tick(null), tick(10.0), tick(7.5))
        assertEquals(listOf(10.0, 20.0, 7.5), Amounts.usual(ticks))
        // Only the latest 20 entries are looked at.
        val many = listOf(tick(3.0), tick(3.0), tick(3.0)) + (1..20).map { tick(if (it % 2 == 0) 1.0 else 2.0) }
        assertEquals(listOf(1.0, 2.0), Amounts.usual(many))
    }
}
