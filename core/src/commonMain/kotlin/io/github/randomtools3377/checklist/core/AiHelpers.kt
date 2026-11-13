package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Steps for a project, suggested by the AI from its name, for the user to keep, change or drop. */
object ProjectSteps {
    const val MAX_TOKENS = 220
    const val MAX_STEPS = 8

    val SYSTEM = """
        You break a project into the steps to do it, for a checklist app. Reply with JSON only.
        steps: 3 to 8 short steps in the order to do them, each 2 to 8 words, starting with a verb.
        When steps are listed as already there or already suggested, give different ones.
        Example: "plan a birthday party" -> {"steps":["Pick a date","Write the guest list","Book the place","Send the invitations","Order the cake","Buy decorations"]}
    """.trimIndent()

    val SCHEMA = """
        {"type":"object","properties":{"steps":{"type":"array","items":{"type":"string"},"minItems":3,"maxItems":8}},"required":["steps"]}
    """.trimIndent()

    const val MAX_HINT = 100

    /**
     * What the AI is asked: the project's or to-do's name, a [hint] the user typed to steer it
     * ("it's for 20 people, outdoors"), the steps [existing] already, and the ones already
     * [shown], so asking again brings different steps.
     */
    fun prompt(project: String, existing: List<String> = emptyList(), shown: List<String> = emptyList(), hint: String = ""): String = buildString {
        fun tidy(text: String, length: Int) = text.trim().replace("\"", "'").replace(Regex("\\s+"), " ").take(length)
        val has = existing.map { tidy(it, 60) }.filter { it.isNotEmpty() }.take(10)
        val seen = shown.map { tidy(it, 60) }.filter { it.isNotEmpty() }.takeLast(16)
        if (hint.isNotBlank()) appendLine("About it: ${tidy(hint, MAX_HINT)}.")
        if (has.isNotEmpty()) appendLine("Steps already there: ${has.joinToString("; ")}.")
        if (seen.isNotEmpty()) appendLine("Already suggested, so not these: ${seen.joinToString("; ")}.")
        append("\"${tidy(project, 120)}\" ->")
    }

