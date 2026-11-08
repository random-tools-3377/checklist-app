@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.randomtools3377.checklist.core.GoalKind

private enum class AppTab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.CheckCircle),
    GOALS("goals", "Goals", Icons.AutoMirrored.Filled.List),
    SHOP("shop", "Shop", Icons.Filled.ShoppingCart),
}

private val RoutesSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/**
 * The whole app. Screens are kept on a small stack of routes: a tab ("today", "goals", "shop")
 * at the bottom, with "settings" or an editor ("habit/12", "project/0" for a new one,
 * "reward/3") on top.
 */
@Composable
fun ChecklistApp(vm: MainViewModel = viewModel()) {
    val celebrations = remember { Celebrations() }
    val stack = rememberSaveable(saver = RoutesSaver) { mutableListOf(AppTab.TODAY.route).toMutableStateList() }
    var showBought by rememberSaveable { mutableStateOf(false) }
    var newGoal by remember { mutableStateOf(false) }
    var reorderGoals by rememberSaveable { mutableStateOf(false) }
    var reorderRewards by rememberSaveable { mutableStateOf(false) }

    val route = stack.last()
    fun push(next: String) {
        stack.add(next)
    }
    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else stack[0] = AppTab.TODAY.route
    }
    fun goTo(tab: AppTab) {
        stack.clear()
        stack.add(tab.route)
        reorderGoals = false
        reorderRewards = false
    }

    BackHandler(enabled = stack.size > 1 || route != AppTab.TODAY.route) { pop() }

    val today by vm.todayState.collectAsStateWithLifecycle()
    val goals by vm.goalsView.collectAsStateWithLifecycle()
    val shop by vm.shopView.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val unexplained by vm.unexplained.collectAsStateWithLifecycle()
    val penalty by vm.openPenalty.collectAsStateWithLifecycle()
    val makeUp by vm.makeUpChoices.collectAsStateWithLifecycle()
    val penaltyTasks by vm.penaltyTasks.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val skipRequest by vm.skipRequest.collectAsStateWithLifecycle()
    val unlock = rememberUnlock(vm)

    // Opening the app: misses waiting for a reason first, then the penalty task, then the rest.
    val misses = unexplained
    val owed = penalty
    if (!misses.isNullOrEmpty()) {
        Box(Modifier.fillMaxSize()) {
            MissesScreen(misses, vm)
            celebrations.Overlay()
        }
        return
    }
    if (owed != null) {
        Box(Modifier.fillMaxSize()) {
            PenaltyScreen(owed, makeUp, penaltyTasks, vm)
            celebrations.Overlay()
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        val parts = route.split("/")
        val id = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        when (parts[0]) {
            "habit" -> HabitEditor(id, vm, onClose = ::pop)
            "project" -> ProjectEditor(id, vm, onClose = ::pop)
            "reward" -> RewardEditor(id, vm, onClose = ::pop)
            "settings" -> SettingsScreen(settings, vm, onBack = ::pop)
            "history" -> HistoryScreen(history, locked = settings.locks.history && !unlocked, onUnlock = unlock, onBack = ::pop)
            else -> {
                val tab = AppTab.entries.firstOrNull { it.route == route } ?: AppTab.TODAY
                TabScaffold(
                    tab = tab,
                    onTab = ::goTo,
                    onSettings = { push("settings") },
                    actions = {
                        if (tab == AppTab.GOALS && goals?.goals?.isNotEmpty() == true) {
                            TextButton(onClick = { reorderGoals = !reorderGoals }, modifier = Modifier.testTag("reorder")) {
                                Text(if (reorderGoals) "Done" else "Reorder")
                            }
                        }
                        if (tab == AppTab.SHOP && !showBought && shop?.rewards?.isNotEmpty() == true) {
                            TextButton(onClick = { reorderRewards = !reorderRewards }, modifier = Modifier.testTag("reorder")) {
                                Text(if (reorderRewards) "Done" else "Reorder")
                            }
                        }
                    },
                    fab = {
                        if (tab == AppTab.GOALS && !reorderGoals) AddButton("New goal", "add-goal") { newGoal = true }
                        if (tab == AppTab.SHOP && !showBought && !reorderRewards) AddButton("New reward", "add-reward") { push("reward/0") }
                    },
                ) { padding ->
                    when (tab) {
                        AppTab.TODAY -> TodayScreen(
                            today, vm, celebrations,
                            onAddGoal = { goTo(AppTab.GOALS); newGoal = true },
                            onHistory = { push("history") },
                            onSettings = { push("settings") },
                            onUnlock = unlock,
                            skipRequest = skipRequest,
                            onSkipRequestHandled = vm::clearSkipRequest,
                            padding = padding,
                        )
                        AppTab.GOALS -> GoalsScreen(
                            goals,
                            reordering = reorderGoals,
                            onEdit = { goal -> push((if (goal.kind == GoalKind.HABIT) "habit/" else "project/") + goal.id) },
                            onMove = { goal, by -> vm.moveGoal(goal.id, by) },
                            onUnlock = unlock,
                            padding = padding,
                        )
                        AppTab.SHOP -> if (settings.locks.shop && !unlocked) {
                            LockedPanel("The shop", unlock, Modifier.padding(padding))
                        } else ShopScreen(
                            shop, vm, celebrations,
                            showBought = showBought,
                            onShowBought = {
                                showBought = it
                                reorderRewards = false
                            },
                            reordering = reorderRewards,
                            onEdit = { reward -> push("reward/${reward.id}") },
                            padding = padding,
                        )
                    }
                }
            }
        }

        if (newGoal) {
            NewGoalDialog(
                onHabit = { newGoal = false; push("habit/0") },
                onProject = { newGoal = false; push("project/0") },
                onDismiss = { newGoal = false },
            )
        }

        celebrations.Overlay()
    }
}

@Composable
private fun AddButton(label: String, tag: String, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(label) },
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun TabScaffold(
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    onSettings: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    fab: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.label) },
                actions = {
                    actions()
                    if (tab == AppTab.TODAY) {
                        IconButton(onClick = onSettings, modifier = Modifier.testTag("settings")) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                for (item in AppTab.entries) {
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { onTab(item) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        modifier = Modifier.testTag("nav:${item.route}"),
                    )
                }
            }
        },
        floatingActionButton = fab,
        content = content,
    )
}
