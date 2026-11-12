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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import io.github.randomtools3377.checklist.core.MoodJournal
import io.github.randomtools3377.checklist.core.MoodSummaries
import io.github.randomtools3377.checklist.core.Moods
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay
import io.github.randomtools3377.checklist.data.toLocalDateTime
import kotlinx.datetime.LocalDate

/*
 * The mood journal: each day's first mood and the later one, once everything was done, the things
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

/** The faces for a day: the first check-in's, then the later one, if given. */
fun moodFaces(mood: Mood): String = Moods.face(mood.score) + (mood.after?.let { " → " + Moods.face(it) } ?: "")

/** What a day's first answer is called: "Morning" when it was given that morning, else "First check-in". */
internal fun firstLabel(mood: Mood): String =
    MoodJournal.firstLabel(mood.day, mood.at.takeIf { it > 0 }?.toLocalDateTime())

/**
 * Every day with a mood, newest first, with what was behind it and the notes. Tap a day to change
 * or delete it; "Add a day" fills in one that was missed. The days can be narrowed to one noted
 * thing, or to words in the notes. Opened from the review with [from] and [to], it starts on that
 * stretch.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoodJournalScreen(vm: MainViewModel, onBack: () -> Unit, from: LocalDate? = null, to: LocalDate? = null) {
    val days by vm.moodJournal.collectAsStateWithLifecycle()
    val list by vm.moodFactorList.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    // A day being filled in, not saved yet.
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingDay by rememberSaveable { mutableStateOf(false) }
    var editingList by rememberSaveable { mutableStateOf(false) }
    var factor by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var wholeJournal by rememberSaveable { mutableStateOf(from == null || to == null) }
    val inStretch = if (wholeJournal) days else MoodJournal.filter(days, from = from, to = to)
    val used = MoodSummaries.used(inStretch)
    // A chip no longer on any of these days (its last day was changed or deleted) stops narrowing them.
    val narrowed = factor?.takeIf { picked -> used.any { (name, _) -> MoodFactors.has(listOf(name), picked) } }
    val shown = MoodJournal.filter(inStretch, factor = narrowed, query = query)
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
                    "Every day you checked in, newest first: your first check-in and the later one, what played a part, and your notes. Tap a day to change it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!wholeJournal && from != null && to != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (from == to) longDate(from) else "${shortDate(from)} to ${shortDate(to)}",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f).testTag("journal-stretch"),
                        )
                        TextButton(onClick = { wholeJournal = true }, modifier = Modifier.testTag("journal-all-days")) { Text("Show every day") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { pickingDay = true }, modifier = Modifier.testTag("journal-add-day")) { Text("Add a day") }
            }
            if (inStretch.isEmpty()) {
                item {
                    Text(
                        if (days.isEmpty()) "Nothing yet. Answer the mood check-in on Today, and each day lands here." else "No check-ins in these days.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp).testTag("journal-empty"),
                    )
                }
            } else {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        placeholder = { Text("Search your notes") },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }, modifier = Modifier.testTag("journal-search-clear")) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear the search")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("journal-search"),
                    )
                }
                if (used.isNotEmpty()) {
                    item {
                        // Tap one to see only its days; tap it again for every day.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("journal-filters")) {
                            used.forEach { (name, count) ->
                                FilterChip(
                                    selected = narrowed?.let { MoodFactors.has(listOf(it), name) } == true,
                                    onClick = { factor = if (narrowed?.let { MoodFactors.has(listOf(it), name) } == true) null else name },
                                    label = { Text("$name · $count") },
                                    modifier = Modifier.testTag("journal-filter:$name"),
                                )
                            }
                        }
                    }
                }
                if (shown.isEmpty()) {
                    item {
                        Text(
                            "No day matches. Clear the search, or tap the chip again to see every day.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp).testTag("journal-no-match"),
                        )
                    }
                }
            }
            // The journal as a spreadsheet, saved where the user picks.
            if (days.isNotEmpty()) item(key = "journal-export") { MoodJournalExportButton(vm) }
            items(shown, key = { it.day.toString() }) { mood -> JournalDay(mood) { editing = mood.day.toString() } }
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
                onDelete = {
                    vm.deleteMoodDay(mood.day)
                    editing = null
                },
            )
        }
    }
    adding?.let { key ->
        val day = runCatching { LocalDate.parse(key) }.getOrNull()
        if (day == null) {
            adding = null
        } else {
            MoodDayDialog(
                mood = Mood(day, 0),
                list = list,
                onAdd = { name -> vm.addMoodFactor(name, day = null) },
                onSave = { added ->
                    vm.saveMoodDay(added)
                    adding = null
                },
                onDismiss = { adding = null },
            )
        }
    }
    if (pickingDay) {
        val today = vm.currentDay()
        val state = rememberDatePickerState(initialSelectedDateMillis = today.toEpochDay() * 86_400_000L)
        val picked = state.selectedDateMillis?.floorDiv(86_400_000L)?.toDay()
        val allowed = picked != null && MoodJournal.canAdd(picked, today)
        DatePickerDialog(
            onDismissRequest = { pickingDay = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickingDay = false
                        // A day that has a mood opens to be changed; a new one to be filled in.
                        val key = picked?.toString() ?: return@TextButton
                        if (days.any { it.day == picked }) editing = key else adding = key
                    },
                    enabled = allowed,
                    modifier = Modifier.testTag("journal-add-day-ok"),
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDay = false }) { Text("Cancel") } },
        ) {
            Text(
                if (picked != null && !allowed) "That day hasn't come yet. Pick today or an earlier day." else "Pick today or an earlier day.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (picked != null && !allowed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp).testTag("journal-add-day-rule"),
            )
            DatePicker(state = state)
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
                mood.note?.let { Text("${firstLabel(mood)}: “$it”", style = MaterialTheme.typography.bodyMedium) }
                mood.afterNote?.let { Text("Later: “$it”", style = MaterialTheme.typography.bodyMedium) }
                if (mood.factors.isEmpty() && mood.note == null && mood.afterNote == null) {
                    Text("Tap to add notes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A row of faces to pick a mood from; [score] is the one picked, or 0 for none yet. */
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

