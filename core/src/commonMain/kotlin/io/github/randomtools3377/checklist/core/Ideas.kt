package io.github.randomtools3377.checklist.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/*
 * Ideas to level up a skill area (with the character). Three kinds: stepping up a goal the user
 * already has (a little longer, a little more, once more a week), so an area keeps growing;
 * new activities from a list that comes with the app, suited to the skill's level; and, with
 * the AI ready, new ones the AI makes up. There are always more: the list goes round again,
 * and a stepped-up goal can always be stepped up again. Nothing is added until the user has
 * seen it in the goal editor and saved it. All the ideas are the app's own.
 */

/** Where an idea comes from. */
enum class IdeaKind { STEP_UP, LIBRARY, AI }

/**
 * An idea for [area]. [goal] is what it would fill the goal editor with; for a step-up,
 * [goalId] is the goal it changes and [goal] holds its new length, amount or times a week.
 */
data class AreaIdea(
    val key: String,
    val area: SkillArea,
    val kind: IdeaKind,
    val goal: GoalIdea,
    val effort: Effort,
    /** 1 for getting started, 2 for building up, 3 for pushing further. */
    val stage: Int = 1,
    val goalId: Long? = null,
    /** For a step-up: what it was, like "30 min". */
    val from: String? = null,
) {
    /** "3 times a week · 20 min", for under the name. */
    val detail: String
        get() = listOfNotNull(
            when (goal.repeat) {
                IdeaRepeat.EVERY_DAY -> "Every day"
                IdeaRepeat.TIMES_PER_WEEK -> goal.timesPerWeek?.let { if (it == 1) "Once a week" else "$it times a week" }
                IdeaRepeat.DAYS_OF_WEEK -> "${goal.days.size} days a week"
            },
            goal.minutes?.let { "$it min" },
            goal.amount?.let { "${Amounts.format(it)}${goal.unit?.let { u -> " $u" } ?: ""}" },
            effort.label,
        ).joinToString(" · ")
}

object Ideas {
    /** Ideas the user said aren't for them, by key, comma-separated. */
    const val HIDDEN = "ideas_hidden"

    /** How many new ideas show at a time. */
    const val PAGE = 3

    /** What each area is about, for the AI and for the screen. */
    fun about(area: SkillArea): String = when (area) {
        SkillArea.FITNESS -> "exercise, movement and sport"
        SkillArea.HEALTH -> "eating well, sleep, water and looking after the body"
        SkillArea.MIND -> "reading, learning, languages and calm"
        SkillArea.HOME -> "cleaning, cooking, tidying and the garden"
        SkillArea.WORK -> "work, study, money and career"
        SkillArea.CREATIVE -> "art, music, writing and making things"
    }

    /** The stage that suits a skill at [level]: getting started below 5, building up below 10, then pushing further. */
    fun stageFor(level: Int): Int = when {
        level < 5 -> 1
        level < 10 -> 2
        else -> 3
    }

    fun stageName(stage: Int): String = when (stage) {
        1 -> "getting started"
        2 -> "building up"
        else -> "pushing further"
    }

    /** The area furthest behind: the least experience, the first in the list on a tie. */
    fun weakest(sheet: CharacterSheet): SkillArea = SkillArea.entries.minBy { sheet.skills[it] ?: 0 }

    fun decodeHidden(text: String?): Set<String> = text.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun encodeHidden(keys: Set<String>): String = keys.sorted().joinToString(",")

    /** The goals that build [area]: habits being done, not ones to quit. */
    private fun building(goals: List<Goal>, area: SkillArea): List<Goal> =
        goals.filter { !it.archived && it.kind == GoalKind.HABIT && !it.quit && it.repeat != null && SkillArea.of(it) == area }

