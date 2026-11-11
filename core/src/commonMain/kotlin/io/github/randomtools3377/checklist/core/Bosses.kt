package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlin.random.Random

/*
 * Bosses and items (an extra, off to start). Every tick earns an item from its goal's skill
 * area. Each Monday a boss arrives, weaker to two areas: items from those hit it twice as hard.
 * A boss beaten pays a bonus; one still standing on Sunday just leaves, nothing lost, and the
 * items are kept. All the bosses and items are original to this app.
 */

/** The item each skill area's ticks earn. */
enum class Item(val area: SkillArea, val icon: String, val label: String) {
    BOLT(SkillArea.FITNESS, "⚡", "Energy bolt"),
    POTION(SkillArea.HEALTH, "🧪", "Potion"),
    SCROLL(SkillArea.MIND, "📜", "Scroll"),
    HAMMER(SkillArea.HOME, "🔨", "Hammer"),
    CLIP(SkillArea.WORK, "📎", "Clip"),
    PAINT(SkillArea.CREATIVE, "🎨", "Paint bomb"),
    ;

    companion object {
        fun of(area: SkillArea): Item = entries.first { it.area == area }
    }
}

/** A boss: its name, look and the two areas it is weaker to. */
enum class Boss(val icon: String, val title: String, val weakTo: Set<SkillArea>) {
    COUCH_LUMP("🛋️", "The Couch Lump", setOf(SkillArea.FITNESS, SkillArea.HOME)),
    FOG("🌫️", "The Fog of Forgetting", setOf(SkillArea.MIND, SkillArea.HEALTH)),
    CLUTTER_BEAST("📦", "The Clutter Beast", setOf(SkillArea.HOME, SkillArea.WORK)),
    SNOOZE_GOBLIN("😴", "The Snooze Goblin", setOf(SkillArea.HEALTH, SkillArea.FITNESS)),
    DEADLINE_DRAGON("🐉", "The Deadline Dragon", setOf(SkillArea.WORK, SkillArea.MIND)),
    BLANK_PAGE("📄", "The Blank Page", setOf(SkillArea.CREATIVE, SkillArea.MIND)),
    GRUMBLE_CLOUD("⛈️", "The Grumble Cloud", setOf(SkillArea.CREATIVE, SkillArea.HEALTH)),
    INBOX_HYDRA("🐙", "The Inbox Hydra", setOf(SkillArea.WORK, SkillArea.CREATIVE)),
}

/** This week's boss: who, its health, the damage done so far, and whether it is beaten. */
data class BossFight(val week: LocalDate, val boss: Boss, val health: Int, val damage: Int) {
    val left: Int get() = (health - damage).coerceAtLeast(0)
    val beaten: Boolean get() = damage >= health
    val share: Float get() = if (health <= 0) 1f else (left.toFloat() / health).coerceIn(0f, 1f)
}

/** This week's fight and the items in hand. */
data class BossView(val fight: BossFight, val items: Map<Item, Int>) {
    val itemCount: Int get() = items.values.sum()
    val wouldDeal: Int get() = Bosses.damage(items, fight.boss)
}

/** What an attack did. */
data class Attack(val dealt: Int, val beaten: Boolean, val paid: Int)

object Bosses {
    /** What an item does to a boss; twice that for an area it is weaker to (see [Rules]). */
    val HIT: Int get() = Rules.current.bossHit

    /** What beating a boss pays. */
    val REWARD: Int get() = Rules.current.bossReward

    /** The settings: this week's fight as "monday:damage"; items used as "FITNESS:3,MIND:2"; when items started; the week paid for. */
    const val FIGHT = "boss_fight"
    const val USED = "items_used"
    const val ITEMS_FROM = "items_from"
    const val PAID = "boss_paid"

    fun monday(day: LocalDate): LocalDate = day.minusDays(day.dayOfWeek.isoDayNumber - 1)

    /** The boss of the week starting [monday]: picked at random, the same all week, never the same twice running. */
    fun of(monday: LocalDate): Boss {
        val week = monday.toEpochDays().toLong() / 7
        val pick = Random(week * 131 + 17).nextInt(Boss.entries.size)
        val last = Random((week - 1) * 131 + 17).nextInt(Boss.entries.size)
        return Boss.entries[if (pick == last) (pick + 1) % Boss.entries.size else pick]
    }

