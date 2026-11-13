package io.github.randomtools3377.checklist.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.SwipeTick
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A card on Today that can be slid to the right to do what its button does (see SwipeTick in the
 * rules). The card follows the finger, with [label] showing behind it; let go past a third of its
 * width and [onSwipe] runs, once, and the card goes back to its place. A shorter slide does
 * nothing. Only sideways drags are taken, so the list still scrolls up and down, and a slide to
 * the left does nothing at all. While not [enabled] it is just the card.
 */
@Composable
internal fun SwipeToTick(label: String, enabled: Boolean, onSwipe: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val swiped by rememberUpdatedState(onSwipe)
    val calm by rememberUpdatedState(LocalCalm.current)
    // No longer something to swipe, like a goal ticked elsewhere mid-slide: back in its place.
    LaunchedEffect(enabled) { if (!enabled && !offset.isRunning) offset.snapTo(0f) }
    Box(Modifier.onSizeChanged { width = it.width }) {
        if (offset.value > 0f) {
            // What letting go will do, behind the card. The button says the same to a screen reader.
            Box(
                Modifier
                    .matchParentSize()
                    .clip(CardDefaults.shape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clearAndSetSemantics {},
            ) {
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = if (SwipeTick.crossed(offset.value, width)) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp),
                )
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .then(
                    if (!enabled) Modifier else Modifier.pointerInput(Unit) {
                        // Where the card is, kept here so quick moves add up without waiting on a frame.
                        var at = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { at = offset.value },
                            onDragEnd = {
                                val crossed = SwipeTick.crossed(at, width)
                                // Back to its place: sprung, or at once with less motion.
                                scope.launch { if (calm) offset.snapTo(0f) else offset.animateTo(0f, spring()) }
                                if (crossed) swiped()
                            },
                            onDragCancel = { scope.launch { offset.snapTo(0f) } },
                        ) { change, dragged ->
                            change.consume()
                            at = (at + dragged).coerceIn(0f, width.toFloat())
                            val to = at
                            scope.launch { offset.snapTo(to) }
                        }
                    },
                ),
        ) { content() }
    }
}
