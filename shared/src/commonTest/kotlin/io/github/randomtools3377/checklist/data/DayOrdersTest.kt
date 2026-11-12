package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Busy
import io.github.randomtools3377.checklist.core.MorningPlan
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.minusDays
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the widgets, the quick settings tile and the morning plan read: the same next goal as Today. */
class DayOrdersTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class MemoryGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val repository = Repository(db, MemoryGifs())
    private val day = LocalDate(2026, 11, 11)
    private val twoPm = LocalDateTime(day, LocalTime(14, 0))

    @AfterTest
    fun close() = db.close()

    private suspend fun addHabit(name: String, private: Boolean = false, minutes: Int? = null): Long {
        repository.saveHabit(
            HabitDraft(
                id = 0, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(LocalDate(2026, 10, 1)),
                target = 1, bonus = 0, minutes = minutes, at = null, finishBy = null, finishDay = null, private = private,
            ),
        )
        return dao.activeGoals().single { it.name == name }.id
    }

    private suspend fun tick(goalId: Long, on: LocalDate, hour: Int) {
        val at = LocalDateTime(on, LocalTime(hour, 0)).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        dao.insertTick(TickEntity(goalId = goalId, stepId = null, at = at, day = on.toEpochDay(), period = Period.DAILY.name, periodStart = on.toEpochDay()))
    }

    private suspend fun next() = DayOrders.load(dao, twoPm).order.next?.row?.goal?.name

    @Test
    fun theNextGoalFollowsTheChosenOrderAndTheUsualTimes() = runTest {
        addHabit("Read")
        val walk = addHabit("Walk")
        addHabit("Tidy")
        // Walk is usually done at 10 in the morning, so by 2 pm it is falling behind: it comes first.
        (1..5).forEach { tick(walk, day.minusDays(it), hour = 10) }
        assertEquals("Walk", next())
        // In the user's own order, the first in their list comes first.
        dao.putSetting(SettingEntity(SETTING_TODAY_ORDER, ORDER_OWN))
        assertEquals("Read", next())
        val loaded = DayOrders.load(dao, twoPm)
        assertEquals(listOf("Read", "Walk", "Tidy"), loaded.order.planned.map { it.row.goal.name })
        // The morning plan's "First:" is the same goal.
        assertEquals("Read", MorningPlan.of(loaded.view, loaded.order)?.first?.row?.goal?.name)
    }

    @Test
    fun aGoalDoneIsNoLongerNextAndNothingIsNextOnceAllAreDone() = runTest {
        val read = addHabit("Read")
        val walk = addHabit("Walk")
        dao.putSetting(SettingEntity(SETTING_TODAY_ORDER, ORDER_OWN))
        tick(read, day, hour = 9)
        assertEquals("Walk", next())
        tick(walk, day, hour = 10)
        assertNull(next())
        assertNull(MorningPlan.of(DayOrders.load(dao, twoPm).view, DayOrders.load(dao, twoPm).order))
    }

    @Test
    fun aLockedPrivateGoalIsLeftOut() = runTest {
        addHabit("Journal", private = true)
        addHabit("Read")
        dao.putSetting(SettingEntity(SETTING_TODAY_ORDER, ORDER_OWN))
        assertEquals("Journal", next())
        dao.putSetting(SettingEntity(LOCK_PRIVATE, "1"))
        assertEquals("Read", next())
    }

    @Test
    fun theCalendarIsOnlyReadWhileItIsTurnedOnAndFromTheCalendarsPicked() = runTest {
        addHabit("Read", minutes = 30)
        val meeting = Busy(LocalDateTime(day, LocalTime(14, 15)), LocalDateTime(day, LocalTime(15, 15)), "Meeting")
        var asked: Set<Long>? = null
        val calendar: suspend (Set<Long>, LocalDate) -> List<Busy> = { ids, _ ->
            asked = ids
            listOf(meeting)
        }
        assertTrue(DayOrders.load(dao, twoPm, calendar).order.busy.isEmpty())
        assertNull(asked)
        dao.putSetting(SettingEntity(SETTING_CALENDAR, "1"))
        dao.putSetting(SettingEntity(SETTING_CALENDAR_IDS, "3,8"))
        val loaded = DayOrders.load(dao, twoPm, calendar)
        assertEquals(setOf(3L, 8L), asked)
        assertEquals(listOf(meeting), loaded.order.busy)
        // The half hour of reading is planned around the meeting: done by 3:30, not 2:30.
        assertEquals(LocalDateTime(day, LocalTime(15, 30)), loaded.order.planned.single().time)
        // A calendar that can't be read counts as empty.
        assertTrue(DayOrders.load(dao, twoPm) { _, _ -> error("not allowed") }.order.busy.isEmpty())
    }
}
