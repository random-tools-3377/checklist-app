package io.github.randomtools3377.checklist.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineLayoutTest {
    private fun span(fromHour: Int, fromMinute: Int, minutes: Int) =
        TimelineSpan(fromHour * 60 + fromMinute, fromHour * 60 + fromMinute + minutes)

    @Test
    fun aBoxIsAsLongAsTheGoalTakesWithALeastLength() {
        assertEquals(90, TimelineLayout.length(90))
        assertEquals(30, TimelineLayout.length(null))
        assertEquals(30, TimelineLayout.length(5))
    }

    @Test
    fun aBoxStaysWithinTheHoursShown() {
        // 6 am to midnight: a 2-hour goal at 11 pm is cut short at midnight.
        assertEquals(TimelineSpan(23 * 60, 24 * 60), TimelineLayout.span(23 * 60, 120, 6 * 60, 24 * 60))
        // One at 11:45 pm still gets the least length, moved up to fit.
        assertEquals(TimelineSpan(23 * 60 + 30, 24 * 60), TimelineLayout.span(23 * 60 + 45, null, 6 * 60, 24 * 60))
        assertEquals(TimelineSpan(7 * 60, 7 * 60 + 45), TimelineLayout.span(7 * 60, 45, 6 * 60, 24 * 60))
    }

    @Test
    fun boxesApartEachTakeTheWholeWidth() {
        val slots = TimelineLayout.place(listOf(span(7, 0, 30), span(9, 0, 60)))
        assertEquals(listOf(TimelineSlot(0, 1), TimelineSlot(0, 1)), slots)
    }

    @Test
    fun oneEndingAsAnotherStartsDoesNotOverlap() {
        assertEquals(listOf(TimelineSlot(0, 1), TimelineSlot(0, 1)), TimelineLayout.place(listOf(span(7, 0, 30), span(7, 30, 30))))
    }

    @Test
    fun boxesCloseTogetherSitSideBySide() {
        // 7:00 for half an hour and 7:15 for half an hour overlap; so do two at the same time.
        assertEquals(listOf(TimelineSlot(0, 2), TimelineSlot(1, 2)), TimelineLayout.place(listOf(span(7, 0, 30), span(7, 15, 30))))
        assertEquals(
            listOf(TimelineSlot(0, 3), TimelineSlot(1, 3), TimelineSlot(2, 3)),
            TimelineLayout.place(listOf(span(8, 0, 30), span(8, 0, 30), span(8, 0, 30))),
        )
    }

    @Test
    fun aColumnIsUsedAgainOnceItIsFree() {
        // A long one beside two short ones in a row: two columns, not three.
        val slots = TimelineLayout.place(listOf(span(9, 0, 120), span(9, 0, 30), span(10, 0, 30)))
        assertEquals(listOf(TimelineSlot(0, 2), TimelineSlot(1, 2), TimelineSlot(1, 2)), slots)
    }

    @Test
    fun theOrderGivenIsKeptAndNoTwoOverlappingBoxesShareAColumn() {
        val spans = listOf(span(10, 0, 30), span(7, 0, 180), span(9, 30, 60), span(8, 0, 30), span(8, 15, 30), span(12, 0, 30))
        val slots = TimelineLayout.place(spans)
        assertEquals(spans.size, slots.size)
        for (a in spans.indices) for (b in spans.indices) {
            if (a < b && spans[a].start < spans[b].end && spans[b].start < spans[a].end) {
                assertTrue(slots[a].column != slots[b].column, "$a and $b overlap")
                assertEquals(slots[a].columns, slots[b].columns)
            }
        }
        assertTrue(slots.all { it.column < it.columns })
        // The one at noon is on its own.
        assertEquals(TimelineSlot(0, 1), slots[5])
        // A calendar event counts like a goal: the 7–10 one shares with everything in it.
        assertEquals(3, slots[1].columns)
    }
}
