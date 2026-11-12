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
    /** When it was started, in epoch milliseconds; 0 when the phone didn't keep it. */
    val startedAt: Long = 0,
) {
    fun left(now: Long): Long = pausedLeft ?: (endsAt - now).coerceAtLeast(0)
    val running: Boolean get() = pausedLeft == null

    /** A break after a focus session: a plain countdown with no goal, never counted as focus time. */
    val isBreak: Boolean get() = goalId == BREAK_GOAL

    /** The time really focused by [now], in milliseconds. The countdown stops while paused, so pauses aren't in it. */
    fun focused(now: Long): Long = io.github.randomtools3377.checklist.core.FocusTime.focusedMillis(totalMillis, left(now))

    companion object {
        /** What stands in for the goal while the timer is a break. */
        const val BREAK_GOAL = -2L
    }
}
