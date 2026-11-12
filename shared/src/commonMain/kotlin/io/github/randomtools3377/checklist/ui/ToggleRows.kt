package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A row with a switch: a title, an optional hint, and the switch. The whole row turns it on or
 * off, and a screen reader reads the title and hint with the switch, as one. [tag] is on the row.
 */
@Composable
internal fun SwitchRow(
    title: String,
    on: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    padding: PaddingValues = PaddingValues(0.dp),
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .testTag(tag)
            .padding(padding)
            .heightIn(min = 48.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

/**
 * A tick box with what it is for beside it: the whole of it ticks or unticks with one tap, and a
 * screen reader reads it as one. [tag] is on the row.
 */
@Composable
internal fun TickBoxRow(
    checked: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .testTag(tag),
    ) {
        // The same size as a tick box that takes taps itself.
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(12.dp))
        content()
    }
}

/** A section's title, marked as a heading so a screen reader can jump from one to the next. */
internal fun Modifier.asHeading(): Modifier = semantics { heading() }

/**
 * What a screen reader says for something shown as a symbol, like "◀" or "🎙", in place of the
 * symbol itself. Inside a button, the button reads it out with its role.
 */
internal fun Modifier.spoken(label: String): Modifier = clearAndSetSemantics { contentDescription = label }
