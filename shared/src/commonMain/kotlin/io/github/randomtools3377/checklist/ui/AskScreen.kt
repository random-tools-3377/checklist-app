package io.github.randomtools3377.checklist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.randomtools3377.checklist.core.AreaIdea
import io.github.randomtools3377.checklist.core.AskAction
import io.github.randomtools3377.checklist.core.AskAnswer
import io.github.randomtools3377.checklist.core.AskFigure
import io.github.randomtools3377.checklist.core.AskGuide
import io.github.randomtools3377.checklist.core.AskKind
import io.github.randomtools3377.checklist.core.AskLocks
import io.github.randomtools3377.checklist.core.AskState
import io.github.randomtools3377.checklist.core.AskTurn
import io.github.randomtools3377.checklist.core.Asker
import io.github.randomtools3377.checklist.core.Budget
import io.github.randomtools3377.checklist.core.CharacterSheet
import io.github.randomtools3377.checklist.core.Feature
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.MoodFindings
import io.github.randomtools3377.checklist.core.ReviewSummary
import io.github.randomtools3377.checklist.core.Streaks
import io.github.randomtools3377.checklist.core.VoiceCommand
import io.github.randomtools3377.checklist.core.aboutNow
import io.github.randomtools3377.checklist.core.WeekProgress
import io.github.randomtools3377.checklist.core.minusDays
import kotlinx.coroutines.Job

/*
 * "Ask": a page to ask about the day, the goals, or how any part of the app works, like
 * chatting. The app finds the facts (see Asker); with the AI helper on and ready, the AI puts
 * them into words, and without it the app's own short answer shows. Under every answer the app
 * itself shows its own figures, the guides it came from and the buttons that go there. The
 * conversation lives in this page's memory only: it is gone when the page closes or the
 * privacy lock closes, and it never reaches the database, a backup, a notification or a widget.
 */

/** Every guide for [device], in the user's own words and numbers, for Ask to search. */
internal fun askGuides(device: GuideDevice, extras: List<Feature> = emptyList()): List<AskGuide> {
    val topics = HelpTopic.entries.filter { it.shownOn(device) }
    val guides = topics.map { topic ->
        AskGuide(topic.name, topic.title, guideWords(topic.intro), topic.stepsOn(device).map { guideWords(it) }, topic.extras)
    }
    // An extra with no guide of its own (a challenge, savings interest, habit chains) is answered
    // from its line on the Features page, with its switch.
    val covered = HelpTopic.entries.flatMap { it.extras }.toSet()
    return guides + extras.filter { it !in covered }.map { feature ->
        AskGuide(Asker.FEATURE + feature.name, feature.title, feature.aboutNow(), listOf("It's an extra: turn \"${feature.title}\" on or off in Settings → Features."), setOf(feature))
    }
}

/** This month's money as figures, from the Budget tab's own sums; null for another month or no money at all. */
internal fun moneyFigures(budget: BudgetView?, now: kotlinx.datetime.LocalDateTime?): List<AskFigure>? {
    if (budget == null || now == null || !budget.thisMonth) return null
    val month = Budget.month(budget.first, budget.plans, budget.changes, budget.entries, budget.categories, now)
    if (month.pay == null && month.receivedCents == 0L && month.spentCents == 0L && month.savedCents == 0L) return null
    return buildList {
        month.pay?.let { add(AskFigure("Pay earned", moneyText(it.earned, alwaysCents = true))) }
        if (month.receivedCents > 0) add(AskFigure("Other money in", centsText(month.receivedCents)))
        add(AskFigure("Spent", centsText(month.spentCents)))
        if (month.savedCents != 0L) add(AskFigure("Saved", centsText(month.savedCents)))
        add(AskFigure("Left", moneyText(month.leftNow, alwaysCents = true)))
    }
}

/**
 * What Ask may know right now. Everything behind the privacy lock is left out here, before any
 * answer is built: locked private goals (Today's list, the week and the balance check come
 * without them already), the review's numbers, the shop's rewards, the budget, and moods.
 */
