package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Alert
import io.github.randomtools3377.checklist.core.AlertKind
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.Entry
import io.github.randomtools3377.checklist.core.EntryKind
import io.github.randomtools3377.checklist.core.EveningCheck
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.PointsName
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.StreakTarget
import io.github.randomtools3377.checklist.core.Tick
import io.github.randomtools3377.checklist.core.TickResult
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.plusDays
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What is at stake, read from the database as it stands: reminders, the evening check, the note after a tick, and Sunday's summary. */
class AtStakesDataTest {
    private val db = openTestDatabase()
    private val dao = db.dao()

    private class NoGifs : GifStore {
        override fun read(name: String): ByteArray? = null
        override fun write(name: String, bytes: ByteArray) {}
        override fun keepOnly(names: Set<String>) {}
    }

    private val parts = SharedParts(db, NoGifs(), null)

    /** The user's day now: the day ends at midnight here. */
    private val today: LocalDate = Days.dayOf(nowLocal(), 0)
    private val noon = LocalDateTime(today, LocalTime(12, 0))

    @AfterTest
    fun close() = db.close()

    /** A daily habit that has been there for a month. */
    private suspend fun habit(name: String, private: Boolean = false, target: Int = 1): Long {
        parts.repository.saveHabit(
            HabitDraft(
                id = 0, name = name, icon = "⭐", points = 5, repeat = Repeat.everyDay(today.minusDays(40)), target = target, bonus = 0,
                minutes = null, at = null, finishBy = null, finishDay = null, private = private,
            ),
        )
        val added = today.minusDays(40).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        dao.observeGoals().first().forEach { dao.updateGoal(it.copy(createdAt = added)) }
        return dao.activeGoals().single { it.name == name }.id
    }

    private suspend fun tickOn(goalId: Long, day: LocalDate) {
        dao.insertTick(Tick(goalId = goalId, stepId = null, at = 0, day = day, period = Period.DAILY, periodStart = day).toEntity())
    }

    private suspend fun set(key: String, value: String) = dao.putSetting(SettingEntity(key, value))

    private suspend fun goal(id: Long) = dao.activeCoreGoals().single { it.id == id }

    private fun alert(goalId: Long, day: LocalDate = today, kind: AlertKind = AlertKind.ON_TIME) = Alert(goalId, day, LocalDateTime(day, LocalTime(12, 0)), kind)

    @Test
    fun aReminderSaysWhatIsAtStakeAsThingsStandNow() = runTest {
        val a = habit("A")
        val b = habit("B")
        val c = habit("C")
        // Everything done on each of the last three days: a streak of 3 through yesterday.
        for (back in 1..3) listOf(a, b, c).forEach { tickOn(it, today.minusDays(back)) }

        var writer = stakeWriter(dao, noon)
        assertEquals(today, writer.day)
        assertEquals(3, writer.standing.streak)
        assertEquals(3, writer.standing.left)
        // At 90%, all three are needed: one reminder alone promises nothing.
        assertNull(writer.reminder(goal(a), alert(a)))

        assertTrue(parts.book.tickHabit(a) is TickResult.Ticked)
        assertTrue(parts.book.tickHabit(b) is TickResult.Ticked)
        writer = stakeWriter(dao, noon)
        assertEquals("This one makes today count for your streak: day 4", writer.reminder(goal(c), alert(c)))
        assertEquals("This one makes today count for your streak: day 4", writer.reminder(goal(c), alert(c, kind = AlertKind.AGAIN)))
        // A reminder handed over ahead for tomorrow can't know how tomorrow will stand.
        assertNull(writer.reminder(goal(c), alert(c, day = today.plusDays(1))))
        // A late nudge keeps its own words.
        assertNull(writer.reminder(goal(c), alert(c, kind = AlertKind.NUDGE)))
        // Done already: nothing.
        assertNull(writer.reminder(goal(a), alert(a)))

        // With a target of one goal a day, today counts already: it is simply the last one.
        set(SETTING_STREAK_TARGET, StreakTarget.COUNT.name)
        set(SETTING_STREAK_VALUE, "1")
        assertEquals("Last one for today", stakeWriter(dao, noon).reminder(goal(c), alert(c)))
    }

