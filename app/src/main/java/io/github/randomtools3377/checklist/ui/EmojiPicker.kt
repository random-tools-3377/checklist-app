package io.github.randomtools3377.checklist.ui

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One emoji: [toned] is it with "~" where a skin tone goes, or null when it takes none. */
data class EmojiEntry(val emoji: String, val name: String, val toned: String?) {
    fun withTone(tone: String): String = if (toned == null || tone.isEmpty()) emoji else toned.replace("~", tone)
}

data class EmojiGroup(val name: String, val emojis: List<EmojiEntry>)

/**
 * The full standard emoji set, bundled with the app (assets/emoji.tsv, built by
 * tools/make-emoji-list.py from Unicode's data). Emoji the phone's font can't draw are left out.
 */
object EmojiData {
    /** The skin tone modifiers, light to dark, after the default yellow. */
    val TONES = listOf("", "🏻", "🏼", "🏽", "🏾", "🏿")

    @Volatile private var cache: List<EmojiGroup>? = null
    private val paint = Paint()

    fun canShow(text: String): Boolean = try {
        paint.hasGlyph(text)
    } catch (e: Exception) {
        false
    }

    fun load(context: Context): List<EmojiGroup> {
        cache?.let { return it }
        val groups = linkedMapOf<String, MutableList<EmojiEntry>>()
        context.assets.open("emoji.tsv").bufferedReader().useLines { lines ->
            for (line in lines) {
                val parts = line.split('\t')
                if (parts.size < 3) continue
                val (group, emoji, name) = parts
                if (!canShow(emoji)) continue
                // Only offer tones the phone can draw.
                val toned = parts.getOrNull(3)?.takeIf { it.isNotEmpty() && canShow(it.replace("~", TONES[3])) }
                groups.getOrPut(group) { mutableListOf() } += EmojiEntry(emoji, name, toned)
            }
        }
        return groups.map { (name, emojis) -> EmojiGroup(name, emojis) }.also { cache = it }
    }
}

/** The picker remembers the last emoji picked and the skin tone, on this phone only. */
private object EmojiPrefs {
    private const val FILE = "icons"
    private const val RECENT = "recent"
    private const val TONE = "tone"
    private const val MAX_RECENT = 24

    fun recent(context: Context): List<String> =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(RECENT, null)?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()

    fun remember(context: Context, emoji: String) {
        val list = (listOf(emoji) + recent(context).filter { it != emoji }).take(MAX_RECENT)
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(RECENT, list.joinToString(" ")).apply()
    }

    fun tone(context: Context): Int = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(TONE, 0).coerceIn(0, EmojiData.TONES.lastIndex)

    fun setTone(context: Context, tone: Int) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(TONE, tone).apply()
    }
}

/** A short label for each group's tab. */
private val GroupIcons = mapOf(
    "Smileys & Emotion" to "😀",
    "People & Body" to "👋",
    "Animals & Nature" to "🐶",
    "Food & Drink" to "🍎",
    "Travel & Places" to "✈️",
    "Activities" to "⚽",
    "Objects" to "💡",
    "Symbols" to "❤️",
    "Flags" to "🏁",
)

/**
 * Picks an emoji from the full standard set: the recent ones first, then every group, with
 * search by name and a skin tone choice.
 */
@Composable
fun EmojiPickerDialog(current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val groups by produceState<List<EmojiGroup>?>(null) { value = withContext(Dispatchers.IO) { EmojiData.load(context) } }
    var query by remember { mutableStateOf("") }
    var tone by remember { mutableStateOf(EmojiPrefs.tone(context)) }
    val recent = remember { EmojiPrefs.recent(context) }
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    fun pick(emoji: String) {
        EmojiPrefs.remember(context, emoji)
        onPick(emoji)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("emoji-picker")) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    Text("Choose an icon", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Text(current, fontSize = 30.sp)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    placeholder = { Text("Search, like \"dog\" or \"book\"") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("emoji-search"),
                )
                // Skin tone for the emoji that take one.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Text("Skin tone", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 4.dp))
                    EmojiData.TONES.forEachIndexed { index, modifier ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .then(if (index == tone) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .clickable {
                                    tone = index
                                    EmojiPrefs.setTone(context, index)
                                }
                                .semantics { contentDescription = if (index == 0) "Default skin tone" else "Skin tone $index" }
                                .testTag("emoji-tone:$index"),
                        ) {
                            Text("✋$modifier", fontSize = 20.sp)
                        }
                    }
                }
                val all = groups
                if (all == null) {
                    Box(Modifier.fillMaxSize())
                    return@Column
                }
                val toneText = EmojiData.TONES[tone]
                val searching = query.isNotBlank()
                val results = if (searching) {
                    val words = query.trim().lowercase().split(' ').filter { it.isNotEmpty() }
                    all.flatMap { it.emojis }.filter { e -> words.all { w -> e.name.contains(w) } }
                } else {
                    emptyList()
                }
                // Where each group starts in the grid, for the tabs.
                val starts = remember(all, recent) {
                    var index = if (recent.isNotEmpty()) recent.size + 1 else 0
                    all.map { group -> index.also { index += group.emojis.size + 1 } }
                }
                if (!searching) {
                    val selected = starts.indexOfLast { it <= grid.firstVisibleItemIndex }.coerceAtLeast(0)
                    ScrollableTabRow(selectedTabIndex = selected, edgePadding = 8.dp) {
                        all.forEachIndexed { i, group ->
                            Tab(
                                selected = i == selected,
                                onClick = { scope.launch { grid.scrollToItem(starts[i]) } },
                                text = { Text(GroupIcons[group.name] ?: group.name.take(1), fontSize = 20.sp) },
                                modifier = Modifier.semantics { contentDescription = group.name }.testTag("emoji-group:$i"),
                            )
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(48.dp),
                    state = grid,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).testTag("emoji-grid"),
                ) {
                    if (searching) {
                        if (results.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text("No emoji called that.", modifier = Modifier.padding(16.dp))
                            }
                        }
                        items(results, key = { "s:" + it.emoji }) { entry ->
                            EmojiCell(entry.withTone(toneText), entry.name, entry.emoji == current) { pick(entry.withTone(toneText)) }
                        }
                    } else {
                        if (recent.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "h:recent") { GroupHeader("Recent") }
                            items(recent, key = { "r:$it" }) { emoji -> EmojiCell(emoji, "recent $emoji", emoji == current) { pick(emoji) } }
                        }
                        all.forEach { group ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "h:" + group.name) { GroupHeader(group.name) }
                            items(group.emojis, key = { "e:" + it.emoji }) { entry ->
                                EmojiCell(entry.withTone(toneText), entry.name, entry.emoji == current) { pick(entry.withTone(toneText)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(name: String) {
    Text(
        name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun EmojiCell(emoji: String, name: String, chosen: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .then(if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .semantics { contentDescription = name }
            .testTag("emoji:$name"),
    ) {
        Text(emoji, fontSize = 28.sp)
    }
}
