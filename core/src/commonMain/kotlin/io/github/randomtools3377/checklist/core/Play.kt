package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlin.random.Random

/*
 * Daily and weekly play (extras, off to start): quests each day, combos of perfect days, and a
 * race against last week. They pay points like any bonus; experience still only comes from
 * what goals pay.
 */

enum class QuestKind { EARLY, GOAL, AREA, ALL_BY, POINTS }

/** One of the day's quests. [id] is its place in the day's list. */
data class Quest(
    val id: Int,
    val kind: QuestKind,
    val text: String,
    val target: Int,
    val reward: Int,
    val goalId: Long? = null,
    val area: SkillArea? = null,
    val by: LocalTime? = null,
)

/** How far along a quest is. */
data class QuestProgress(val quest: Quest, val done: Int, val complete: Boolean)

/** The quests of [day], each with how far along it is. */
data class DayQuests(val day: LocalDate, val list: List<QuestProgress>)

object Quests {
    /** What a quest pays. */
    const val REWARD = 5

    /** The quests already paid, as "2026-11-11:0,2" for that day. */
    const val SETTING_PAID = "quests_paid"

    private val NOON = LocalTime(12, 0)
    private val EVENING = LocalTime(20, 0)

    /**
     * Up to three quests for [day] from the goals [due] then, the same all day: picked at
     * random, but the same random for the same day.
     */
    fun of(day: LocalDate, due: List<Goal>): List<Quest> {
        if (due.isEmpty()) return emptyList()
        val random = Random(day.toEpochDays().toLong() * 31 + 7)
        val possible = mutableListOf<(Int) -> Quest>()
        if (due.size >= 2) {
            val early = if (due.size >= 4) 3 else 2
            possible += { id -> Quest(id, QuestKind.EARLY, "Tick $early goals before noon", early, REWARD, by = NOON) }
            possible += { id -> Quest(id, QuestKind.ALL_BY, "Finish everything due by 8 pm", due.size, REWARD * 2, by = EVENING) }
        }
        val goal = due[random.nextInt(due.size)]
        possible += { id -> Quest(id, QuestKind.GOAL, "Do ${goal.icon} ${goal.name}", 1, REWARD, goalId = goal.id) }
        SkillArea.entries.filter { area -> due.count { SkillArea.of(it) == area } >= 2 }.takeIf { it.isNotEmpty() }?.let { areas ->
            val area = areas[random.nextInt(areas.size)]
            val count = due.count { SkillArea.of(it) == area }
            possible += { id -> Quest(id, QuestKind.AREA, "Do all $count ${area.icon} ${area.label} goals", count, REWARD, area = area) }
        }
        val points = due.sumOf { it.points * it.target }
        if (points >= 15) {
            val aim = ((points * 0.6) / 5).toInt().coerceAtLeast(2) * 5
            possible += { id -> Quest(id, QuestKind.POINTS, "Earn $aim points from goals", aim, REWARD, by = null) }
        }
        return possible.shuffled(random).take(3).mapIndexed { index, make -> make(index) }
    }

    /**
     * How far along [quest] is: [rows] are today's goals, [ticks] today's ticks with [timeOf]
     * giving their time, [earned] what goals have paid today.
     */
    fun progress(quest: Quest, rows: List<HabitRow>, ticks: List<Tick>, timeOf: (Long) -> LocalTime, earned: Int): QuestProgress {
        val done = when (quest.kind) {
            QuestKind.EARLY -> ticks.filter { timeOf(it.at) < quest.by!! }.map { it.goalId }.distinct().size
            QuestKind.GOAL -> if (rows.any { it.goal.id == quest.goalId && it.done }) 1 else 0
            QuestKind.AREA -> rows.count { SkillArea.of(it.goal) == quest.area && it.done }
            QuestKind.ALL_BY -> rows.count { it.done }
            QuestKind.POINTS -> earned
        }
        val complete = when (quest.kind) {
            // Everything done, and the last of it ticked in time.
            QuestKind.ALL_BY -> rows.isNotEmpty() && rows.all { it.done } &&
                (ticks.maxOfOrNull { timeOf(it.at) } ?: LocalTime(0, 0)) <= quest.by!!
            else -> done >= quest.target
        }
        return QuestProgress(quest, done.coerceAtMost(quest.target), complete)
    }

