package io.github.randomtools3377.checklist.reminders

import android.content.Context

/** A snoozed reminder, due to come back [at] (epoch milliseconds). */
data class Snooze(val goalId: Long, val number: Int, val at: Long)

/**
 * Snoozed reminders, kept on this phone until they come back. "Snooze" on a notification puts
 * one here; the alarm shows it again when it is due, unless the goal was done in the meantime.
 */
object Snoozes {
    private const val FILE = "snoozes"
    private const val KEY = "all"

    fun all(context: Context): List<Snooze> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty().mapNotNull { text ->
            val parts = text.split(':')
            if (parts.size != 3) return@mapNotNull null
            val goal = parts[0].toLongOrNull() ?: return@mapNotNull null
            val number = parts[1].toIntOrNull() ?: return@mapNotNull null
            val at = parts[2].toLongOrNull() ?: return@mapNotNull null
            Snooze(goal, number, at)
        }

    private fun save(context: Context, snoozes: List<Snooze>) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putStringSet(KEY, snoozes.map { "${it.goalId}:${it.number}:${it.at}" }.toSet())
            .apply()
    }

    /** Snoozes a goal's reminder until [at], replacing an earlier snooze of the same one. */
    fun add(context: Context, snooze: Snooze) {
        save(context, all(context).filter { it.goalId != snooze.goalId || it.number != snooze.number } + snooze)
    }

    /** Takes out and returns the snoozes due by [until]. */
    fun takeDue(context: Context, until: Long): List<Snooze> {
        val (due, later) = all(context).partition { it.at <= until }
        if (due.isNotEmpty()) save(context, later)
        return due
    }

    fun next(context: Context): Long? = all(context).minOfOrNull { it.at }

    /** Drops every snooze, after a restore, when a goal's id can mean another goal. */
    fun clear(context: Context) = save(context, emptyList())
}