    /** The steps in [answer], without any of [not] (already there or already shown). */
    fun parse(answer: String, not: Collection<String>): List<String> {
        val gone = not.map { it.trim().lowercase() }.toSet()
        return parse(answer).filter { it.lowercase() !in gone }
    }

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** The steps in the AI's [answer], tidied: numbering and full stops gone, no repeats, at most [MAX_STEPS]. */
    fun parse(answer: String): List<String> {
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val obj = runCatching { json.parseToJsonElement(answer.substring(start, end + 1)) as? JsonObject }.getOrNull() ?: return emptyList()
        val raw = (obj["steps"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        val seen = mutableSetOf<String>()
        return raw.mapNotNull { step ->
            val clean = step.trim()
                .replace(Regex("^(\\d+[.)]|[-*•])\\s*"), "")
                .trim().trimEnd('.').replace(Regex("\\s+"), " ").take(60).trim()
            clean.takeIf { it.any(Char::isLetter) && seen.add(it.lowercase()) }?.replaceFirstChar { it.uppercaseChar() }
        }.take(MAX_STEPS)
    }

    private fun JsonArray?.orEmpty() = this ?: emptyList()
}

/** Why the app picked a suggestion for next week, which decides its button. */
enum class AdviceKind { MAKE_SMALLER, PAY_MORE, OPEN, STEP_UP, HARD_DAY }

/**
 * The one suggestion for next week, chosen by the app from the user's record. [button], when
 * there is one, opens the goal's editor filled in with [idea], to check and save.
 */
data class WeekAdvice(val kind: AdviceKind, val text: String, val button: String? = null, val idea: AreaIdea? = null)

object WeekAdvices {
    private val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun weekdayName(iso: Int): String = weekdays[iso - 1]

    /** A smaller version of a length: about two thirds, in steps of 5 minutes, at least 5. */
    fun shorter(minutes: Int): Int = maxOf(5, (minutes * 2 / 3) / 5 * 5).let { if (it >= minutes) maxOf(1, minutes / 2) else it }

    /** A smaller amount: about two thirds, as a whole number where that works. */
    fun less(amount: Double): Double = if (amount >= 3) kotlin.math.floor(amount * 2 / 3) else amount / 2

    /**
     * The suggestion for the week in [week]: for the goal that was hardest (done half the time
     * or less of at least 2 times), a smaller version, a little more pay (from the balance
     * check's [checks]) or a look at it; after a week that went well, the next step for the goal
     * that went best; else a lighter hardest day. Private goals are never picked. Null when
     * there is nothing to say.
     */
    fun pick(week: ReviewSummary, checks: List<GoalCheck> = emptyList()): WeekAdvice? {
        val open = week.goals.filter { !it.goal.private && !it.goal.archived && it.goal.kind == GoalKind.HABIT && !it.goal.atMost }
        val hardest = open.filter { it.due >= 2 && it.done * 2 <= it.due }.minWithOrNull(compareBy({ it.consistency }, { -it.due }))
        if (hardest != null) {
            val goal = hardest.goal
            val area = SkillArea.of(goal)
            val base = GoalIdea(name = goal.name, icon = goal.icon, repeat = IdeaRepeat.EVERY_DAY)
            val went = "${goal.name} got done ${hardest.done} of ${hardest.due} times."
            val minutes = goal.minutes
            val amount = goal.amount
            val more = checks.firstOrNull { it.goal.id == goal.id && it.hint == GoalHint.PAY_MORE }?.suggested?.takeIf { it > goal.points }
            return when {
                minutes != null && minutes > 5 && !goal.quit -> {
                    val to = shorter(minutes)
                    WeekAdvice(
                        AdviceKind.MAKE_SMALLER, "$went A smaller version is easier to start: $to minutes instead of $minutes.", "Make it smaller",
                        AreaIdea("advice:${goal.id}", area, IdeaKind.STEP_UP, base.copy(minutes = to), Effort.EASY, goalId = goal.id, from = "$minutes min"),
                    )
                }
                amount != null && amount > 1 -> {
                    val to = less(amount)
                    val unit = goal.unit?.let { " $it" } ?: ""
                    WeekAdvice(
                        AdviceKind.MAKE_SMALLER, "$went A smaller version is easier to start: ${Amounts.format(to)}$unit instead of ${Amounts.format(amount)}$unit.", "Make it smaller",
                        AreaIdea("advice:${goal.id}", area, IdeaKind.STEP_UP, base.copy(amount = to, unit = goal.unit), Effort.EASY, goalId = goal.id, from = Amounts.format(amount) + unit),
                    )
                }
                more != null -> WeekAdvice(
                    AdviceKind.PAY_MORE, "$went It could pay a little more to be worth the effort: $more instead of ${goal.points}.", "Pay a little more",
                    AreaIdea("advice:${goal.id}", area, IdeaKind.STEP_UP, base, Effort.EASY, goalId = goal.id, points = more),
                )
                else -> WeekAdvice(
                    AdviceKind.OPEN, "$went A set time or a reminder can make it easier to get to.", "Open ${goal.name}",
                    AreaIdea("advice:${goal.id}", area, IdeaKind.STEP_UP, base, Effort.EASY, goalId = goal.id),
                )
            }
        }
        val due = open.sumOf { it.due }
        val done = open.sumOf { it.done }
        if (due > 0 && done * 10 >= due * 9) {
            val goals = open.filter { it.due > 0 && it.done == it.due }.sortedByDescending { it.due }.map { it.goal }
            for (goal in goals) {
                val step = Ideas.stepUps(listOf(goal), SkillArea.of(goal)).firstOrNull() ?: continue
                val to = listOfNotNull(
                    step.goal.minutes?.let { "$it min" },
                    step.goal.amount?.let { Amounts.format(it) + (step.goal.unit?.let { u -> " $u" } ?: "") },
                    step.goal.timesPerWeek?.let { "$it times a week" },
                ).firstOrNull() ?: continue
                return WeekAdvice(AdviceKind.STEP_UP, "${goal.name} went well. A next step, if you like: $to instead of ${step.from}.", "Step it up", step)
            }
        }
        val best = week.bestWeekday
        val worst = week.worstWeekday
        if (worst != null && worst != best) {
            return WeekAdvice(AdviceKind.HARD_DAY, "${weekdayName(worst)} was the hardest day. A lighter ${weekdayName(worst)}, or one goal moved to another day, could help.")
        }
        return null
    }
}

/** What else the note on the week can mention, beside the week's own numbers. */
data class WeekMore(
    val lastWeek: ReviewSummary? = null,
    /** The main streak now, in days. */
    val streak: Int? = null,
    val target: WeekProgress? = null,
    /** Badges earned in the week, by name. */
    val badges: List<String> = emptyList(),
    val advice: WeekAdvice? = null,
)

/** A note on a week that was written and kept: [reasons] says it was written with the reasons for misses. */
data class KeptNote(val week: LocalDate, val text: String, val reasons: Boolean)

/** The notes kept, in one setting of the checklist app (Checklist AI keeps nothing): the latest [KEEP] weeks. */
object CoachNotes {
    const val SETTING = "coach_notes"
    const val KEEP = 8

    fun decode(text: String?): List<KeptNote> = text.orEmpty().split("\n").mapNotNull { line ->
        val parts = line.split("|", limit = 3)
        if (parts.size < 3) return@mapNotNull null
        val week = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
        KeptNote(week, parts[2], parts[1] == "1").takeIf { it.text.isNotBlank() }
    }

    fun encode(notes: List<KeptNote>): String =
        notes.sortedBy { it.week }.takeLast(KEEP).joinToString("\n") { "${it.week}|${if (it.reasons) 1 else 0}|${it.text.replace(Regex("\\s+"), " ").trim()}" }

    /** [notes] with [note] in place of any other for its week. */
    fun put(notes: List<KeptNote>, note: KeptNote): List<KeptNote> = notes.filter { it.week != note.week } + note

    /**
     * The note kept for [week], unless it was written with reasons and they are locked now, or
     * it names one of [hiddenNames]: goals that are private and locked now (one made private
     * after the note was written).
     */
    fun shown(notes: List<KeptNote>, week: LocalDate, hideReasons: Boolean, hiddenNames: List<String> = emptyList()): KeptNote? =
        notes.firstOrNull { it.week == week }?.takeIf { note ->
            !(note.reasons && hideReasons) && hiddenNames.none { it.isNotBlank() && note.text.contains(it.trim(), ignoreCase = true) }
        }
}

/**
 * A few sentences about the week from the AI: one thing that went well, and the one suggestion
 * the app chose. It is given only the user's own numbers, and the reasons for misses unless
 * they are locked away. Private goals aren't named.
 */
object CoachNote {
    const val MAX_TOKENS = 160

    val SYSTEM = """
        You are a kind, practical coach for someone using a habit checklist app.
        From the facts given, write 2 to 4 short sentences in plain, warm English:
        say one thing that went well, then pass on the suggestion for next week in your own words.
        Use only the facts given, and give no other advice. No lists, no headings, no emoji, no greeting.
    """.trimIndent()

    /** The week's facts, one per line, for the AI to write from. */
    fun prompt(summary: ReviewSummary, hideReasons: Boolean, more: WeekMore = WeekMore()): String = buildString {
        val due = summary.goals.sumOf { it.due }
        val done = summary.goals.sumOf { it.done }
        appendLine("Week: ${dateText(summary.from)} to ${dateText(summary.to)}.")
        if (due > 0) appendLine("Done $done of $due goals due (${done * 100 / due}%).")
        appendLine("Points earned ${summary.earned}, lost ${summary.lost}.")
        more.lastWeek?.let { last ->
            val lastDue = last.goals.sumOf { it.due }
            val lastDone = last.goals.sumOf { it.done }
            if (lastDue > 0) appendLine("The week before: done $lastDone of $lastDue (${lastDone * 100 / lastDue}%), points earned ${last.earned}.")
        }
        more.streak?.takeIf { it > 0 }?.let { appendLine("Streak now: $it days in a row.") }
        more.target?.takeIf { it.target > 0 }?.let { appendLine("Weekly points target: ${it.earned} of ${it.target}, ${if (it.reached) "reached" else "not reached yet"}.") }
        if (more.badges.isNotEmpty()) appendLine("New badges: ${more.badges.take(4).joinToString(", ")}.")
        val goals = summary.goals.filter { it.due > 0 }.sortedByDescending { it.due }.take(8)
        if (goals.isNotEmpty()) {
            appendLine("Goals:")
            goals.forEach { g ->
                val name = if (g.goal.private) "A private goal" else g.goal.name
                append("- $name: done ${g.done} of ${g.due}")
                if (g.missed > 0) append(", missed ${g.missed}")
                if (g.skipped > 0) append(", skipped ${g.skipped}")
                if (g.currentRun >= 3) append(", on a run of ${g.currentRun}")
                appendLine(".")
            }
        }
        if (!hideReasons && summary.reasons.isNotEmpty()) {
            appendLine("Reasons for misses: " + summary.reasons.take(4).joinToString(", ") { (reason, n) -> "$reason ($n)" } + ".")
        }
        summary.bestWeekday?.let { best ->
            val worst = summary.worstWeekday
            append("Best day: ${weekdayName(best)}")
            if (worst != null && worst != best) append("; hardest day: ${weekdayName(worst)}")
            appendLine(".")
        }
        more.advice?.let { appendLine("Suggestion for next week: ${it.text}") }
    }.trim()

    /**
     * A short note the app writes itself, for when there is no AI or it can't answer: "Best day:
     * Tuesday. Water is on a run of 6. Hardest: Stretch, 1 of 4." Private goals aren't named.
     * Null for a week with nothing due.
     */
    fun plain(summary: ReviewSummary, more: WeekMore = WeekMore()): String? {
        val due = summary.goals.sumOf { it.due }
        if (due == 0) return null
        val done = summary.goals.sumOf { it.done }
        val named = summary.goals.filter { !it.goal.private && it.due > 0 }
        return buildList {
            add("$done of $due done.")
            summary.bestWeekday?.let { add("Best day: ${weekdayName(it)}.") }
            named.filter { it.currentRun >= 3 }.maxByOrNull { it.currentRun }?.let { add("${it.goal.name} is on a run of ${it.currentRun}.") }
            more.target?.takeIf { it.reached }?.let { add("Weekly target reached.") }
            if (more.badges.isNotEmpty()) add("New: ${more.badges.take(3).joinToString(", ")}.")
            named.filter { it.due >= 2 && it.done < it.due }.minWithOrNull(compareBy({ it.consistency }, { -it.due }))
                ?.let { add("Hardest: ${it.goal.name}, ${it.done} of ${it.due}.") }
        }.joinToString(" ")
    }

    /** The AI's [answer] as plain sentences: no thinking, markup or lists, at most 4 sentences. Null if there is nothing usable. */
    fun clean(answer: String): String? {
        val text = answer.replace(Regex("(?s)<think>.*?</think>"), " ")
            .replace(Regex("[*#_`>]"), "")
            .replace(Regex("(?m)^\\s*([-•]|\\d+[.)])\\s+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.length < 20) return null
        val sentences = Regex("[^.!?]+[.!?]+").findAll(text).map { it.value.trim() }.toList()
        val kept = (if (sentences.isEmpty()) listOf(text) else sentences.take(4)).joinToString(" ")
        return kept.take(500).trim()
    }

    private fun weekdayName(iso: Int): String = WeekAdvices.weekdayName(iso)
}

/**
 * Waiting for the AI, and what to say when it can't answer: a few plain messages with what to
 * try, never the engine's own error text.
 */
object AiTrouble {
    /** How long the app waits for an answer before it stops waiting by itself. */
    const val TIMEOUT_MILLIS = 120_000L

    /** What stopping by itself is reported as. */
    const val TIMED_OUT = "timed out"

    /** Under the progress bar: how long it has been, and what to expect. */
    fun waiting(seconds: Int): String = when {
        seconds < 5 -> "Thinking, on this phone…"
        seconds < 20 -> "Thinking, on this phone: $seconds seconds. The first answer takes longer while the AI loads."
        seconds < 60 -> "Still thinking: $seconds seconds. A slower phone can take a minute."
        else -> "Still thinking: ${seconds / 60} min ${seconds % 60} s. You can stop waiting and carry on by hand."
    }

    /** What went wrong, in plain words with what to try, from the engine's [error]. */
    fun plain(error: String?): String {
        val e = error.orEmpty().lowercase()
        return when {
            TIMED_OUT in e || "timeout" in e -> "That took too long, so the app stopped waiting. Try again: the second time is usually quicker."
            "ai file" in e || "model" in e && "not" in e -> "Checklist AI has no AI file yet. Open Checklist AI to add it, then try again."
            "only the checklist app" in e || "permission" in e || "security" in e ->
                "Checklist AI and this app don't match. Install both from the same Release, then try again."
            "memory" in e || "alloc" in e -> "The phone ran short of memory. Close a few other apps and try again."
            "unknown authority" in e || "didn't answer" in e || "no answer" in e -> "Checklist AI didn't answer. Open it once, then try again."
            else -> "The AI couldn't answer this time. Try again in a moment."
        }
    }
}
