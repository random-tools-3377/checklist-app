package io.github.randomtools3377.checklist.core

/**
 * The best run of each streak: the longest it has ever stood at, over the days it is given. For
 * the review's stats: every day there is for the best ever, or the days of one past week, month
 * or year for the longest streak within that stretch.
 */
object BestStreaks {
    /** Each streak, read off [Streaks]. */
    private val PARTS: List<(Streaks) -> Int> = listOf({ it.done }, { it.noMisses }, { it.noSkips }, { it.withinLimit }, { it.perfect })

    /**
     * The longest run of each streak in [records], which are the streaks' own day records (see
     * [StreakCounter.records]). It holds no rules of its own: each day is put to
     * [StreakCounter.count] beside the last day that counted, looked at from the day after, so a
     * day adds to a streak, leaves it as it is (nothing due) or breaks it exactly as it does for
     * the current streaks. A day still going counts once it qualifies, as it does there.
     */
    fun of(records: List<DayRecord>, settings: StreakSettings): Streaks {
        // For each streak: the last day that counted while its run goes on, how long the run is, and the longest so far.
        val last = arrayOfNulls<DayRecord>(PARTS.size)
        val run = IntArray(PARTS.size)
        val best = IntArray(PARTS.size)
        for (record in records.sortedBy { it.day }) {
            val after = record.day.plusDays(1)
            PARTS.forEachIndexed { i, part ->
                val before = last[i]
                val length = part(StreakCounter.count(listOfNotNull(before, record), after, settings))
                when {
                    // One more than the day before alone: this day counts.
                    length > (if (before == null) 0 else 1) -> {
                        run[i]++
                        last[i] = record
                    }
                    // Not even the day before is reached: this day breaks the run.
                    length == 0 -> {
                        run[i] = 0
                        last[i] = null
                    }
                    // Otherwise it was passed over, like a day with nothing due.
                }
                if (run[i] > best[i]) best[i] = run[i]
            }
        }
        return Streaks(done = best[0], noMisses = best[1], noSkips = best[2], withinLimit = best[3], perfect = best[4])
    }

    /**
     * [best] beside the current streaks [now], for showing: a best run is never below the
     * streak running now. Streak shields can keep the main streak going past a day that would
     * break it, and best runs are counted without them, as badges are.
     */
    fun atLeast(best: Streaks, now: Streaks): Streaks = Streaks(
        done = maxOf(best.done, now.done),
        noMisses = maxOf(best.noMisses, now.noMisses),
        noSkips = maxOf(best.noSkips, now.noSkips),
        withinLimit = maxOf(best.withinLimit, now.withinLimit),
        perfect = maxOf(best.perfect, now.perfect),
    )
}
