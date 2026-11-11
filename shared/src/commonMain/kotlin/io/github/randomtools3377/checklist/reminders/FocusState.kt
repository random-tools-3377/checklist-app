package io.github.randomtools3377.checklist.reminders

/**
 * A focus timer for one goal with a length: it counts down, and when time is up the goal is
 * ticked. Each phone keeps it, so it carries on while the app is closed.
 */
data class FocusState(
    val goalId: Long,
    /** When it runs out, in epoch milliseconds, while running. */
    val endsAt: Long,
    /** Milliseconds left while paused; null while running. */
    val pausedLeft: Long?,
    val totalMillis: Long,
) {
    fun left(now: Long): Long = pausedLeft ?: (endsAt - now).coerceAtLeast(0)
    val running: Boolean get() = pausedLeft == null
}
