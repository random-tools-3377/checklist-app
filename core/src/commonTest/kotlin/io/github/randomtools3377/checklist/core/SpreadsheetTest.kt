package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SpreadsheetTest {
    @Test
    fun everyChangeIsALineAndCellsAreQuotedWhenNeeded() {
        val day = LocalDate(2026, 11, 9)
        val entries = listOf(
            Entry(at = 2, day = day, kind = EntryKind.PURCHASE, amount = -30, label = "Coffee, large"),
            Entry(at = 1, day = day, kind = EntryKind.TICK, amount = 5, label = "Read", tickId = 7),
        )
        val csv = Spreadsheet.csv(entries, mapOf(7L to "said \"wow\""), { LocalDateTime(2026, 11, 9, 8, 5) })
        assertEquals(
            listOf(
                Spreadsheet.HEADER,
                "2026-11-09,08:05,Ticked,Read,5,\"said \"\"wow\"\"\"",
                "2026-11-09,08:05,Bought,\"Coffee, large\",-30,",
            ),
            csv.trimEnd().split("\r\n"),
        )
    }

    @Test
    fun wordsThatLookLikeAFormulaStayWords() {
        val day = LocalDate(2026, 11, 9)
        val entries = listOf(Entry(at = 1, day = day, kind = EntryKind.TICK, amount = 5, label = "=1+1", tickId = 3))
        val csv = Spreadsheet.csv(entries, mapOf(3L to "-tired"), { LocalDateTime(2026, 11, 9, 21, 30) })
        assertEquals("2026-11-09,21:30,Ticked,'=1+1,5,'-tired", csv.trimEnd().split("\r\n")[1])
    }
}
