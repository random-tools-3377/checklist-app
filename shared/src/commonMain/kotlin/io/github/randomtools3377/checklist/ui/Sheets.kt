package io.github.randomtools3377.checklist.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.data.nowLocal
import kotlinx.datetime.LocalDate

/**
 * Runs something only once the app is unlocked: while anything is behind the privacy lock, it
 * asks for the phone's own lock first, then goes ahead, as exporting everything does. With
 * nothing locked, or where there is no lock to ask for, it goes ahead straight away.
 */
@Composable
fun rememberUnlockFirst(vm: MainViewModel): (action: () -> Unit) -> Unit {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val unlock = rememberUnlock(vm)
    var afterUnlock by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(unlocked) {
        val waiting = afterUnlock
        if (unlocked && waiting != null) {
            afterUnlock = null
            waiting()
        }
    }
    return { action ->
        if (!settings.locks.anyOn || vm.unlocked.value) {
            action()
        } else {
            afterUnlock = action
            unlock()
            // Where there is no lock to ask for, it opens straight away.
            if (vm.unlocked.value) {
                afterUnlock = null
                action()
            }
        }
    }
}

/** A button that saves a spreadsheet (CSV) where the user picks, unlocking first when anything is locked. */
@Composable
private fun SheetButton(label: String, tag: String, fileName: () -> String, vm: MainViewModel, modifier: Modifier = Modifier, write: (PickedFile, (String) -> Unit) -> Unit) {
    val phone = LocalPhone.current
    val launcher = rememberSaveFile("text/csv") { file -> if (file != null) write(file) { phone.toast(it) } }
    val unlockFirst = rememberUnlockFirst(vm)
    TextButton(onClick = { unlockFirst { launcher(fileName()) } }, modifier = modifier.testTag(tag)) { Text(label) }
}

/** Settings → Backup: each goal each day, done, skipped or missed, with the reason. */
@Composable
fun GoalRecordExportButton(vm: MainViewModel, modifier: Modifier = Modifier) =
    SheetButton("Export the goal record as a spreadsheet", "export-goal-record", { "checklist-goals-${nowLocal().date}.csv" }, vm, modifier, vm::exportGoalRecord)

/** The mood journal's page: every day with a check-in. */
@Composable
fun MoodJournalExportButton(vm: MainViewModel, modifier: Modifier = Modifier) =
    SheetButton("Save as a spreadsheet", "export-mood-journal", { "checklist-mood-journal-${nowLocal().date}.csv" }, vm, modifier, vm::exportMoodJournal)

/** The budget report: the month starting on [first]. */
@Composable
fun BudgetMonthExportButton(first: LocalDate, vm: MainViewModel, modifier: Modifier = Modifier) =
    SheetButton(
        "Save this month as a spreadsheet", "export-budget-month",
        { "checklist-budget-${first.year}-${first.monthNumber.toString().padStart(2, '0')}.csv" }, vm, modifier,
    ) { file, onDone -> vm.exportBudgetMonth(first, file, onDone) }