    /**
     * The next step for each of the user's own goals in [area]: a little longer, a little more,
     * or once more a week. Goals with none of those have no step.
     */
    fun stepUps(goals: List<Goal>, area: SkillArea): List<AreaIdea> = building(goals, area).mapNotNull { goal ->
        val repeat = goal.repeat!!
        val base = GoalIdea(name = goal.name, icon = goal.icon, repeat = IdeaRepeat.EVERY_DAY)
        when {
            goal.hasAmount -> {
                val next = moreOf(goal.amount!!)
                AreaIdea(
                    "step:${goal.id}", area, IdeaKind.STEP_UP, base.copy(amount = next, unit = goal.unit), effortOf(goal.points),
                    goalId = goal.id, from = Amounts.format(goal.amount) + (goal.unit?.let { " $it" } ?: ""),
                )
            }
            goal.minutes != null && goal.minutes > 0 -> AreaIdea(
                "step:${goal.id}", area, IdeaKind.STEP_UP, base.copy(minutes = longer(goal.minutes)), effortOf(goal.points),
                goalId = goal.id, from = "${goal.minutes} min",
            )
            repeat.kind == RepeatKind.TIMES_PER && repeat.per == Period.WEEKLY && goal.target in 1..6 -> AreaIdea(
                "step:${goal.id}", area, IdeaKind.STEP_UP,
                base.copy(repeat = IdeaRepeat.TIMES_PER_WEEK, timesPerWeek = goal.target + 1), effortOf(goal.points),
                goalId = goal.id, from = if (goal.target == 1) "once a week" else "${goal.target} times a week",
            )
            else -> null
        }
    }

    /** A little longer: 5 minutes more up to half an hour, 10 up to an hour, then 15. */
    fun longer(minutes: Int): Int = minutes + when {
        minutes < 30 -> 5
        minutes < 60 -> 10
        else -> 15
    }

