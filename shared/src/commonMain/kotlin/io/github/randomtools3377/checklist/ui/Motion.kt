package io.github.randomtools3377.checklist.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/*
 * Everything that moves in the app goes through here, so it all follows one rule: "Less motion"
 * in Settings → Appearance (see LessMotion in the rules), which starts on the device's own setting.
 * While it is on, LocalCalm is true and each helper below simply shows the new state: bars and
 * numbers change at once, cards appear and go without folding, rows take their new place without
 * gliding, screens swap without sliding, and nothing pops.
 */

/** Whether to hold still: "Less motion" is on, or the device asks for it and the user hasn't said otherwise. */
val LocalCalm = staticCompositionLocalOf { false }

/** How long the app's small movements take, in milliseconds. */
internal object MotionTimes {
    const val BAR = 450
    const val COUNT = 500
    const val FOLD = 220
    const val SCREEN = 240
    const val ROLL = 180
}

/**
 * A progress bar that slides to its new length. Every bar in the app is one of these. With less
 * motion it is the plain bar, at its new length straight away.
 */
@Composable
fun SmoothBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
) {
    val target = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    val shown = if (LocalCalm.current) target else {
        val moving by animateFloatAsState(target, tween(MotionTimes.BAR, easing = FastOutSlowInEasing), label = "bar")
        moving
    }
    LinearProgressIndicator(progress = { shown }, modifier = modifier, color = color, trackColor = trackColor)
}

/**
 * A number that counts up or down to its new value, like the balance after a tick. [text] turns
 * the number into what is shown ("126 points"). A screen reader, and a test, always get the final
 * value, never the numbers rolling past. With less motion it is plain text.
 */
@Composable
fun CountingText(
    value: Int,
    text: (Int) -> String,
    tag: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    if (LocalCalm.current) {
        Text(text(value), color = color, fontSize = fontSize, fontWeight = fontWeight, style = style, modifier = modifier.testTag(tag))
        return
    }
    val shown by animateIntAsState(value, tween(MotionTimes.COUNT, easing = FastOutSlowInEasing), label = "count")
    val final = text(value)
    Text(
        text(shown),
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        style = style,
        modifier = modifier.testTag(tag).clearAndSetSemantics { this.text = AnnotatedString(final) },
    )
}

/**
 * A short text with a count in it, like "2 of 8", that rolls to its next value: up when [order]
 * grows, and back down when it shrinks, as when a tick is taken back. With less motion it is
 * plain text.
 */
@Composable
fun RollingText(
    text: String,
    order: Int,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = LocalTextStyle.current,
) {
    if (LocalCalm.current) {
        Text(text, style = style, color = color, modifier = modifier)
        return
    }
    AnimatedContent(
        targetState = order to text,
        transitionSpec = {
            val up = targetState.first >= initialState.first
            val spec = tween<androidx.compose.ui.unit.IntOffset>(MotionTimes.ROLL)
            (slideInVertically(spec) { if (up) it else -it } + fadeIn(tween(MotionTimes.ROLL)))
                .togetherWith(slideOutVertically(spec) { if (up) -it else it } + fadeOut(tween(MotionTimes.ROLL)))
                .using(SizeTransform(clip = true))
        },
        // The same count with other words (a renamed goal) changes without rolling.
        contentKey = { it.first },
        modifier = modifier,
        label = "roll",
    ) { (_, shown) ->
        Text(shown, style = style, color = color)
    }
}

/**
 * A small bounce when [count] goes up, as a tick lands, and a small dip when it goes down, as
 * one is taken back. Nothing on first showing, and nothing with less motion.
 */