internal fun askState(
    today: TodayState?,
    week: ReviewSummary?,
    lastWeek: ReviewSummary?,
    target: WeekProgress?,
    best: Streaks?,
    goals: List<Goal>,
    check: BalanceCheckView?,
    shop: ShopView?,
    starred: Long?,
    character: CharacterSheet?,
    findings: MoodFindings,
    moods: List<Mood>,
    money: List<AskFigure>?,
    features: Set<Feature>,
    locks: Locks,
    unlocked: Boolean,
    guides: List<AskGuide>,
): AskState {
    val hidePrivate = locks.privateGoals && !unlocked
    val asked = AskLocks(
        review = locks.review && !unlocked,
        shop = locks.shop && !unlocked,
        budget = locks.budget && !unlocked,
        // Moods and what goes with them lock with reasons and notes, and with the review, like their own pages.
        moods = (locks.reasons || locks.review) && !unlocked,
    )
    fun shown(goal: Goal) = !(hidePrivate && goal.private)
    val weeks = if (asked.review) emptyList() else listOfNotNull(lastWeek, week)
    val missed = weeks.flatMap { it.goals }.filter { shown(it.goal) && it.missed > 0 }.groupBy { it.goal.id }
        .maxByOrNull { (_, rows) -> rows.sumOf { it.missed } }?.value?.first()?.goal
    val day = today?.view?.day
    return AskState(
        today = today?.view,
        nextId = today?.view?.dueToday?.firstOrNull { !it.settled }?.goal?.id,
        hiddenPrivate = if (hidePrivate) today?.hiddenPrivate ?: 0 else 0,
        streaks = today?.streaks,
        // The best run is only shown in the review.
        bestStreak = best?.done.takeIf { !asked.review },
        week = week?.takeIf { !asked.review }?.let { w -> w.copy(goals = w.goals.filter { shown(it.goal) }) },
        lastWeek = lastWeek?.takeIf { !asked.review }?.let { w -> w.copy(goals = w.goals.filter { shown(it.goal) }) },
        target = target?.takeIf { it.target > 0 },
        goals = goals.filter { !it.archived && shown(it) },
        checks = if (asked.review) emptyList() else check?.goals.orEmpty().filter { shown(it.goal) },
        rewards = if (asked.shop) emptyList() else shop?.rewards.orEmpty().map { it.reward },
        balance = today?.view?.balance ?: shop?.balance ?: 0,
        // The usual earnings are shown in the shop and worked out from the review's numbers.
        perDay = (shop?.perDay ?: check?.perDay).takeIf { !asked.shop && !asked.review },
        starredId = starred.takeIf { !asked.shop },
        character = character.takeIf { Feature.CHARACTER in features },
        moodLifts = if (asked.moods || Feature.MOOD !in features) emptyList() else findings.lifts.filter { shown(it.goal) }.map { it.goal.name },
        moods = if (asked.moods || Feature.MOOD !in features || day == null) emptyList() else moods.filter { it.day > day.minusDays(7) && it.day <= day }.sortedBy { it.day },
        money = money.takeIf { !asked.budget && Feature.BUDGET in features },
        missedMost = missed,
        features = features,
        locks = asked,
        guides = guides,
    )
}

/** The state Ask answers from, kept up to date while the page or the sheet is open. */
@Composable
internal fun rememberAskState(vm: MainViewModel): AskState {
    val today by vm.todayState.collectAsStateWithLifecycle()
    val week by vm.thisWeek.collectAsStateWithLifecycle()
    val lastWeek by vm.lastWeek.collectAsStateWithLifecycle()
    val target by vm.weekProgress.collectAsStateWithLifecycle()
    val best by vm.bestStreaks.collectAsStateWithLifecycle()
    val goals by vm.currentGoals.collectAsStateWithLifecycle()
    val check by vm.balanceCheck.collectAsStateWithLifecycle()
    val shop by vm.shopView.collectAsStateWithLifecycle()
    val starred by vm.starredReward.collectAsStateWithLifecycle()
    val character by vm.character.collectAsStateWithLifecycle()
    val findings by vm.moodFindings.collectAsStateWithLifecycle()
    val moods by vm.moodJournal.collectAsStateWithLifecycle()
    val budget by vm.budget.collectAsStateWithLifecycle()
    val features by vm.features.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val device = LocalPhone.current.guideDevice()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val phone = LocalPhone.current
    val guides = remember(device, rules, features) { askGuides(device, featuresOn(phone)) }
    val money = remember(budget, today?.now) { moneyFigures(budget, today?.now) }
    return remember(today, week, lastWeek, target, best, goals, check, shop, starred, character, findings, moods, money, features, settings.locks, unlocked, guides) {
        askState(today, week, lastWeek, target, best, goals, check, shop, starred, character, findings, moods, money, features, settings.locks, unlocked, guides)
    }
}