/**
 * One day's mood in full, to change: its faces, what played a part, and both notes. A [mood] with
 * no face yet (score 0) is a day being filled in, saved once a face is picked. A day without a
 * later mood can be given one with "Add how you felt later", like when that check-in was put
 * off. With [onDelete] the day can be deleted, after asking; with [onJournal] the journal is a
 * button away. While [locked], behind the lock with reasons and notes, only the faces show.
 */
@Composable
internal fun MoodDayDialog(
    mood: Mood,
    list: List<String>,
    onAdd: (String) -> Unit,
    onSave: (Mood) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onJournal: (() -> Unit)? = null,
    locked: Boolean = false,
    onUnlock: () -> Unit = {},
    /** Whether the day is today, which is asked about as it is now. */
    today: Boolean = false,
) {
    var score by remember(mood) { mutableStateOf(mood.score) }
    var after by remember(mood) { mutableStateOf(mood.after) }
    var addingAfter by remember(mood.day) { mutableStateOf(false) }
    var factors by remember(mood) { mutableStateOf(mood.factors) }
    var note by remember(mood) { mutableStateOf(mood.note.orEmpty()) }
    var afterNote by remember(mood) { mutableStateOf(mood.afterNote.orEmpty()) }
    var deleting by remember(mood.day) { mutableStateOf(false) }
    val first = if (mood.score != 0) firstLabel(mood) else if (today) "How are you feeling?" else "How did you feel?"
    val later = after != null || addingAfter
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(longDate(mood.day)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(first, style = MaterialTheme.typography.labelLarge, modifier = Modifier.testTag("journal-first-label"))
                FacePicker(score, "journal-mood") { score = it }
                if (later) {
                    Text("Later", style = MaterialTheme.typography.labelLarge)
                    FacePicker(after ?: 0, "journal-after") { after = it }
                } else if (score > 0) {
                    TextButton(onClick = { addingAfter = true }, modifier = Modifier.testTag("journal-after-add")) { Text("Add how you felt later") }
                }
                if (locked) {
                    OutlinedButton(onClick = onUnlock, modifier = Modifier.testTag("journal-notes-unlock")) { Text("🔒 Notes are locked · Unlock") }
                } else {
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
                        label = { Text(if (mood.score == 0) "A note, if you like" else "$first note") },
                        modifier = Modifier.fillMaxWidth().testTag("journal-note"),
                    )
                    if (later || mood.afterNote != null) {
                        OutlinedTextField(
                            value = afterNote,
                            onValueChange = { afterNote = it.take(Moods.NOTE_LENGTH) },
                            label = { Text("Later note") },
                            modifier = Modifier.fillMaxWidth().testTag("journal-after-note"),
                        )
                    }
                }
                if (onJournal != null || onDelete != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        onJournal?.let { TextButton(onClick = it, modifier = Modifier.testTag("mood-day-journal")) { Text("Mood journal") } }
                        onDelete?.let {
                            TextButton(onClick = { deleting = true }, modifier = Modifier.testTag("journal-delete")) {
                                Text("Delete this day", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(mood.copy(score = score, after = after, factors = factors, note = note, afterNote = afterNote)) },
                enabled = score in 1..Moods.FACES.size,
                modifier = Modifier.testTag("journal-save"),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        modifier = Modifier.testTag("journal-dialog"),
    )
    if (deleting && onDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete this day's mood?") },
            text = { Text("${longDate(mood.day)}: its faces, what played a part and its notes go from the journal. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = false
                        onDelete()
                    },
                    modifier = Modifier.testTag("journal-delete-yes"),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Keep it") } },
        )
    }
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
