package io.github.randomtools3377.checklist

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
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
            onAllNodes(isRoot(), useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE)
        } catch (t: Throwable) {
            "(the screen could not be read: $t)"
        }
        throw AssertionError("Still waiting after ${timeoutMs / 1000} s. On the screen:\n$screen", e)
    }
}

/**
 * Taps [tag] after scrolling it fully into view. A tap lands on the middle of a button, so a
 * button half below the edge of a small screen would otherwise miss.
 */
fun ComposeTestRule.tap(tag: String) {
    val node = onNodeWithTag(tag)
    try {
        node.performScrollTo()
    } catch (e: Throwable) {
        // Not inside anything that scrolls: it is already where it is.
    }
    node.performClick()
}

/** Scrolls the list tagged [list] to the item holding [tag], then taps it. */
fun ComposeTestRule.tapIn(list: String, tag: String) {
    onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
    tap(tag)
}

/** Whether the list tagged [list] holds [tag], scrolling to it if it does. For waitFor. */
fun ComposeTestRule.shownIn(list: String, tag: String): Boolean = try {
    onNodeWithTag(list).performScrollToNode(hasTestTag(tag))
    true
} catch (e: Throwable) {
    false
}

/** Back to the top of today's list, where the balance is. */
fun ComposeTestRule.toTopOfToday() {
    onNodeWithTag("today-list").performScrollToIndex(0)
}
