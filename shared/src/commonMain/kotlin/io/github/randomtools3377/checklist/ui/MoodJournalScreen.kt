package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.MoodFactors
import io.github.randomtools3377.checklist.core.Moods

/*
 * The mood journal: each day's mood in the morning and once everything was done, the things
 * behind it, and the notes written at each check-in. The same chips and note box are used on
 * Today, under each check-in.
 */

/**
 * The things that can be behind a mood, as chips to pick, and "Add your own". [offered] is the
 * list (with the day's own that are no longer on it); [picked] those picked. Each chip is tagged
 * [tag]:name for the phone tests; "Add your own" is [tag]-add.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodFactorChips(offered: List<String>, picked: List<String>, tag: String, onToggle: (String) -> Unit, onAdd: (String) -> Unit) {
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            offered.forEach { name ->
                FilterChip(
                    selected = MoodFactors.has(picked, name),
                    onClick = { onToggle(name) },
                    label = { Text(name) },
                    modifier = Modifier.testTag("$tag:$name"),
                )
            }
            AssistChip(
                onClick = { adding = if (adding == null) "" else null },
                label = { Text("+ Add your own") },
                modifier = Modifier.testTag("$tag-add"),
            )
        }
        adding?.let { text ->
            fun add() {
                if (MoodFactors.tidy(text).isEmpty()) return
                onAdd(text)
                adding = null
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { adding = it.replace("\n", " ").take(MoodFactors.LENGTH) },
                    placeholder = { Text("Like \"Coffee\"") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f).testTag("$tag-new"),
                )
                TextButton(onClick = ::add, enabled = MoodFactors.tidy(text).isNotEmpty(), modifier = Modifier.testTag("$tag-new-add")) { Text("Add") }
            }
        }
    }
}

/**
 * A short note for a check-in, saved with "Save", and also when the box goes, like when its card
 * is closed with "Done" or "Close" or closes by itself, so nothing typed is lost. The box is
 * tagged [tag], its button [tag]-save.
 */
@Composable
fun MoodNoteField(stored: String?, placeholder: String, tag: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(stored) { mutableStateOf(stored.orEmpty()) }
    val typed by rememberUpdatedState(text)
    val saved by rememberUpdatedState(stored)
    val save by rememberUpdatedState(onSave)
    DisposableEffect(Unit) {
        onDispose { if (Moods.tidyNote(typed) != Moods.tidyNote(saved)) save(typed) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(Moods.NOTE_LENGTH) },
            placeholder = { Text(placeholder) },
            modifier = Modifier.weight(1f).testTag(tag),
        )
        TextButton(
            onClick = { onSave(text) },
            enabled = Moods.tidyNote(text) != Moods.tidyNote(stored),
            modifier = Modifier.testTag("$tag-save"),
        ) { Text("Save") }
    }
}

/** The faces for a day: the morning's, then the one once everything was done, if given. */
fun moodFaces(mood: Mood): String = Moods.face(mood.score) + (mood.after?.let { " → " + Moods.face(it) } ?: "")

/** Every day with a mood, newest first, with what was behind it and the notes. Tap a day to change it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodJournalScreen(vm: MainViewModel, onBack: () -> Unit) {
    val days by vm.moodJournal.collectAsStateWithLifecycle()
    val list by vm.moodFactorList.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var editingList by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood journal") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = { editingList = true }, modifier = Modifier.testTag("journal-edit-list")) { Text("Edit the list") }
                    HelpAction()
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("mood-journal"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Every day you checked in, newest first: how you felt in the morning and once everything was done, what played a part, and your notes. Tap a day to change it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (days.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. Answer the mood check-in on Today, and each day lands here.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp).testTag("journal-empty"),
                    )
                }
            }
            items(days, key = { it.day.toString() }) { mood -> JournalDay(mood) { editing = mood.day.toString() } }
        }
    }
    editing?.let { key ->
        val mood = days.firstOrNull { it.day.toString() == key }
        if (mood == null) {
            editing = null
        } else {
            MoodDayDialog(
                mood = mood,
                list = list,
                onAdd = { name -> vm.addMoodFactor(name, day = null) },
                onSave = { changed ->
                    vm.editMoodDay(changed)
                    editing = null
                },
                onDismiss = { editing = null },
            )
        }
    }
    if (editingList) {
        FactorListDialog(
            list = list,
            onRemove = vm::removeMoodFactor,
            onAdd = { name -> vm.addMoodFactor(name, day = null) },
            onRestore = vm::restoreMoodFactors,
            onDismiss = { editingList = false },
        )
    }
}

@Composable
private fun JournalDay(mood: Mood, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick).testTag("journal-day:${mood.day}")) {
        Row(Modifier.padding(12.dp)) {
            Text(moodFaces(mood), fontSize = 20.sp, modifier = Modifier.width(76.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(longDate(mood.day), style = MaterialTheme.typography.titleSmall)
                if (mood.factors.isNotEmpty()) {
                    Text(mood.factors.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                mood.note?.let { Text("Morning: “$it”", style = MaterialTheme.typography.bodyMedium) }
                mood.afterNote?.let { Text("Later: “$it”", style = MaterialTheme.typography.bodyMedium) }
                if (mood.factors.isEmpty() && mood.note == null && mood.afterNote == null) {
                    Text("Tap to add notes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A row of faces to pick a mood from; [score] is the one picked. */
