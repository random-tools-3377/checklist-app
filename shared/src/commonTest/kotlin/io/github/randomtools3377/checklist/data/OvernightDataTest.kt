package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Combos
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Lapses
import io.github.randomtools3377.checklist.core.Overnight
import io.github.randomtools3377.checklist.core.OvernightPaid
import io.github.randomtools3377.checklist.core.PerfectDays
import io.github.randomtools3377.checklist.core.Period
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
import kotlin.test.assertNull

/** What the day's end pays is kept for the morning card on Today, on the real database. */
class OvernightDataTest {
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

    private suspend fun dailyHabit(): Long {
        parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = "Read", icon = "📖", points = 4, repeat = Repeat.everyDay(today.minusDays(10)), target = 1, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null,
            ),
        )
        val added = today.minusDays(10).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        dao.observeGoals().first().forEach { dao.updateGoal(it.copy(createdAt = added)) }
        return dao.activeGoals().single().id
    }

    @Test
    fun whatTheDaysEndPaidIsKeptForTheMorningCard() = runTest {
        val id = dailyHabit()
        val yesterday = today.minusDays(1)
        dao.putSetting(SettingEntity(Lapses.TRACKING_SINCE, yesterday.toString()))
        dao.putSetting(SettingEntity(Lapses.CHECKED_THROUGH, yesterday.minusDays(1).toString()))
        dao.putSetting(SettingEntity(Feature.PERFECT_DAY.key, Feature.PERFECT_DAY.onValue))
        dao.putSetting(SettingEntity(Feature.COMBOS.key, Feature.COMBOS.onValue))
        // Both were last paid the day before yesterday, with a combo of 2 running.
        dao.putSetting(SettingEntity(PerfectDays.THROUGH, yesterday.minusDays(1).toString()))
        dao.putSetting(SettingEntity(Combos.THROUGH, yesterday.minusDays(1).toString()))
        dao.putSetting(SettingEntity(Combos.COUNT, "2"))
        dao.insertTick(Tick(goalId = id, stepId = null, at = 0, day = yesterday, period = Period.DAILY, periodStart = yesterday).toEntity())
        assertNull(dao.setting(Overnight.SETTING))

        parts.closeDays()

        val bonus = PerfectDays.bonus(RoomStore(db))
        val kept = Overnight.decode(dao.setting(Overnight.SETTING))
        assertEquals(OvernightPaid(today, perfect = bonus, comboDays = 3, comboPoints = Combos.pay(3)), kept)
        // Looking again the same day pays nothing more and keeps what the card shows.
        parts.closeDays()
        assertEquals(kept, Overnight.decode(dao.setting(Overnight.SETTING)))
    }

    @Test
    fun aDayThatPaidNothingKeepsNothing() = runTest {
        dailyHabit()
        dao.putSetting(SettingEntity(Lapses.TRACKING_SINCE, today.toString()))
        dao.putSetting(SettingEntity(Lapses.CHECKED_THROUGH, today.minusDays(1).toString()))
        parts.closeDays()
        assertNull(dao.setting(Overnight.SETTING))
    }

    @Test
    fun theTickCountForBadgesLeavesOutWhatIsAddedToAnAtMostGoal() = runTest {
        val id = dailyHabit()
        dao.insertTick(Tick(goalId = id, stepId = null, at = 0, day = today, period = Period.DAILY, periodStart = today).toEntity())
        // One use of an "at most" goal: a tick with no period.
        dao.insertTick(Tick(goalId = id, stepId = null, at = 0, day = today, period = null, periodStart = today).toEntity())
        assertEquals(2, dao.observeTickCount().first())
        assertEquals(1, dao.observeGoalTickCount().first())
    }
}
