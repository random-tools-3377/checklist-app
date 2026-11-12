package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Combos
import io.github.randomtools3377.checklist.core.EntryKind
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.PerfectDays
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.minusDays
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "I forgot to tick it" on the real database: inside a gap it ticks the day and gives the miss's
 * cost back, and the gap goes away with the last one; and a day put right gets its perfect-day
 * bonus and its place in the combo, once.
 */
class ForgottenDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val parts = SharedParts(db, NoGifs(), null)
    private val today: LocalDate = nowLocal().date

    @AfterTest
    fun close() = db.close()

    /** A daily habit paying 4 with no skips or misses allowed, added ten days ago. */
    private suspend fun read(): Long {
        parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(today.minusDays(10)), target = 1, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null, allowance = 0,
            ),
        )
        val added = today.minusDays(10).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        dao.observeGoals().first().forEach { dao.updateGoal(it.copy(createdAt = added)) }
        return dao.activeGoals().single().id
    }

    private suspend fun lastLookedThreeDaysAgo() {
        dao.putSetting(SettingEntity(Lapses.TRACKING_SINCE, today.minusDays(3).toString()))
        dao.putSetting(SettingEntity(Lapses.CHECKED_THROUGH, today.minusDays(3).toString()))
    }

    @Test
    fun aGapsMissesAreForgottenOneByOneAndTheGapGoesAwayWithTheLast() = runTest {
        read()
        lastLookedThreeDaysAgo()
        parts.closeDays()
        val gap = dao.observeUnansweredGaps().first().single()
        val misses = parts.gaps.misses(gap.toCore())
        assertEquals(listOf(today.minusDays(2), today.minusDays(1)), misses.map { it.day })
        assertEquals(-4, dao.balance())
        assertTrue(dao.observeUnexplained().first().isEmpty(), "the gap's one reason covers them")

        assertTrue(parts.lapses.forgotToTick(misses[0].id))
        assertEquals(-4 + 2 + 4, dao.balance())
        assertEquals(1, dao.observeUnansweredGaps().first().size)
        assertEquals(listOf(gap.id), dao.observeOpenPenalties().first().map { it.gapId })

        assertTrue(parts.lapses.forgotToTick(misses[1].id))
        assertTrue(dao.gaps().isEmpty())
        assertTrue(dao.observeOpenPenalties().first().isEmpty())
        assertTrue(dao.observeUnexplained().first().isEmpty())
        assertEquals(8, dao.balance())

        // The next time the app looks, nothing comes back.
        parts.closeDays()
        assertTrue(dao.gaps().isEmpty())
        assertTrue(dao.observeOpenPenalties().first().isEmpty())
        assertEquals(8, dao.balance())
    }

    @Test
    fun aDayPutRightGetsItsBonusAndItsPlaceInTheComboOnce() = runTest {
        val id = read()
        dao.updateGoal(dao.activeGoals().single().copy(allowance = 5))
        lastLookedThreeDaysAgo()
        dao.putSetting(SettingEntity(Feature.PERFECT_DAY.key, Feature.PERFECT_DAY.onValue))
        dao.putSetting(SettingEntity(Feature.COMBOS.key, Feature.COMBOS.onValue))
        // Both were last paid three days ago, with a combo of 12 running.
        dao.putSetting(SettingEntity(PerfectDays.THROUGH, today.minusDays(3).toString()))
        dao.putSetting(SettingEntity(Combos.THROUGH, today.minusDays(3).toString()))
        dao.putSetting(SettingEntity(Combos.COUNT, "12"))
        dao.insertEntry(io.github.randomtools3377.checklist.core.Entry(at = 0, day = today.minusDays(3), kind = EntryKind.PERFECT_DAY, amount = 20, label = "Combo ×12").toEntity())
        // Two days ago it was done; yesterday it wasn't ticked.
        val done = today.minusDays(2)
        dao.insertTick(Tick(goalId = id, stepId = null, at = 0, day = done, period = Period.DAILY, periodStart = done).toEntity())
        parts.closeDays()
        val yesterday = today.minusDays(1)
        suspend fun dayEnds(day: LocalDate) = dao.allEntries().filter { it.kind == EntryKind.PERFECT_DAY.name && it.day == day.toEpochDay() }.map { it.label to it.amount }
        assertEquals(listOf("Perfect day" to 10, "Combo ×13" to 20), dayEnds(done))
        assertTrue(dayEnds(yesterday).isEmpty())
        assertEquals("0", dao.setting(Combos.COUNT))
        val miss = dao.observeUnexplained().first().single()

        assertTrue(parts.lapses.forgotToTick(miss.id))

        assertEquals(listOf("Perfect day" to 10, "Combo ×14" to 20), dayEnds(yesterday))
        assertEquals("14", dao.setting(Combos.COUNT), "the combo carries on from where it was")
        val balance = dao.balance()
        // Nothing is paid twice: not by the app looking again, and not by saying it again.
        parts.closeDays()
        assertTrue(!parts.lapses.forgotToTick(miss.id))
        assertEquals(2, dayEnds(yesterday).size)
        assertEquals(balance, dao.balance())
    }
}
