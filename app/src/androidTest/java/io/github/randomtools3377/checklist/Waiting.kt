package io.github.randomtools3377.checklist

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.printToString

/**
 * Waits like waitUntil, but when time runs out the failure says what was on the screen, so a
 * failing run on CI shows where the app was.
 */
fun ComposeTestRule.waitFor(timeoutMs: Long = 10_000, condition: () -> Boolean) {
    try {
        waitUntil(timeoutMs, condition)
    } catch (e: Throwable) {
        val screen = try {
            onAllNodes(isRoot(), useUnmergedTree = true).printToString()
        } catch (t: Throwable) {
            "(the screen could not be read: $t)"
        }
        throw AssertionError("Still waiting after ${timeoutMs / 1000} s. On the screen:\n$screen", e)
    }
}
