package io.github.randomtools3377.checklist.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import io.github.randomtools3377.checklist.core.DragOrder
import kotlinx.coroutines.delay

/*
 * Drag to reorder: hold a row and drag it to its new place. The row follows the finger, the rows
 * it passes step aside, and letting go calls the list's own move once, with where the row came
 * from and where it landed (the index maths is DragOrder, in the rules). The arrows on each row
 * stay, for screen readers and as another way. A row can be dragged as far as the screen shows;
 * for further, drop it, scroll, and drag again, or use the arrows.
 */

/** What outlives one order of the list: whether rows should hold off gliding, just after a drop. */
@Stable
internal class DragMemory {
    /** True from a drop until the list has taken its new order: the rows are already where they belong. */
    var settling by mutableStateOf(false)
}

/** One list being reordered by dragging, for one order of its rows. */
@Stable
internal class DragList(private val count: Int, val memory: DragMemory, private val onMove: (from: Int, to: Int) -> Unit) {
    /** The row being dragged, or dropped and waiting for the list to follow; -1 for none. */
    var from by mutableIntStateOf(-1)
        private set

    /** The place it hovers over. */
    var target by mutableIntStateOf(-1)
        private set

    /** How far the dragged row is from where it started, in pixels. */
    var dy by mutableFloatStateOf(0f)
        private set

    /** Whether a finger is still on the row. */
    var held by mutableStateOf(false)
        private set

    private val heights = HashMap<Int, Int>()

    fun measured(index: Int, height: Int) {
        heights[index] = height
    }

    private fun heightOf(index: Int): Int = heights[index] ?: heights[from] ?: 0

    private fun allHeights(): List<Int> = List(count) { heightOf(it) }

    fun start(index: Int) {
        if (from >= 0 && !held) return
        from = index
        target = index
        dy = 0f
        held = true
    }

    fun drag(by: Float) {
        if (!held) return
        dy += by
        target = DragOrder.target(from, dy, allHeights())
    }

    /** Let go: the row settles in the place it hovered over, and the list is asked to move it there, once. */
    fun drop() {
        if (!held) return
        held = false
        if (target == from || from < 0) {
            reset()
            return
        }
        // Exactly in its new place, until the list itself has it there.
        var rest = 0
        if (target > from) for (i in from + 1..target) rest += heightOf(i) else for (i in target until from) rest -= heightOf(i)
        dy = rest.toFloat()
        memory.settling = true
        onMove(from, target)
    }

    fun reset() {
        held = false
        from = -1
        target = -1
        dy = 0f
    }

    /** How far the row at [index] is drawn from its own place. */
    fun offsetOf(index: Int): Float = when {
        from < 0 -> 0f
        index == from -> dy
        else -> DragOrder.shift(index, from, target, heightOf(from)).toFloat()
    }
}

/**
 * The drag state for a list whose rows are [keys], in order. [onMove] is called once per drop,
 * with the row's old and new index in [keys]. A new order of the list starts a fresh state, so
 * rows are drawn in their own places again the moment the list has moved them.
 */
@Composable
internal fun rememberDragList(keys: List<Any>, onMove: (from: Int, to: Int) -> Unit): DragList {
    val memory = remember { DragMemory() }
    val move by rememberUpdatedState(onMove)
    val list = remember(keys) { DragList(keys.size, memory) { from, to -> move(from, to) } }
    // The list has its new order: a frame later rows may glide again (they are already in place).
    LaunchedEffect(keys) {
        if (memory.settling) {
            withFrameNanos { }
            memory.settling = false
        }
    }
    // A move the list didn't take (nothing changed): the row goes back.
    LaunchedEffect(list, list.from, list.held) {
        if (list.from >= 0 && !list.held) {
            delay(1500)
            list.reset()
            memory.settling = false
        }
    }
    return list
}

/**
 * Makes a row of [list] one that can be held and dragged to another place, while [enabled]. It
 * goes on the row's outermost composable (in a lazy list, the item's root), so the dragged row
 * draws over its neighbours.
 */
@Composable
internal fun Modifier.dragRow(list: DragList, index: Int, enabled: Boolean = true): Modifier =
    dragPlace(list, index).dragHandle(list, index, enabled)

/** The part of [dragRow] that draws the row where it is dragged to, and its neighbours stepping aside. */
@Composable
internal fun Modifier.dragPlace(list: DragList, index: Int): Modifier {
    val mine = list.from == index
    val calm = LocalCalm.current
    // Neighbours step aside smoothly; the dragged row itself follows the finger exactly.
    val aside by animateFloatAsState(
        if (mine) 0f else list.offsetOf(index),
        if (calm || list.from < 0) snap() else tween(MotionTimes.ROLL),
        label = "aside",
    )
    return this
        .zIndex(if (mine) 1f else 0f)
        .onSizeChanged { list.measured(index, it.height) }
        .graphicsLayer {
            translationY = when (list.from) {
                -1 -> 0f
                index -> list.dy
                else -> aside
            }
            if (list.from == index && list.held) {
                scaleX = 1.02f
                scaleY = 1.02f
                shadowElevation = 8f
            }
        }
}

/**
 * The part of [dragRow] that takes the hold and the drag. On its own it makes a handle: in an
 * editor it goes on a row's arrows, since holding a text box selects its text.
 */
internal fun Modifier.dragHandle(list: DragList, index: Int, enabled: Boolean = true): Modifier =
    if (!enabled) this else this.pointerInput(list, index) {
        detectDragGesturesAfterLongPress(
            onDragStart = { list.start(index) },
            onDragEnd = { list.drop() },
            onDragCancel = { list.reset() },
        ) { change, dragged ->
            change.consume()
            list.drag(dragged.y)
        }
    }

/** Whether rows of a lazy list being dragged may glide just now: not right after a drop, when they are already in place. */
internal val DragList.glides: Boolean get() = !memory.settling