@Composable
private fun FacePicker(score: Int, tag: String, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
        Moods.FACES.forEachIndexed { index, face ->
            val value = index + 1
            Text(
                face,
                fontSize = if (value == score) 30.sp else 22.sp,
                modifier = Modifier
                    .clickable { onPick(value) }
                    .semantics { contentDescription = Moods.spoken(value) + if (value == score) ", picked" else "" }
                    .padding(4.dp)
                    .testTag("$tag:$value"),
            )
        }
    }
}

/** One day of the journal, to change: its moods, what played a part, and both notes. */
@Composable
private fun MoodDayDialog(mood: Mood, list: List<String>, onAdd: (String) -> Unit, onSave: (Mood) -> Unit, onDismiss: () -> Unit) {
    var score by remember(mood) { mutableStateOf(mood.score) }
    var after by remember(mood) { mutableStateOf(mood.after) }
    var factors by remember(mood) { mutableStateOf(mood.factors) }
    var note by remember(mood) { mutableStateOf(mood.note.orEmpty()) }
    var afterNote by remember(mood) { mutableStateOf(mood.afterNote.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(longDate(mood.day)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("In the morning", style = MaterialTheme.typography.labelLarge)
                FacePicker(score, "journal-mood") { score = it }
                after?.let { value ->
                    Text("Once everything was done", style = MaterialTheme.typography.labelLarge)
                    FacePicker(value, "journal-after") { after = it }
                }
                Text("What played a part?", style = MaterialTheme.typography.labelLarge)
                MoodFactorChips(
                    offered = MoodFactors.offered(list, factors),
                    picked = factors,
                    tag = "journal-factor",
                    onToggle = { factors = MoodFactors.toggle(factors, it) },
                    onAdd = { name ->
                        onAdd(name)
                        if (!MoodFactors.has(factors, MoodFactors.tidy(name))) factors = MoodFactors.toggle(factors, name)
                    },
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(Moods.NOTE_LENGTH) },
                    label = { Text("Morning note") },
                    modifier = Modifier.fillMaxWidth().testTag("journal-note"),
                )
                if (mood.after != null || mood.afterNote != null) {
                    OutlinedTextField(
                        value = afterNote,
                        onValueChange = { afterNote = it.take(Moods.NOTE_LENGTH) },
                        label = { Text("Note once everything was done") },
                        modifier = Modifier.fillMaxWidth().testTag("journal-after-note"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(mood.copy(score = score, after = after, factors = factors, note = note, afterNote = afterNote)) },
                modifier = Modifier.testTag("journal-save"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        modifier = Modifier.testTag("journal-dialog"),
    )
}

/**
 * "Edit the list": the things offered at the check-ins, each with ✕ to take it off, a box to add
 * the user's own, and the app's own put back. Days that had one keep it.
 */
@Composable
private fun FactorListDialog(list: List<String>, onRemove: (String) -> Unit, onAdd: (String) -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What can play a part") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "These are offered at each check-in. Taking one off leaves it on the days you picked it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (list.isEmpty()) Text("Nothing on the list.", style = MaterialTheme.typography.bodyMedium)
                list.forEach { name ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemove(name) }, modifier = Modifier.testTag("factor-remove:$name")) {
                            Icon(Icons.Filled.Close, contentDescription = "Take $name off the list")
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.replace("\n", " ").take(MoodFactors.LENGTH) },
                        placeholder = { Text("Add your own") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("factor-list-new"),
                    )
                    TextButton(
                        onClick = {
                            onAdd(text)
                            text = ""
                        },
                        enabled = MoodFactors.tidy(text).isNotEmpty(),
                        modifier = Modifier.testTag("factor-list-add"),
                    ) { Text("Add") }
                }
                if (MoodFactors.BUILT_IN.any { name -> !MoodFactors.has(list, name) }) {
                    TextButton(onClick = onRestore, modifier = Modifier.testTag("factors-restore")) { Text("Put back the usual ones") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("factor-list-done")) { Text("Done") } },
    )
}