    /**
     * The boss's health: what doing about 70% of the week's goals would deal with plain items,
     * so it can be beaten with an ordinary good week, and at least 50 (see [Rules]).
     * [expectedTicks] is how many ticks the week's goals ask for.
     */
    fun health(expectedTicks: Int): Int {
        val rules = Rules.current
        val health = expectedTicks.toLong() * rules.bossHit * rules.bossHealthPercent / 100
        return health.coerceIn(rules.bossMinHealth.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    /** What [item] does to [boss]. */
    fun hit(item: Item, boss: Boss): Int {
        val rules = Rules.current
        return if (item.area in boss.weakTo) (rules.bossHit.toLong() * rules.bossWeakPercent / 100).toInt() else rules.bossHit
    }

    /** What using [items] does to [boss]. */
    fun damage(items: Map<Item, Int>, boss: Boss): Int = items.entries.sumOf { (item, count) -> hit(item, boss) * count }

    /** How many ticks the goals ask for in the week starting [monday]. */
    fun expectedTicks(goals: List<Goal>, monday: LocalDate): Int =
        goals.filter { !it.archived && it.kind == GoalKind.HABIT }.sumOf { goal ->
            val repeat = goal.repeat ?: return@sumOf 0
            (0 until 7).count { repeat.window(monday.plusDays(it))?.start == monday.plusDays(it) } * goal.target
        }

    /** Items in hand: one for every tick since items started, less those used. */
    fun inventory(ticks: List<Tick>, goals: List<Goal>, since: Long, used: Map<Item, Int>): Map<Item, Int> {
        val byId = goals.associateBy { it.id }
        val earned = ticks.filter { it.at >= since }
            .mapNotNull { tick -> byId[tick.goalId]?.let { Item.of(SkillArea.of(it)) } }
            .groupingBy { it }.eachCount()
        return Item.entries.associateWith { ((earned[it] ?: 0) - (used[it] ?: 0)).coerceAtLeast(0) }.filterValues { it > 0 }
    }

    fun encodeUsed(used: Map<Item, Int>): String = used.filterValues { it > 0 }.entries.joinToString(",") { "${it.key.name}:${it.value}" }

    fun decodeUsed(text: String?): Map<Item, Int> = text.orEmpty().split(",").mapNotNull { part ->
        val (name, count) = part.split(":").takeIf { it.size == 2 } ?: return@mapNotNull null
        val item = Item.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
        count.toIntOrNull()?.takeIf { it > 0 }?.let { item to it }
    }.toMap()

    /** The damage done this week, from "monday:damage"; 0 for another week. */
    fun damageIn(text: String?, monday: LocalDate): Int {
        val (week, damage) = text?.split(":")?.takeIf { it.size == 2 } ?: return 0
        return if (week == monday.toString()) damage.toIntOrNull() ?: 0 else 0
    }
}

/**
 * Lucky ticks (an extra): now and then a tick pays extra. One tick in ten is lucky: half of
 * those pay their points again, the other half open a chest of 5 to 15 points (the numbers are
 * the user's to change, see [Rules]). It never takes anything away; the same tick is always as
 * lucky, and undoing it takes the extra back too.
 */
object Lucky {
    sealed interface Luck {
        data class Double(val points: Int) : Luck
        data class Chest(val points: Int) : Luck
    }

    /** The luck of a tick: [seed] identifies it (its goal, day and which tick of the day it is). */
    fun of(seed: Long, points: Int): Luck? {
        val rules = Rules.current
        val least = rules.luckyChestMin
        val most = maxOf(least, rules.luckyChestMax)
        val random = Random(seed * 7919 + 13)
        if (random.nextInt(rules.luckyOneIn.coerceAtLeast(1)) != 0) return null
        return if (random.nextBoolean() && points > 0) Luck.Double(points) else Luck.Chest(least + random.nextInt(most - least + 1))
    }
}

/** What identifies a tick for its luck: the goal, the day, and which tick of the day it is. */
fun luckSeed(goalId: Long, day: LocalDate, nth: Int): Long = goalId * 1_000_003L + day.toEpochDays().toLong() * 101L + nth