@Composable
fun Modifier.pop(count: Int): Modifier {
    if (LocalCalm.current) return this
    val scale = remember { Animatable(1f) }
    val last = remember { intArrayOf(count) }
    LaunchedEffect(count) {
        val before = last[0]
        last[0] = count
        if (count > before) {
            scale.snapTo(0.82f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        } else if (count < before) {
            scale.animateTo(0.9f, tween(90))
            scale.animateTo(1f, tween(140))
        }
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** [pop] for something that is done or not. */
@Composable
fun Modifier.pop(done: Boolean): Modifier = pop(if (done) 1 else 0)

/** A colour that fades to its new value, like a tick button filling in. With less motion it changes at once. */
@Composable
fun fading(color: Color): Color {
    if (LocalCalm.current) return color
    val shown by animateColorAsState(color, tween(MotionTimes.FOLD), label = "fade")
    return shown
}

/**
 * A row of a lazy list that glides to its new place when the list changes order, and fades in
 * and out as it comes and goes. It needs a stable key. With less motion rows simply take their
 * new place.
 */
@Composable
fun LazyItemScope.glide(): Modifier = if (LocalCalm.current) Modifier else Modifier.animateItem()

/**
 * Something that comes and goes on a screen, like a card on Today: it folds open and closed so
 * what is below doesn't lurch. With less motion it is simply there or not.
 */
@Composable
fun Fold(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (LocalCalm.current) {
        if (visible) Box(modifier) { content() }
        return
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(tween(MotionTimes.FOLD)) + fadeIn(tween(MotionTimes.FOLD)),
        exit = shrinkVertically(tween(MotionTimes.FOLD)) + fadeOut(tween(MotionTimes.FOLD)),
        label = "fold",
    ) { content() }
}

/** [Fold] for something shown while there is a [value]; it keeps showing the last one while it folds away. */
@Composable
fun <T : Any> Fold(value: T?, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    if (LocalCalm.current) {
        if (value != null) Box(modifier) { content(value) }
        return
    }
    val last = remember { arrayOfNulls<Any>(1) }
    if (value != null) last[0] = value
    Fold(visible = value != null, modifier = modifier) {
        @Suppress("UNCHECKED_CAST")
        (last[0] as T?)?.let { content(it) }
    }
}

/** A screen in the stack: its route, and how deep it sits, to tell opening a page from going back. */
internal data class ScreenStep(val route: String, val depth: Int)

/** How one screen gives way to the next: a page opened slides in from the side, Back slides it away, and tabs simply swap. */
internal fun screenMove(from: ScreenStep, to: ScreenStep, calm: Boolean): ContentTransform {
    val none = EnterTransition.None togetherWith ExitTransition.None
    if (calm || from.route == to.route) return none
    val time = MotionTimes.SCREEN
    return when {
        to.depth > from.depth ->
            (slideInHorizontally(tween(time)) { it / 5 } + fadeIn(tween(time))) togetherWith fadeOut(tween(time / 2))
        to.depth < from.depth ->
            fadeIn(tween(time)) togetherWith (slideOutHorizontally(tween(time)) { it / 5 } + fadeOut(tween(time)))
        // One tab to another: the bar stays put, so nothing moves.
        to.depth == 1 -> none
        // One page in another's place, like a copy opening in its editor.
        else -> fadeIn(tween(time)) togetherWith fadeOut(tween(time / 2))
    }
}

/**
 * Shows the screen for [route], sliding to the next one as the stack changes. With less motion,
 * from the start, screens are shown directly, as they always were; turned on later, the next
 * screen opened starts sliding. The switch never rebuilds the screen it is flipped on.
 * [screenOf] names the screen a route belongs to, for routes that share one (the tabs).
 */
@Composable
internal fun ScreenSwitch(route: String, depth: Int, screenOf: (String) -> Any = { it }, content: @Composable (String) -> Unit) {
    val calm = LocalCalm.current
    val mode = remember { object { var sliding = !calm; var last = route } }
    if (mode.last != route) {
        mode.last = route
        if (!calm) mode.sliding = true
    }
    if (!mode.sliding) {
        content(route)
        return
    }
    AnimatedContent(
        targetState = ScreenStep(route, depth),
        transitionSpec = { screenMove(initialState, targetState, calm).using(SizeTransform(clip = false)) },
        // Routes that are one screen, like the tabs with their shared bar, change in place.
        contentKey = { screenOf(it.route) },
        label = "screen",
    ) { step -> content(step.route) }
}

/**
 * Faint card shapes for the moment before a screen's data arrives, so it doesn't flash blank.
 * They say nothing to a screen reader.
 */
@Composable
fun PlaceholderCards(modifier: Modifier = Modifier, count: Int = 3, height: Dp = 72.dp) {
    Column(modifier.fillMaxWidth().padding(16.dp).clearAndSetSemantics {}.testTag("placeholder")) {
        repeat(count) { index ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .height(if (index == 0) height + 24.dp else height)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
            )
        }
    }
}

/** One keyed row of a lazy list that glides to its new place and fades in and out (see [glide]). */
internal fun androidx.compose.foundation.lazy.LazyListScope.glidingItem(key: Any, content: @Composable () -> Unit) =
    item(key = key) { Column(glide()) { content() } }

/** Keyed rows of a lazy list that glide to their new places when the list changes order (see [glide]). */
internal fun <T> androidx.compose.foundation.lazy.LazyListScope.glidingItems(
    items: List<T>,
    key: (T) -> Any,
    content: @Composable (T) -> Unit,
) = items(items.size, key = { index -> key(items[index]) }) { index -> Column(glide()) { content(items[index]) } }

/** A part of a screen that grows and shrinks smoothly as what is in it comes and goes. With less motion it simply changes size. */
@Composable
fun Modifier.gentleSize(): Modifier =
    if (LocalCalm.current) this else this.animateContentSize(tween(MotionTimes.FOLD))
