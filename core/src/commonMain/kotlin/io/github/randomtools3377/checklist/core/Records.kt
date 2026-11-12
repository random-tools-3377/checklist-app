package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

private fun line(vararg cells: String): String = cells.joinToString(",") { Spreadsheet.cell(it) } + "\r\n"

/** Words the user wrote, made safe for a spreadsheet (see [Spreadsheet.text]). */
private fun words(text: String?): String = Spreadsheet.text(text.orEmpty())

/**
 * The goal record as a spreadsheet (CSV): each goal on each day it was due, and whether it was
 * done, skipped or missed, with the reason. The day rules are those of a goal's page and the
 * review (see [GoalPage.times]). A project has a line for each step done and for a missed deadline.
 */
object GoalRecord {
    const val HEADER = "Date,Goal,Result,Reason,Note,Step"

    const val DONE = "Done"
    const val SKIPPED = "Skipped"
    const val MISSED = "Missed"

    /** Due on a day gone by with nothing recorded: from before misses were looked for. */
    const val NOT_DONE = "Not done"
    const val STEP_DONE = "Step done"

    /** Ticked toward a week, month or year that hasn't ended yet. */
    const val TICKED = "Ticked"

    private data class Row(val day: LocalDate, val position: Int, val cells: List<String>)

    /** Goals that have been removed are left out. Today's goals show once they are done or skipped. */
    fun csv(
        goals: List<Goal>,
        steps: List<Step>,
        ticks: List<Tick>,
        lapses: List<Lapse>,
        today: LocalDate,
        countsFrom: (Goal) -> LocalDate? = { it.addedOn() },
    ): String {
        val rows = mutableListOf<Row>()
        val stepNames = steps.associate { it.id to it.name }
        goals.filter { !it.archived }.sortedBy { it.position }.forEachIndexed { index, goal ->
            val ownTicks = ticks.filter { it.goalId == goal.id && it.day <= today }
            val ownLapses = lapses.filter { it.goalId == goal.id && !it.forgotten }
            fun row(day: LocalDate, result: String, lapse: Lapse? = null, note: String? = null, step: String = "") {
                rows += Row(day, index, listOf(day.toString(), words(goal.name), result, words(lapse?.let { reasonLabel(it.reason, it.reasonText) }), words(note ?: lapse?.note), words(step)))
            }
            if (goal.kind == GoalKind.PROJECT) {
                ownTicks.filter { it.stepId != null }.forEach { row(it.day, STEP_DONE, note = it.note, step = stepNames[it.stepId].orEmpty()) }
                ownLapses.filter { it.kind == LapseKind.MISS }.forEach { row(it.day, MISSED, it) }
                return@forEachIndexed
            }
            val times = GoalPage.times(goal, today, ownTicks, ownLapses, countsFrom)
            val covered = times.map { it.start }.toSet()
            for (time in times) {
                val notes = ownTicks.filter { it.stepId == null && it.periodStart == time.start && !it.note.isNullOrBlank() }.sortedBy { it.at }.joinToString("; ") { it.note!! }
                // The newest skip or miss of that time carries the reason.
                fun lapse(kind: LapseKind) = ownLapses.filter { it.kind == kind && it.periodStart == time.start }.maxByOrNull { it.at }
                when (time.state) {
                    TimeState.DONE -> row(time.day, DONE, note = notes)
                    TimeState.SKIPPED -> row(time.day, SKIPPED, lapse(LapseKind.SKIP))
                    TimeState.MISSED -> row(time.day, MISSED, lapse(LapseKind.MISS))
                    TimeState.OPEN -> if (time.day < today) row(time.day, NOT_DONE)
                }
            }
            // A to-do without a date is done on the day it was ticked. Ticks outside the times above, like
            // those of a week still going, are listed as ticked; the week is done or not when it ends.
            ownTicks.filter { it.stepId == null && it.periodStart !in covered }.groupBy { it.day }.forEach { (day, group) ->
                row(day, if (goal.repeat == null) DONE else TICKED, note = group.filter { !it.note.isNullOrBlank() }.sortedBy { it.at }.joinToString("; ") { it.note!! })
            }
        }
        return buildString {
            append(HEADER).append("\r\n")
            rows.sortedWith(compareBy({ it.day }, { it.position })).forEach { append(line(*it.cells.toTypedArray())) }
        }
    }
}

/** The mood journal as a spreadsheet (CSV): one line for each day with a check-in, oldest first. */
object MoodSheet {
    const val HEADER = "Date,Mood (1 to 5),Mood once everything was done,What played a part,Note,Later note"

    fun csv(moods: List<Mood>): String = buildString {
        append(HEADER).append("\r\n")
        for (mood in moods.sortedBy { it.day }) {
            append(
                line(
                    mood.day.toString(), mood.score.toString(), mood.after?.toString().orEmpty(),
                    words(mood.factors.joinToString("; ")), words(mood.note), words(mood.afterNote),
                ),
            )
        }
    }
}

/**
 * One month of the budget as a spreadsheet (CSV): what was spent, money in, bills, and money put
 * into and taken out of savings, oldest first. Amounts are what they do to what's left: spending,
 * bills and saving below zero, money in and money taken out of savings above it. With pay set,
 * the month's pay (so far, for the month still going) is a line of its own, so the amounts add
 * up to what's left.
 */
object BudgetSheet {
    const val HEADER = "Date,What,Category,Amount,Note"

    const val SPENT = "Spent"
    const val BILL = "Bill"
    const val RECEIVED = "Money in"
    const val SAVED = "Put into savings"
    const val TAKEN_OUT = "Taken out of savings"
    const val PAY = "Pay"
    const val PAY_SO_FAR = "Pay so far"

    /** "-12.50", "1200.00": always two decimals and a point, whatever the phone's language. */
    fun amount(cents: Long): String {
        val abs = if (cents < 0) -cents else cents
        return (if (cents < 0) "-" else "") + "${abs / 100}." + (abs % 100).toString().padStart(2, '0')
    }

    fun csv(first: LocalDate, entries: List<MoneyEntry>, categories: List<MoneyCategory>, pay: MonthPay?, now: LocalDateTime): String = buildString {
        val names = categories.associate { it.id to it.name }
        val last = Days.periodEnd(first, Period.MONTHLY)
        append(HEADER).append("\r\n")
        for (entry in entries.filter { it.day >= Days.periodStart(first, Period.MONTHLY) && it.day <= last }.sortedWith(compareBy({ it.day }, { it.at }, { it.id }))) {
            val (what, cents) = when (entry.kind) {
                MoneyKind.SPENT -> (if (entry.billId != null) BILL else SPENT) to -entry.cents
                MoneyKind.RECEIVED -> RECEIVED to entry.cents
                MoneyKind.SAVED -> SAVED to -entry.cents
                MoneyKind.TAKEN_OUT -> TAKEN_OUT to entry.cents
            }
            append(line(entry.day.toString(), what, words(entry.categoryId?.let { names[it] }), amount(cents), words(entry.note)))
        }
        if (pay != null) {
            val going = now.date <= last
            append(line(minOf(now.date, last).toString(), if (going) PAY_SO_FAR else PAY, "", amount(Budget.cents(pay.earned)), ""))
        }
    }
}
