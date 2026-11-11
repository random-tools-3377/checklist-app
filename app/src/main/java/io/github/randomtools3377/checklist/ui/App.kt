@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.MaterialTheme
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
    // The box from the quick-add widget.
    var quickTodo by remember { mutableStateOf(false) }
    // The Undo bar: the last tick, purchase or removed goal, for 5 seconds. A newer one replaces it.
    val undoHost = remember { androidx.compose.material3.SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.undoBar.collectLatest { offer ->
            kotlinx.coroutines.coroutineScope {
                val timer = launch {
                    kotlinx.coroutines.delay(UNDO_MILLIS)
                    undoHost.currentSnackbarData?.dismiss()
                }
                val result = undoHost.showSnackbar(offer.message, actionLabel = "Undo", duration = androidx.compose.material3.SnackbarDuration.Indefinite)
                timer.cancel()
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) offer.undo()
            }
        }
    }
    var reorderGoals by rememberSaveable { mutableStateOf(false) }
    // Choosing several goals to change or remove together.
    var selectGoals by rememberSaveable { mutableStateOf(false) }
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
        selectGoals = false
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
    val balanceCheck by vm.balanceCheck.collectAsStateWithLifecycle()
    val calendarEvents by vm.calendarEvents.collectAsStateWithLifecycle()
    val moods by vm.reviewMoods.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
    // The optional AI: how Checklist AI stands, and voice through its listening screen.
    val aiStatus by vm.aiStatus.collectAsStateWithLifecycle()
    LaunchedEffect(io.github.randomtools3377.checklist.core.Feature.AI in features) {
        if (io.github.randomtools3377.checklist.core.Feature.AI in features) vm.refreshAi()
    }
    var voiceCommand by remember { mutableStateOf<io.github.randomtools3377.checklist.core.VoiceCommand?>(null) }
    var voiceProblem by remember { mutableStateOf<String?>(null) }
    val listen = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val heard = result.data?.getStringExtra(io.github.randomtools3377.checklist.core.AiContract.KEY_TEXT)
        if (heard != null) {
            // Goals due today, by name, to match what was said.
            val choices = vm.todayState.value?.view?.dueToday.orEmpty().map { it.goal.id to it.goal.name }
            voiceCommand = io.github.randomtools3377.checklist.core.VoiceCommand.parse(heard, choices)
        } else {
            result.data?.getStringExtra(io.github.randomtools3377.checklist.core.AiContract.KEY_ERROR)?.let { voiceProblem = it }
        }
    }
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
        Page {
            GapCheckInScreen(checkIn, vm)
        }
        return
    }
    if (!misses.isNullOrEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Page { MissesScreen(misses, vm) }
            celebrations.Overlay()
        }
        return
    }
    if (owed != null) {
        Box(Modifier.fillMaxSize()) {
            Page { PenaltyScreen(owed, makeUp, penaltyTasks, vm) }
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
            OPEN_QUICK_TODO -> {
                goTo(AppTab.TODAY)
                quickTodo = true
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
        // On a wide window a screen with its own top bar sits in the middle at a readable width;
        // the tabs handle their own width.
        val onTab = AppTab.entries.any { it.route == route }
        Box(if (onTab) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).readable()) {
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
            "balance-check" -> if (settings.locks.shop && !unlocked) {
                LockedPanel("The shop", unlock, Modifier)
            } else {
                BalanceCheckScreen(balanceCheck, vm, onBack = ::pop)
            }
            "timeline" -> {
                val day = planDays.getOrNull(id.toInt())
                TimelineScreen(
                    view = day,
                    title = day?.let { if (id == 0L) "Today" else shortDate(it.day) } ?: "",
                    firstHour = settings.dayStart?.hour ?: 6,
                    onRetime = { goalId, minute -> day?.let { vm.retime(goalId, it.day, minute) } },
                    onBack = ::pop,
                    events = day?.let { io.github.randomtools3377.checklist.core.BusyTime.on(calendarEvents, it.day, settings.dayEndHour) }.orEmpty(),
                )
            }
            "character" -> {
                val sheet by vm.character.collectAsStateWithLifecycle()
                val look by vm.look.collectAsStateWithLifecycle()
                val titles by vm.characterTitles.collectAsStateWithLifecycle()
                CharacterScreen(
                    sheet, look, onLook = vm::setLook, onBack = ::pop, titles = titles, onPath = vm::setPath, onTitle = vm::setTitle,
                    onIdeas = { area -> push("ideas/${area.name}") },
                )
            }
            "ideas" -> IdeasScreen(
                vm,
                start = parts.getOrNull(1)?.let { name -> io.github.randomtools3377.checklist.core.SkillArea.entries.firstOrNull { it.name == name } }
                    ?: io.github.randomtools3377.checklist.core.SkillArea.FITNESS,
                onAdd = { idea ->
                    vm.useIdea(idea)
                    push("habit/${idea.goalId ?: 0}")
                },
                onBack = ::pop,
            )
            "plan-week" -> WeekPlanScreen(vm, onOpenPlan = { goTo(AppTab.PLAN) }, onBack = ::pop)
            "done" -> DoneListScreen(doneList, onOpen = { goal -> push(editRoute(goal)) }, onBack = ::pop)
            "removed" -> RemovedGoalsScreen(removedGoals, onRestore = { vm.restoreGoal(it.id) }, onBack = ::pop)
            "reward" -> androidx.compose.runtime.key(route) { RewardEditor(id, vm, onClose = ::pop) }
            "settings" -> SettingsScreen(
                settings, vm,
                onAppearance = { push("appearance") },
                onGuide = { push("guide") },
                onFeatures = { push("features") },
                onOpenTab = { kind -> goTo(AppTab.entries[kind.ordinal]) },
                onBack = ::pop,
            )
            "features" -> FeaturesScreen(vm, onAppearance = { push("appearance") }, onBack = ::pop)
            "appearance" -> AppearanceScreen(appearance, vm, onAnyColour = { push("colour") }, onBack = ::pop)
            "colour" -> AppColourScreen(appearance, onUse = { vm.setColor(it); pop() }, onBack = ::pop)
            "badges" -> if (settings.locks.review && !unlocked) {
                LockedPanel("The badges page", unlock, Modifier)
            } else {
                BadgesScreen(badges, onSeen = vm::badgesSeen, onBack = ::pop, character = io.github.randomtools3377.checklist.core.Feature.CHARACTER in features)
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
                    snackbarHost = { androidx.compose.material3.SnackbarHost(undoHost, modifier = Modifier.testTag("undo-bar")) },
                    actions = {
                        // Voice, with the AI helper on and Checklist AI's speech file added.
                        if (tab == AppTab.TODAY && io.github.randomtools3377.checklist.core.Feature.AI in features && aiStatus?.speechReady == true) {
                            IconButton(
                                onClick = {
                                    runCatching { listen.launch(io.github.randomtools3377.checklist.data.AiClient.listenIntent()) }
                                        .onFailure { voiceProblem = "Checklist AI couldn't be opened. Is it installed?" }
                                },
                                modifier = Modifier.testTag("voice"),
                            ) { Text("🎙") }
                        }
                        if (tab == AppTab.GOALS && (goals?.goals?.size ?: 0) > 1 && !reorderGoals) {
                            TextButton(onClick = { selectGoals = !selectGoals }, modifier = Modifier.testTag("select")) {
                                Text(if (selectGoals) "Done" else "Select")
                            }
                        }
                        if (tab == AppTab.GOALS && goals?.goals?.isNotEmpty() == true && !selectGoals) {
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
                        if (tab == AppTab.GOALS && !reorderGoals && !selectGoals) AddButton("New goal", "add-goal") { newGoal = true }
                        if (tab == AppTab.SHOP && !showBought && !reorderRewards) AddButton("New reward", "add-reward") { push("reward/0") }
                    },
                ) { padding ->
                    when (tab) {
                        AppTab.TODAY -> androidx.compose.runtime.CompositionLocalProvider(
                            LocalBoosted provides today?.boostedGoalId,
                            LocalStreakPercent provides (today?.streakPercent ?: 100),
                            LocalTodayExtras provides TodayExtras(today?.quitDays.orEmpty(), today?.challenges.orEmpty(), vm::slip),
                        ) {
                            TodayScreen(
                                today, vm, celebrations,
                                onAddGoal = { goTo(AppTab.GOALS); newGoal = true },
                                onHistory = { push("history") },
                                onSettings = { push("settings") },
                                onUnlock = unlock,
                                onBadges = { push("badges") },
                                onEditTodo = { id -> push("todo/$id") },
                                onGuide = { push("guide") },
                                onTimeline = { push("timeline/0") },
                                onPlanWeek = { push("plan-week") },
                                onCharacter = { push("character") },
                                onIdeas = { area -> push("ideas/${area.name}") },
                                skipRequest = skipRequest,
                                onSkipRequestHandled = vm::clearSkipRequest,
                                padding = padding,
                            )
                        }
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
                            selecting = selectGoals,
                            vm = vm,
                        )
                        AppTab.PLAN -> PlanScreen(planDays, vm, padding, onTimeline = { index -> push("timeline/$index") })
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
                                badges = badges.keys.count { io.github.randomtools3377.checklist.core.Feature.CHARACTER in features || !it.forCharacter },
                                onBadges = { push("badges") },
                                padding = padding,
                                moods = moods,
                                coachCard = { week -> AiCoachCard(vm, week, hideReasons = settings.locks.reasons && !unlocked) },
                                character = io.github.randomtools3377.checklist.core.Feature.CHARACTER in features,
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
                            onBalanceCheck = { push("balance-check") },
                        )
                    }
                }
            }
        }
        }

        voiceCommand?.let { command ->
            VoiceDialog(
                command,
                onTick = { id -> vm.tickHabit(id) },
                onAdd = { todo -> vm.addTodo(todo) },
                onDismiss = { voiceCommand = null },
            )
        }
        voiceProblem?.let { problem ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { voiceProblem = null },
                title = { Text("Voice") },
                text = { Text(problem) },
                confirmButton = { TextButton(onClick = { voiceProblem = null }) { Text("OK") } },
            )
        }
        if (quickTodo) {
            QuickTodoDialog(onAdd = { name -> vm.addTodo(name); quickTodo = false }, onDismiss = { quickTodo = false })
        }
        if (newGoal) {
            NewGoalDialog(
                onHabit = { newGoal = false; push("habit/0") },
                onProject = { newGoal = false; push("project/0") },
                onTodo = { newGoal = false; push("todo/0") },
                // To-dos are an extra: offered when turned on, or while there are some.
                showTodo = io.github.randomtools3377.checklist.core.Feature.TODOS in features ||
                    goals?.goals.orEmpty().any { it.kind == GoalKind.TODO },
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
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    // A wide window, like a tablet or a phone on its side, has the tabs in a rail down the side.
    val wide = isWide()
    Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (wide) {
            NavigationRail(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
                    .testTag("nav-rail"),
            ) {
                // Scrolls on a small phone on its side, where five tabs only just fit.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (item in tabs) {
                        NavigationRailItem(
                            selected = item == tab,
                            onClick = { onTab(item) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag("nav:${item.route}"),
                        )
                    }
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            snackbarHost = snackbarHost,
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
                if (!wide) {
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
                }
            },
            floatingActionButton = fab,
        ) { padding ->
            // Today keeps its backdrop across the whole window and holds its own list to a
            // readable width; the other tabs are held to it here.
            if (tab == AppTab.TODAY) content(padding) else Box(Modifier.fillMaxSize().readable()) { content(padding) }
        }
    }
}

/** The editor for a goal of each kind. */
private fun editRoute(goal: io.github.randomtools3377.checklist.core.Goal): String = when (goal.kind) {
    GoalKind.HABIT -> "habit/${goal.id}"
    GoalKind.PROJECT -> "project/${goal.id}"
    GoalKind.TODO -> "todo/${goal.id}"
}

/** How long the Undo bar stays. */
private const val UNDO_MILLIS = 5_000L

/** What the app shortcuts ask for, through MainActivity.EXTRA_OPEN (see res/xml/shortcuts.xml). */
const val OPEN_NEW_TODO = "new-todo"
const val OPEN_NEW_GOAL = "new-goal"

/** From the quick-add widget: a box on Today to add a to-do. */
const val OPEN_QUICK_TODO = "quick-todo"

/** The box the quick-add widget opens: type a to-do and add it. */
@Composable
private fun QuickTodoDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a to-do") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(80) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("quick-todo-field"),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onAdd(text) }, enabled = text.isNotBlank(), modifier = Modifier.testTag("quick-todo-add")) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
