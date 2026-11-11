package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate

/*
 * Bets on yourself and streak shields (extras, off to start).
 */

enum class BetState { OPEN, WON, LOST }

/** A bet that [goalId] will be ticked [times] times in the week starting [week], for [stake] points. */
data class Bet(val goalId: Long, val week: LocalDate, val times: Int, val stake: Int, val state: BetState = BetState.OPEN) {
    /** What winning pays: the stake back, twice over (see [Rules.betWinTimes]). */
    val prize: Int get() = (stake.toLong() * Rules.current.betWinTimes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** A bet this week with its goal's name and how many times it has been done. */
data class BetRow(val bet: Bet, val goal: String, val done: Int)

/**
 * Why a bet can't be placed. [DONE_ALREADY]: the goal has already been done that many times this
 * week; [OUT_OF_REACH]: what is left of the week can't hold that many.
 */
enum class BetRefusal { TOO_POOR, ALREADY, TOO_MANY, NOT_A_HABIT, DONE_ALREADY, OUT_OF_REACH }

/**
 * Betting on yourself: the stake is taken when the bet is placed. Doing the goal the number of
 * times in the week (whole times, see [count]) wins twice the stake straight away; a week that
 * ends short loses it. A bet is for more times than are done already, and no more than the rest
 * of the week can hold. One bet per goal a week, three at a time. The numbers are the user's to
 * change (see [Rules]).
 */
class Bets(private val store: Store, private val book: Book) {
    companion object {
        /** The bets, one a line: "goalId|monday|times|stake|state". */
        const val SETTING = "bets"
        val MOST_OPEN: Int get() = Rules.current.betsMostOpen
        val STAKES: List<Int> get() = Rules.current.let { listOf(it.betStake1, it.betStake2, it.betStake3) }

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

        /**
         * How many times [goal] was done in the week starting [monday], in whole times: an amount
         * counts once it is reached, not for each entry, and a goal done several times a day
         * counts a day once all of them are done.
         */
        fun count(ticks: List<Tick>, goal: Goal, monday: LocalDate): Int =
            ticks.filter { it.goalId == goal.id && it.stepId == null && it.day >= monday && it.day <= monday.plusDays(6) }
                .groupBy { it.period to it.periodStart }.values
                .sumOf { window -> timesIn(goal, window) }

        /** Whole times done in one window's [ticks]: a day once all are done, for a goal done several times a day. */
        private fun timesIn(goal: Goal, ticks: List<Tick>): Int {
            val target = goal.target.coerceAtLeast(1)
            val done = goal.timesDone(ticks).coerceAtMost(target)
            return if (goal.severalADay) (if (done >= target) 1 else 0) else done
        }

        /**
         * The most times [goal] can be done in the week starting [monday]: what is done, and what
         * is still open from [today] to Sunday.
         */
        fun reachable(ticks: List<Tick>, goal: Goal, monday: LocalDate, today: LocalDate): Int {
            val done = count(ticks, goal, monday)
            val repeat = goal.repeat ?: return done
            val sunday = monday.plusDays(6)
            val days = generateSequence(maxOf(today, monday)) { it.plusDays(1) }.takeWhile { it <= sunday }.toList()
            if (repeat.kind == RepeatKind.AFTER_DONE) {
                // Due again so many days after each time it is done.
                val first = days.firstOrNull { it >= repeat.start } ?: return done
                return done + days.filter { it >= first }.withIndex().count { (n, _) -> n % repeat.interval.coerceAtLeast(1) == 0 }
            }
            val more = days.mapNotNull { repeat.window(it) }.distinctBy { it.start }.sumOf { window ->
                val inWindow = ticks.filter { it.goalId == goal.id && it.stepId == null && it.period == window.period && it.periodStart == window.start }
                val target = goal.target.coerceAtLeast(1)
                val doneThere = goal.timesDone(inWindow).coerceAtMost(target)
                if (goal.severalADay) (if (doneThere >= target) 0 else 1) else target - doneThere
            }
            return done + more
        }

        /**
         * The counts a bet on [goal] can be placed for on [today]: more times than are done so
         * far this week, and no more than the rest of the week can hold. Empty when none.
         */
        fun room(ticks: List<Tick>, goal: Goal, today: LocalDate): IntRange {
            val monday = Bosses.monday(today)
            return (count(ticks, goal, monday) + 1)..reachable(ticks, goal, monday, today)
        }
    }

    /** Places a bet for this week, taking the stake. Null when placed, or why not. */
    suspend fun place(goalId: Long, times: Int, stake: Int): BetRefusal? = store.transaction {
        Rules.load(store)
        val goal = store.goal(goalId)?.takeIf { it.kind == GoalKind.HABIT && !it.archived } ?: return@transaction BetRefusal.NOT_A_HABIT
        val monday = Bosses.monday(book.today())
        val bets = decode(store.setting(SETTING))
        if (bets.any { it.goalId == goalId && it.week == monday }) return@transaction BetRefusal.ALREADY
        if (bets.count { it.state == BetState.OPEN } >= MOST_OPEN) return@transaction BetRefusal.TOO_MANY
        // A bet is on doing more than is done already, and no more than the week can still hold.
        val room = room(store.habitTicksSince(Days.earliestOpenDay(monday)), goal, book.today())
        if (times < room.first) return@transaction BetRefusal.DONE_ALREADY
        if (times > room.last) return@transaction BetRefusal.OUT_OF_REACH
        if (stake > book.balance()) return@transaction BetRefusal.TOO_POOR
        store.addEntry(Entry(at = book.millis(), day = book.today(), kind = EntryKind.BET, amount = -stake, goalId = goalId, label = "Bet: ${goal.name} $times times this week"))
        store.putSetting(SETTING, encode(bets + Bet(goalId, monday, times.coerceAtLeast(1), stake)))
        null
    }

    /**
     * Wins every open bet whose goal has been ticked enough, and loses those whose week has
     * ended short. Returns the bets settled now.
     */
    suspend fun settle(): List<Bet> = store.transaction {
        Rules.load(store)
        val bets = decode(store.setting(SETTING))
        if (bets.none { it.state == BetState.OPEN }) return@transaction emptyList()
        val today = book.today()
        val earliest = bets.filter { it.state == BetState.OPEN }.minOf { it.week }
        val ticks = store.habitTicksSince(Days.earliestOpenDay(earliest))
        val settled = mutableListOf<Bet>()
        val updated = bets.map { bet ->
            if (bet.state != BetState.OPEN) return@map bet
            val goal = store.goal(bet.goalId)
            val name = goal?.name ?: "a goal"
            when {
                goal != null && count(ticks, goal, bet.week) >= bet.times -> {
                    store.addEntry(Entry(at = book.millis(), day = today, kind = EntryKind.BET, amount = bet.prize, goalId = bet.goalId, label = "Bet won: $name ${bet.times} times"))
                    bet.copy(state = BetState.WON).also { settled += it }
                }
                today > bet.week.plusDays(6) -> bet.copy(state = BetState.LOST).also { settled += it }
                else -> bet
            }
        }
        // Only the last few weeks are kept.
        store.putSetting(SETTING, encode(updated.filter { it.state == BetState.OPEN || it.week >= Bosses.monday(today).minusDays(28) }))
        settled
    }

    /** Undoing a tick that won a bet takes the win back: the bet is open again. */
    suspend fun takeBackWins() = store.transaction {
        Rules.load(store)
        val bets = decode(store.setting(SETTING))
        val monday = Bosses.monday(book.today())
        if (bets.none { it.state == BetState.WON && it.week == monday }) return@transaction
        val ticks = store.habitTicksSince(Days.earliestOpenDay(monday))
        val updated = bets.map { bet ->
            if (bet.state != BetState.WON || bet.week != monday) return@map bet
            val goal = store.goal(bet.goalId)
            if (goal == null || count(ticks, goal, bet.week) >= bet.times) return@map bet
            val wins = store.entries(bet.week, book.today()).filter { it.kind == EntryKind.BET && it.goalId == bet.goalId }
            // What it won; if the rules changed since, the win of this goal's bet all the same.
            (wins.lastOrNull { it.amount == bet.prize } ?: wins.lastOrNull { it.amount > 0 && it.label.startsWith("Bet won:") })
                ?.let { store.removeEntry(it.id) }
            bet.copy(state = BetState.OPEN)
        }
        if (updated != bets) store.putSetting(SETTING, encode(updated))
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
            if (record.due == 0 && record.misses == 0 && record.skips == 0) continue
            val ok = StreakCounter.meetsTarget(record, settings)
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
