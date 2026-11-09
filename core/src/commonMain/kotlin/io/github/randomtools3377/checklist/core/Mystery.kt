package io.github.randomtools3377.checklist.core

import kotlin.random.Random

/**
 * A mystery reward in the shop: the user writes what can come out of it, and each time it is
 * bought one is drawn at random. What comes out waits in the inventory like any reward, and can
 * be returned for the mystery reward's price until it is used.
 */
object Mystery {
    /** The most prizes one mystery reward can hold. */
    const val MAX_PRIZES = 20

    /** The longest a prize's name can be. */
    const val PRIZE_LENGTH = 60

    /** Tidies the prizes the user wrote: trimmed, blank ones and repeats left out. */
    fun tidy(prizes: List<String>): List<String> =
        prizes.map { it.trim().replace(Regex("\\s+"), " ").take(PRIZE_LENGTH).trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(MAX_PRIZES)

    /** One of [prizes], each as likely as the others. */
    fun draw(prizes: List<String>, random: Random = Random.Default): String = prizes[random.nextInt(prizes.size)]

    /** Stored in the database one per line. */
    fun encode(prizes: List<String>): String? = tidy(prizes).takeIf { it.isNotEmpty() }?.joinToString("\n")

    fun decode(text: String?): List<String> = text?.split("\n")?.let { tidy(it) } ?: emptyList()
}
