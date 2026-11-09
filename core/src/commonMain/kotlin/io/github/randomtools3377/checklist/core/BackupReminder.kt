package io.github.randomtools3377.checklist.core

/**
 * The backup reminder. Phone backup is off for this app, so the export file is the only copy;
 * the app reminds the user when the last one is getting old.
 */
object BackupReminder {
    /** A backup older than this many days is due for another. */
    const val DAYS = 7

    private const val DAY_MILLIS = 86_400_000L

    /**
     * Whether to remind now: there is something worth keeping, and the last backup (an export or
     * an automatic one) was over [DAYS] days ago, or never. [since] is when the app started
     * tracking, so a new install isn't nagged on its first day.
     */
    fun due(lastBackup: Long?, since: Long, now: Long, hasData: Boolean): Boolean {
        if (!hasData) return false
        val from = lastBackup ?: since
        return now - from >= DAYS * DAY_MILLIS
    }

    /** Whole days since [lastBackup], for "Last backup 9 days ago". */
    fun daysSince(lastBackup: Long, now: Long): Int = ((now - lastBackup) / DAY_MILLIS).toInt().coerceAtLeast(0)
}
