package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDateTime

/**
 * The points history as a spreadsheet (CSV): one line for every change to the balance, with
 * the note on a tick where there is one. The user picks where the file goes.
 */
object Spreadsheet {
    const val HEADER = "Date,Time,What,For,Points,Note"

    fun csv(entries: List<Entry>, notes: Map<Long, String>, timeOf: (Long) -> LocalDateTime): String = buildString {
        append(HEADER).append("\r\n")
        for (entry in entries.sortedWith(compareBy({ it.day }, { it.at }))) {
            val time = timeOf(entry.at)
            val note = entry.tickId?.let { notes[it] }.orEmpty()
            append(listOf(entry.day.toString(), time.time.toString().take(5), kindText(entry), text(entry.label), entry.amount.toString(), text(note)).joinToString(",") { cell(it) })
            append("\r\n")
        }
    }

    /** What [entry] was: its kind, or for a penalty given back with its skip, that. */
    fun kindText(entry: Entry): String = if (entry.isSkipTakenBack) SKIP_TAKEN_BACK else kindText(entry.kind)

    /** What the points history calls the penalty points that come back when a skip is taken back. */
    const val SKIP_TAKEN_BACK = "Skip taken back"

    fun kindText(kind: EntryKind): String = when (kind) {
        EntryKind.TICK -> "Ticked"
        EntryKind.BONUS -> "Bonus"
        EntryKind.PURCHASE -> "Bought"
        EntryKind.RETURN -> "Returned"
        EntryKind.PENALTY -> "Penalty"
        EntryKind.REFUND -> "Penalty given back"
        EntryKind.INTEREST -> "Savings interest"
        EntryKind.PERFECT_DAY -> "Perfect day"
        EntryKind.CHALLENGE -> "Challenge completed"
        EntryKind.BET -> "Bet"
    }

    /**
     * Words the user wrote. One starting like a formula (=, +, -, @) gets a ' in front, so a
     * spreadsheet shows it as written instead of working it out.
     */
    internal fun text(words: String): String = if (words.firstOrNull() in listOf('=', '+', '-', '@', '\t')) "'$words" else words

    /** A cell, quoted when it holds a comma, a quote or a line break. */
    internal fun cell(text: String): String =
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}
