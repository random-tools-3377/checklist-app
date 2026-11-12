package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.core.Offered
import io.github.randomtools3377.checklist.core.RuleField
import io.github.randomtools3377.checklist.core.Rules

/**
 * Points and rules: every number that decides what's paid, taken or suggested, grouped, each
 * with its usual value and a way back to it. Changes apply from now on. The numbers for extras
 * that are off stay hidden until the extra is on, unless already changed (see Offered).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(vm: MainViewModel, onBack: () -> Unit) {
    val rules by vm.rules.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
    var resetting by remember { mutableStateOf(false) }
    val usual = remember { Rules() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Points and rules") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { HelpAction()

                    TextButton(onClick = { resetting = true }, enabled = rules != usual, modifier = Modifier.testTag("rules-reset-all")) { Text("Reset all") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("rules-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            item {
                Text(
                    "Every number the app uses for ${pointsWord()}, prices and the game. Changes apply from now on; what's already paid stays. " +
                        "The numbers for an extra show here once it's turned on in Features.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Offered.rules(Rules.FIELDS, features, rules, usual).groupBy { it.group }.forEach { (group, fields) ->
                item(key = "group-$group") { SectionTitle(group) }
                items(fields, key = { it.key }) { field -> RuleRow(field, rules, usual) { vm.setRules(field.set(rules, it)) } }
            }
        }
    }
    if (resetting) {
        AlertDialog(
            onDismissRequest = { resetting = false },
            title = { Text("Put every number back?") },
            text = { Text("Each goes back to its usual value.") },
            confirmButton = {
                TextButton(onClick = { vm.setRules(Rules()); resetting = false }, modifier = Modifier.testTag("rules-reset-confirm")) { Text("Reset all") }
            },
            dismissButton = { TextButton(onClick = { resetting = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RuleRow(field: RuleField, rules: Rules, usual: Rules, onChange: (Int) -> Unit) {
    val value = field.get(rules)
    val default = field.get(usual)
    var text by remember(value) { mutableStateOf(value.toString()) }
    val typed = text.toIntOrNull()
    val bad = typed == null || typed < field.min || typed > field.max
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("rule:${field.key}")) {
        Column(Modifier.weight(1f)) {
            Text(field.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                (if (field.unit.isNotEmpty()) "In ${field.unit}. " else "") + "Usually $default.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Out of range, or not a number: say what it takes. The last good number stays in use.
            if (bad) {
                Text(
                    Rules.rangeText(field),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("rule-range:${field.key}"),
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                text = new.filter { it.isDigit() || it == '-' }.take(6)
                text.toIntOrNull()?.takeIf { it in field.min..field.max }?.let(onChange)
            },
            singleLine = true,
            isError = bad,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp).testTag("rule-field:${field.key}"),
        )
        if (value != default) {
            TextButton(onClick = { onChange(default) }, modifier = Modifier.testTag("rule-reset:${field.key}")) { Text("Reset") }
        }
    }
}