/** One answer on the page: what was asked, the answer, and whether the AI put it into words. */
private class Said(val turn: AskTurn, val byAi: Boolean)

/** A question still being put into words by the AI. */
private class Pending(val question: String, val answer: AskAnswer, val job: Job)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskScreen(
    vm: MainViewModel,
    /** A question to ask as the page opens, from voice. */
    first: String? = null,
    onOpen: (String) -> Unit,
    onFill: (AreaIdea) -> Unit,
    onUnlock: () -> Unit,
    onBack: () -> Unit,
) {
    val state = rememberAskState(vm)
    val features by vm.features.collectAsStateWithLifecycle()
    val status by vm.aiStatus.collectAsStateWithLifecycle()
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val aiOn = Feature.AI in features
    val usable = aiOn && status?.usable == true
    LaunchedEffect(aiOn) {
        if (aiOn) {
            vm.refreshAi()
            vm.warmAi()
        }
    }
    // In memory only, on purpose: never saved, and gone with the page.
    val said = remember { mutableStateListOf<Said>() }
    var pending by remember { mutableStateOf<Pending?>(null) }
    var box by rememberSaveable { mutableStateOf("") }
    var command by remember { mutableStateOf<VoiceCommand?>(null) }
    var guide by remember { mutableStateOf<HelpTopic?>(null) }
    var voiceProblem by remember { mutableStateOf<String?>(null) }

    fun ask(question: String) {
        val asked = question.trim().take(Asker.MAX_QUESTION)
        if (asked.isEmpty() || pending != null) return
        box = ""
        // "done reading" and "add buy milk" typed here are asked about first, like by voice.
        if (!VoiceCommand.isQuestion(asked)) {
            val goals = state.open.map { it.goal.id to it.goal.name }
            val todos = state.openTodos.map { it.goal.id to it.goal.name }
            val parsed = VoiceCommand.parse(asked, goals, todos)
            if (parsed is VoiceCommand.Tick || parsed is VoiceCommand.Add || (parsed is VoiceCommand.NotUnderstood && parsed.closest.isNotEmpty())) {
                command = parsed
                return
            }
        }
        val answer = Asker.answer(asked, state, said.lastOrNull()?.turn)
        if (usable && answer.facts.isNotBlank() && answer.kind != AskKind.UNKNOWN) {
            val earlier = said.map { it.turn }
            // The answer can come back before this line is over (a test's AI answers at once),
            // so what came is kept until the wait is known.
            var came = false
            var words: String? = null
            var waiting: Pending? = null
            fun show() {
                said += Said(AskTurn(asked, answer, words ?: answer.text), byAi = words != null)
            }
            val job = vm.askInWords(asked, answer, earlier) { fromAi ->
                came = true
                words = fromAi
                val mine = waiting
                if (mine != null && pending === mine) {
                    pending = null
                    show()
                }
            }
            if (came) {
                show()
            } else {
                waiting = Pending(asked, answer, job).also { pending = it }
            }
        } else {
            said += Said(AskTurn(asked, answer, answer.text), byAi = false)
        }
    }

    // The lock closing clears the conversation; the lock opening answers a locked question again.
    var wasUnlocked by remember { mutableStateOf(unlocked) }
    LaunchedEffect(unlocked) {
        if (wasUnlocked && !unlocked) {
            pending?.job?.cancel()
            pending = null
            said.clear()
        } else if (!wasUnlocked && unlocked) {
            said.lastOrNull()?.takeIf { it.turn.answer.locked }?.let { last ->
                said.removeAt(said.lastIndex)
                ask(last.turn.question)
            }
        }
        wasUnlocked = unlocked
    }
    // A question that came with the page is asked once Today's list has loaded.
    var askedFirst by remember(first) { mutableStateOf(false) }
    LaunchedEffect(first, state.today != null) {
        if (first != null && state.today != null && !askedFirst) {
            askedFirst = true
            ask(first)
        }
    }

    val listen = rememberVoice(onHeard = { heard -> ask(heard) }, onProblem = { voiceProblem = it })
    val list = rememberLazyListState()
    val calm = LocalCalm.current
    LaunchedEffect(said.size, pending) {
        val count = said.size + (if (pending != null) 1 else 0) + 2
        if (calm) list.scrollToItem((count - 1).coerceAtLeast(0)) else list.animateScrollToItem((count - 1).coerceAtLeast(0))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                actions = { HelpAction() },
            )
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedTextField(
                    value = box,
                    onValueChange = { box = it.take(Asker.MAX_QUESTION) },
                    placeholder = { Text("Ask a question") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { ask(box) }),
                    modifier = Modifier.weight(1f).testTag("ask-box"),
                )
                if (aiOn && status?.speechReady == true && listen != null) {
                    IconButton(onClick = listen, modifier = Modifier.testTag("ask-voice")) { Text("🎙", modifier = Modifier.spoken("Say your question")) }
                }
                IconButton(onClick = { ask(box) }, enabled = box.isNotBlank() && pending == null, modifier = Modifier.testTag("ask-send")) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize().testTag("ask"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Ask about your day, your goals, or how any part of the app works. Typing \"done\" and a goal, or \"add\" and a to-do, works here too.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (usable) {
                            "The app finds the answer in your own numbers and its guides; the AI on this ${LocalPhone.current.deviceWord} puts it into words. Nothing you ask is kept."
                        } else {
                            "Answers come from your own numbers and the app's guides. Nothing you ask is kept."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("ask-how"),
                    )
                }
            }
            itemsIndexed(said, key = { index, _ -> "said:$index" }) { index, entry ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuestionBubble(entry.turn.question)
                    AnswerCard(
                        entry.turn.answer, entry.turn.said, entry.byAi, tag = "ask-answer:$index",
                        onGuide = { key ->
                            // An extra with no guide of its own opens at its switch.
                            if (key.startsWith(Asker.FEATURE)) onOpen("features/" + key.removePrefix(Asker.FEATURE)) else guide = HelpTopic.entries.firstOrNull { it.name == key }
                        },
                        onOpen = onOpen, onFill = onFill, onUnlock = onUnlock,
                    )
                }
            }
            pending?.let { waiting ->
                item(key = "pending") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuestionBubble(waiting.question)
                        AiWaiting(onStop = {
                            // Stopping shows the app's own answer straight away.
                            waiting.job.cancel()
                            pending = null
                            said += Said(AskTurn(waiting.question, waiting.answer, waiting.answer.text), byAi = false)
                        })
                    }
                }
            }
            item(key = "suggestions") {
                val suggestions = remember(state, said.size) { Asker.suggestions(state).filter { s -> said.none { it.turn.question == s } } }
                if (pending == null && suggestions.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("ask-suggestions")) {
                        suggestions.forEachIndexed { index, suggestion ->
                            AssistChip(onClick = { ask(suggestion) }, label = { Text(suggestion) }, modifier = Modifier.testTag("ask-suggestion:$index"))
                        }
                    }
                }
            }
        }
    }
    guide?.let { topic -> HelpDialog(topic, onDismiss = { guide = null }) }
    command?.let { parsed ->
        VoiceDialog(
            parsed,
            // A to-do with steps is done by finishing its steps.
            onTick = { id, _ -> if (state.openTodos.any { it.goal.id == id && it.items.isNotEmpty() }) vm.finishRoutine(id) else vm.tickHabit(id) },
            onAdd = { todo -> vm.addTodo(todo) },
            onDismiss = { command = null },
        )
    }
    voiceProblem?.let { problem ->
        AlertDialog(
            onDismissRequest = { voiceProblem = null },
            title = { Text("Voice") },
            text = { Text(problem) },
            confirmButton = { TextButton(onClick = { voiceProblem = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun QuestionBubble(question: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.widthIn(max = 420.dp)) {
            Text(question, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
}

/**
 * An answer: its words, then what the app itself adds under them, whoever worded it: its own
 * figures, the guides it came from, and the buttons, which only open screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnswerCard(
    answer: AskAnswer,
    words: String,
    byAi: Boolean,
    tag: String,
    onGuide: (String) -> Unit,
    onOpen: (String) -> Unit,
    onFill: (AreaIdea) -> Unit,
    onUnlock: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag(tag)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(words, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("$tag:text"))
            AnswerExtras(answer, byAi, tag, onGuide, onOpen, onFill, onUnlock)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnswerExtras(
    answer: AskAnswer,
    byAi: Boolean,
    tag: String,
    onGuide: (String) -> Unit,
    onOpen: (String) -> Unit,
    onFill: (AreaIdea) -> Unit,
    onUnlock: () -> Unit,
) {
    if (answer.figures.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.testTag("$tag:figures")) {
            answer.figures.forEach { figure ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text("${figure.label}: ${figure.value}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
    if (answer.guides.isNotEmpty()) {
        FlowRow(verticalArrangement = Arrangement.Center, modifier = Modifier.testTag("$tag:guides")) {
            Text("From:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
            answer.guides.forEach { from ->
                TextButton(onClick = { onGuide(from.key) }, modifier = Modifier.testTag("$tag:guide:${from.key}")) { Text(from.title) }
            }
        }
    }
    if (answer.actions.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("$tag:actions")) {
            answer.actions.forEachIndexed { index, action ->
                val click = when (action) {
                    is AskAction.Open -> ({ onOpen(action.route) })
                    is AskAction.Fill -> ({ onFill(action.idea) })
                    AskAction.Unlock -> onUnlock
                }
                if (index == 0) {
                    FilledTonalButton(onClick = click, modifier = Modifier.testTag("$tag:action:$index")) { Text(action.label) }
                } else {
                    OutlinedButton(onClick = click, modifier = Modifier.testTag("$tag:action:$index")) { Text(action.label) }
                }
            }
        }
    }
    if (byAi) {
        Text(
            "Worded by the AI on this ${LocalPhone.current.deviceWord}, which can get things wrong. The figures, links and buttons are the app's own.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "How am I doing?", on Today, in the review and on a goal's page ([goal] for that one): the
 * summary from the app's own numbers, with one next step and its button. With the AI helper on
 * and ready, "Say it in words" has the AI write the same facts as a friendly paragraph.
 */
@Composable
fun HowDoingDialog(
    vm: MainViewModel,
    goal: Goal? = null,
    onOpen: (String) -> Unit,
    onFill: (AreaIdea) -> Unit,
    onUnlock: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberAskState(vm)
    val features by vm.features.collectAsStateWithLifecycle()
    val status by vm.aiStatus.collectAsStateWithLifecycle()
    val usable = Feature.AI in features && status?.usable == true
    LaunchedEffect(Unit) { if (Feature.AI in features) vm.refreshAi() }
    val answer = remember(state, goal?.id) {
        when {
            goal == null -> Asker.today(state)
            state.goals.none { it.id == goal.id } -> AskAnswer(AskKind.GOAL, "This goal is behind the privacy lock.", locked = true, actions = listOf(AskAction.Unlock))
            // Opened from the goal's own page, so there is no need for a button back to it.
            else -> Asker.goal(state, goal).let { a -> a.copy(actions = a.actions.filterNot { it is AskAction.Open && it.route == "goal/${goal.id}" }) }
        }
    }
    var inWords by remember(answer) { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }
    var message by remember(answer) { mutableStateOf<String?>(null) }
    var guide by remember { mutableStateOf<HelpTopic?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goal == null) "How am I doing?" else "How is ${goal.name} going?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("how-doing-sheet"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                inWords?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("how-doing-words")) }
                val lines = answer.facts.lines().filter { it.isNotBlank() }.ifEmpty { listOf(answer.text) }
                if (inWords == null) {
                    lines.forEach { line -> Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("how-doing-line")) }
                    // What isn't a fact for the AI, like a locked part, is still said.
                    if (answer.facts.isNotBlank() && answer.locked) Text("Part of this is behind the privacy lock.", style = MaterialTheme.typography.bodySmall)
                }
                AnswerExtras(
                    answer, byAi = inWords != null, tag = "how-doing",
                    onGuide = { key -> guide = HelpTopic.entries.firstOrNull { it.name == key } },
                    onOpen = { route -> onDismiss(); onOpen(route) },
                    onFill = { idea -> onDismiss(); onFill(idea) },
                    onUnlock = onUnlock,
                )
                if (usable && answer.facts.isNotBlank() && inWords == null && job == null) {
                    TextButton(
                        onClick = {
                            message = null
                            job = vm.sayInWords(answer) { words, error ->
                                job = null
                                inWords = words
                                if (words == null) message = (error ?: "The AI's words didn't come out right this time.") + " The app's own summary is above."
                            }
                        },
                        modifier = Modifier.testTag("how-doing-say"),
                    ) { Text("✨ Say it in words") }
                }
                if (job != null) {
                    AiWaiting(onStop = {
                        job?.cancel()
                        job = null
                    })
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("how-doing-close")) { Text("Close") } },
        modifier = Modifier.testTag("how-doing"),
    )
    guide?.let { topic -> HelpDialog(topic, onDismiss = { guide = null }) }
}
