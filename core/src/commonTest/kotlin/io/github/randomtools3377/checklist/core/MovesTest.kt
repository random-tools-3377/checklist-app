package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovesTest {
    private val monday = LocalDate(2026, 11, 9)
    private val wednesday = monday.plusDays(2)
    private val friday = monday.plusDays(4)

    // Monday, Wednesday and Friday.
    private val swim = Goal(
        id = 1, kind = GoalKind.HABIT, name = "Swim", icon = "🏊", points = 5,
        repeat = Repeat(RepeatKind.WEEKDAYS, LocalDate(2026, 1, 1), weekdays = setOf(1, 3, 5)),
    )

    @Test
    fun aDayMovesWithinItsWeekToADayItIsNotAlreadyDue() {
        // From Wednesday, seen on Tuesday: Tuesday, Thursday, Saturday or Sunday.
        assertEquals(listOf(monday.plusDays(1), monday.plusDays(3), monday.plusDays(5), monday.plusDays(6)), Moves.choices(swim, wednesday, monday.plusDays(1)))
        // Not into the past, and not a day that has gone.
        assertTrue(Moves.choices(swim, monday, wednesday).isEmpty())
        // Not from a day it isn't due.
        assertTrue(Moves.choices(swim, monday.plusDays(1), monday).isEmpty())
        // Goals due across a week don't move.
        val walks = swim.copy(repeat = Repeat(RepeatKind.TIMES_PER, LocalDate(2026, 1, 1), per = Period.WEEKLY))
        assertTrue(Moves.choices(walks, wednesday, monday).isEmpty())
    }

    @Test
    fun aMovedDayIsDueOnItsNewDayAndNotOnItsOldOne() {
        val thursday = monday.plusDays(3)
        val repeat = Moves.move(swim.repeat!!, wednesday, thursday)
        assertNull(repeat.window(wednesday))
        assertEquals(Window(thursday, thursday, Period.DAILY), repeat.window(thursday))
        assertEquals(thursday, repeat.nextDue(monday.plusDays(1)))
        assertEquals(wednesday, Moves.movedFrom(repeat, thursday))

        // Moved on again, it still came from Wednesday; moved back, the move goes away.
        val again = Moves.move(repeat, thursday, monday.plusDays(5))
        assertEquals(listOf(Move(wednesday, monday.plusDays(5))), again.moves)
        assertEquals(emptyList(), Moves.move(again, monday.plusDays(5), wednesday).moves)
    }

    @Test
    fun aMovedDayNotDoneIsAMissOnTheNewDayAndTodaysListFollowsIt() {
        val thursday = monday.plusDays(3)
        val moved = swim.copy(repeat = Moves.move(swim.repeat!!, wednesday, thursday))
        val wed = TodayList.build(wednesday, listOf(moved), emptyList(), emptyList(), 0)
        assertTrue(wed.dueToday.isEmpty())
        val thu = TodayList.build(thursday, listOf(moved), emptyList(), emptyList(), 0)
        assertEquals("Swim", thu.dueToday.single().goal.name)
        val records = StreakCounter.records(monday, friday, listOf(moved), emptyList(), emptyList())
        assertEquals(listOf(1, 0, 0, 1, 1), records.map { it.due })
    }

    @Test
    fun movesAreStoredWithTheRepeat() {
        val repeat = Moves.move(swim.repeat!!, wednesday, monday.plusDays(3))
        val back = RepeatText.decode(RepeatText.encode(repeat))
        assertEquals(repeat, back)
        // A repeat stored before moves existed still reads.
        assertEquals(swim.repeat, RepeatText.decode(Json.encodeToString(Repeat.serializer(), swim.repeat!!).replace(",\"moves\":[]", "")))
        assertEquals(listOf(Move(wednesday, monday.plusDays(3))), Moves.keep(repeat.moves, monday.plusDays(30)))
        assertTrue(Moves.keep(repeat.moves, monday.plusDays(200)).isEmpty())
    }
}
