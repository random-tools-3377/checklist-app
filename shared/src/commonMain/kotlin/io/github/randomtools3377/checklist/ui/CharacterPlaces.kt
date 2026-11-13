package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.Checkins
import io.github.randomtools3377.checklist.core.Expression
import io.github.randomtools3377.checklist.core.Figure
import io.github.randomtools3377.checklist.core.Look
import io.github.randomtools3377.checklist.core.Mark
import io.github.randomtools3377.checklist.core.Outfit
import io.github.randomtools3377.checklist.core.Scene
import io.github.randomtools3377.checklist.data.nowLocal

/*
 * The character on screens other than Today and its own page: the misses, penalty and gap
 * check-in screens, the focus timer, the big celebrations and the setup. Its face, move and
 * line for each come from the core (Checkins.at), and it never names a goal or a mood.
 */

/**
 * The user's own character, for any screen: how it looks, what it wears, and whether it talks
 * ("Character check-ins"). Null while "Character and skills" is off, and then it is drawn nowhere.
 */
data class OwnCharacter(val look: Look, val outfit: Outfit, val talks: Boolean)

val LocalCharacter = compositionLocalOf<OwnCharacter?> { null }

/** How long a hard screen stays once it is put right, so the character can be seen to brighten. */
internal const val BRIGHTEN_MILLIS = 900L

/** The shapes of the drawing as paths, parsed once. */
internal fun pathsOf(marks: List<Mark>): Map<Mark.Path, androidx.compose.ui.graphics.Path> =
    marks.filterIsInstance<Mark.Path>().distinct().associateWith { PathParser().parsePathString(it.d).toPath() }

/** Draws [marks] on the drawing's own grid; [paths] holds each path, parsed (see [pathsOf]). */
internal fun DrawScope.drawMarks(marks: List<Mark>, paths: Map<Mark.Path, androidx.compose.ui.graphics.Path>) {
    marks.forEach { mark ->
        when (mark) {
            is Mark.Circle -> {
                mark.fill?.let { drawCircle(Color(it), mark.r, Offset(mark.cx, mark.cy)) }
                mark.stroke?.let { drawCircle(Color(it), mark.r, Offset(mark.cx, mark.cy), style = Stroke(mark.width)) }
            }
            is Mark.Oval -> drawOval(Color(mark.fill), Offset(mark.cx - mark.rx, mark.cy - mark.ry), Size(mark.rx * 2, mark.ry * 2))
            is Mark.Box -> drawRoundRect(Color(mark.fill), Offset(mark.x, mark.y), Size(mark.w, mark.h), CornerRadius(mark.radius))
            is Mark.Path -> {
                val path = paths.getValue(mark)
                mark.fill?.let { drawPath(path, Color(it)) }
                mark.stroke?.let { drawPath(path, Color(it), style = Stroke(mark.width, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
        }
    }
}

/**
 * The character's head as a picture, [pixels] wide and high, on a clear background: for places
 * that can only show an image, like a home-screen widget.
 */
fun headImage(look: Look, outfit: Outfit, expression: Expression, pixels: Int): ImageBitmap {
    val marks = Figure.head(look, outfit, expression)
    val paths = pathsOf(marks)
    val image = ImageBitmap(pixels, pixels)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(pixels.toFloat(), pixels.toFloat())) {
        scale(size.width / Figure.HEAD_SIZE, pivot = Offset.Zero) {
            translate(-Figure.HEAD_LEFT, -Figure.HEAD_TOP) { drawMarks(marks, paths) }
        }
    }
    return image
}

/** A line the character says, in a soft bubble. */
@Composable
internal fun SpeechBubble(line: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(line, style = MaterialTheme.typography.bodyMedium, modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/**
 * The character standing on another screen, [size] high, with the face and the move for
 * [scene]. Draws nothing while "Character and skills" is off. A new [scene] plays its move.
 */
@Composable
fun SceneCharacter(scene: Scene, size: Dp, modifier: Modifier = Modifier) {
    val own = LocalCharacter.current ?: return
    val say = remember(scene) { Checkins.at(scene, nowLocal().date) }
    Character(own.look, own.outfit, say.expression, say.move, size, modifier.testTag("scene-character:${scene.name}"), moveKey = scene)
}

/**
 * The character keeping the user company at the top of a screen: small, with the face for
 * [scene] and, with "Character check-ins" on, one line beside it. Smaller on a short window.
 * Draws nothing while "Character and skills" is off.
 */
@Composable
fun CompanyRow(scene: Scene, modifier: Modifier = Modifier) {
    val own = LocalCharacter.current ?: return
    val say = remember(scene) { Checkins.at(scene, nowLocal().date) }
    Row(
        modifier.fillMaxWidth().testTag("company:${scene.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Character(own.look, own.outfit, say.expression, say.move, if (isShort()) 44.dp else 64.dp, moveKey = scene)
        if (own.talks) SpeechBubble(say.line, Modifier.testTag("company-line"))
    }
}
