@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Alignment
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
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.data.toDay
import io.github.randomtools3377.checklist.data.toEpochDay

private enum class AppTab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.CheckCircle),
    PLAN("plan", "Plan", Icons.Filled.DateRange),
    GOALS("goals", "Goals", Icons.AutoMirrored.Filled.List),
    SHOP("shop", "Shop", Icons.Filled.ShoppingCart),
    REVIEW("review", "Review", Icons.Filled.Star),
    BUDGET("budget", "Budget", MoneyIcon),
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
/**
 * The app on any phone: the [phone]'s own parts, the theme, and the screens. Android's activity
 * and the iPhone's view controller each show this.
 */
@Composable
fun ChecklistRoot(phone: Phone, vm: MainViewModel) {
    androidx.compose.runtime.CompositionLocalProvider(LocalPhone provides phone) {
        val appearance by vm.appearance.collectAsStateWithLifecycle()
        val rate by vm.currentDifficulty.collectAsStateWithLifecycle()
        ChecklistTheme(appearance) {
            androidx.compose.runtime.CompositionLocalProvider(LocalPayRate provides rate) {
                // Measures the window, for tablets and phones on their side.
                WindowSized { ChecklistApp(vm) }
            }
        }
    }
}

@Composable
fun ChecklistApp(vm: MainViewModel) {
    val celebrations = remember { Celebrations() }
    val sounds = rememberSounds()
    androidx.compose.runtime.DisposableEffect(sounds) {
        celebrations.sounds = sounds
        onDispose {
            celebrations.sounds = null
            sounds?.release()
        }
    }
    val stack = rememberSaveable(saver = RoutesSaver) { mutableListOf(AppTab.TODAY.route).toMutableStateList() }
    var showBought by rememberSaveable { mutableStateOf(false) }
    var newGoal by remember { mutableStateOf(false) }
    // The box from the quick-add widget.
    var quickTodo by remember { mutableStateOf(false) }
    // The Undo bar: the last tick, skip, purchase or removed goal, for 5 seconds. A newer one replaces it.
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

    BackButton(enabled = stack.size > 1 || route != AppTab.TODAY.route) { pop() }

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
    val privateIds by vm.privateGoalIds.collectAsStateWithLifecycle()
    // Goals marked private, while the lock is on for them: left out or not named.
    val hiddenGoals = if (settings.locks.privateGoals && !unlocked) privateIds else emptySet()
    // Leaving the app closes the lock, and the pages behind it close with it. The private goals
    // are a key too, as they can load after the settings when the phone restores the app.
    LaunchedEffect(unlocked, settings.locks, privateIds) {
        if (!unlocked && stack.removeAll { lockedPage(it, settings.locks, privateIds) != null } && stack.isEmpty()) {
            stack.add(AppTab.TODAY.route)
        }
    }
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
    val listen = rememberVoice(
        onHeard = { heard ->
            // Goals due today, by name, to match what was said.
            val choices = vm.todayState.value?.view?.dueToday.orEmpty().map { it.goal.id to it.goal.name }
            voiceCommand = io.github.randomtools3377.checklist.core.VoiceCommand.parse(heard, choices)
        },
        onProblem = { voiceProblem = it },
    )
    val badges by vm.badges.collectAsStateWithLifecycle()
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val planDays by vm.planDays.collectAsStateWithLifecycle()
    celebrations.style = appearance.celebrate
    // The app opens on the tab chosen in Appearance, once the settings have been read.
    var firstTabApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(appearance.loaded) {
        if (appearance.loaded && !firstTabApplied) {
            firstTabApplied = true
            // The Budget tab only while Budget is on, read here as the features may not be loaded yet.
            val budgetOn = appearance.tabs.opensOn != io.github.randomtools3377.checklist.core.AppTabKind.BUDGET ||
                vm.featureOn(io.github.randomtools3377.checklist.core.Feature.BUDGET)
            val first = AppTab.entries[appearance.tabs.opensOn(budgetOn).ordinal]
            if (first != AppTab.TODAY && stack.size == 1 && stack[0] == AppTab.TODAY.route) stack[0] = first.route
        }
    }
    // In the user's order.
    val shownTabs = appearance.tabs.shown.map { kind -> AppTab.entries[kind.ordinal] }.filter { tab ->
        tab != AppTab.BUDGET || io.github.randomtools3377.checklist.core.Feature.BUDGET in features
    }
    val tickNotes by vm.tickNotes.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val reviewPeriod by vm.reviewPeriod.collectAsStateWithLifecycle()
    val allGaps by vm.gaps.collectAsStateWithLifecycle()
    // Misses that still stand in the review's stretch, and the one opened to be put right.
    val reviewMisses by vm.reviewMisses.collectAsStateWithLifecycle()
    val openMiss by vm.openMiss.collectAsStateWithLifecycle()

    // Opening the app: the gap check-in or misses waiting for a reason first, then the penalty task, then the rest.
    val misses = unexplained
    val owed = penalty
    val checkIn = gap
    if (checkIn != null) {
        Page {
            GapCheckInScreen(checkIn, vm, hiddenGoals = hiddenGoals, onUnlock = unlock)
        }
        return
    }
    if (!misses.isNullOrEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Page { MissesScreen(misses, vm, hiddenGoals = hiddenGoals, onUnlock = unlock) }
            celebrations.Overlay()
        }
        return
    }
    if (owed != null) {
        Box(Modifier.fillMaxSize()) {
            Page { PenaltyScreen(owed, makeUp, penaltyTasks, vm, hiddenGoals = hiddenGoals, onUnlock = unlock) }
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
        val window = LocalWindowSize.current ?: androidx.compose.ui.unit.DpSize(360.dp, 640.dp)
        val density = androidx.compose.ui.platform.LocalDensity.current
        val center = with(density) { androidx.compose.ui.geometry.Offset(window.width.toPx() / 2, window.height.toPx() / 2) }
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

    // Someone brand new: the first-run setup, until it's done or skipped. "Add my own" opens the
    // habit editor over it, and the setup carries on once the editor closes.
    val setup by vm.setupStep.collectAsStateWithLifecycle()
    val setupStep = setup
    if (setupStep != null && stack.size == 1) {
        Page { FirstRunScreen(setupStep, vm, onOwnGoal = { push("habit/0") }) }
        return
    }

    androidx.compose.runtime.SideEffect { ShownRoute.route = route }
    FirstTimeTip(route, vm)
    Box(Modifier.fillMaxSize()) {
        val parts = route.split("/")
        val id = parts.getOrNull(1)?.toLongOrNull() ?: 0L
        // On a wide window a screen with its own top bar sits in the middle at a readable width;
        // the tabs handle their own width.
        val onTab = AppTab.entries.any { it.route == route }
        Box(if (onTab) Modifier.fillMaxSize() else Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).readable()) {
        // A page behind the lock shows the lock until unlocked, with a way back where the phone has no back button.
        val pageLock = if (unlocked) null else lockedPage(route, settings.locks, privateIds)
        if (pageLock != null) Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            LockedPanel(pageLock, unlock, Modifier)
            TextButton(onClick = ::pop, modifier = Modifier.testTag("locked-back")) { Text("Back") }
        } else
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
                hasGoals = goals?.goals?.isNotEmpty() == true,
                onExamples = { vm.addExamples(); goTo(AppTab.TODAY) },
                onScratch = { vm.closeWelcome(); goTo(AppTab.TODAY) },
                onBack = ::pop,
            )
            "balance-check" -> BalanceCheckScreen(balanceCheck, vm, onBack = ::pop)
            "timeline" -> {
                val day = planDays.getOrNull(id.toInt())
                TimelineScreen(
                    view = day,
                    title = day?.let { if (id == 0L) "Today" else shortDate(it.day) } ?: "",
                    firstHour = settings.dayStart?.hour ?: 6,
                    onRetime = { goalId, minute -> day?.let { vm.retime(goalId, it.day, minute) } },
                    onBack = ::pop,
                    events = day?.let { io.github.randomtools3377.checklist.core.BusyTime.on(calendarEvents, it.day, settings.dayEndHour) }.orEmpty(),
                    pace = io.github.randomtools3377.checklist.core.PaceSettings(settings.dayEndHour, settings.defaultFinish, settings.dayStart),
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
            // Last week's figures are part of the review, so they sit behind its lock.
            "plan-week" -> WeekPlanScreen(
                vm, onOpenPlan = { goTo(AppTab.PLAN) }, onBack = ::pop,
                lastWeekLocked = settings.locks.review && !unlocked, onUnlock = unlock,
            )
            "mood-goals" -> MoodGoalsScreen(vm, onEdit = { goalId -> push("habit/$goalId") }, onBack = ::pop, onJournal = { push("mood-journal") })
            "mood-journal" -> MoodJournalScreen(vm, onBack = ::pop)
            "done" -> DoneListScreen(doneList, onOpen = { goal -> push(editRoute(goal)) }, onBack = ::pop)
            "removed" -> RemovedGoalsScreen(removedGoals, onRestore = { vm.restoreGoal(it.id) }, onBack = ::pop)
            "reward" -> androidx.compose.runtime.key(route) { RewardEditor(id, vm, onClose = ::pop) }
            "budget-pay" -> PayEditor(vm, onClose = ::pop)
            "money" -> androidx.compose.runtime.key(route) { MoneyEditor(id, received = parts.getOrNull(2) == "in", vm = vm, onClose = ::pop) }
            "budget-categories" -> CategoriesScreen(vm, onBack = ::pop)
            "work-day" -> androidx.compose.runtime.key(route) { WorkDayEditor(id.toDay(), vm, onClose = ::pop) }
            "budget-bills" -> BillsScreen(vm, onBill = { billId -> push("bill/$billId") }, onBack = ::pop)
            "bill" -> androidx.compose.runtime.key(route) { BillEditor(id, vm, onClose = ::pop) }
            "budget-report" -> BudgetReportScreen(vm, onBack = ::pop)
            "rules" -> RulesScreen(vm, onBack = ::pop)
            "help" -> HelpScreen(vm, onBack = ::pop)
            "settings" -> SettingsScreen(
                settings, vm,
                onAppearance = { push("appearance") },
                onGuide = { push("guide") },
                onFeatures = { push("features") },
                onRules = { push("rules") },
                onHelp = { push("help") },
                onOpenTab = { kind -> goTo(AppTab.entries[kind.ordinal]) },
                onBack = ::pop,
                onLook = { place -> push("appearance/$place") },
                onFeature = { feature -> push("features/${feature.name}") },
            )
            // From a Settings search, these open at what was found: "features/QUESTS", "appearance/theme".
            "features" -> FeaturesScreen(vm, onAppearance = { push("appearance") }, onBack = ::pop, focus = parts.getOrNull(1))
            "appearance" -> AppearanceScreen(appearance, vm, onAnyColour = { push("colour") }, onBack = ::pop, focus = parts.getOrNull(1))
            "colour" -> AppColourScreen(appearance, onUse = { vm.setColor(it); pop() }, onBack = ::pop)
            "badges" -> BadgesScreen(badges, onSeen = vm::badgesSeen, onBack = ::pop, character = io.github.randomtools3377.checklist.core.Feature.CHARACTER in features)
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
                        if (tab == AppTab.TODAY && io.github.randomtools3377.checklist.core.Feature.AI in features && aiStatus?.speechReady == true && listen != null) {
                            IconButton(
                                onClick = listen,
                                modifier = Modifier.testTag("voice"),
                            ) { Text("🎙", modifier = Modifier.spoken("Speak to tick a goal or add a to-do")) }
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
                        if (tab == AppTab.SHOP && !showBought && shop?.rewards?.isNotEmpty() == true && !(settings.locks.shop && !unlocked)) {
                            TextButton(onClick = { reorderRewards = !reorderRewards }, modifier = Modifier.testTag("reorder")) {
                                Text(if (reorderRewards) "Done" else "Reorder")
                            }
                        }
                    },
                    fab = {
                        if (tab == AppTab.GOALS && !reorderGoals && !selectGoals) AddButton("New goal", "add-goal") { newGoal = true }
                        if (tab == AppTab.SHOP && !showBought && !reorderRewards && !(settings.locks.shop && !unlocked)) AddButton("New reward", "add-reward") { push("reward/0") }
                        if (tab == AppTab.BUDGET && !(settings.locks.budget && !unlocked)) AddButton("Spent", "add-money") { push("money/0") }
                    },
                ) { padding ->
                    when (tab) {
                        AppTab.TODAY -> androidx.compose.runtime.CompositionLocalProvider(
                            LocalBoosted provides today?.boostedGoalId,
                            LocalStreakPercent provides (today?.streakPercent ?: 100),
                            LocalTodayExtras provides TodayExtras(today?.quitDays.orEmpty(), today?.challenges.orEmpty(), vm::slip, vm::takeBackSkip),
                        ) {
                            TodayScreen(
                                today, vm, celebrations,
                                onAddGoal = { goTo(AppTab.GOALS); newGoal = true },
                                onHistory = { push("history") },
                                onSettings = { push("settings") },
                                onUnlock = unlock,
                                onBadges = { push("badges") },
                                onEditTodo = { id -> push("todo/$id") },
                                onTimeline = { push("timeline/0") },
                                onPlanWeek = { push("plan-week") },
                                onCharacter = { push("character") },
                                onIdeas = { area -> push("ideas/${area.name}") },
                                onBudget = { goTo(AppTab.BUDGET) },
                                onMoodGoals = { push("mood-goals") },
                                onMoodJournal = { push("mood-journal") },
                                skipRequest = skipRequest,
                                onSkipRequestHandled = vm::clearSkipRequest,
                                padding = padding,
                            )
                        }
                        AppTab.GOALS -> GoalsScreen(
                            goals,
                            reordering = reorderGoals,
                            onEdit = { goal -> push(editRoute(goal)) },
                            onMove = { goal, beside -> vm.moveGoalTo(goal.id, beside.id) },
                            onUnlock = unlock,
                            padding = padding,
                            removed = removedGoals.size,
                            onRemoved = { push("removed") },
                            doneCount = doneList.size,
                            onDoneList = { push("done") },
                            selecting = selectGoals,
                            vm = vm,
                            onNew = { newGoal = true },
                            finishedProjects = doneList.filter { it.goal.kind == io.github.randomtools3377.checklist.core.GoalKind.PROJECT }.associate { it.goal.id to it.day },
                        )
                        AppTab.PLAN -> PlanScreen(planDays, vm, padding, onTimeline = { index -> push("timeline/$index") }, onEditTodo = { id -> push("todo/$id") })
                        AppTab.REVIEW -> if (settings.locks.review && !unlocked) {
                            LockedPanel("The review", unlock, Modifier.padding(padding))
                        } else {
                            // Best streaks look through every day there is, so only while the review is open.
                            val bestStreaks by vm.bestStreaks.collectAsStateWithLifecycle()
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
                                onMoodGoals = { push("mood-goals") },
                                onMoodJournal = { push("mood-journal") },
                                // Nothing to look back on yet: a way to add a first goal.
                                onAddGoal = if (goals?.goals?.isEmpty() == true && removedGoals.isEmpty()) ({ goTo(AppTab.GOALS); newGoal = true }) else null,
                                misses = reviewMisses,
                                onMiss = { vm.openMiss.value = it },
                                best = bestStreaks,
                                streakSettings = settings.streak,
                                today = today?.view?.day,
                            )
                        }
                        AppTab.BUDGET -> if (settings.locks.budget && !unlocked) {
                            LockedPanel("The budget", unlock, Modifier.padding(padding))
                        } else BudgetScreen(
                            vm, padding,
                            onPay = { push("budget-pay") },
                            onEntry = { id -> push("money/$id") },
                            onReceived = { push("money/0/in") },
                            onCategories = { push("budget-categories") },
                            onChangeDay = { day -> push("work-day/${day.toEpochDay()}") },
                            onBills = { push("budget-bills") },
                            onReport = { push("budget-report") },
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
                            onBalanceCheck = { push("balance-check") },
                            onNew = { push("reward/0") },
                        )
                    }
                }
            }
        }
        }

        // A past miss opened from the review or the points history, to be put right.
        openMiss?.let { lapseId -> PastMissDialog(lapseId, vm, onUnlock = unlock) { vm.openMiss.value = null } }
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
                        HelpAction()
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

/**
 * What a page holds behind the privacy lock, named for its lock panel, or null when none of its
 * locks is on. The tabs and the history show their own lock in place. [privateGoals] are the ids
 * of the goals marked private.
 */
internal fun lockedPage(route: String, locks: Locks, privateGoals: Set<Long>): String? {
    val parts = route.split("/")
    return when (parts[0]) {
        "budget-pay", "money", "budget-categories", "work-day", "budget-bills", "bill", "budget-report" -> "The budget".takeIf { locks.budget }
        "reward", "balance-check" -> "The shop".takeIf { locks.shop }
        "habit", "project", "todo" -> "This private goal".takeIf { locks.privateGoals && parts.getOrNull(1)?.toLongOrNull()?.let { it in privateGoals } == true }
        // Moods sit behind the lock with reasons and notes, and they are part of the review.
        "mood-goals" -> "Mood and your goals".takeIf { locks.reasons || locks.review }
        // The journal holds the notes, so it is locked the same way.
        "mood-journal" -> "The mood journal".takeIf { locks.reasons || locks.review }
        "badges" -> "The badges page".takeIf { locks.review }
        else -> null
    }
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