    fun paidOn(text: String?, day: LocalDate): Set<Int> {
        val (on, ids) = text?.split(":")?.takeIf { it.size == 2 } ?: return emptySet()
        if (on != day.toString()) return emptySet()
        return ids.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun encodePaid(day: LocalDate, ids: Set<Int>): String = "$day:" + ids.sorted().joinToString(",")
}

/**
 * Combos: every perfect day in a row (everything due done, nothing skipped) adds one, and pays
 * [PER_STEP] points for each, up to [MOST]. A day with something left undone ends the combo;
 * a day with nothing due neither adds to it nor ends it.
 */
class Combos(private val store: Store, private val book: Book) {
    companion object {
        const val PER_STEP = 2
        const val MOST = 10
        const val COUNT = "combo_count"
        const val THROUGH = "combo_through"

        /** What a combo of [combo] days pays. */
        fun pay(combo: Int): Int = combo.coerceAtMost(MOST) * PER_STEP
    }

    /** Pays for every day that ended since the last time, if combos are on. Returns what was paid. */
    suspend fun pay(): List<Entry> = store.transaction {
        val yesterday = book.today().minusDays(1)
        if (!Feature.isOn(store, Feature.COMBOS)) {
            store.putSetting(THROUGH, yesterday.toString())
            return@transaction emptyList()
        }
        val through = store.setting(THROUGH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (through == null) {
            store.putSetting(THROUGH, yesterday.toString())
            return@transaction emptyList()
        }
        var combo = store.setting(COUNT)?.toIntOrNull() ?: 0
        val paid = mutableListOf<Entry>()
        val day = through.plusDays(1)
        if (day <= yesterday) {
            val goals = store.activeGoals()
            val ticks = store.habitTicksSince(Days.earliestOpenDay(day))
            val lapses = store.allLapses(day, yesterday)
            for (record in StreakCounter.records(day, yesterday, goals, ticks, lapses)) {
                if (record.due == 0) continue
                if (record.done >= record.due && record.skips == 0) {
                    combo++
                    val entry = Entry(at = book.millis(), day = record.day, kind = EntryKind.PERFECT_DAY, amount = pay(combo), label = "Combo ×$combo")
                    paid += entry.copy(id = store.addEntry(entry))
                } else {
                    combo = 0
                }
            }
            store.putSetting(COUNT, combo.toString())
            store.putSetting(THROUGH, yesterday.toString())
        }
        paid
    }
}

/** This week against last week, by the same point in the week. */
data class RaceStanding(val thisWeek: Int, val lastWeek: Int) {
    val ahead: Int get() = thisWeek - lastWeek
}

object Race {
    private val EARNED = setOf(EntryKind.TICK, EntryKind.BONUS, EntryKind.REFUND, EntryKind.PERFECT_DAY, EntryKind.CHALLENGE)

    /**
     * Points earned from Monday until [nowMillis] on [today], against last week from its Monday
     * until the same moment a week before.
     */
    fun of(entries: List<Entry>, today: LocalDate, nowMillis: Long): RaceStanding {
        val monday = today.minusDays(today.dayOfWeek.isoDayNumber - 1)
        val weekMillis = 7L * 24 * 60 * 60 * 1000
        val thisWeek = entries.filter { it.kind in EARNED && it.day >= monday && it.day <= today && it.at <= nowMillis }.sumOf { it.amount }
        val lastWeek = entries.filter {
            it.kind in EARNED && it.day >= monday.minusDays(7) && it.day <= today.minusDays(7) && it.at <= nowMillis - weekMillis
        }.sumOf { it.amount }
        return RaceStanding(thisWeek, lastWeek)
    }
}