    /** A little more: about a fifth more, in a round step (1, 2 or 5 of something), and always more. */
    fun moreOf(amount: Double): Double {
        val raw = amount * 0.2
        val power = 10.0.pow(floor(log10(raw.coerceAtLeast(1.0))))
        val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * power }.first { it >= raw.coerceAtLeast(1.0) - 1e-9 }
        return (floor(amount / step) + 1) * step
    }

    private fun effortOf(points: Int): Effort = Effort.entries.lastOrNull { points >= it.points } ?: Effort.EASY

    /**
     * New ideas for [area] from the app's list, best first for a skill at [level]: its own stage,
     * then the next one up, then the rest. Leaves out those [hidden] and those like a goal the
     * user already has.
     */
    fun library(area: SkillArea, level: Int, goals: List<Goal>, hidden: Set<String>): List<AreaIdea> {
        val stage = stageFor(level)
        val have = goals.filter { !it.archived }.map { simple(it.name) }.toSet()
        return LIBRARY.filter { it.area == area && it.key !in hidden && simple(it.goal.name) !in have }
            .sortedWith(compareBy({ if (it.stage >= stage) it.stage - stage else 10 + stage - it.stage }, { LIBRARY.indexOf(it) }))
    }

    /** The page of [ideas] after [page] pages: it goes round again at the end, so there are always more. */
    fun page(ideas: List<AreaIdea>, page: Int, size: Int = PAGE): List<AreaIdea> {
        if (ideas.isEmpty()) return emptyList()
        if (ideas.size <= size) return ideas
        val start = (page.coerceAtLeast(0) * size) % ideas.size
        return List(size) { ideas[(start + it) % ideas.size] }
    }

    private fun simple(name: String) = name.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun idea(
        area: SkillArea,
        stage: Int,
        icon: String,
        name: String,
        perWeek: Int? = null,
        minutes: Int? = null,
        amount: Double? = null,
        unit: String? = null,
        effort: Effort = Effort.EASY,
    ) = AreaIdea(
        key = "${area.name.lowercase()}:${simple(name)}",
        area = area,
        kind = IdeaKind.LIBRARY,
        goal = GoalIdea(
            name = name,
            icon = icon,
            repeat = if (perWeek == null) IdeaRepeat.EVERY_DAY else IdeaRepeat.TIMES_PER_WEEK,
            timesPerWeek = perWeek,
            minutes = minutes,
            amount = amount,
            unit = unit,
        ),
        effort = effort,
        stage = stage,
    )

    private val F = SkillArea.FITNESS
    private val H = SkillArea.HEALTH
    private val M = SkillArea.MIND
    private val O = SkillArea.HOME
    private val W = SkillArea.WORK
    private val C = SkillArea.CREATIVE
    private val E = Effort.EASY
    private val MD = Effort.MEDIUM
    private val HD = Effort.HARD
    private val VH = Effort.VERY_HARD

    /** The ideas that come with the app: eight for each stage of each area. */
    val LIBRARY: List<AreaIdea> = listOf(
        idea(F, 1, "🚶", "Walk 10 minutes", minutes = 10),
        idea(F, 1, "🧘", "Stretch", minutes = 5),
        idea(F, 1, "🪜", "Take the stairs"),
        idea(F, 1, "🏋️", "Squats", amount = 10.0, unit = "squats"),
        idea(F, 1, "💃", "Dance to a song", perWeek = 3, minutes = 4),
        idea(F, 1, "🚲", "Bike ride", perWeek = 2, minutes = 20, effort = MD),
        idea(F, 1, "🤸", "Plank", minutes = 1),
        idea(F, 1, "🏃", "Run and walk", perWeek = 2, minutes = 15, effort = MD),
        idea(F, 2, "🏃", "Run", perWeek = 3, minutes = 20, effort = MD),
        idea(F, 2, "🧘", "Yoga", perWeek = 3, minutes = 20, effort = MD),
        idea(F, 2, "💪", "Push-ups", amount = 15.0, unit = "push-ups", effort = MD),
        idea(F, 2, "👟", "Walk 8,000 steps", amount = 8000.0, unit = "steps", effort = MD),
        idea(F, 2, "🏊", "Swim", perWeek = 2, minutes = 30, effort = MD),
        idea(F, 2, "🏋️", "Strength workout", perWeek = 2, minutes = 30, effort = HD),
        idea(F, 2, "🥾", "Hike", perWeek = 1, minutes = 90, effort = HD),
        idea(F, 2, "🚴", "Cycle", perWeek = 2, amount = 10.0, unit = "km", effort = MD),
        idea(F, 3, "🏃", "Run 5 km", perWeek = 3, amount = 5.0, unit = "km", effort = HD),
        idea(F, 3, "🏋️", "Gym session", perWeek = 4, minutes = 60, effort = HD),
        idea(F, 3, "⏱️", "Interval training", perWeek = 2, minutes = 25, effort = HD),
        idea(F, 3, "🧗", "Climbing", perWeek = 1, minutes = 90, effort = HD),
        idea(F, 3, "🧘", "Long yoga flow", perWeek = 2, minutes = 45, effort = MD),
        idea(F, 3, "🚴", "Long ride", perWeek = 1, amount = 40.0, unit = "km", effort = VH),
        idea(F, 3, "🏃", "Long run", perWeek = 1, amount = 10.0, unit = "km", effort = VH),
        idea(F, 3, "🤸", "Mobility routine", minutes = 15, effort = MD),

        idea(H, 1, "💧", "Glass of water on waking"),
        idea(H, 1, "🍎", "Eat a piece of fruit"),
        idea(H, 1, "🦷", "Floss"),
        idea(H, 1, "😴", "In bed by 11 pm"),
        idea(H, 1, "🥗", "Vegetables with lunch"),
        idea(H, 1, "💊", "Take vitamins"),
        idea(H, 1, "🌞", "Daylight outside", minutes = 10),
        idea(H, 1, "📵", "No phone for the first half hour"),
        idea(H, 2, "💧", "Drink water", amount = 8.0, unit = "glasses", effort = MD),
        idea(H, 2, "🥦", "Fruit and veg", amount = 5.0, unit = "portions", effort = MD),
        idea(H, 2, "😴", "Sleep 8 hours", effort = MD),
        idea(H, 2, "🍳", "Cook a healthy dinner", perWeek = 4, minutes = 40, effort = MD),
        idea(H, 2, "🚫", "No sugary drinks", effort = MD),
        idea(H, 2, "🧴", "Skincare", minutes = 5),
        idea(H, 2, "🌙", "Screens off an hour before bed", effort = MD),
        idea(H, 2, "🫁", "Breathing exercise", minutes = 5),
        idea(H, 3, "🥡", "Meal prep for the week", perWeek = 1, minutes = 90, effort = HD),
        idea(H, 3, "🍵", "Alcohol-free day", perWeek = 5, effort = MD),
        idea(H, 3, "🚿", "Cold shower", perWeek = 3, minutes = 2, effort = HD),
        idea(H, 3, "🥤", "Drink 2 litres of water", amount = 2.0, unit = "litres", effort = MD),
        idea(H, 3, "⏰", "Same wake-up time", effort = HD),
        idea(H, 3, "🍽️", "Meals without screens", effort = MD),
        idea(H, 3, "🚶", "Walk after dinner", minutes = 15, effort = MD),
        idea(H, 3, "🩺", "Weekly health check-in", perWeek = 1, minutes = 10),

        idea(M, 1, "📖", "Read", amount = 10.0, unit = "pages"),
        idea(M, 1, "🧘", "Meditate", minutes = 5),
        idea(M, 1, "🙏", "Three good things", minutes = 5),
        idea(M, 1, "🧩", "Puzzle", perWeek = 3, minutes = 15),
        idea(M, 1, "🎧", "Learning podcast", perWeek = 2, minutes = 30),
        idea(M, 1, "🗣️", "New words", amount = 5.0, unit = "words"),
        idea(M, 1, "📝", "Journal", minutes = 5),
        idea(M, 1, "🌳", "Sit outside, no phone", perWeek = 3, minutes = 10),
        idea(M, 2, "📚", "Read for half an hour", minutes = 30, effort = MD),
        idea(M, 2, "🧘", "Meditate longer", minutes = 15, effort = MD),
        idea(M, 2, "🌍", "Language lesson", perWeek = 5, minutes = 20, effort = MD),
        idea(M, 2, "🎓", "Online course lesson", perWeek = 3, minutes = 30, effort = MD),
        idea(M, 2, "♟️", "Chess or a strategy game", perWeek = 2, minutes = 30),
        idea(M, 2, "📓", "Journal a page", minutes = 10),
        idea(M, 2, "🧠", "Learn something and explain it", perWeek = 2, minutes = 20, effort = MD),
        idea(M, 2, "📰", "Read a long article", perWeek = 3, minutes = 20),
        idea(M, 3, "📚", "Read 50 pages", perWeek = 4, amount = 50.0, unit = "pages", effort = HD),
        idea(M, 3, "💬", "Speak the language", perWeek = 3, minutes = 15, effort = HD),
        idea(M, 3, "🧘", "Meditate half an hour", minutes = 30, effort = HD),
        idea(M, 3, "🎓", "Study for a qualification", perWeek = 4, minutes = 60, effort = VH),
        idea(M, 3, "✍️", "Write up what you learned", perWeek = 1, minutes = 30, effort = MD),
        idea(M, 3, "🧑‍🏫", "Teach someone something", perWeek = 1, minutes = 30, effort = HD),
        idea(M, 3, "📜", "Learn a poem by heart", perWeek = 3, minutes = 15, effort = MD),
        idea(M, 3, "🔬", "Deep dive into a topic", perWeek = 2, minutes = 45, effort = HD),

        idea(O, 1, "🛏️", "Make the bed"),
        idea(O, 1, "🍽️", "Dishes done before bed", minutes = 10),
        idea(O, 1, "🧺", "A load of laundry", perWeek = 2, minutes = 15),
        idea(O, 1, "🧹", "Ten-minute tidy", minutes = 10),
        idea(O, 1, "🗑️", "Take the bins out", perWeek = 1),
        idea(O, 1, "🪴", "Water the plants", perWeek = 2, minutes = 5),
        idea(O, 1, "👕", "Put clothes away", minutes = 5),
        idea(O, 1, "🧽", "Wipe the kitchen", minutes = 5),
        idea(O, 2, "🧹", "Hoover the floors", perWeek = 2, minutes = 20, effort = MD),
        idea(O, 2, "🍲", "Cook dinner from scratch", perWeek = 4, minutes = 45, effort = MD),
        idea(O, 2, "📦", "Declutter a drawer", perWeek = 2, minutes = 15, effort = MD),
        idea(O, 2, "🛒", "Plan meals and shop", perWeek = 1, minutes = 45, effort = MD),
        idea(O, 2, "🛁", "Clean the bathroom", perWeek = 1, minutes = 30, effort = MD),
        idea(O, 2, "🧊", "Clear out the fridge", perWeek = 1, minutes = 15),
        idea(O, 2, "🐾", "Time with the pet", minutes = 20),
        idea(O, 2, "🌱", "Garden", perWeek = 2, minutes = 20, effort = MD),
        idea(O, 3, "📦", "Declutter a room", perWeek = 1, minutes = 90, effort = HD),
        idea(O, 3, "🧽", "Deep clean one area", perWeek = 1, minutes = 60, effort = HD),
        idea(O, 3, "🔧", "Fix something broken", perWeek = 1, minutes = 45, effort = HD),
        idea(O, 3, "🍲", "Batch-cook for the week", perWeek = 1, minutes = 120, effort = HD),
        idea(O, 3, "🎁", "Give away things you don't use", perWeek = 1, minutes = 30, effort = MD),
        idea(O, 3, "🥕", "Grow something to eat", perWeek = 3, minutes = 15, effort = MD),
        idea(O, 3, "🪟", "Wash the windows", perWeek = 1, minutes = 45, effort = MD),
        idea(O, 3, "🗂️", "Sort the paperwork", perWeek = 1, minutes = 30, effort = MD),

        idea(W, 1, "📥", "Clear the inbox", minutes = 15),
        idea(W, 1, "📝", "Write tomorrow's top three", minutes = 5),
        idea(W, 1, "💷", "Check your spending", minutes = 5),
        idea(W, 1, "⏰", "Start work on time", perWeek = 5),
        idea(W, 1, "🎯", "One focused 25 minutes", perWeek = 5, minutes = 25),
        idea(W, 1, "📅", "Look at the week ahead", perWeek = 1, minutes = 15),
        idea(W, 1, "🧾", "File receipts", perWeek = 1, minutes = 10),
        idea(W, 1, "📞", "Answer one put-off message", minutes = 5),
        idea(W, 2, "🎯", "Two focused hours", perWeek = 5, minutes = 120, effort = HD),
        idea(W, 2, "💼", "Learn a work skill", perWeek = 3, minutes = 30, effort = MD),
        idea(W, 2, "🐷", "Put money in savings", perWeek = 1, minutes = 5, effort = MD),
        idea(W, 2, "📊", "Update the budget", perWeek = 1, minutes = 20, effort = MD),
        idea(W, 2, "🤝", "Get in touch with a contact", perWeek = 1, minutes = 10, effort = MD),
        idea(W, 2, "📄", "Work on your CV", perWeek = 1, minutes = 30, effort = MD),
        idea(W, 2, "🗒️", "Plan the day before starting", perWeek = 5, minutes = 10),
        idea(W, 2, "🔕", "No work email after 7 pm", effort = MD),
        idea(W, 3, "🚀", "Side project", perWeek = 3, minutes = 60, effort = HD),
        idea(W, 3, "🎤", "Practise a presentation", perWeek = 2, minutes = 30, effort = HD),
        idea(W, 3, "🧭", "Review your goals for the year", perWeek = 1, minutes = 30, effort = MD),
        idea(W, 3, "📜", "Study for a certificate", perWeek = 4, minutes = 45, effort = VH),
        idea(W, 3, "📈", "Invest or save a tenth", perWeek = 1, minutes = 15, effort = HD),
        idea(W, 3, "🧑‍🤝‍🧑", "Mentor someone", perWeek = 1, minutes = 30, effort = HD),
        idea(W, 3, "✍️", "Write about your work", perWeek = 1, minutes = 45, effort = HD),
        idea(W, 3, "🔍", "Go for one opportunity", perWeek = 1, minutes = 45, effort = HD),

        idea(C, 1, "✏️", "Doodle", minutes = 5),
        idea(C, 1, "📷", "One photo you like", minutes = 5),
        idea(C, 1, "🎵", "Listen to a new album", perWeek = 1, minutes = 45),
        idea(C, 1, "✍️", "Write three sentences", minutes = 5),
        idea(C, 1, "🖍️", "Colour or paint", perWeek = 3, minutes = 15),
        idea(C, 1, "🎹", "Play an instrument", minutes = 10),
        idea(C, 1, "🧶", "Knit or craft", perWeek = 3, minutes = 15),
        idea(C, 1, "🍪", "Bake something", perWeek = 1, minutes = 60),
        idea(C, 2, "🖊️", "Sketch something real", minutes = 15, effort = MD),
        idea(C, 2, "📝", "Write", perWeek = 5, amount = 300.0, unit = "words", effort = MD),
        idea(C, 2, "🎸", "Practise your instrument", perWeek = 5, minutes = 30, effort = MD),
        idea(C, 2, "🌆", "Photo walk", perWeek = 1, minutes = 45, effort = MD),
        idea(C, 2, "🎨", "Paint a small piece", perWeek = 2, minutes = 45, effort = MD),
        idea(C, 2, "🎤", "Learn a song", perWeek = 2, minutes = 20, effort = MD),
        idea(C, 2, "🎬", "Edit a short video", perWeek = 1, minutes = 60, effort = MD),
        idea(C, 2, "🧵", "Sew or make something", perWeek = 1, minutes = 60, effort = MD),
        idea(C, 3, "📖", "Write a thousand words", perWeek = 5, amount = 1000.0, unit = "words", effort = HD),
        idea(C, 3, "🎼", "Write a song", perWeek = 1, minutes = 90, effort = HD),
        idea(C, 3, "🖼️", "Finish a piece to share", perWeek = 1, minutes = 120, effort = HD),
        idea(C, 3, "🎹", "Learn a whole piece", perWeek = 4, minutes = 45, effort = HD),
        idea(C, 3, "📚", "Work on a long project", perWeek = 4, minutes = 60, effort = HD),
        idea(C, 3, "🧪", "Try a new medium", perWeek = 1, minutes = 60, effort = MD),
        idea(C, 3, "📸", "Photo project with a theme", minutes = 10, effort = MD),
        idea(C, 3, "🎭", "Go to a class or group", perWeek = 1, minutes = 90, effort = HD),
    )
}

