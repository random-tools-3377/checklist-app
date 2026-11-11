package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * How the user felt on [day], from 1 (low) to 5 (great), with an optional note. Once a day. Once
 * everything due that day is done, a second check-in asks again: [after] is that answer, if given
 * (format 26), and [afterAt] when it was given, in epoch milliseconds.
 */
@Serializable
data class Mood(
    val day: LocalDate,
    val score: Int,
    val note: String? = null,
    val at: Long = 0,
    val after: Int? = null,
    val afterAt: Long? = null,
) {
    /** How the day ended up feeling: the mood once everything was done if given, else the first one. */
    val last: Int get() = after ?: score
}

object Moods {
    val FACES = listOf("😞", "🙁", "😐", "🙂", "😄")

    fun face(score: Int): String = FACES[(score - 1).coerceIn(0, FACES.lastIndex)]

    /** A note is kept short. */
    const val NOTE_LENGTH = 200
}
