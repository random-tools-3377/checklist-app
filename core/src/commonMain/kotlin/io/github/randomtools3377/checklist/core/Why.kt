package io.github.randomtools3377.checklist.core

/**
 * A goal's "Why it's good" ([Goal.why]): the user's own reason for it, in their own words, like
 * "so I sleep better". Reminders are about the task and why it is good, not the points (decided
 * after 0.71.0): a goal's reminders ([Alerts.goalWords]) and its late nudges ([Nudges]) lead
 * with it, and no longer say what the goal pays. It is optional, and the app never writes one
 * itself, the AI helper included. A private goal behind the privacy lock never shows it outside
 * the app, and no widget shows it.
 */
object Why {
    /** The most it can be, in characters. */
    const val LENGTH = 120

    /** What the user typed, tidied: on one line, without spaces round it, null when empty. Their words are kept as they are. */
    fun tidy(typed: String?): String? =
        typed?.replace(Regex("\\s+"), " ")?.trim()?.take(LENGTH)?.trim()?.takeIf { it.isNotEmpty() }

    /** [why] as a sentence's end: a full stop after it unless it ends in its own punctuation, an emoji or a bracket. */
    fun sentence(why: String): String = if (why.last().isLetterOrDigit()) "$why." else why
}