/**
 * New ideas for an area from the AI: what it is asked and reading its answer. It is told the
 * area, the skill's level, the goals already in it (private ones not named) and the ideas
 * already shown, so each ask brings different ones.
 */
object AreaIdeasFromAi {
    const val MAX_TOKENS = 260
    const val COUNT = 3

    val SYSTEM = """
        You suggest new habits that build one area of someone's life, for a checklist app. Reply with JSON only.
        ideas: exactly 3 new habits that suit their level and grow from what they already do, all different from the ones listed.
        Each idea: name (2 to 5 words, like "Swim" or "Cook a new recipe"), emoji (one emoji that fits),
        times_per_week (1 to 7; 7 means every day), minutes (how long each time, 5 to 120).
        Example: {"ideas":[{"name":"Cook a new recipe","emoji":"🍲","times_per_week":2,"minutes":45},{"name":"Tidy one shelf","emoji":"📚","times_per_week":3,"minutes":10},{"name":"Plan the meals","emoji":"🗒️","times_per_week":1,"minutes":20}]}
    """.trimIndent()

    val SCHEMA = """
        {"type":"object","properties":{"ideas":{"type":"array","minItems":3,"maxItems":3,"items":{"type":"object","properties":{
          "name":{"type":"string"},"emoji":{"type":"string"},"times_per_week":{"type":"integer"},"minutes":{"type":"integer"}
        },"required":["name","emoji","times_per_week","minutes"]}}},"required":["ideas"]}
    """.trimIndent()

