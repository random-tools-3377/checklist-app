package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/*
 * Streak shields (an extra, off to start), and what is left of bets on yourself.
 */

/**
 * A bet's state. Bets are no longer in the app: [CANCELLED] is a bet that was still open then,
 * and has had its stake given back (see [Bets.giveBackOpen]).
 */
enum class BetState { OPEN, WON, LOST, CANCELLED }

/** A bet, from before bets were removed, that [goalId] would be ticked [times] times in the week starting [week], for [stake] points. */
data class Bet(val goalId: Long, val week: LocalDate, val times: Int, val stake: Int, val state: BetState = BetState.OPEN)

/**
 * Bets on yourself are no longer in the app. The bets placed before are still kept in the
 * setting [SETTING], so a backup from before reads as it always did; the only thing done with
 * them is that a bet still open gives its stake back, once. Past bet entries stay in the points
 * history as they were.
 */
class Bets(private val store: Store, private val book: Book) {
    companion object {
        /** The bets, one a line: "goalId|monday|times|stake|state". */
        const val SETTING = "bets"

        /** What the points history says for a stake given back. */
        const val STAKE_BACK = "Bet stake back: bets are no longer in the app"

        fun decode(text: String?): List<Bet> = text.orEmpty().lines().mapNotNull { line ->
            val parts = line.split("|").takeIf { it.size == 5 } ?: return@mapNotNull null
            Bet(
                goalId = parts[0].toLongOrNull() ?: return@mapNotNull null,
                week = runCatching { LocalDate.parse(parts[1]) }.getOrNull() ?: return@mapNotNull null,
                times = parts[2].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null,
                stake = parts[3].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null,
                state = BetState.entries.firstOrNull { it.name == parts[4] } ?: return@mapNotNull null,
            )
        }

        fun encode(bets: List<Bet>): String = bets.joinToString("\n") { "${it.goalId}|${it.week}|${it.times}|${it.stake}|${it.state.name}" }
    }

    /**
     * Gives every open bet its stake back, with an entry in the points history, and marks it
     * cancelled so it never pays or gives back twice. When the app starts and after an import.
     * Returns what was given back.
     */
    suspend fun giveBackOpen(): Int = store.transaction {
        val bets = decode(store.setting(SETTING))
        val open = bets.filter { it.state == BetState.OPEN }
        if (open.isEmpty()) return@transaction 0
        val day = book.today()
        for (bet in open) {
            store.addEntry(Entry(at = book.millis(), day = day, kind = EntryKind.BET, amount = bet.stake, goalId = bet.goalId, label = STAKE_BACK))
        }
        store.putSetting(SETTING, encode(bets.map { if (it.state == BetState.OPEN) it.copy(state = BetState.CANCELLED) else it }))
        open.sumOf { it.stake }
    }
}

/** The main streak with shields: its length, the shields held, and the days a shield saved. */
data class Shielded(val main: Int, val held: Int, val saved: List<LocalDate>)

/**
 * Streak shields: every 30 days of main streak earns one, holding 2 at most (see [Rules]). A
 * day that would break the main streak uses one up instead, and the streak goes on (that day
 * doesn't add to it). Worked out from the days themselves, counting from [from], when shields
 * were turned on.
 */
object Shields {
    val EVERY: Int get() = Rules.current.shieldEveryDays.coerceAtLeast(1)
    val MOST: Int get() = Rules.current.shieldsMost.coerceAtLeast(0)

    /** When shields were turned on: the day they count from. */
    const val FROM = "shields_from"

    fun of(records: List<DayRecord>, from: LocalDate, today: LocalDate, settings: StreakSettings): Shielded {
        // The streak as it stood before shields started, counted the usual way.
        var main = StreakCounter.count(records.filter { it.day < from }, from.minusDays(1), settings).done
        var held = 0
        val every = EVERY
        val most = MOST
        val saved = mutableListOf<LocalDate>()
        for (record in records.filter { it.day in from..today }.sortedBy { it.day }) {
            // Nothing due and nothing missed, or everything due skipped: neither counts nor breaks it.
            val ok = StreakCounter.mainDay(record, settings) ?: continue
            when {
                ok -> {
                    main++
                    if (main % every == 0) held = (held + 1).coerceAtMost(most)
                }
                // Today still going doesn't break it yet.
                record.day == today -> Unit
                held > 0 -> {
                    held--
                    saved += record.day
                }
                else -> main = 0
            }
        }
        return Shielded(main, held, saved)
    }

    /**
     * Every streak on [today] as Today shows them: with shields on, counting from [from], the
     * main one is the one shields keep going. [from] is null while shields are off.
     */
    fun streaks(records: List<DayRecord>, from: LocalDate?, today: LocalDate, settings: StreakSettings): Streaks {
        val plain = StreakCounter.count(records, today, settings)
        return if (from == null) plain else plain.copy(done = of(records, from, today, settings).main)
    }
}
