package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val GOAL_ICON = "⭐"
const val REWARD_ICON = "🎁"

/** Emoji to pick from. They are drawn with the phone's own emoji font. */
private val IconChoices = listOf(
    "⭐", "✅", "💧", "🏃", "🚶", "🚴", "🏊", "💪", "🧘", "🛌",
    "🍎", "🥗", "🍳", "☕", "🍵", "💊", "🦷", "🚿", "🧹", "🧺",
    "🛒", "🍽", "📚", "📖", "✏", "📝", "💻", "📞", "✉", "📅",
    "🎸", "🎹", "🎨", "📷", "🌱", "🌻", "🐶", "🐱", "🧠", "❤",
    "🎁", "🍫", "🍰", "🍦", "🍕", "🍔", "🍿", "🎬", "🎮", "🎧",
    "🛍", "👟", "👗", "💄", "💆", "🛁", "✈", "🏖", "🎉", "🏆",
    "🔨", "🔧", "🏠", "🚗", "💰", "🎯", "⏰", "🌞", "🌙", "🙂",
)

fun pointsText(points: Int): String = if (points == 1 || points == -1) "$points point" else "$points points"

private val ShortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val LongDate = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())

fun shortDate(date: LocalDate): String = date.toJavaLocalDate().format(ShortDate)
fun longDate(date: LocalDate): String = date.toJavaLocalDate().format(LongDate)

/** The balance, big. It turns red below zero. */
@Composable
fun BalanceCard(balance: Int, modifier: Modifier = Modifier, note: String? = null) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                text = pointsText(balance),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("balance"),
            )
            if (note != null) {
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** A text field for a whole number. Keeps only digits. */
@Composable
fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    hint: String? = null,
) {
    val support = error ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = if (support != null) ({ Text(support) }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth().testTag(tag),
    )
}

/** Shows the chosen emoji. Tapping it opens a grid to pick another. */
@Composable
fun IconPicker(icon: String, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable { open = true }
                .testTag("icon"),
        ) {
            Text(icon, fontSize = 34.sp)
        }
        Spacer(Modifier.width(16.dp))
        TextButton(onClick = { open = true }) { Text("Choose icon") }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
            title = { Text("Icon") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(52.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    items(IconChoices) { choice ->
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .then(
                                    if (choice == icon) {
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable {
                                    onPick(choice)
                                    open = false
                                },
                        ) {
                            Text(choice, fontSize = 28.sp)
                        }
                    }
                }
            },
        )
    }
}

/** A short line of grey text under a heading. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
    )
}