    /** The facts for the AI: the area, the level, what the user does and what was already suggested. */
    fun prompt(area: SkillArea, level: Int, goals: List<Goal>, shown: List<String>): String = buildString {
        appendLine("Area: ${area.label} (${Ideas.about(area)}).")
        appendLine("Their skill level: $level, so ideas for ${Ideas.stageName(Ideas.stageFor(level))}.")
        val doing = goals.filter { !it.archived && SkillArea.of(it) == area }.map { if (it.private) "a private goal" else it.name }
        if (doing.isNotEmpty()) appendLine("They already do: ${doing.take(10).joinToString(", ")}.")
        if (shown.isNotEmpty()) appendLine("Already suggested, so not these: ${shown.takeLast(15).joinToString(", ")}.")
        append("New ideas ->")
    }

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** The ideas in the AI's [answer], checked: odd numbers left out, no repeats of [shown] or of each other. */
    fun parse(answer: String, area: SkillArea, shown: Collection<String> = emptyList()): List<AreaIdea> {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val obj = runCatching { json.parseToJsonElement(answer.substring(start, end + 1)) as? JsonObject }.getOrNull() ?: return emptyList()
        val seen = shown.map { it.lowercase() }.toMutableSet()
        return (obj["ideas"] as? JsonArray ?: return emptyList()).mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val name = (item["name"] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content
                ?.trim()?.trim('"', '\'', '.')?.replace(Regex("\\s+"), " ")?.take(40)?.trim()
                ?.takeIf { it.any(Char::isLetter) }?.replaceFirstChar { it.uppercaseChar() } ?: return@mapNotNull null
            if (!seen.add(name.lowercase())) return@mapNotNull null
            val emoji = (item["emoji"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
                ?.takeIf { it.length in 1..16 && it.none { c -> c.isLetterOrDigit() || c.isWhitespace() } }
            val times = (item["times_per_week"] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }?.coerceIn(1, 7) ?: 3
            val minutes = (item["minutes"] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }?.takeIf { it in 1..600 }
            AreaIdea(
                key = "ai:${area.name.lowercase()}:${name.lowercase()}",
                area = area,
                kind = IdeaKind.AI,
                goal = GoalIdea(
                    name = name,
                    icon = emoji,
                    repeat = if (times >= 7) IdeaRepeat.EVERY_DAY else IdeaRepeat.TIMES_PER_WEEK,
                    timesPerWeek = times.takeIf { it < 7 },
                    minutes = minutes,
                ),
                effort = when {
                    minutes == null || minutes <= 10 -> Effort.EASY
                    minutes <= 30 -> Effort.MEDIUM
                    minutes <= 60 -> Effort.HARD
                    else -> Effort.VERY_HARD
                },
            )
        }.take(COUNT)
    }
}
