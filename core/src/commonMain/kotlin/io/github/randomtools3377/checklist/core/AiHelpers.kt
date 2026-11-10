package io.github.randomtools3377.checklist.core

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
        Example: "plan a birthday party" -> {"steps":["Pick a date","Write the guest list","Book the place","Send the invitations","Order the cake","Buy decorations"]}
    """.trimIndent()

    val SCHEMA = """
        {"type":"object","properties":{"steps":{"type":"array","items":{"type":"string"},"minItems":3,"maxItems":8}},"required":["steps"]}
    """.trimIndent()

    fun prompt(project: String): String = "\"${project.trim().replace("\"", "'").take(120)}\" ->"

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

/**
 * A few sentences about the week from the AI: one thing that went well and one small change to
 * try. It is given only the week's own numbers, and the reasons for misses unless they are
 * locked away. Private goals aren't named.
 */
object CoachNote {
    const val MAX_TOKENS = 160

    val SYSTEM = """
        You are a kind, practical coach for someone using a habit checklist app.
        From the facts about their week, write 2 to 4 short sentences in plain, warm English:
        say one thing that went well, then suggest one small, specific change to try next week.
        Use only the facts given. No lists, no headings, no emoji, no greeting.
    """.trimIndent()

    /** The week's facts, one per line, for the AI to write from. */
    fun prompt(summary: ReviewSummary, hideReasons: Boolean): String = buildString {
        val due = summary.goals.sumOf { it.due }
        val done = summary.goals.sumOf { it.done }
        appendLine("Week: ${dateText(summary.from)} to ${dateText(summary.to)}.")
        if (due > 0) appendLine("Done $done of $due goals due (${done * 100 / due}%).")
        appendLine("Points earned ${summary.earned}, lost ${summary.lost}.")
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
    }.trim()

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

    private fun weekdayName(iso: Int): String =
        listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")[iso - 1]
}
