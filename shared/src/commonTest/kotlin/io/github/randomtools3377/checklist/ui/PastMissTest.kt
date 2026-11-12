package io.github.randomtools3377.checklist.ui

import io.github.randomtools3377.checklist.core.Lapse
import io.github.randomtools3377.checklist.core.LapseKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reason
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A past skip or miss opened from the review or the points history: what it says, and when it asks to unlock first. */
class PastMissTest {
    private val day = LocalDate(2026, 11, 10)

    private fun lapse(kind: LapseKind = LapseKind.MISS, period: Period? = Period.DAILY, reason: Reason? = null, text: String? = null, note: String? = null) = Lapse(
        goalId = 1, kind = kind, day = day, period = period, periodStart = day, at = 0, overLimit = false,
        reason = reason, reasonText = text, note = note, label = "Read",
    )

    private fun past(lapse: Lapse, slip: Boolean = false) = PastMiss(lapse, private = false, slip = slip, canForget = true)

    @Test
    fun itSaysWhatHappenedAndWhen() {
        assertEquals("Missed 10 Nov", pastMissLine(past(lapse()), "10 Nov"))
        assertEquals("You slipped on 10 Nov", pastMissLine(past(lapse(), slip = true), "10 Nov"))
        assertEquals("Skipped 10 Nov", pastMissLine(past(lapse(kind = LapseKind.SKIP)), "10 Nov"))
        assertEquals("Its deadline passed on 10 Nov", pastMissLine(past(lapse(period = null)), "10 Nov"))
    }

    @Test
    fun onePutRightSaysSoInItsOwnWords() {
        assertEquals("You said you forgot to tick it, so it counts as done.", pastMissPutRight(past(lapse())))
        assertEquals(
            "You said you forgot to tick it, so it counts as done, and what it cost came back.",
            pastMissPutRight(past(lapse().copy(overLimit = true, cost = 2))),
        )
        // A slip that wasn't one was never a forgotten tick.
        assertEquals("You said you didn't slip, so it counts as avoided.", pastMissPutRight(past(lapse(), slip = true)))
    }

    @Test
    fun theReviewListsItsReasonAndNote() {
        assertEquals("No reason yet", missSaid(lapse()))
        assertEquals("Ill", missSaid(lapse(reason = Reason.ILL)))
        assertEquals("Ill · “flu”", missSaid(lapse(reason = Reason.ILL, note = "flu")))
        assertEquals("Out late", missSaid(lapse(reason = Reason.OTHER, text = "Out late")))
    }

    @Test
    fun itAsksToUnlockFirstWhileReasonsAndNotesAreLocked() {
        assertTrue(pastMissLocked(Locks(reasons = true), unlocked = false, private = false))
        assertFalse(pastMissLocked(Locks(reasons = true), unlocked = true, private = false), "open until the user leaves the app")
        assertFalse(pastMissLocked(Locks(review = true, history = true), unlocked = false, private = false), "only the lock on reasons and notes")
        // A private goal's, while private goals are locked, would name it.
        assertTrue(pastMissLocked(Locks(privateGoals = true), unlocked = false, private = true))
        assertFalse(pastMissLocked(Locks(privateGoals = true), unlocked = false, private = false))
        assertFalse(pastMissLocked(Locks(), unlocked = false, private = true))
    }
}
