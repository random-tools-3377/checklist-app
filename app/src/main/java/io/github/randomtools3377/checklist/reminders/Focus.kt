package io.github.randomtools3377.checklist.reminders

import android.content.Context

/**
 * A focus timer for one goal with a length: it counts down, and when time is up the goal is
 * ticked. Kept on this phone, so it carries on while the app is closed; the alarm then says
 * time is up.
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

object Focus {
    private const val FILE = "focus"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun current(context: Context): FocusState? {
        val p = prefs(context)
        val goal = p.getLong("goal", -1)
        if (goal < 0) return null
        val paused = p.getLong("paused_left", -1)
        return FocusState(goal, p.getLong("ends_at", 0), paused.takeIf { it >= 0 }, p.getLong("total", 0))
    }

    fun start(context: Context, goalId: Long, minutes: Int, now: Long = System.currentTimeMillis()) {
        val total = minutes * 60_000L
        prefs(context).edit().putLong("goal", goalId).putLong("ends_at", now + total).putLong("paused_left", -1).putLong("total", total).apply()
    }

    fun pause(context: Context, now: Long = System.currentTimeMillis()) {
        val state = current(context) ?: return
        if (!state.running) return
        prefs(context).edit().putLong("paused_left", state.left(now)).apply()
    }

    fun resume(context: Context, now: Long = System.currentTimeMillis()) {
        val state = current(context) ?: return
        val left = state.pausedLeft ?: return
        prefs(context).edit().putLong("ends_at", now + left).putLong("paused_left", -1).apply()
    }

    fun stop(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** When the alarm should go off for it: its end, while running. */
    fun alarmAt(context: Context): Long? = current(context)?.takeIf { it.running }?.endsAt
}
