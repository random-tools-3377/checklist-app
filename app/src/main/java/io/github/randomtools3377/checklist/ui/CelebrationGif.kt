package io.github.randomtools3377.checklist.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.randomtools3377.checklist.ChecklistApplication
import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.data.CelebrationFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Context.celebrationFiles(): CelebrationFiles =
    (applicationContext as ChecklistApplication).container.celebrationFiles

/**
 * Loads a celebration GIF: one that comes with the app, or one the user picked. It moves on
 * Android 9 and later; older phones show its first frame.
 */
private fun loadGif(context: Context, celebration: String): Drawable? = try {
    val builtIn = Celebration.builtIn(celebration)
    val own = Celebration.ownFile(celebration)?.let { context.celebrationFiles().file(it) }?.takeIf { it.isFile }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = when {
            builtIn != null -> ImageDecoder.createSource(context.assets, "celebrations/$builtIn.gif")
            own != null -> ImageDecoder.createSource(own)
            else -> null
        }
        source?.let { ImageDecoder.decodeDrawable(it) }
    } else {
        val stream = when {
            builtIn != null -> context.assets.open("celebrations/$builtIn.gif")
            own != null -> own.inputStream()
            else -> null
        }
        stream?.use { input -> BitmapFactory.decodeStream(input)?.let { BitmapDrawable(context.resources, it) } }
    }
} catch (e: Exception) {
    null
}

/** Plays a celebration GIF, over and over while it is on screen. */
@Composable
fun CelebrationGif(celebration: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable by produceState<Drawable?>(null, celebration) {
        value = withContext(Dispatchers.IO) { loadGif(context, celebration) }
    }
    val shown = drawable
    if (shown == null) {
        Box(modifier)
        return
    }
    DisposableEffect(shown) {
        (shown as? android.graphics.drawable.Animatable)?.start()
        onDispose { (shown as? android.graphics.drawable.Animatable)?.stop() }
    }
    AndroidView(
        factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
        update = { it.setImageDrawable(shown) },
        modifier = modifier.semantics { contentDescription = "Celebration GIF" },
    )
}

private fun builtInLabel(name: String): String = when (name) {
    "confetti" -> "Confetti"
    "star" -> "Star"
    "fireworks" -> "Fireworks"
    "check" -> "Big tick"
    else -> name
}

/**
 * Picks a goal's celebration: the usual one, a GIF that comes with the app, or the user's own
 * GIF through the Photo Picker, which hands over just that one file and needs no permission.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CelebrationPicker(value: String?, onChange: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = remember { context.celebrationFiles() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val added = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.let { files.add(it) }
                        ?: Result.failure<String>(CelebrationFiles.AddError("That file couldn't be opened."))
                } catch (e: Exception) {
                    Result.failure<String>(CelebrationFiles.AddError("That file couldn't be opened."))
                }
            }
            added
                .onSuccess { onChange(Celebration.own(it)) }
                .onFailure { Toast.makeText(context, it.message ?: "That file couldn't be opened.", Toast.LENGTH_LONG).show() }
        }
    }
    val chosen = value?.takeIf { Celebration.isValid(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Celebration when it's finished", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = chosen == null,
                onClick = { onChange(null) },
                label = { Text("The usual") },
                modifier = Modifier.testTag("celebration:none"),
            )
            Celebration.BUILT_IN.forEach { name ->
                FilterChip(
                    selected = chosen == name,
                    onClick = { onChange(name) },
                    label = { Text(builtInLabel(name)) },
                    modifier = Modifier.testTag("celebration:$name"),
                )
            }
            FilterChip(
                selected = Celebration.ownFile(chosen) != null,
                onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.SingleMimeType("image/gif"))) },
                label = { Text("Your own GIF…") },
                modifier = Modifier.testTag("celebration:own"),
            )
        }
        if (chosen != null) {
            CelebrationGif(chosen, Modifier.size(120.dp).align(Alignment.CenterHorizontally).testTag("celebration-preview"))
        }
        Text(
            "It fills the screen when you finish this goal. Your own GIF is copied into the app, and goes in your backup file.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
