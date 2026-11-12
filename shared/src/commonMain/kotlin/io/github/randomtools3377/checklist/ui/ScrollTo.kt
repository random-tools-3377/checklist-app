package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Where a page opened from a Settings search scrolls to, once, when it opens: the place marked
 * [place] with [scrollPlace]. With no place, the page opens at the top as usual.
 */
internal class ScrollTarget(private val place: String?, private val scroll: ScrollState, private val scope: CoroutineScope) {
    private var done = place == null

    fun placed(at: String, y: Float) {
        if (done || at != place) return
        done = true
        scope.launch { scroll.animateScrollTo(y.toInt().coerceAtLeast(0)) }
    }
}

@Composable
internal fun rememberScrollTarget(place: String?, scroll: ScrollState): ScrollTarget {
    val scope = rememberCoroutineScope()
    return remember(place, scroll) { ScrollTarget(place, scroll, scope) }
}

/** Marks a place [key] on a page that scrolls with [target]'s scroll state, as a direct child of what scrolls. */
internal fun Modifier.scrollPlace(target: ScrollTarget, key: String): Modifier =
    onGloballyPositioned { target.placed(key, it.positionInParent().y) }
