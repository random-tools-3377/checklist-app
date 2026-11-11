package io.github.randomtools3377.checklist.core

import kotlinx.serialization.Serializable

/*
 * The character and its skills (an extra, off to start). Every goal belongs to one of six skill
 * areas. The points goals pay are the character's experience, and each goal's points are also
 * experience for its area, so the character and each skill level up. Spending and penalties
 * don't take experience away.
 */

/** The six skill areas, each with the words in a goal's name or category that suggest it. */
@Serializable
enum class SkillArea(val label: String, val icon: String, private val words: List<String>) {
    FITNESS(
        "Fitness", "🏃",
        listOf(
            "run", "jog", "gym", "walk", "step", "swim", "bike", "cycl", "yoga", "stretch", "exercis", "workout", "sport", "push",
            "squat", "plank", "train", "hike", "dance", "football", "tennis", "lift", "fitness", "pilates", "climb", "row", "skip",
        ),
    ),
    HEALTH(
        "Health", "💧",
        listOf(
            "water", "drink", "sleep", "vitamin", "pill", "medic", "floss", "teeth", "brush", "eat", "fruit", "veg", "breakfast",
            "lunch", "dinner", "diet", "health", "sugar", "alcohol", "smok", "caffeine", "coffee", "doctor", "dentist", "skin",
            "shower", "snack", "protein", "salad", "weigh", "rest", "nap",
        ),
    ),
    MIND(
        "Mind", "📚",
        listOf(
            "read", "book", "learn", "study", "language", "meditat", "journal", "mind", "course", "lesson", "podcast",
            "gratitude", "pray", "breath", "calm", "therapy", "puzzle", "spanish", "french", "german", "word", "vocab",
        ),
    ),
    HOME(
        "Home", "🏠",
        listOf(
            "clean", "tidy", "wash", "laundry", "dish", "cook", "bin", "hoover", "vacuum", "garden", "plant", "water the", "iron", "shop",
            "grocer", "home", "house", "kitchen", "fridge", "pet", "dog", "cat", "feed", "declutter", "mop", "bed", "chore", "dust",
        ),
    ),
    WORK(
        "Work", "💼",
        listOf(
            "work", "email", "inbox", "meeting", "report", "job", "client", "code", "budget", "money", "save", "invoice", "tax",
            "admin", "call", "office", "homework", "career", "cv", "spreadsheet", "deadline", "task", "bill", "bank",
        ),
    ),
    CREATIVE(
        "Creative", "🎨",
        listOf(
            "draw", "paint", "write", "music", "piano", "guitar", "sing", "photo", "craft", "knit", "sew", "design", "art", "poem",
            "blog", "film", "compose", "sketch", "creativ", "bake", "song", "story", "instrument", "violin", "drum",
        ),
    ),
    ;

    /** How strongly [text] suggests this area: the number of its words found at the start of words in it. */
    internal fun score(text: String): Int {
        val lower = " " + text.lowercase().replace(Regex("[^a-z ]"), " ") + " "
        return words.count { word -> lower.contains(" $word") }
    }

    companion object {
        /** The area a goal most likely belongs to: its category counts double. Health when nothing fits. */
        fun guess(name: String, category: String?): SkillArea {
            category?.let { c -> entries.firstOrNull { it.label.equals(c.trim(), ignoreCase = true) }?.let { return it } }
            val scored = entries.map { area -> area to area.score(name) + 2 * area.score(category.orEmpty()) }
            return scored.filter { it.second > 0 }.maxByOrNull { it.second }?.first ?: HEALTH
        }

        /** The area [goal] belongs to: the one chosen for it, or the guess. */
        fun of(goal: Goal): SkillArea = goal.skill ?: guess(goal.name, goal.category)
    }
}

/** A medal: bronze, silver or gold. */
enum class Tier(val label: String, val medal: String) { BRONZE("Bronze", "🥉"), SILVER("Silver", "🥈"), GOLD("Gold", "🥇") }

/** Where experience has got to: the level, how far into it, and how much the level takes in all. */
data class Level(val level: Int, val into: Long, val span: Long) {
    /** How far to the next level, from 0 to 1. */
    val share: Float get() = if (span <= 0) 0f else (into.toFloat() / span).coerceIn(0f, 1f)

    /** What is left to the next level. */
    val toNext: Long get() = (span - into).coerceAtLeast(0)
}

/**
 * Levels from experience. Each level takes [step] more than the one before: with a step of 50,
 * level 2 is at 50, level 3 at 150, level 5 at 500 and level 10 at 2,250.
 */
object Levels {
    /** The character: about a level a day at first, slower later. */
    const val CHARACTER_STEP = 50

    /** A skill, which only gets its own goals' points. */
    const val SKILL_STEP = 20

    /** Experience needed in all to reach [level]. */
    fun needed(level: Int, step: Int): Long = step.toLong() * level * (level - 1) / 2

    fun of(xp: Long, step: Int): Level {
        var level = 1
        while (needed(level + 1, step) <= xp) level++
        val start = needed(level, step)
        return Level(level, xp - start, needed(level + 1, step) - start)
    }

    /** A skill's medal: bronze from level 5, silver from 10, gold from 20. */
    fun skillTier(level: Int): Tier? = when {
        level >= 20 -> Tier.GOLD
        level >= 10 -> Tier.SILVER
        level >= 5 -> Tier.BRONZE
        else -> null
    }
}

/** The character's experience and level, and each skill's. */
data class CharacterSheet(val xp: Long, val level: Level, val skills: Map<SkillArea, Long>) {
    fun skillLevel(area: SkillArea): Level = Levels.of(skills[area] ?: 0, Levels.SKILL_STEP)
}

object Experience {
    /** What counts as experience: what goals pay, as ticks, finishing bonuses and challenges. */
    val KINDS = setOf(EntryKind.TICK, EntryKind.BONUS, EntryKind.CHALLENGE)

    /** The character sheet from the points history. [goals] should include removed goals. */
    fun of(entries: List<Entry>, goals: List<Goal>): CharacterSheet {
        val byId = goals.associateBy { it.id }
        val perGoal = entries.filter { it.kind in KINDS && it.goalId != null }.groupBy { it.goalId!! }
            .mapValues { (_, list) -> list.sumOf { it.amount.toLong() }.coerceAtLeast(0) }
        val skills = mutableMapOf<SkillArea, Long>()
        perGoal.forEach { (id, xp) ->
            val goal = byId[id] ?: return@forEach
            val area = SkillArea.of(goal)
            skills[area] = (skills[area] ?: 0) + xp
        }
        val xp = perGoal.values.sum()
        return CharacterSheet(xp, Levels.of(xp, Levels.CHARACTER_STEP), skills)
    }
}
