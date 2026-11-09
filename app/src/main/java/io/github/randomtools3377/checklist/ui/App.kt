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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.randomtools3377.checklist.core.GoalKind

private enum class AppTab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.CheckCircle),
    PLAN("plan", "Plan", Icons.Filled.DateRange),
    GOALS("goals", "Goals", Icons.AutoMirrored.Filled.List),
    SHOP("shop", "Shop", Icons.Filled.ShoppingCart),
    REVIEW("review", "Review", Icons.Filled.Star),
}

private val RoutesSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/**
 * The whole app. Screens are kept on a small stack of routes: a tab ("today", "goals", "shop")
 * at the bottom, with "settings" or an editor ("habit/12", "project/0" for a new one, "todo/3",
 * "reward/3") on top.
 */
@Composable
fun ChecklistApp(vm: MainViewModel = viewModel()) {
    val celebrations = remember { Celebrations() }
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val sounds = Sounds(context.applicationContext)
        celebrations.sounds = sounds
        onDispose {
            celebrations.sounds = null
            sounds.release()
        }
    }
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
    val gap by vm.unansweredGap.collectAsStateWithLifecycle()
    val removedGoals by vm.removedGoals.collectAsStateWithLifecycle()
    val doneList by vm.doneList.collectAsStateWithLifecycle()
    val badges by vm.badges.collectAsStateWithLifecycle()
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val planDays by vm.planDays.collectAsStateWithLifecycle()
    celebrations.style = appearance.celebrate
    // The app opens on the tab chosen in Appearance, once the settings have been read.
    var firstTabApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(appearance.loaded) {
        if (appearance.loaded && !firstTabApplied) {
            firstTabApplied = true
            val first = AppTab.entries[appearance.tabs.opensOn.ordinal]
            if (first != AppTab.TODAY && stack.size == 1 && stack[0] == AppTab.TODAY.route) stack[0] = first.route
        }
    }
    val shownTabs = AppTab.entries.filter { tab -> appearance.tabs.shown.any { it.ordinal == tab.ordinal } }
    val tickNotes by vm.tickNotes.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val reviewPeriod by vm.reviewPeriod.collectAsStateWithLifecycle()
    val allGaps by vm.gaps.collectAsStateWithLifecycle()

    // Opening the app: the gap check-in or misses waiting for a reason first, then the penalty task, then the rest.
    val misses = unexplained
    val owed = penalty
    val checkIn = gap
    if (checkIn != null) {
        Box(Modifier.fillMaxSize()) {
            GapCheckInScreen(checkIn, vm)
        }
        return
    }
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

    // A notification can ask for a tab, like the weekly summary opening the review.
    val openTab by vm.openTab.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(openTab) {
        val asked = openTab ?: return@LaunchedEffect
        when (asked) {
            // The app shortcuts: a new to-do, or a new goal of any kind.
            OPEN_NEW_TODO -> {
                goTo(AppTab.TODAY)
                push("todo/0")
            }
            OPEN_NEW_GOAL -> {
                goTo(AppTab.GOALS)
                newGoal = true
            }
            else -> AppTab.entries.firstOrNull { it.route == asked }?.let { goTo(it) }
        }
        vm.openTab.value = null
    }

    // The focus timer fills the screen while it runs.
    val focusState by vm.focus.collectAsStateWithLifecycle()
    focusState?.let { state ->
        val goal = goals?.goals?.find { it.id == state.goalId }
        val config = androidx.compose.ui.platform.LocalConfiguration.current
        val density = androidx.compose.ui.platform.LocalDensity.current
        val center = with(density) { androidx.compose.ui.geometry.Offset(config.screenWidthDp.dp.toPx() / 2, config.screenHeightDp.dp.toPx() / 2) }
        Box(Modifier.fillMaxSize()) {
            FocusScreen(
                state, goal,
                onPause = vm::pauseFocus,
                onResume = vm::resumeFocus,
                onStop = vm::stopFocus,
                onDone = {
                    vm.finishFocus { result ->
                        if (result is io.github.randomtools3377.checklist.core.TickResult.Ticked) {
                            celebrations.burst(center, "+${result.points + result.bonus}", big = true)
                        }
                    }
                },
            )
            celebrations.Overlay()
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        val parts = route.split("/")
        val id = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        when (parts[0]) {
            // Each editor screen keeps its own fields, so opening a copy doesn't carry the last one's over.
            "habit" -> androidx.compose.runtime.key(route) {
                HabitEditor(id, vm, onClose = ::pop, onOpen = { copy -> pop(); push("habit/$copy") })
            }
            "project" -> androidx.compose.runtime.key(route) {
                ProjectEditor(id, vm, onClose = ::pop, onOpen = { copy -> pop(); push("project/$copy") })
            }
            "todo" -> androidx.compose.runtime.key(route) { TodoEditor(id, vm, onClose = ::pop) }
            "guide" -> GuideScreen(
                onExamples = { vm.addExamples(); goTo(AppTab.TODAY) },
                onScratch = { vm.closeWelcome(); goTo(AppTab.TODAY) },
                onBack = ::pop,
            )
            "done" -> DoneListScreen(doneList, onOpen = { goal -> push(editRoute(goal)) }, onBack = ::pop)
            "removed" -> RemovedGoalsScreen(removedGoals, onRestore = { vm.restoreGoal(it.id) }, onBack = ::pop)
            "reward" -> androidx.compose.runtime.key(route) { RewardEditor(id, vm, onClose = ::pop) }
            "settings" -> SettingsScreen(
                settings, vm,
                onAppearance = { push("appearance") },
                onGuide = { push("guide") },
                onOpenTab = { kind -> goTo(AppTab.entries[kind.ordinal]) },
                onBack = ::pop,
            )
            "appearance" -> AppearanceScreen(appearance, vm, onAnyColour = { push("colour") }, onBack = ::pop)
            "colour" -> AppColourScreen(appearance, onUse = { vm.setColor(it); pop() }, onBack = ::pop)
            "badges" -> if (settings.locks.review && !unlocked) {
                LockedPanel("The badges page", unlock, Modifier)
            } else {
                BadgesScreen(badges, onSeen = vm::badgesSeen, onBack = ::pop)
            }
            "history" -> HistoryScreen(
                history,
                gaps = if (settings.locks.gaps && !unlocked) emptyList() else allGaps,
                locked = settings.locks.history && !unlocked,
                onUnlock = unlock,
                onBack = ::pop,
                notes = tickNotes,
                vm = vm,
            )
            else -> {
                val tab = AppTab.entries.firstOrNull { it.route == route } ?: AppTab.TODAY
                TabScaffold(
                    tab = tab,
                    tabs = shownTabs,
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
                            onBadges = { push("badges") },
                            onEditTodo = { id -> push("todo/$id") },
                            onGuide = { push("guide") },
                            skipRequest = skipRequest,
                            onSkipRequestHandled = vm::clearSkipRequest,
                            padding = padding,
                        )
                        AppTab.GOALS -> GoalsScreen(
                            goals,
                            reordering = reorderGoals,
                            onEdit = { goal -> push(editRoute(goal)) },
                            onMove = { goal, by -> vm.moveGoal(goal.id, by) },
                            onUnlock = unlock,
                            padding = padding,
                            removed = removedGoals.size,
                            onRemoved = { push("removed") },
                            doneCount = doneList.size,
                            onDoneList = { push("done") },
                        )
                        AppTab.PLAN -> PlanScreen(planDays, vm, padding)
                        AppTab.REVIEW -> if (settings.locks.review && !unlocked) {
                            LockedPanel("The review", unlock, Modifier.padding(padding))
                        } else {
                            ReviewScreen(
                                summary = review,
                                period = reviewPeriod,
                                streaks = today?.streaks,
                                hideReasons = settings.locks.reasons && !unlocked,
                                hideGapNotes = settings.locks.gaps && !unlocked,
                                onPeriod = { vm.reviewPeriod.value = it; vm.reviewDay.value = null },
                                onStep = vm::reviewStep,
                                badges = badges.size,
                                onBadges = { push("badges") },
                                padding = padding,
                            )
                        }
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
                onTodo = { newGoal = false; push("todo/0") },
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
    tabs: List<AppTab>,
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
                for (item in tabs) {
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

/** The editor for a goal of each kind. */
private fun editRoute(goal: io.github.randomtools3377.checklist.core.Goal): String = when (goal.kind) {
    GoalKind.HABIT -> "habit/${goal.id}"
    GoalKind.PROJECT -> "project/${goal.id}"
    GoalKind.TODO -> "todo/${goal.id}"
}

/** What the app shortcuts ask for, through MainActivity.EXTRA_OPEN (see res/xml/shortcuts.xml). */
const val OPEN_NEW_TODO = "new-todo"
const val OPEN_NEW_GOAL = "new-goal"