    @Test
    fun theReviewsLockLeavesTheStreakOutAndAPrivateGoalSaysNothing() = runTest {
        val a = habit("A")
        val secret = habit("Secret", private = true)
        for (back in 1..3) listOf(a, secret).forEach { tickOn(it, today.minusDays(back)) }
        assertTrue(parts.book.tickHabit(a) is TickResult.Ticked)
        assertEquals("This one makes today count for your streak: day 4", stakeWriter(dao, noon).reminder(goal(secret), alert(secret)))
        assertEquals("Day 4 of your streak", stakeWriter(dao, noon).morning())

        // The review behind the lock: no streak anywhere, only what is left.
        set(LOCK_REVIEW, "1")
        assertEquals("Last one for today", stakeWriter(dao, noon).reminder(goal(secret), alert(secret)))
        assertNull(stakeWriter(dao, noon).morning())
        assertNull(stakeWriter(dao, noon).evening())
        set(LOCK_REVIEW, "0")

        // Private goals behind the lock: its own reminder says nothing at stake.
        set(LOCK_PRIVATE, "1")
        val locked = stakeWriter(dao, noon)
        assertTrue(locked.hidden(goal(secret)))
        assertNull(locked.reminder(goal(secret), alert(secret)))
    }

    @Test
    fun aLockedPrivateGoalStillOpenMeansAnotherIsNotTheLastOne() = runTest {
        val a = habit("A")
        habit("Secret", private = true)
        set(SETTING_STREAK_TARGET, StreakTarget.COUNT.name)
        set(SETTING_STREAK_VALUE, "1")
        tickOn(a, today.minusDays(1))
        set(LOCK_PRIVATE, "1")
        // The count is the real one: with the private goal still open, A isn't the last.
        val writer = stakeWriter(dao, noon)
        assertEquals(2, writer.standing.left)
        assertEquals("This one makes today count for your streak: day 2", writer.reminder(goal(a), alert(a)))
        assertTrue(parts.book.tickHabit(a) is TickResult.Ticked)
        assertEquals(1, stakeWriter(dao, noon).standing.left)
    }

    @Test
    fun aTickMadeOutsideTheAppAnswersBack() = runTest {
        PointsName.current = PointsName.DEFAULT
        val a = habit("Water")
        val b = habit("Read")
        val c = habit("Walk")
        assertTrue(tickAnswerOn(null) && tickAnswerOn("1"))
        assertFalse(tickAnswerOn("0"))

        var result = parts.book.tickHabit(a)
        var answer = tickAnswer(dao, a, result)!!
        assertEquals("Done: ⭐ Water, +5", answer.title)
        assertEquals("2 left today", answer.text)
        assertEquals("Done: ⭐ Water, +5 · 2 left today", answer.line)
        // With its switch off, only the first part is left for lighting the edges.
        assertEquals("Done: ⭐ Water, +5", tickAnswer(dao, a, result, stakes = false)!!.line)

        result = parts.book.tickHabit(b)
        assertEquals("1 left today", tickAnswer(dao, b, result)!!.text)
        result = parts.book.tickHabit(c)
        answer = tickAnswer(dao, c, result)!!
        // The third of three at 90% is the one that makes today count; no streak was running, so it is day 1.
        assertEquals("Today now counts for your streak: day 1 · That's everything for today", answer.text)
        set(LOCK_REVIEW, "1")
        assertEquals("That's everything for today", tickAnswer(dao, c, result)!!.text)

        // Nothing was ticked: nothing to say.
        assertNull(tickAnswer(dao, c, parts.book.tickHabit(c)))
        assertNull(tickAnswer(dao, c, TickResult.NotDue))
    }

    @Test
    fun aGoalDoneSeveralTimesSaysHowFarAlongItIs() = runTest {
        val glasses = habit("Water", target = 3)
        habit("Read")
        val first = parts.book.tickHabit(glasses)
        assertEquals("1 of 3 · 2 left today", tickAnswer(dao, glasses, first)!!.text)
        parts.book.tickHabit(glasses)
        val last = parts.book.tickHabit(glasses)
        assertTrue((last as TickResult.Ticked).finished)
        assertEquals("1 left today", tickAnswer(dao, glasses, last)!!.text)
    }

    @Test
    fun theAnswerForALockedPrivateGoalNamesNothingAndSaysNothingMore() = runTest {
        val secret = habit("Secret", private = true)
        habit("Read")
        set(LOCK_PRIVATE, "1")
        val answer = tickAnswer(dao, secret, parts.book.tickHabit(secret))!!
        assertEquals("Done: 🔒 A private goal, +5", answer.title)
        assertNull(answer.text)
    }

