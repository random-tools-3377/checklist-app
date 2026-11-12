package io.github.randomtools3377.checklist.ui

import io.github.randomtools3377.checklist.core.Streaks
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What Settings says about the evening check, and the streak lines of the review's stats. */
class EveningAndStreakWordsTest {
    @Test
    fun theClockSaysTheTimeMustBeBeforeTheDayEnds() {
        assertEquals("Pick a time from noon on, before your day ends at midnight.", eveningCheckRule(0))
        assertEquals("Pick a time from noon on, before your day ends at 4 am.", eveningCheckRule(4))
        // A time it can't take: said under the clock, whichever side of the evening it falls.
        assertTrue(eveningCheckNotAllowed(LocalTime(8, 0), 0).endsWith(" isn't between noon and midnight, when your day ends."))
        assertTrue(eveningCheckNotAllowed(LocalTime(5, 0), 4).endsWith(" isn't between noon and 4 am, when your day ends."))
    }

    @Test
    fun theHintSaysWhetherItComes() {
        assertTrue(eveningCheckHint(null, 0).contains("off until you pick a time"))
        assertTrue(eveningCheckHint(LocalTime(21, 0), 0).contains("Nothing comes on a day when everything is done"))
        // Set for 1:30 am while the day ended at 4 am, and the day's end has since moved to midnight.
        assertTrue(eveningCheckHint(LocalTime(1, 30), 4).contains("lists what is still open today"))
        assertEquals(
            "Your day now ends at midnight, before this time, so it doesn't come. Pick an earlier time.",
            eveningCheckHint(LocalTime(1, 30), 0),
        )
    }

    private val now = Streaks(done = 12, noMisses = 5, noSkips = 3, withinLimit = 20, perfect = 2)
    private val best = Streaks(done = 40, noMisses = 21, noSkips = 31, withinLimit = 60, perfect = 9)

    @Test
    fun todaysStretchShowsTheBestBesideTheCurrentStreak() {
        val lines = streakStats(now, best, within = null)
        assertEquals(
            listOf(
                "Current main streak" to "12 days · best 40 days",
                "Best miss-free run" to "21 days",
                "Best skip-free run" to "31 days",
                "Best run within the limit" to "60 days",
                "Best run of perfect days" to "9 days",
            ),
            lines.map { it.label to it.value },
        )
        assertEquals(
            listOf("review-streak:main", "review-streak:miss-free", "review-streak:skip-free", "review-streak:within-limit", "review-streak:perfect"),
            lines.map { it.tag },
        )
        // Until the best is worked out, the current streak shows on its own.
        assertEquals(listOf("Current main streak" to "12 days"), streakStats(now, null, null).map { it.label to it.value })
        assertTrue(streakStats(null, null, null).isEmpty())
    }

    @Test
    fun aBestRunIsNeverShownBelowTheStreakRunningNow() {
        // Streak shields can keep the main streak going past a day that would break it.
        val shielded = now.copy(done = 45)
        assertEquals("45 days · best 45 days", streakStats(shielded, best, null).first().value)
        assertEquals("1 day · best 1 day", streakStats(Streaks(1, 0, 0, 0, 0), Streaks(0, 0, 0, 0, 0), null).first().value)
    }

    @Test
    fun aPastStretchShowsTheLongestWithinItInsteadOfTodaysNumbers() {
        val within = Streaks(done = 5, noMisses = 7, noSkips = 1, withinLimit = 7, perfect = 2)
        val lines = streakStats(now = null, best = null, within = within)
        assertEquals(
            listOf(
                "Longest main streak in this stretch" to "5 days",
                "Longest miss-free run in this stretch" to "7 days",
                "Longest skip-free run in this stretch" to "1 day",
                "Longest run within the limit in this stretch" to "7 days",
                "Longest run of perfect days in this stretch" to "2 days",
            ),
            lines.map { it.label to it.value },
        )
        // Today's numbers stay out of it even when they are known.
        assertEquals(lines, streakStats(now, best, within))
    }

    @Test
    fun aStreakThatNeverRanHasNoLineButTheMainStreakAlwaysDoes() {
        val none = Streaks(0, 0, 0, 0, 0)
        // Someone new: the current main streak alone, with no best to put beside it yet.
        assertEquals(listOf("Current main streak" to "0 days"), streakStats(none, none, null).map { it.label to it.value })
        // Only the runs there were.
        val some = none.copy(done = 4, noSkips = 9)
        assertEquals(
            listOf("Current main streak" to "0 days · best 4 days", "Best skip-free run" to "9 days"),
            streakStats(none, some, null).map { it.label to it.value },
        )
        assertEquals(
            listOf("Longest main streak in this stretch" to "0 days", "Longest run within the limit in this stretch" to "3 days"),
            streakStats(null, null, none.copy(withinLimit = 3)).map { it.label to it.value },
        )
        assertEquals(listOf("review-streak:main", "review-streak:skip-free"), streakStats(none, some, null).map { it.tag })
    }
}
