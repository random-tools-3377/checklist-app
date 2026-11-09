package io.github.randomtools3377.checklist.core

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/** How the user felt on [day], from 1 (low) to 5 (great), with an optional note. Once a day. */
@Serializable
data class Mood(val day: LocalDate, val score: Int, val note: String? = null, val at: Long = 0)

object Moods {
    val FACES = listOf("😞", "🙁", "😐", "🙂", "😄")

    fun face(score: Int): String = FACES[(score - 1).coerceIn(0, FACES.lastIndex)]

    /** A note is kept short. */
    const val NOTE_LENGTH = 200
}