    @Test
    fun theEveningCheckSaysWhatIsAtStakeForTodayOnly() = runTest {
        val a = habit("A")
        val b = habit("B")
        for (back in 1..3) listOf(a, b).forEach { tickOn(it, today.minusDays(back)) }
        set(SETTING_STREAK_TARGET, StreakTarget.COUNT.name)
        set(SETTING_STREAK_VALUE, "1")
        set(EveningCheck.SETTING, EveningCheck.encode(LocalTime(21, 0)))
        val ahead = eveningChecks(dao, LocalDateTime(today, LocalTime(20, 0)), LocalDateTime(today.plusDays(2), LocalTime(20, 0)))
        assertEquals(listOf(today, today.plusDays(1)), ahead.map { it.list.day })
        assertEquals("1 more keeps your 3-day streak", ahead[0].list.stake)
        assertEquals(listOf("1 more keeps your 3-day streak", "⭐ A", "⭐ B"), ahead[0].list.lines())
        assertEquals("2 still open today", ahead[0].list.title)
        // Tomorrow's, handed over ahead, can't know how tomorrow will stand.
        assertNull(ahead[1].list.stake)
        assertEquals(listOf("⭐ A", "⭐ B"), ahead[1].list.lines())

        // One done: today counts, and the next win is the perfect day.
        assertTrue(parts.book.tickHabit(a) is TickResult.Ticked)
        assertEquals("1 more for a perfect day", eveningList(dao, today)?.stake)
        // Behind the review's lock nothing is added.
        set(LOCK_REVIEW, "1")
        assertNull(eveningList(dao, today)?.stake)
    }

    // The last whole week, so nothing depends on the day the test runs on.
    private val sunday: LocalDate = today.minusDays(today.dayOfWeek.isoDayNumber)
    private val monday: LocalDate = sunday.minusDays(6)

    private suspend fun earnOn(goalId: Long, day: LocalDate) {
        tickOn(goalId, day)
        dao.insertEntry(Entry(at = 1, day = day, kind = EntryKind.TICK, amount = 5, goalId = goalId, label = "x").toEntity())
    }

    @Test
    fun sundaysSummaryComparesWithTheWeekBeforeOnlyWhenItIsUp() = runTest {
        PointsName.current = PointsName.DEFAULT
        assertNull(weeklyWords(dao, sunday))
        val a = habit("A")
        // 3 of 7 the week before, 5 of 7 this week.
        for (i in 0 until 3) earnOn(a, monday.minusDays(7).plusDays(i))
        for (i in 0 until 5) earnOn(a, monday.plusDays(i))
        val up = weeklyWords(dao, sunday)!!
        assertTrue(up.startsWith("5 of 7 done (71%, up from 42%) · +25 points"), up)
        // The race is an extra: nothing about it while it is off.
        assertFalse("🏁" in up)
        set(Feature.RACE.key, Feature.RACE.onValue)
        assertTrue("🏁 beat last week by 10" in weeklyWords(dao, sunday)!!)

        // Seen from the week before, which had less: no comparison and nothing about the race.
        for (i in 0 until 6) earnOn(a, monday.minusDays(14).plusDays(i))
        val down = weeklyWords(dao, sunday.minusDays(7))!!
        assertTrue(down.startsWith("3 of 7 done (42%) · +15 points"), down)
        assertFalse("🏁" in down || "last week" in down || "behind" in down, down)

        // Behind the review's lock it only says the review is ready.
        set(LOCK_REVIEW, "1")
        assertEquals(WEEKLY_LOCKED, weeklyWords(dao, sunday))
    }

    @Test
    fun aLockedPrivateGoalIsLeftOutOfSundaysRace() = runTest {
        PointsName.current = PointsName.DEFAULT
        val a = habit("A")
        val secret = habit("Secret", private = true)
        set(Feature.RACE.key, Feature.RACE.onValue)
        for (i in 0 until 2) earnOn(a, monday.minusDays(7).plusDays(i))
        for (i in 0 until 3) earnOn(a, monday.plusDays(i))
        for (i in 0 until 4) earnOn(secret, monday.plusDays(i))
        assertTrue("🏁 beat last week by 25" in weeklyWords(dao, sunday)!!)
        set(LOCK_PRIVATE, "1")
        val locked = weeklyWords(dao, sunday)!!
        assertTrue("🏁 beat last week by 5" in locked, locked)
        assertFalse("Secret" in locked)
    }
}
