package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.GoalRecord
import io.github.randomtools3377.checklist.core.Lapse
import io.github.randomtools3377.checklist.core.LapseKind
import io.github.randomtools3377.checklist.core.MoneyEntry
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reason
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.minusDays
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The spreadsheets written from what is in the database: the goal record, the mood journal and a month of the budget. */
class SheetsDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, NoGifs())
    private val today = LocalDate(2026, 11, 12)

    @AfterTest
    fun close() = db.close()

    private fun lines(csv: String) = csv.trimEnd().split("\r\n")

    @Test
    fun theGoalRecordIsWrittenFromTheGoalsTicksAndMisses() = runTest {
        val start = today.minusDays(2)
        val id = dao.insertGoal(Goal(kind = GoalKind.HABIT, name = "Read", icon = "📖", points = 5, repeat = Repeat.everyDay(start)).toEntity())
        dao.insertTick(Tick(goalId = id, stepId = null, at = 1, day = start, period = Period.DAILY, periodStart = start, note = "two chapters").toEntity())
        val missed = today.minusDays(1)
        dao.insertLapses(
            listOf(
                Lapse(goalId = id, kind = LapseKind.MISS, day = missed, period = Period.DAILY, periodStart = missed, at = 2, overLimit = false, reason = Reason.ILL, label = "Read").toEntity(),
            ),
        )
        assertEquals(
            listOf(GoalRecord.HEADER, "2026-11-10,Read,Done,,two chapters,", "2026-11-11,Read,Missed,Ill,,"),
            lines(repository.goalRecordSpreadsheet(today)),
        )
    }

    @Test
    fun theMoodJournalIsWrittenWithBothCheckIns() = runTest {
        repository.setMood(today, 2, "slow start")
        repository.setMoodAfter(today, 4)
        val rows = lines(repository.moodSpreadsheet())
        assertEquals(2, rows.size)
        assertEquals("2026-11-12,2,4,,slow start,", rows[1])
    }

    @Test
    fun aMonthOfTheBudgetIsWrittenWithItsCategories() = runTest {
        repository.startBudget()
        val food = dao.moneyCategories().first { it.name == "Food" }.id
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 1_250, categoryId = food, note = "lunch", day = today, at = 1))
        repository.saveMoney(MoneyEntry(kind = MoneyKind.RECEIVED, cents = 2_000, note = "gift", day = today.minusDays(1), at = 2))
        repository.saveMoney(MoneyEntry(kind = MoneyKind.SPENT, cents = 900, categoryId = food, day = LocalDate(2026, 10, 30), at = 3))
        assertEquals(
            listOf("Date,What,Category,Amount,Note", "2026-11-11,Money in,,20.00,gift", "2026-11-12,Spent,Food,-12.50,lunch"),
            lines(repository.budgetSpreadsheet(today, LocalDateTime(2026, 11, 12, 12, 0))),
        )
    }
}
