package io.github.randomtools3377.checklist.reminders

import android.content.Context

/**
 * The focus timer, kept in the app's own settings so it carries on while the app is closed.
 * Changes are made one at a time, so when the time is up, either the app or the alarm takes
 * the timer, never both.
 */
object Focus {
    private const val FILE = "focus"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun current(context: Context): FocusState? {
        val p = prefs(context)
        val goal = p.getLong("goal", -1)
        // A break is kept as a timer with no goal (FocusState.BREAK_GOAL).
        if (goal < 0 && goal != FocusState.BREAK_GOAL) return null
        val paused = p.getLong("paused_left", -1)
        return FocusState(goal, p.getLong("ends_at", 0), paused.takeIf { it >= 0 }, p.getLong("total", 0), p.getLong("started_at", 0))
    }

    @Synchronized
    fun start(context: Context, goalId: Long, minutes: Int, now: Long = System.currentTimeMillis()) {
        val total = minutes * 60_000L
        prefs(context).edit().putLong("goal", goalId).putLong("ends_at", now + total).putLong("paused_left", -1).putLong("total", total).putLong("started_at", now).apply()
    }

    @Synchronized
    fun pause(context: Context, now: Long = System.currentTimeMillis()) {
        val state = current(context) ?: return
        if (!state.running) return
        prefs(context).edit().putLong("paused_left", state.left(now)).apply()
    }

    @Synchronized
    fun resume(context: Context, now: Long = System.currentTimeMillis()) {
        val state = current(context) ?: return
        val left = state.pausedLeft ?: return
        prefs(context).edit().putLong("ends_at", now + left).putLong("paused_left", -1).apply()
    }

    @Synchronized
    fun stop(context: Context) {
        prefs(context).edit().clear().apply()
    }

    /** Ends the timer and hands it over: null if there was none, or it was already taken. */
    @Synchronized
    fun take(context: Context): FocusState? {
        val state = current(context) ?: return null
        // Written straight away, so a tick made next can't be made again after a restart.
        prefs(context).edit().clear().commit()
        return state
    }

    /** Like [take], but only a running timer whose time is up by [now]. */
    @Synchronized
    fun takeEnded(context: Context, now: Long = System.currentTimeMillis()): FocusState? {
        val state = current(context)?.takeIf { it.running && it.endsAt <= now } ?: return null
        prefs(context).edit().clear().commit()
        return state
    }

    /** When the alarm should go off for it: its end, while running. */
    fun alarmAt(context: Context): Long? = current(context)?.takeIf { it.running }?.endsAt
}
