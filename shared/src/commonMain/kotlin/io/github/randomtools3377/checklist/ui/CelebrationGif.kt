package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.randomtools3377.checklist.core.Celebration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A celebration GIF's bytes: one that comes with the app, or one the user picked. */
@OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)
suspend fun celebrationBytes(phone: Phone, celebration: String): ByteArray? = try {
    val builtIn = Celebration.builtIn(celebration)
    val own = Celebration.ownFile(celebration)
    when {
        builtIn != null -> io.github.randomtools3377.checklist.ui.resources.Res.readBytes("files/celebrations/$builtIn.gif")
        own != null -> withContext(Dispatchers.Default) { phone.parts.gifs.read(own) }
        else -> null
    }
} catch (e: Exception) {
    null
}

internal fun builtInLabel(name: String): String = when (name) {
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
    val phone = LocalPhone.current
    val scope = rememberCoroutineScope()
    val pick = rememberPickImage(gifOnly = true) { file ->
        if (file == null) return@rememberPickImage
        scope.launch {
            phone.addGif(file)
                .onSuccess { onChange(Celebration.own(it)) }
                .onFailure { phone.toast(it.message ?: "That file couldn't be opened.") }
        }
    }
    val chosen = value?.takeIf { Celebration.isValid(it) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Celebration when it's done", style = MaterialTheme.typography.titleSmall)
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
                onClick = pick,
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
