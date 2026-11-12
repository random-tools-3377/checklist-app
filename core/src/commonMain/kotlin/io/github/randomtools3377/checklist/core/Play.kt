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

/** One of the day's quests. [id] is its place in the day's list, which is kept all day (see [Quests.SETTING_DAY]). */
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
    /** What a quest pays (see [Rules]). */
    val REWARD: Int get() = Rules.current.questReward

    /** What the "everything due by 8 pm" quest pays. */
    val BIG_REWARD: Int get() = Rules.current.questBigReward

    /** The quests already paid, as "2026-11-11:0,2" for that day. */
    const val SETTING_PAID = "quests_paid"

    /**
     * The day's quests, kept the first time they are made, so adding, moving or reordering goals
     * later in the day doesn't change them: the day on the first line, then a quest a line.
     */
    const val SETTING_DAY = "quests_day"

    private val NOON = LocalTime(12, 0)
    private val EVENING = LocalTime(20, 0)

    /**
     * Up to three quests for [day] from the goals [due] then, the same all day: picked at
     * random, but the same random for the same day.
     */
    fun of(day: LocalDate, due: List<Goal>): List<Quest> {
        if (due.isEmpty()) return emptyList()
        val reward = REWARD
        val bigReward = BIG_REWARD
        val random = Random(day.toEpochDays().toLong() * 31 + 7)
        val possible = mutableListOf<(Int) -> Quest>()
        if (due.size >= 2) {
            val early = if (due.size >= 4) 3 else 2
            possible += { id -> Quest(id, QuestKind.EARLY, "Tick $early goals before noon", early, reward, by = NOON) }
            possible += { id -> Quest(id, QuestKind.ALL_BY, "Finish everything due by 8 pm", due.size, bigReward, by = EVENING) }
        }
        val goal = due[random.nextInt(due.size)]
        possible += { id -> Quest(id, QuestKind.GOAL, "Do ${goal.icon} ${goal.name}", 1, reward, goalId = goal.id) }
        SkillArea.entries.filter { area -> due.count { SkillArea.of(it) == area } >= 2 }.takeIf { it.isNotEmpty() }?.let { areas ->
            val area = areas[random.nextInt(areas.size)]
            val count = due.count { SkillArea.of(it) == area }
            possible += { id -> Quest(id, QuestKind.AREA, "Do all $count ${area.icon} ${area.label} goals", count, reward, area = area) }
        }
        val points = due.sumOf { it.points * it.target }
        if (points >= 15) {
            val aim = ((points * 0.6) / 5).toInt().coerceAtLeast(2) * 5
            possible += { id -> Quest(id, QuestKind.POINTS, "Earn $aim points from goals", aim, reward, by = null) }
        }
        return possible.shuffled(random).take(3).mapIndexed { index, make -> make(index) }
    }

    /**
     * How far along [quest] is: [rows] are today's goals, [ticks] today's ticks with [timeOf]
     * giving their time, [earned] what goals have paid today. Times are on the user's day, which
     * ends at [dayEndHour]: a tick after midnight that still belongs to the day is late, not early.
     * Only a goal really done counts: a skipped one is never done, so a quest that asks for it,
     * for all of its area or for everything due can't be finished while it stays skipped.
     */
    fun progress(quest: Quest, rows: List<HabitRow>, ticks: List<Tick>, timeOf: (Long) -> LocalTime, earned: Int, dayEndHour: Int = 0): QuestProgress {
        fun second(at: Long) = Days.secondOfDay(timeOf(at), dayEndHour)
        // Adding to an "at most" goal isn't a tick.
        @Suppress("NAME_SHADOWING") val ticks = AtMost.withoutUses(ticks)
        val by = quest.by?.let { Days.secondOfDay(it, dayEndHour) }
        val inArea = rows.filter { SkillArea.of(it.goal) == quest.area }
        val done = when (quest.kind) {
            QuestKind.EARLY -> ticks.filter { by == null || second(it.at) < by }.map { it.goalId }.distinct().size
            QuestKind.GOAL -> if (rows.any { it.goal.id == quest.goalId && it.done }) 1 else 0
            QuestKind.AREA -> inArea.count { it.done }
            QuestKind.ALL_BY -> rows.count { it.done }
            QuestKind.POINTS -> earned
        }
        // Everything due is however many are due now; the quest itself is kept all day.
        val shownQuest = if (quest.kind == QuestKind.ALL_BY) quest.copy(target = rows.size) else quest
        val complete = when (quest.kind) {
            // Everything done, and the last of it ticked in time.
            QuestKind.ALL_BY -> rows.isNotEmpty() && rows.all { it.done } &&
                (by == null || (ticks.maxOfOrNull { second(it.at) } ?: 0) <= by)
            // Every goal of the area: also when one of them was taken off today's list since.
            QuestKind.AREA -> done >= quest.target || (inArea.isNotEmpty() && inArea.all { it.done })
            else -> done >= quest.target
        }
        return QuestProgress(shownQuest, done.coerceAtMost(shownQuest.target), complete)
    }

    /**
     * The day's quests to show: a quest naming a goal that isn't on today's list any more
     * (removed, moved to another day, or private while the lock is on) is left out.
     */
    fun shown(quests: List<Quest>, rows: List<HabitRow>): List<Quest> =
        quests.filter { it.kind != QuestKind.GOAL || rows.any { row -> row.goal.id == it.goalId } }

    fun encodeDay(day: LocalDate, quests: List<Quest>): String =
        (listOf(day.toString()) + quests.map { q ->
            listOf(q.id, q.kind.name, q.target, q.reward, q.goalId ?: "", q.area?.name ?: "", q.by ?: "", q.text.replace('\n', ' ')).joinToString("|")
        }).joinToString("\n")

    /** The quests kept for [day]; null when none are kept for it. */
    fun decodeDay(text: String?, day: LocalDate): List<Quest>? {
        val lines = text?.lines() ?: return null
        if (lines.firstOrNull() != day.toString()) return null
        val quests = lines.drop(1).mapNotNull { line ->
            val p = line.split("|", limit = 8).takeIf { it.size == 8 } ?: return@mapNotNull null
            Quest(
                id = p[0].toIntOrNull() ?: return@mapNotNull null,
                kind = QuestKind.entries.firstOrNull { it.name == p[1] } ?: return@mapNotNull null,
                text = p[7],
                target = p[2].toIntOrNull() ?: return@mapNotNull null,
                reward = p[3].toIntOrNull() ?: return@mapNotNull null,
                goalId = p[4].toLongOrNull(),
                area = SkillArea.entries.firstOrNull { it.name == p[5] },
                by = p[6].takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
            ).takeIf { q ->
                when (q.kind) {
                    QuestKind.EARLY, QuestKind.ALL_BY -> q.by != null
                    QuestKind.GOAL -> q.goalId != null
                    QuestKind.AREA -> q.area != null
                    QuestKind.POINTS -> true
                }
            }
        }
        return quests.takeIf { it.isNotEmpty() }
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
 * [PER_STEP] points for each, up to [MOST] (see [Rules]). A day with something left undone, or
 * with a skip made on it (never a perfect day), ends the combo; a day with nothing due and no
 * skip neither adds to it nor ends it.
 */
class Combos(private val store: Store, private val book: Book) {
    companion object {
        val PER_STEP: Int get() = Rules.current.comboPerDay
        val MOST: Int get() = Rules.current.comboMostDays
        const val COUNT = "combo_count"
        const val THROUGH = "combo_through"

        /**
         * The first day combos count from: the day after the last one that ended while they were
         * off, so a day put right later (see [DayBack]) doesn't join a combo from a time they
         * were off. Not set for someone who has had them on since before this was kept.
         */
        const val SINCE = "combo_since"

        /** What a combo's line in the points history starts with, before its number of days. */
        const val LABEL = "Combo ×"

        /** What a combo of [combo] days pays. */
        fun pay(combo: Int): Int = combo.coerceAtMost(MOST) * PER_STEP
    }

    /** Pays for every day that ended since the last time, if combos are on. Returns what was paid. */
    suspend fun pay(): List<Entry> = store.transaction {
        Rules.load(store)
        val yesterday = book.today().minusDays(1)
        val through = store.setting(THROUGH)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (!Feature.isOn(store, Feature.COMBOS)) {
            // A day that ended while combos were off was never looked at, so it ends the combo:
            // turned on again, it starts from 1.
            if (through == null || through < yesterday) {
                store.putSetting(COUNT, "0")
                store.putSetting(SINCE, yesterday.plusDays(1).toString())
            }
            store.putSetting(THROUGH, yesterday.toString())
            return@transaction emptyList()
        }
        if (through == null) {
            store.putSetting(THROUGH, yesterday.toString())
            store.putSetting(SINCE, yesterday.plusDays(1).toString())
            return@transaction emptyList()
        }
        var combo = store.setting(COUNT)?.toIntOrNull() ?: 0
        val paid = mutableListOf<Entry>()
        val day = through.plusDays(1)
        if (day <= yesterday) {
            val goals = store.activeGoals()
            val ticks = store.habitTicksSince(Days.earliestOpenDay(day))
            // Skips made earlier in a week, month or year that ends on one of these days count too:
            // a goal they cover drops out of its last day, so they don't end the combo there.
            val lapses = store.allLapses(Days.earliestOpenDay(day), yesterday)
            for (record in StreakCounter.records(day, yesterday, goals, ticks, lapses)) {
                // Nothing due, and nothing skipped either: it neither adds to the combo nor ends it.
                if (record.due == 0 && record.skips == 0) continue
                if (record.perfect) {
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
