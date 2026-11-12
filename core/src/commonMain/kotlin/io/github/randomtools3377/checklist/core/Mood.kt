package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * How the user felt on [day], from 1 (low) to 5 (great), with an optional note. Once a day. Once
 * everything due that day is done, a second check-in asks again: [after] is that answer, if given
 * (format 26), and [afterAt] when it was given, in epoch milliseconds. [afterNote] is the note
 * written then, and [factors] the things behind the day's mood, shared by both check-ins, like
 * "Slept well" (format 27). Together they make the mood journal.
 */
@Serializable
data class Mood(
    val day: LocalDate,
    val score: Int,
    val note: String? = null,
    val at: Long = 0,
    val after: Int? = null,
    val afterAt: Long? = null,
    val afterNote: String? = null,
    val factors: List<String> = emptyList(),
) {
    /** How the day ended up feeling: the mood once everything was done if given, else the first one. */
    val last: Int get() = after ?: score
}

object Moods {
    val FACES = listOf("😞", "🙁", "😐", "🙂", "😄")

    fun face(score: Int): String = FACES[(score - 1).coerceIn(0, FACES.lastIndex)]

    /** What a screen reader says for the face of [score]: "Mood 1 of 5, the lowest" up to "Mood 5 of 5, the highest". */
    fun spoken(score: Int): String = "Mood $score of ${FACES.size}" + when (score) {
        1 -> ", the lowest"
        FACES.size -> ", the highest"
        else -> ""
    }

    /** A note is kept short. */
    const val NOTE_LENGTH = 200

    /** A note as it is kept: trimmed, short, and null when blank. */
    fun tidyNote(note: String?): String? = note?.trim()?.take(NOTE_LENGTH)?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * The things that can be behind a mood, offered as chips at both check-ins: the app's own to
 * start with, then the user's list once they change it, kept in the setting [SETTING] one to a
 * line. Taking one off the list leaves it on the days it was picked.
 */
object MoodFactors {
    val BUILT_IN = listOf(
        "Slept well", "Slept badly", "Exercise", "Outdoors", "Friends and family", "Alone time",
        "Work", "Health", "Weather", "Food", "Money", "Screen time", "News",
    )

    const val SETTING = "mood_factors"

    /** A factor's name is kept short. */
    const val LENGTH = 30

    /** One name on one line, spaces tidied and kept short; empty when nothing is left. */
    fun tidy(name: String): String = name.replace(Regex("\\s+"), " ").trim().take(LENGTH).trim()

    /** Names tidied, blanks left out, and each only once, whatever its capitals. */
    fun tidy(names: List<String>): List<String> = names.map { tidy(it) }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }

    /** As kept in the database: one to a line; null for none. */
    fun encode(names: List<String>): String? = tidy(names).takeIf { it.isNotEmpty() }?.joinToString("\n")

    fun decode(text: String?): List<String> = text?.split("\n")?.let { tidy(it) } ?: emptyList()

    /** The list offered at the check-ins: the app's own until the user changes it. */
    fun list(setting: String?): List<String> = if (setting == null) BUILT_IN else decode(setting)

    /** What to keep in [SETTING] for [names]: empty text when the user took every one off. */
    fun encodeList(names: List<String>): String = encode(names) ?: ""

    /** The chips for a day: the list, then any of the day's own that are no longer on it. */
    fun offered(list: List<String>, picked: List<String>): List<String> = tidy(list + picked)

    /** [picked] with [name] taken off if it is there, or added if not. */
    fun toggle(picked: List<String>, name: String): List<String> {
        val clean = tidy(name)
        if (clean.isEmpty()) return tidy(picked)
        return if (picked.any { it.equals(clean, ignoreCase = true) }) {
            tidy(picked).filterNot { it.equals(clean, ignoreCase = true) }
        } else {
            tidy(picked + clean)
        }
    }

    /** Whether [name] is among [picked], whatever its capitals. */
    fun has(picked: List<String>, name: String): Boolean = picked.any { it.equals(name, ignoreCase = true) }

    /** [list] with [name] added at the end, unless it is there already. */
    fun add(list: List<String>, name: String): List<String> = tidy(list + name)

    fun remove(list: List<String>, name: String): List<String> = tidy(list).filterNot { it.equals(name, ignoreCase = true) }

    /** [list] with any of the app's own that were taken off put back, at the end. */
    fun withBuiltIns(list: List<String>): List<String> = tidy(list + BUILT_IN)
}
