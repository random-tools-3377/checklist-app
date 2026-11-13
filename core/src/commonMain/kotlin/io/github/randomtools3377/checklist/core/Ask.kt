package io.github.randomtools3377.checklist.core

/*
 * "Ask": the user types (or says) a question and gets an answer, like chatting. The app finds
 * the facts, here, from the user's own numbers and from the Help guides; the small AI, when it
 * is there, only puts them into words. Without the AI the app's own short answer shows instead,
 * with the same links and buttons, so Ask works on every device. Nothing here ticks, skips,
 * spends or saves: buttons only open screens, and they are picked by the app, never the AI.
 */

/** What is behind the privacy lock right now, and so left out of every answer. */
data class AskLocks(
    val review: Boolean = false,
    val shop: Boolean = false,
    val budget: Boolean = false,
    /** Moods and their notes (they lock with reasons and notes). */
    val moods: Boolean = false,
)

/** One of the app's own figures, shown as a chip beside an answer: "Done today" and "3 of 5". */
data class AskFigure(val label: String, val value: String)

/** A Help guide, with its text already in the user's own words and numbers. */
data class AskGuide(
    val key: String,
    val title: String,
    val intro: String,
    val steps: List<String>,
    /** The extras the guide is about, all of which have to be on to use it. */
    val extras: Set<Feature> = emptySet(),
)

/**
 * Everything an answer can be built from, as the app holds it right now. What is locked is
 * simply not in here: [today], [goals], [week] and the rest come without locked private goals,
 * and a locked part is null or empty, with its flag in [locks] so the answer can say so.
 */
data class AskState(
    val today: TodayView? = null,
    /** The goal that is next on Today, by the one shared order. */
    val nextId: Long? = null,
    /** Private goals left out because the lock is on. */
    val hiddenPrivate: Int = 0,
    val streaks: Streaks? = null,
    val bestStreak: Int? = null,
    val week: ReviewSummary? = null,
    val lastWeek: ReviewSummary? = null,
    val target: WeekProgress? = null,
    val goals: List<Goal> = emptyList(),
    /** Each goal over the last 4 weeks, with the balance check's hints. */
    val checks: List<GoalCheck> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    val balance: Int = 0,
    /** What the user usually earns a day. */
    val perDay: Double? = null,
    val starredId: Long? = null,
    val character: CharacterSheet? = null,
    /** Goals that go with a better mood, by name, biggest first. */
    val moodLifts: List<String> = emptyList(),
    /** The latest moods, newest last. */
    val moods: List<Mood> = emptyList(),
    /** This month's money, already worded; null without a budget. */
    val money: List<AskFigure>? = null,
    /** The goal missed most lately. */
    val missedMost: Goal? = null,
    val features: Set<Feature> = emptySet(),
    val locks: AskLocks = AskLocks(),
    val guides: List<AskGuide> = emptyList(),
) {
    val open: List<HabitRow> get() = today?.dueToday.orEmpty().filter { !it.settled }
    val openTodos: List<TodoRow> get() = today?.todos.orEmpty().filter { !it.done }
}

/** What kind of question it was. */
enum class AskKind { NEXT, LEFT, TODAY, WEEK, GOAL, REWARD, STREAK, LEVEL, MONEY, MOOD, BETTER, HOW_TO, UNKNOWN }

/** A button under an answer. Each only opens a screen. */
sealed interface AskAction {
    val label: String

    /** Opens a screen by its route, like "goal/12", "features/QUESTS" or "shop". */
    data class Open(override val label: String, val route: String) : AskAction

    /** Opens a goal's editor filled in with [idea], to check and save. */
    data class Fill(override val label: String, val idea: AreaIdea) : AskAction

    /** Asks for the phone's lock, to answer with what was locked. */
    data object Unlock : AskAction {
        override val label: String get() = "Unlock"
    }
}

/**
 * The app's answer. [text] is its own short answer; [facts] is what the AI may word instead
 * (empty when there is nothing for it to add); [figures] are the app's own numbers, shown
 * beside whatever the AI says; [guides] are where it came from; [actions] are the buttons.
 */
data class AskAnswer(
    val kind: AskKind,
    val text: String,
    val facts: String = "",
    val figures: List<AskFigure> = emptyList(),
    val guides: List<AskGuide> = emptyList(),
    val actions: List<AskAction> = emptyList(),
    /** Part of the answer is behind the privacy lock. */
    val locked: Boolean = false,
)

/** A question and its answer, kept in memory only while the page is open, for follow-ups. */
data class AskTurn(val question: String, val answer: AskAnswer, val said: String)

object Asker {
    const val MAX_QUESTION = 200

    /** The start of the key of a guide that is an extra's line on the Features page, followed by the extra's name. */
    const val FEATURE = "FEATURE_"

    /** How much of the facts and of the earlier turns the small AI is given, in letters. */
    const val MAX_FACTS = 1400
    const val MAX_EARLIER = 400
    const val MAX_TOKENS = 220

    val SYSTEM = """
        You are the helper inside a habit checklist app, talking with its user like a friendly guide.
        Answer the question in 2 to 5 short, friendly sentences, using ONLY the notes given.
        If the notes don't answer it, say you don't know and that Help in Settings has every guide.
        Never make up numbers, buttons or features. Write button names exactly as in the notes.
        Notes about mood say what goes together, never what causes what; keep it that way.
        No lists, no headings, no emoji.
    """.trimIndent()

    /** For "Say it in words": the same facts as a friendly paragraph. */
    val SYSTEM_WORDS = """
        You are a kind, practical coach inside a habit checklist app.
        Rewrite the notes given as 2 to 4 short, warm sentences to the user, as "you".
        Use ONLY the notes: every number stays as it is, and nothing is added. No lists, no headings, no emoji.
    """.trimIndent()

    // Matching the question

    private fun norm(text: String): String =
        " " + text.lowercase().replace('’', '\'').replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim() + " "

    private fun has(q: String, vararg phrases: String): Boolean = phrases.any { " $it " in q || (it.endsWith("*") && " ${it.dropLast(1)}" in q) }

    /** The topics "how do I get better at …" knows, beside the skill areas. */
    enum class Topic { STREAKS, PACE, POINTS, MONEY, MOOD, FOCUS, GENERAL }

    private val stop = setOf(
        "a", "an", "the", "i", "me", "my", "do", "does", "did", "is", "are", "am", "be", "to", "of", "in", "on", "at", "for", "it", "its",
        "how", "what", "whats", "what's", "where", "when", "why", "which", "who", "can", "could", "should", "would", "will", "and", "or",
        "that", "this", "there", "you", "your", "app", "about", "with", "get", "have", "has", "if", "so", "mean", "means", "work", "works",
        "tell", "show", "please", "s", "t", "up", "use", "using", "one", "thing", "things", "again", "then", "any", "some",
        "much", "many", "need", "want", "just", "really", "into", "from", "im", "i'm", "ive", "dont", "was", "were", "been", "they", "them",
        "we", "us", "our", "also", "too", "very", "something", "anything", "everything", "out", "off", "as", "by", "no", "not", "yes", "data", "stuff",
    )

    /** The words of [text] that carry meaning, in their simplest form. */
    fun keywords(text: String): List<String> =
        words(text.replace('’', '\'').replace("'s", " ")).filter { it.length > 1 && it !in stop && it !in stopStems }.distinct()

    /** The stop words as they come out of [words], so "does" isn't kept as "doe". */
    private val stopStems: Set<String> by lazy { stop.map { SettingsSearch.stem(it) }.toSet() }

    /** The words of [text] in their simplest form, with "to-do" kept as one word. */
    private fun words(text: String): List<String> = SettingsSearch.words(text.lowercase().replace(Regex("\\bto-dos?\\b"), "todo").replace(Regex("\\btodos\\b"), "todo"))

    private fun isHowTo(q: String): Boolean = has(
        q, "how do", "how do i", "how do you", "how to", "how can i", "how does", "how would i", "where do i", "where is", "where are", "where can i",
        "what is a", "what is an", "what is the", "what's a", "what's an", "what does", "what do", "what are", "what happens", "explain",
        "can i", "why", "is there", "does it", "do i need", "what if",
    )

    private fun wantsBetter(q: String): Boolean = has(
        q, "better at", "get better", "be better", "do better", "improve", "level up", "more consistent", "tips", "advice", "help me with",
        "struggling", "struggle", "get ahead", "keep up", "catch up", "stay on track", "back on track", "grow my", "raise my", "boost my", "build my",
        "better with", "good at", "earn more", "save more", "spend less", "feel better", "focus better", "be faster", "go faster", "keep my streak",
        "keep a streak", "longer streak",
    )

    private fun topicOf(q: String): Topic? = when {
        has(q, "streak", "streaks") -> Topic.STREAKS
        has(q, "pace", "pacing", "on time", "faster", "keep up", "catch up", "behind", "late", "finish everything") -> Topic.PACE
        has(q, "money", "budget", "spend", "spending", "save", "saving", "savings", "bills") -> Topic.MONEY
        has(q, "points", "rewards", "reward", "earn", "earn more", "balance", "shop", "prices") -> Topic.POINTS
        has(q, "mood", "moods", "feel", "feeling", "happier") -> Topic.MOOD
        has(q, "focus", "focusing", "concentrate", "concentrating", "concentration") -> Topic.FOCUS
        else -> null
    }

    private fun areaOf(q: String): SkillArea? =
        SkillArea.entries.firstOrNull { " ${it.label.lowercase()} " in q } ?: SkillArea.entries.firstOrNull { " ${it.name.lowercase()} " in q }

    /**
     * The goal or reward a question names. Loosely, the one sharing the most whole words with
     * it; with [whole], only one whose every word was said, so "the morning plan" isn't taken
     * for a goal called "Morning run".
     */
    private fun <T> named(q: String, things: List<T>, whole: Boolean = false, small: Boolean = true, name: (T) -> String): T? {
        val every = words(q).toSet()
        if (every.isEmpty()) return null
        return things.map { thing ->
            // A name made only of small words ("Work", "Do it") is matched on those words as they are.
            val words = keywords(name(thing)).ifEmpty { if (small) words(name(thing)).filter { it in every } else emptyList() }
            val shared = words.count { it in every }
            thing to if (words.isEmpty() || (whole && shared < words.size)) 0.0 else shared.toDouble() / words.size + shared
        }.filter { it.second >= 1.0 }.maxByOrNull { it.second }?.first
    }

    /** Whether [question] leans on the one before: "and how do I change that?". */
    fun isFollowUp(question: String): Boolean {
        val q = norm(question)
        return q.startsWith(" and ") || q.startsWith(" what about ") || q.startsWith(" how about ") || q.startsWith(" so ") ||
            has(q, "that", "it", "this", "those", "them")
    }

    /** Asking how to do something in the app, rather than about the user's own numbers. */
    private fun isInstruction(q: String): Boolean = has(
        q, "how do i", "how do you", "how to", "how can i", "how would i", "how does", "where do i", "where can i", "where is", "where are",
        "can i", "explain", "what happens", "what if", "is there", "does it",
    )

    /** "Why did I lose points?", "why did my balance go down?" */
    private fun asksAboutLoss(q: String): Boolean =
        has(q, "points", "point", "balance", "coins", "xp") && has(q, "lose", "lost", "losing", "go down", "went down", "gone down", "going down", "dropped", "drop", "less", "lower", "low", "down", "minus", "negative", "taken off", "came off", "missing")

    /** When the day starts and ends is a setting, not today's list. */
    private fun aboutDayTimes(q: String): Boolean = has(q, "day start", "day starts", "day end", "day ends", "day begin", "day begins", "day finish", "day finishes", "end of day", "end of the day", "start of day", "start of the day")

    /**
     * Answers [question] from [state]. [previous] is the turn before, so a follow-up that only
     * says "that" still finds its subject.
     */
    fun answer(question: String, state: AskState, previous: AskTurn? = null): AskAnswer {
        val q = norm(question.take(MAX_QUESTION))
        if (q.isBlank()) return unknown(state)
        val said = keywords(q)
        if (has(q, "thanks", "thank you", "thank", "cheers") && said.size <= 2) {
            return AskAnswer(AskKind.UNKNOWN, "You're welcome. Ask me anything else about your day or the app.")
        }
        if (has(q, "hello", "hi", "hey", "good morning", "good evening", "good afternoon") && said.size <= 2) return hello(state)
        if (has(q, "what can you do", "what can i ask", "what can i ask you", "what do you know", "what can you tell me", "what can you answer") || (has(q, "help", "help me") && said.size <= 1)) return hello(state)
        val howTo = isHowTo(q)
        val instruction = isInstruction(q)
        val better = wantsBetter(q)
        val area = areaOf(q)
        if (better) {
            if (area != null) return betterAt(area, state)
            // "I'm struggling with my morning run" is about that goal.
            named(q, state.goals) { it.name }?.let { return goal(state, it) }
            topicOf(q)?.let { return betterAt(it, state) }
            if (!howTo || has(q, "get better", "do better", "improve", "back on track", "stay on track", "catch up", "keep up", "get ahead", "struggling", "struggle", "tips", "advice")) {
                return betterAt(Topic.GENERAL, state)
            }
        }
        if (asksAboutLoss(q)) return lost(state)
        if (has(q, "target", "targets") && !has(q, "how do i", "how to", "how can i", "set", "change", "where")) target(state)?.let { return it }
        // Asked about so often that the guide is known: these never fall to the user's numbers.
        if (knownGuides(q).isNotEmpty()) guideAnswer(question, state)?.let { return it }
        val dayTimes = aboutDayTimes(q)
        if (!howTo && !dayTimes) {
            val data = dataAnswer(q, state)
            if (data != null) return data
        } else if (!dayTimes) {
            // "What is my streak?" and "what are my skills?" ask for the user's own numbers.
            if (has(q, "my") && !has(q, "what happens", "what if", "explain", "how do i", "how to", "how can i")) dataAnswer(q, state, loose = false)?.let { return it }
            // "What do I still have to do?" and "is there anything left today?" are about today's list.
            if (!has(q, "how do i", "how to", "how can i", "how does", "where do i", "where can i", "can i", "explain", "what happens", "what if") &&
                has(q, "my", "today", "still", "am i", "do i have", "have i", "did i", "start with", "next", "now", "first")
            ) {
                dataAnswer(q, state)?.let { return it }
            }
            if (has(q, "can i buy", "can i afford", "can i get", "do i have enough")) return reward(state, named(q, state.rewards) { it.name })
            // "What does Reading pay?" is about the user's own goal, not about the app.
            if (!instruction) named(q, state.goals, whole = true, small = false) { it.name }?.let { return goal(state, it) }
        }
        // A follow-up with little of its own ("and how do I change that?") is searched with the question before.
        val carriesOn = q.startsWith(" and ") || q.startsWith(" what about ") || q.startsWith(" how about ") || q.startsWith(" so ")
        val leans = previous != null && isFollowUp(question) && (carriesOn || said.size <= 1)
        val search = if (leans) previous!!.question + " " + question else question
        guideAnswer(search, state)?.let { return it }
        // A follow-up with nothing new in it: the guides of the answer before.
        if (previous != null && previous.answer.guides.isNotEmpty() && isFollowUp(question)) {
            return fromGuides(previous.answer.guides, asked(search), index(previous.answer.guides), state)
        }
        if (howTo && area != null) return betterAt(area, state)
        return unknown(state)
    }

    private fun dataAnswer(q: String, state: AskState, loose: Boolean = true): AskAnswer? {
        // A goal whose whole name was said comes before the general words; one only partly named, after them.
        val goal = named(q, state.goals, whole = true) { it.name }
        val partly = named(q, state.goals) { it.name }
        val reward = named(q, state.rewards) { it.name }
        val aboutLevel = has(q, "level", "levels", "skill", "skills", "xp", "experience", "character")
        return when {
            has(q, "what's next", "whats next", "what is next", "what next", "next", "start with", "what should i do", "what do i do now", "what now", "where do i start") && loose && !aboutLevel -> next(state)
            has(
                q, "left", "remaining", "still to do", "to do today", "open", "what's due", "what is due", "due today", "on today", "today's list",
                "still have to do", "have to do", "left to do", "need to do", "still need", "not done", "haven't done", "to do",
            ) && loose -> left(state)
            has(q, "how close", "how far", "afford", "saving for", "can i buy", "enough for", "enough points", "how long until", "how long till", "when can i buy") ||
                (reward != null && partly == null) -> reward(state, reward)
            has(q, "streak", "streaks") -> streak(state)
            goal != null -> goal(state, goal)
            has(q, "week", "weekly", "last week", "this week") -> week(state)
            aboutLevel -> level(state, areaOf(q))
            has(q, "budget", "money", "spent", "spend", "spending", "pay", "earned this month", "bills") -> money(state)
            has(q, "mood", "moods", "feeling", "felt", "feel") -> mood(state)
            partly != null -> goal(state, partly)
            has(q, "balance", "points", "how many points", "how much do i have") && loose -> reward(state, null)
            has(q, "how am i doing", "how am i", "how's it going", "how is it going", "how's today", "how is today", "my day", "progress", "summary", "overview", "doing today", "how did i do", "doing") && loose -> today(state)
            else -> null
        }
    }

    // Data answers

    private fun privateNote(state: AskState): String? =
        if (state.hiddenPrivate > 0) "Private goals are locked, so they're left out." else null

    private fun withPrivate(state: AskState, answer: AskAnswer): AskAnswer {
        val note = privateNote(state) ?: return answer
        return answer.copy(text = answer.text + " " + note, locked = true, actions = (answer.actions + AskAction.Unlock).distinct())
    }

    private fun locked(kind: AskKind, what: String): AskAnswer =
        AskAnswer(kind, "$what is behind the privacy lock. Unlock to ask about it.", locked = true, actions = listOf(AskAction.Unlock))

    private fun rowName(row: HabitRow): String = row.goal.name

    private fun todayCounts(state: AskState): Triple<Int, Int, Int> {
        val due = state.today?.dueToday.orEmpty()
        return Triple(due.count { it.done }, due.size, due.count { it.skipped })
    }

    /** "What's next?" */
    fun next(state: AskState): AskAnswer {
        val view = state.today ?: return AskAnswer(AskKind.NEXT, "Today's list isn't ready yet. Try again in a moment.")
        val open = state.open
        val row = open.firstOrNull { it.goal.id == state.nextId } ?: open.firstOrNull()
        val todo = state.openTodos.firstOrNull()
        val (done, due, _) = todayCounts(state)
        val answer = when {
            row != null -> {
                val more = open.size - 1
                val detail = listOfNotNull(
                    row.goal.at?.let { "at ${clockText(it)}" },
                    row.goal.minutes?.let { "about $it minutes" },
                    if (row.goal.target > 1) "${row.count} of ${row.goal.target} so far" else null,
                ).joinToString(", ")
                val text = "Next is ${rowName(row)}" + (if (detail.isNotEmpty()) " ($detail)" else "") + ". " +
                    when (more) {
                        0 -> "It's the last one for today."
                        1 -> "After that, 1 more is left today."
                        else -> "After that, $more more are left today."
                    }
                AskAnswer(
                    AskKind.NEXT, text, facts = text,
                    figures = listOf(AskFigure("Next", rowName(row)), AskFigure("Done today", "$done of $due")),
                    actions = listOf(AskAction.Open("Open Today", "today"), AskAction.Open("Open ${rowName(row)}", "goal/${row.goal.id}")),
                )
            }
            todo != null -> {
                val text = "Every goal due today is settled. A to-do is still open: ${todo.goal.name}."
                AskAnswer(AskKind.NEXT, text, facts = text, figures = listOf(AskFigure("Done today", "$done of $due")), actions = listOf(AskAction.Open("Open Today", "today")))
            }
            due == 0 && view.thisWeek.none { !it.settled } -> AskAnswer(AskKind.NEXT, "Nothing is due today.", actions = listOf(AskAction.Open("Open Goals", "goals")))
            else -> {
                val weekly = view.thisWeek.firstOrNull { !it.settled }
                val text = if (weekly != null) {
                    "Everything due today is done. ${weekly.goal.name} still has ${weekly.left} to go this week, if you'd like to get ahead."
                } else {
                    "Everything due today is done. Nothing is next."
                }
                AskAnswer(AskKind.NEXT, text, facts = text, figures = listOf(AskFigure("Done today", "$done of $due")), actions = listOf(AskAction.Open("Open Today", "today")))
            }
        }
        return withPrivate(state, answer)
    }

    /** "What's left today?" */
    fun left(state: AskState): AskAnswer {
        val view = state.today ?: return AskAnswer(AskKind.LEFT, "Today's list isn't ready yet. Try again in a moment.")
        val open = state.open
        val todos = state.openTodos
        val (done, due, skipped) = todayCounts(state)
        val names = open.map { row -> rowName(row) + if (row.goal.target > 1) " (${row.left} to go)" else "" }
        val text = buildString {
            when {
                due == 0 -> append("Nothing is due today.")
                open.isEmpty() -> append("Everything due today is done" + (if (skipped > 0) ", apart from $skipped skipped" else "") + ".")
                else -> append("${open.size} of $due ${if (open.size == 1) "is" else "are"} left today: ${listText(names.take(6))}" + (if (names.size > 6) " and ${names.size - 6} more" else "") + ".")
            }
            if (todos.isNotEmpty()) append(" Open to-dos: ${listText(todos.take(4).map { it.goal.name })}" + (if (todos.size > 4) " and ${todos.size - 4} more" else "") + ".")
            val weekly = view.thisWeek.filter { !it.settled }
            if (weekly.isNotEmpty()) append(" Still to do this week: ${listText(weekly.take(4).map { "${it.goal.name} (${it.left} to go)" })}.")
        }
        val figures = buildList {
            add(AskFigure("Done today", "$done of $due"))
            if (open.isNotEmpty()) add(AskFigure("Left", open.size.toString()))
            if (skipped > 0) add(AskFigure("Skipped", skipped.toString()))
            if (todos.isNotEmpty()) add(AskFigure("To-dos open", todos.size.toString()))
        }
        return withPrivate(state, AskAnswer(AskKind.LEFT, text, facts = text, figures = figures, actions = listOf(AskAction.Open("Open Today", "today"))))
    }

    /** "How am I doing?": the summary, with one next step. */
    fun today(state: AskState): AskAnswer {
        val doing = HowDoing.of(state)
        return withPrivate(
            state,
            AskAnswer(
                AskKind.TODAY, doing.lines.joinToString(" "), facts = doing.lines.joinToString("\n"), figures = doing.figures,
                actions = listOfNotNull(doing.next?.action, AskAction.Open("Open the Review", "review")),
                locked = state.locks.review,
            ).let { if (state.locks.review) it.copy(text = it.text + " This week's numbers are behind the privacy lock.", actions = it.actions + AskAction.Unlock) else it },
        )
    }

    /** "How is this week going?" */
    fun week(state: AskState): AskAnswer {
        if (state.locks.review) return locked(AskKind.WEEK, "The review")
        val week = state.week ?: return AskAnswer(AskKind.WEEK, "This week's numbers aren't ready yet. Try again in a moment.")
        val lines = HowDoing.weekLines(state)
        if (lines.isEmpty()) return AskAnswer(AskKind.WEEK, "Nothing has been due yet this week.", actions = listOf(AskAction.Open("Open the Review", "review")))
        val due = week.goals.sumOf { it.due }
        val done = week.goals.sumOf { it.done }
        val figures = buildList {
            add(AskFigure("This week", "$done of $due"))
            add(AskFigure("Earned", PointsName.current.amount(week.earned)))
            if (week.lost > 0) add(AskFigure("Lost", week.lost.toString()))
            state.target?.takeIf { it.target > 0 }?.let { add(AskFigure("Weekly target", "${it.earned} of ${it.target}")) }
        }
        return withPrivate(
            state,
            AskAnswer(AskKind.WEEK, lines.joinToString(" "), facts = lines.joinToString("\n"), figures = figures, actions = listOf(AskAction.Open("Open the Review", "review"))),
        )
    }

    /** "How is Reading going?" */
    fun goal(state: AskState, goal: Goal): AskAnswer {
        if (state.locks.review) return locked(AskKind.GOAL, "The review")
        val lines = HowDoing.goalLines(state, goal)
        val check = state.checks.firstOrNull { it.goal.id == goal.id }
        val inWeek = state.week?.goals?.firstOrNull { it.goal.id == goal.id }
        val figures = buildList {
            inWeek?.takeIf { it.due > 0 }?.let { add(AskFigure("This week", "${it.done} of ${it.due}")) }
            check?.let { add(AskFigure("Last 4 weeks", "${it.done} of ${it.due}")) }
            inWeek?.takeIf { it.currentRun >= 2 }?.let { add(AskFigure("Run", "${it.currentRun} in a row")) }
            add(AskFigure("Pays", PointsName.current.amount(goal.points)))
        }
        val text = if (lines.isEmpty()) "${goal.name} hasn't been due often enough yet to say how it is going." else lines.joinToString(" ")
        return AskAnswer(
            AskKind.GOAL, text, facts = if (lines.isEmpty()) "" else lines.joinToString("\n"), figures = figures,
            actions = listOf(AskAction.Open("Open ${goal.name}", "goal/${goal.id}"), AskAction.Open("Edit ${goal.name}", editRoute(goal))),
        )
    }

    private fun editRoute(goal: Goal): String = when (goal.kind) {
        GoalKind.PROJECT -> "project/${goal.id}"
        GoalKind.HABIT -> "habit/${goal.id}"
        else -> "todo/${goal.id}"
    }

    /** "How close is the film night?", or the balance. */
    fun reward(state: AskState, named: Reward?): AskAnswer {
        val balance = PointsName.current.amount(state.balance)
        if (state.locks.shop) {
            return AskAnswer(
                AskKind.REWARD, "Your balance is $balance. The shop is behind the privacy lock; unlock to ask about rewards.",
                figures = listOf(AskFigure("Balance", balance)), locked = true, actions = listOf(AskAction.Unlock),
            )
        }
        val reward = named ?: state.rewards.firstOrNull { it.id == state.starredId }
            ?: state.rewards.filter { !it.archived && it.price > state.balance }.minByOrNull { it.price }
        if (reward == null) {
            val text = if (state.rewards.none { !it.archived }) "Your balance is $balance. There are no rewards in the shop yet." else "Your balance is $balance, enough for every reward in the shop."
            return AskAnswer(AskKind.REWARD, text, facts = text, figures = listOf(AskFigure("Balance", balance)), actions = listOf(AskAction.Open("Open the Shop", "shop")))
        }
        val saving = SavingFor(reward, state.balance, state.perDay)
        val text = if (saving.canBuy) {
            "Your balance is $balance. ${reward.name} costs ${reward.price}, so you can buy it now."
        } else {
            "Your balance is $balance. ${reward.name} costs ${reward.price}: ${saving.left} to go" + (saving.more?.let { ", $it at your usual pace" } ?: "") + "."
        }
        val figures = buildList {
            add(AskFigure("Balance", balance))
            add(AskFigure(reward.name, PointsName.current.amount(reward.price)))
            if (!saving.canBuy) add(AskFigure("To go", saving.left.toString()))
        }
        return AskAnswer(AskKind.REWARD, text, facts = text, figures = figures, actions = listOf(AskAction.Open("Open the Shop", "shop")))
    }

    /** "What's my streak?" */
    fun streak(state: AskState): AskAnswer {
        val streaks = state.streaks ?: return AskAnswer(AskKind.STREAK, "Your streaks aren't ready yet. Try again in a moment.")
        val best = state.bestStreak?.takeIf { it > streaks.done }
        val text = buildString {
            append(if (streaks.done == 0) "Your main streak isn't running right now." else "Your main streak is ${days(streaks.done)}.")
            best?.let { append(" Your best is ${days(it)}.") }
            val others = listOfNotNull(
                streaks.noMisses.takeIf { it > 0 }?.let { "Miss-free ${days(it)}" },
                streaks.noSkips.takeIf { it > 0 }?.let { "Skip-free ${days(it)}" },
                streaks.perfect.takeIf { it > 0 }?.let { "perfect days ${days(it)}" },
            )
            if (others.isNotEmpty()) append(" Also running: ${listText(others)}.")
            NearWins.streak(streaks.done)?.let { append(" $it") }
        }
        val figures = buildList {
            add(AskFigure("Main streak", days(streaks.done)))
            best?.let { add(AskFigure("Best", days(it))) }
            if (streaks.noMisses > 0) add(AskFigure("Miss-free", days(streaks.noMisses)))
            if (streaks.noSkips > 0) add(AskFigure("Skip-free", days(streaks.noSkips)))
            if (streaks.perfect > 0) add(AskFigure("Perfect days", days(streaks.perfect)))
        }
        return AskAnswer(AskKind.STREAK, text, facts = text, figures = figures, guides = guides(state, "STREAKS"), actions = listOf(AskAction.Open("Open the Review", "review")))
    }

    /** "What level am I?" */
    fun level(state: AskState, area: SkillArea? = null): AskAnswer {
        if (Feature.CHARACTER !in state.features) {
            return AskAnswer(
                AskKind.LEVEL, "Levels and skills come with the \"Character and skills\" extra, which is off. Turn it on in Settings → Features.",
                guides = guides(state, "CHARACTER"), actions = listOf(turnOn(Feature.CHARACTER)),
            )
        }
        val sheet = state.character ?: return AskAnswer(AskKind.LEVEL, "Your character isn't ready yet. Try again in a moment.")
        val skills = SkillArea.entries.map { it to sheet.skillLevel(it) }
        val shown = if (area != null) skills.filter { it.first == area } else skills.sortedByDescending { sheet.skills[it.first] ?: 0 }
        val text = buildString {
            append("Your character is level ${sheet.level.level}, ${sheet.level.toNext} experience from level ${sheet.level.level + 1}.")
            if (area != null) {
                val level = sheet.skillLevel(area)
                append(" ${area.label} is level ${level.level}, ${level.toNext} from level ${level.level + 1}.")
            } else {
                append(" Skills: " + listText(shown.map { (a, l) -> "${a.label} ${l.level}" }) + ".")
                append(" ${Ideas.weakest(sheet).label} has the least experience so far.")
            }
        }
        val figures = listOf(AskFigure("Character", "Level ${sheet.level.level}")) + shown.map { (a, l) -> AskFigure(a.label, "Level ${l.level}") }
        return AskAnswer(
            AskKind.LEVEL, text, facts = text, figures = figures, guides = guides(state, "CHARACTER"),
            actions = listOf(AskAction.Open("Open your character", "character"), AskAction.Open("Ideas for ${(area ?: Ideas.weakest(sheet)).label}", "ideas/${(area ?: Ideas.weakest(sheet)).name}")),
        )
    }

    /** "How is my budget this month?" */
    fun money(state: AskState): AskAnswer {
        if (Feature.BUDGET !in state.features) {
            return AskAnswer(
                AskKind.MONEY, "The Budget is an extra, and it's off. Turn it on in Settings → Features to follow your pay and spending.",
                guides = guides(state, "BUDGET"), actions = listOf(turnOn(Feature.BUDGET)),
            )
        }
        if (state.locks.budget) return locked(AskKind.MONEY, "The budget")
        val money = state.money?.takeIf { it.isNotEmpty() }
            ?: return AskAnswer(AskKind.MONEY, "There's no money typed in for this month yet. Set your pay or add spending on the Budget tab.", guides = guides(state, "BUDGET"), actions = listOf(AskAction.Open("Open Budget", "budget")))
        val text = "This month so far: " + listText(money.map { "${it.label.lowercase()} ${it.value}" }) + "."
        return AskAnswer(AskKind.MONEY, text, facts = text, figures = money, guides = guides(state, "BUDGET"), actions = listOf(AskAction.Open("Open Budget", "budget")))
    }

    private val faces = listOf("very low", "low", "okay", "good", "great")

    /** "How has my mood been?" */
    fun mood(state: AskState): AskAnswer {
        if (Feature.MOOD !in state.features) {
            return AskAnswer(
                AskKind.MOOD, "The mood check-in is an extra, and it's off. Turn it on in Settings → Features.",
                guides = guides(state, "MOOD_JOURNAL"), actions = listOf(turnOn(Feature.MOOD)),
            )
        }
        if (state.locks.moods) return locked(AskKind.MOOD, "Your moods and their notes")
        val moods = state.moods.takeLast(7)
        if (moods.isEmpty()) {
            return AskAnswer(AskKind.MOOD, "There are no mood check-ins in the last week yet. Today asks each morning.", guides = guides(state, "MOOD_JOURNAL"), actions = listOf(AskAction.Open("Open Today", "today")))
        }
        val average = moods.map { it.last }.average()
        val word = faces[(average + 0.5).toInt().coerceIn(1, 5) - 1]
        val text = buildString {
            append("Over your last ${moods.size} check-in${if (moods.size == 1) "" else "s"}, your mood has been ${word} on the whole (${oneDecimal(average)} out of 5).")
            val first = moods.take(moods.size / 2).map { it.last }
            val second = moods.drop(moods.size / 2).map { it.last }
            if (first.isNotEmpty() && moods.size >= 4) {
                val change = second.average() - first.average()
                if (change >= 0.5) append(" It has been higher in the last few days.") else if (change <= -0.5) append(" It has been lower in the last few days; that's all right.")
            }
            if (state.moodLifts.isNotEmpty()) append(" ${listText(state.moodLifts.take(3))} ${if (state.moodLifts.take(3).size == 1) "goes" else "go"} with a better mood for you. These go together; it doesn't say what causes what.")
        }
        val figures = listOf(AskFigure("Check-ins", moods.size.toString()), AskFigure("Average", "${oneDecimal(average)} of 5")) +
            state.moodLifts.take(3).map { AskFigure("Goes with a better mood", it) }
        return AskAnswer(
            AskKind.MOOD, text, facts = text, figures = figures, guides = guides(state, "MOOD_GOALS", "MOOD_JOURNAL"),
            actions = listOf(AskAction.Open("Mood and your goals", "mood-goals"), AskAction.Open("Mood journal", "mood-journal")),
        )
    }

    // Guidance: "how do I get better at …"

    /** Guidance for a skill area: where it stands, the next step for the user's own goals in it, and new ideas. */
    fun betterAt(area: SkillArea, state: AskState): AskAnswer {
        val sheet = state.character
        val level = sheet?.skillLevel(area)
        val own = state.goals.filter { !it.archived && it.kind == GoalKind.HABIT && SkillArea.of(it) == area }
        val steps = Ideas.stepUps(state.goals, area).take(2)
        val fresh = Ideas.library(area, level?.level ?: 1, state.goals, emptySet()).take(3)
        val lines = buildList {
            if (level != null && Feature.CHARACTER in state.features) add("${area.label} is level ${level.level}, ${level.toNext} experience from level ${level.level + 1}. It grows with every tick of a goal in that area.")
            else add("${area.label} is about ${Ideas.about(area)}.")
            if (own.isEmpty()) add("You have no goals in ${area.label} yet, so a first small one is the best start.")
            else add("Your goals in it: ${listText(own.take(5).map { it.name })}.")
            steps.forEach { step ->
                val to = listOfNotNull(
                    step.goal.minutes?.let { "$it min" },
                    step.goal.amount?.let { Amounts.format(it) + (step.goal.unit?.let { u -> " $u" } ?: "") },
                    step.goal.timesPerWeek?.let { "$it times a week" },
                ).firstOrNull()
                if (to != null) add("A next step for ${step.goal.name}: $to instead of ${step.from}.")
            }
            state.checks.filter { SkillArea.of(it.goal) == area && it.hint == GoalHint.PAY_MORE }.minByOrNull { it.share }?.let {
                add("${it.goal.name} got done ${it.done} of ${it.due} times lately; making it smaller or giving it a set time could help.")
            }
            if (fresh.isNotEmpty()) add("New ideas: ${listText(fresh.map { "${it.goal.name} (${it.detail.lowercase()})" })}.")
        }
        val actions = buildList {
            if (Feature.CHARACTER in state.features) add(AskAction.Open("Ideas for ${area.label}", "ideas/${area.name}")) else add(turnOn(Feature.CHARACTER))
            steps.firstOrNull()?.goalId?.let { id -> add(AskAction.Open("Open ${steps.first().goal.name}", "goal/$id")) }
        }
        val figures = buildList {
            if (level != null && Feature.CHARACTER in state.features) add(AskFigure(area.label, "Level ${level.level}"))
            add(AskFigure("Goals in it", own.size.toString()))
        }
        return AskAnswer(AskKind.BETTER, lines.joinToString(" "), facts = lines.joinToString("\n"), figures = figures, guides = guides(state, "IDEAS", "CHARACTER"), actions = actions)
    }

    /** Guidance for a part of the app, from what the app already works out. */
    fun betterAt(topic: Topic, state: AskState): AskAnswer = when (topic) {
        Topic.STREAKS -> {
            val streaks = state.streaks
            val lines = buildList {
                if (streaks != null) add(if (streaks.done > 0) "Your main streak is ${days(streaks.done)}." else "Your main streak isn't running right now; one good day starts it again.")
                state.bestStreak?.takeIf { it > (streaks?.done ?: 0) }?.let { add("Your best is ${days(it)}.") }
                add("A day counts when it meets your streak target, set in Settings. A skipped goal is left out of the count, and a day with nothing due neither counts nor breaks it.")
                val open = state.open.size
                if (open > 0) add("Today has $open left, so today still counts if you get to ${if (open == 1) "it" else "them"}.")
                state.missedMost?.let { add("${it.name} is the goal missed most lately. Making it smaller, or giving it a time and a reminder, protects the streak most.") }
                streaks?.let { NearWins.streak(it.done) }?.let { add(it) }
                if (Feature.SHIELDS !in state.features) add("The \"Streak shields\" extra can save the main streak on a day that would break it.")
            }
            AskAnswer(
                AskKind.BETTER, lines.joinToString(" "), facts = lines.joinToString("\n"),
                figures = listOfNotNull(streaks?.let { AskFigure("Main streak", days(it.done)) }, state.missedMost?.let { AskFigure("Missed most", it.name) }),
                guides = guides(state, "STREAKS", "SHIELDS", "SKIPS_AND_MISSES"),
                actions = listOfNotNull(state.missedMost?.let { AskAction.Open("Open ${it.name}", "goal/${it.id}") }, AskAction.Open("Open Settings", "settings")),
            )
        }
        Topic.PACE -> {
            val open = state.open
            val lines = buildList {
                val (done, due, _) = todayCounts(state)
                if (due > 0) add("Today: $done of $due done, ${open.size} left.")
                open.firstOrNull { it.goal.id == state.nextId }?.let { add("${it.goal.name} is next.") }
                add("Today shows how fast to go once \"Finish each day's goals by\" is set in Settings, and \"Recommended order\" puts things in a good order to do them.")
                val long = open.filter { (it.goal.minutes ?: 0) >= 30 }.map { it.goal.name }
                if (long.isNotEmpty()) add("The long ones left are ${listText(long.take(3))}; starting one of those early keeps the day on time.")
                add("A goal with a clock time keeps its time, and a reminder brings it up when it's due.")
            }
            AskAnswer(
                AskKind.BETTER, lines.joinToString(" "), facts = lines.joinToString("\n"),
                figures = listOf(AskFigure("Left today", open.size.toString())),
                guides = guides(state, "TODAY", "REMINDERS", "TIMELINE"),
                actions = listOf(AskAction.Open("Open Today", "today"), AskAction.Open("Open Settings", "settings")),
            )
        }
        Topic.POINTS -> {
            val lines = buildList {
                add("Your balance is ${PointsName.current.amount(state.balance)}." + (state.perDay?.takeIf { it >= 1 }?.let { " You usually earn about ${it.toInt()} a day." } ?: ""))
                state.target?.takeIf { it.target > 0 && !it.reached }?.let { add("This week's target has ${it.left} to go.") }
                val more = state.checks.filter { it.hint == GoalHint.PAY_MORE && it.suggested != null }.sortedBy { it.share }.take(2)
                more.forEach { add("${it.goal.name} got done ${it.done} of ${it.due} times lately; it could pay ${it.suggested} instead of ${it.goal.points}, to be worth the effort.") }
                val less = state.checks.filter { it.hint == GoalHint.PAY_LESS && it.suggested != null }.take(1)
                less.forEach { add("${it.goal.name} is done nearly every time (${it.done} of ${it.due}); it could pay ${it.suggested} instead of ${it.goal.points}, so the harder goals stand out.") }
                state.missedMost?.let { if (Feature.BOOST !in state.features) add("The \"Boost the goal missed most\" extra makes ${it.name} pay more for the week.") }
                if (!state.locks.shop) {
                    state.rewards.firstOrNull { it.id == state.starredId }?.let { r ->
                        val saving = SavingFor(r, state.balance, state.perDay)
                        add(if (saving.canBuy) "You can buy ${r.name} now." else "${r.name} is ${saving.left} away" + (saving.more?.let { ", $it" } ?: "") + ".")
                    }
                }
                if (more.isEmpty() && less.isEmpty()) add("Every tick earns its goal's points, and finishing a counted goal or a project pays a bonus.")
            }
            AskAnswer(
                AskKind.BETTER, lines.joinToString(" "), facts = lines.joinToString("\n"),
                figures = listOf(AskFigure("Balance", PointsName.current.amount(state.balance))) + listOfNotNull(state.perDay?.takeIf { it >= 1 }?.let { AskFigure("Usual a day", it.toInt().toString()) }),
                guides = guides(state, "BALANCE_CHECK", "SHOP", "RULES"),
                actions = listOf(
                    if (Feature.BALANCE_CHECK in state.features) AskAction.Open("Open the balance check", "balance-check") else turnOn(Feature.BALANCE_CHECK),
                    AskAction.Open("Open the Shop", "shop"),
                ),
            )
        }
        Topic.MONEY -> money(state).let { base ->
            if (Feature.BUDGET !in state.features || state.locks.budget) base.copy(kind = AskKind.BETTER) else {
                val lines = listOfNotNull(
                    base.facts.takeIf { it.isNotEmpty() },
                    "A monthly limit on a category shows as a bar, so it's clear before it runs out.",
                    "Money put into a saving goal comes off what's left, so it isn't spent by accident.",
                    if (Feature.BUDGET_GAME !in state.features) "The \"Budget game\" extra pays points for each day that stays under its line." else "The budget game pays for each day that stays under its line.",
                )
                base.copy(
                    kind = AskKind.BETTER, text = lines.joinToString(" "), facts = lines.joinToString("\n"),
                    guides = guides(state, "BUDGET", "CATEGORIES", "BUDGET_GAME"),
                    actions = listOf(AskAction.Open("Open Budget", "budget"), AskAction.Open("Categories and limits", "budget-categories")),
                )
            }
        }
        Topic.MOOD -> mood(state).let { base ->
            if (Feature.MOOD !in state.features || state.locks.moods) base.copy(kind = AskKind.BETTER) else {
                val lines = listOfNotNull(
                    base.facts.takeIf { it.isNotEmpty() },
                    if (state.moodLifts.isEmpty()) "\"Mood and your goals\" shows which goals go with a better mood, once there are a few weeks of check-ins." else null,
                    "On a low morning, starting with one small goal is enough; each thing done counts.",
                )
                base.copy(kind = AskKind.BETTER, text = lines.joinToString(" "), facts = lines.joinToString("\n"))
            }
        }
        Topic.FOCUS -> {
            val timed = state.open.filter { (it.goal.minutes ?: 0) > 0 }
            val lines = buildList {
                add("The focus timer counts down a goal's length and ticks it when the time is up: tap \"▶ Focus for\" on the goal's card on Today.")
                if (timed.isNotEmpty()) add("Left today with a length: ${listText(timed.take(3).map { "${it.goal.name} (${it.goal.minutes} min)" })}.")
                else add("It shows on goals that have a length, set in the goal's editor.")
                add("A break of 5, 10 or 15 minutes after each session can be set in Settings.")
            }
            AskAnswer(
                AskKind.BETTER, lines.joinToString(" "), facts = lines.joinToString("\n"), guides = guides(state, "FOCUS"),
                actions = listOfNotNull(AskAction.Open("Open Today", "today"), timed.firstOrNull()?.let { AskAction.Open("Edit ${it.goal.name}", "habit/${it.goal.id}") }),
            )
        }
        Topic.GENERAL -> {
            val doing = HowDoing.of(state)
            val lines = doing.lines + NearWins.of(state).take(2)
            AskAnswer(
                AskKind.BETTER, lines.joinToString(" "), facts = lines.joinToString("\n"), figures = doing.figures,
                actions = listOfNotNull(doing.next?.action, AskAction.Open("Open the Review", "review")),
            )
        }
    }

    // Guides: "how do I …", "what is …"

    private fun guides(state: AskState, vararg keys: String): List<AskGuide> = keys.mapNotNull { key -> state.guides.firstOrNull { it.key == key } }

    private fun turnOn(feature: Feature): AskAction = AskAction.Open("Turn on \"${feature.title}\"", "features/${feature.name}")

    /**
     * Words people ask with, and the words the guides use for the same thing: "add" a goal is
     * "New goal", "delete" is "Remove". Each is tried when the word itself isn't in a guide.
     */
    private val alsoSaid: Map<String, List<String>> = mapOf(
        "add" to listOf("new"), "create" to listOf("new"), "make" to listOf("new"), "delete" to listOf("remove"),
        "mark" to listOf("tick"), "complete" to listOf("tick", "done"), "check" to listOf("tick"), "finish" to listOf("done"),
        "notification" to listOf("reminder"), "alarm" to listOf("reminder"), "alert" to listOf("reminder"), "notify" to listOf("reminder"),
        "color" to listOf("colour", "appearance"), "colour" to listOf("appearance"), "theme" to listOf("appearance", "colour"),
        "look" to listOf("appearance"), "night" to listOf("dark"),
        "password" to listOf("lock"), "pin" to listOf("lock"), "passcode" to listOf("lock"), "hide" to listOf("private", "lock"), "secret" to listOf("private"),
        "restore" to listOf("import"), "sync" to listOf("backup"), "cloud" to listOf("backup"), "transfer" to listOf("export", "import"),
        "csv" to listOf("spreadsheet"), "excel" to listOf("spreadsheet"), "edit" to listOf("change"), "task" to listOf("todo", "goal"),
        "freeze" to listOf("shield"), "late" to listOf("miss"), "overdue" to listOf("miss"), "cash" to listOf("budget"), "salary" to listOf("pay"),
        "wage" to listOf("pay"), "undo" to listOf("back"), "rename" to listOf("call", "name"), "punish" to listOf("penalty"),
        "holiday" to listOf("skip"), "sick" to listOf("skip", "ill"), "rest" to listOf("skip"), "price" to listOf("cost"), "cost" to listOf("price"),
        "start" to listOf("begin"), "prize" to listOf("reward"), "treat" to listOf("reward"), "habit" to listOf("goal"), "goal" to listOf("habit"),
        "weekday" to listOf("often", "chosen"), "weekend" to listOf("often", "chosen"), "repeat" to listOf("often"), "daily" to listOf("often"),
    ).entries.associate { (word, others) -> SettingsSearch.stem(word) to others.map { SettingsSearch.stem(it) } }

    /** One word of a question, with the other words the guides may use for it. */
    class Asked(val word: String, val also: List<String>)

    /** The words of [question] that carry meaning, ready to look for in the guides. */
    fun asked(question: String): List<Asked> = keywords(question).map { Asked(it, alsoSaid[it].orEmpty()) }

    /** A guide with its words already picked out. */
    class Indexed(val guide: AskGuide) {
        val title: List<String> = words(guide.title)
        val intro: List<String> = words(guide.intro)

        // The guide to Ask itself quotes questions to try, which would match nearly anything asked.
        val steps: List<List<String>> = if (guide.key == "ASK") emptyList() else guide.steps.map { words(it) }
        val all: List<List<String>> = listOf(title, intro) + steps
    }

    /** [guides] indexed, with how much each word tells them apart: a word in few guides counts for more. */
    class Index(val guides: List<Indexed>) {
        private val weights = mutableMapOf<String, Double>()

        fun weight(word: String): Double = weights.getOrPut(word) {
            val inGuides = guides.count { guide -> guide.all.any { hit(word, it) } }
            if (inGuides == 0) 0.0 else kotlin.math.ln(1.0 + guides.size.toDouble() / inGuides) / kotlin.math.ln(2.0)
        }
    }

    fun index(guides: List<AskGuide>): Index = Index(guides.map { Indexed(it) })

    private fun hit(word: String, there: List<String>): Boolean = there.any { it == word || (word.length >= 4 && it.startsWith(word)) }

    /** Whether [first] and then [second] come close together in [there], like "day ends" in "when your day ends". */
    private fun together(first: List<String>, second: List<String>, there: List<String>): Boolean {
        for (i in there.indices) {
            if (first.none { hit(it, listOf(there[i])) }) continue
            for (j in i + 1..minOf(i + 3, there.lastIndex)) if (second.any { hit(it, listOf(there[j])) }) return true
        }
        return false
    }

    /**
     * How well [guide] answers what was [asked]: its title counts most, then its intro, then
     * its steps; a word few guides hold counts for more than one in all of them; and two words
     * asked together that the guide also says together count again.
     */
    fun score(guide: Indexed, asked: List<Asked>, index: Index): Double {
        if (asked.isEmpty()) return 0.0
        fun place(word: String): Int = (if (hit(word, guide.title)) 6 else 0) + (if (hit(word, guide.intro)) 3 else 0) + guide.steps.count { hit(word, it) }.coerceAtMost(3)
        var total = 0.0
        var found = 0
        for (a in asked) {
            val best = maxOf(place(a.word) * index.weight(a.word), a.also.maxOfOrNull { place(it) * index.weight(it) * 0.8 } ?: 0.0)
            if (best > 0) found++
            total += best
        }
        for ((a, b) in asked.zipWithNext()) {
            if (guide.all.any { together(listOf(a.word) + a.also, listOf(b.word) + b.also, it) }) {
                total += 4.0 * maxOf(1.0, (maxWeight(a, index) + maxWeight(b, index)) / 2)
            }
        }
        // A guide whose whole title was asked for is the one meant: "how do streaks work" is Streaks, not Streak shields.
        val named = keywords(guide.guide.title)
        if (named.isNotEmpty() && named.all { word -> asked.any { a -> a.word == word || word in a.also } }) total += 6.0
        // A guide holding every word asked beats one holding a single word many times.
        return if (found == asked.size) total + 2.0 * asked.size else total
    }

    private fun maxWeight(a: Asked, index: Index): Double = maxOf(index.weight(a.word), a.also.maxOfOrNull { index.weight(it) } ?: 0.0)

    /** The best 1 to 3 guides for [question], best first; none when nothing really matches. */
    fun findGuides(question: String, guides: List<AskGuide>): List<AskGuide> = findGuides(asked(question), index(guides))

    private fun findGuides(asked: List<Asked>, index: Index): List<AskGuide> {
        if (asked.isEmpty()) return emptyList()
        val scored = index.guides.map { it.guide to score(it, asked, index) }.filter { it.second >= 2.5 }.sortedByDescending { it.second }
        val best = scored.firstOrNull()?.second ?: return emptyList()
        return scored.filter { it.second * 2 >= best }.take(3).map { it.first }
    }

    private val making = Regex(" (add|adding|new|create|creating|make|making|start|starting|set up)( a| an| my| the| another)?( new| first)? (habit|goal|routine|project|reward)s? ")

    /** What a question is about making, if it is: "habit", "project" or "reward". */
    private fun makes(q: String): String? = making.find(q)?.groupValues?.get(4)?.let { if (it == "goal" || it == "routine") "habit" else it }

    /**
     * The guides for things asked about so often that they are matched by hand, in the user's
     * likely words, rather than left to the word count: adding a goal, ticking, a forgotten
     * tick, when the day starts and ends, what a goal pays.
     */
    private fun knownGuides(q: String): List<String> = when {
        aboutDayTimes(q) -> listOf("SETTINGS")
        has(q, "morning plan", "weekly summary", "evening check") -> listOf("REMINDERS")
        has(q, "forgot", "forget", "forgotten") && has(q, "tick", "ticked", "mark", "check", "it", "something", "goal", "yesterday", "one") -> listOf("SKIPS_AND_MISSES")
        has(q, "undo", "untick", "un tick", "take back", "took back", "by mistake", "by accident") && has(q, "tick", "ticked", "done", "goal") && !has(q, "skip", "skipped") -> listOf("TODAY")
        has(q, "mark", "tick", "complete", "check off", "tick off") && has(q, "goal", "goals", "habit", "done", "off", "something", "it") -> listOf("TODAY")
        makes(q) == "reward" -> listOf("SHOP", "REWARD_EDITOR")
        has(q, "buy", "buying", "return", "returning", "refund", "spend", "use") && has(q, "reward", "rewards", "points") && has(q, "how do i", "how to", "how can i", "can i", "where do i") -> listOf("SHOP")
        makes(q) == "project" -> listOf("PROJECT_EDITOR", "GOALS")
        makes(q) == "habit" -> listOf("GOALS", "HABIT_EDITOR")
        has(q, "points", "pay", "pays") && has(q, "change", "set", "edit", "raise", "lower") && has(q, "goal", "habit", "goals") -> listOf("HABIT_EDITOR", "DIFFICULTY")
        else -> emptyList()
    }

    private fun guideAnswer(question: String, state: AskState): AskAnswer? {
        val asked = asked(question)
        val index = index(state.guides)
        val known = knownGuides(norm(question)).mapNotNull { key -> state.guides.firstOrNull { it.key == key } }
        val found = (known + findGuides(asked, index)).distinctBy { it.key }.take(3)
        if (found.isEmpty()) return null
        return fromGuides(found, asked, index, state, question)
    }

    /** The lines of [guide] that say most about what was [asked], in the guide's own order. */
    fun bestSteps(guide: AskGuide, asked: List<Asked>, index: Index, count: Int): List<String> {
        val lines = guide.steps.map { words(it) }
        // In one guide, a word that most of its lines hold says little about which line is meant.
        fun here(word: String): Double {
            val inLines = lines.count { hit(word, it) }
            return if (inLines == 0) 0.0 else maxOf(0.5, index.weight(word)) * kotlin.math.ln(1.0 + lines.size.toDouble() / inLines) / kotlin.math.ln(2.0)
        }
        val scored = guide.steps.mapIndexed { at, step ->
            val there = lines[at]
            var points = 0.0
            for (a in asked) {
                points += if (hit(a.word, there)) here(a.word) else 0.8 * (a.also.filter { hit(it, there) }.maxOfOrNull { here(it) } ?: 0.0)
            }
            for ((a, b) in asked.zipWithNext()) if (together(listOf(a.word) + a.also, listOf(b.word) + b.also, there)) points += 3.0
            Triple(at, step, points)
        }
        // Only lines that say nearly as much as the best one: a line sharing one small word isn't an answer.
        val top = scored.maxOfOrNull { it.third } ?: 0.0
        val picked = scored.filter { it.third > 0 && it.third >= top * 0.3 }.sortedByDescending { it.third }.take(count)
        return (if (picked.isEmpty()) scored.take(count.coerceAtMost(2)) else picked).sortedBy { it.first }.map { it.second }
    }

    /** Something new to make, when the question is about adding one: the button opens its empty editor. */
    private fun newThing(question: String, state: AskState): AskAction? = when (makes(norm(question))) {
        "reward" -> AskAction.Open("New reward", "reward/0").takeIf { !state.locks.shop }
        "project" -> AskAction.Open("New project", "project/0")
        "habit" -> AskAction.Open("New habit", "habit/0")
        else -> null
    }

    private fun fromGuides(found: List<AskGuide>, asked: List<Asked>, index: Index, state: AskState, question: String = ""): AskAnswer {
        val first = found.first()
        val off = first.extras.filter { it !in state.features }
        val steps = bestSteps(first, asked, index, 2)
        // The intro is left out when the answer is only in the guide's lines: a question about when
        // the day ends shouldn't open with what a skip is.
        val onTopic = first.steps.isEmpty() || Indexed(first).let { g -> asked.any { a -> (listOf(a.word) + a.also).any { hit(it, g.title) || hit(it, g.intro) } } }
        val text = buildString {
            if (onTopic || steps.isEmpty()) append(first.intro)
            steps.forEach { if (isNotEmpty()) append(" "); append(it) }
            if (off.isNotEmpty()) append(" ${listText(off.map { "\"${it.title}\"" })} ${if (off.size == 1) "is" else "are"} off now: turn ${if (off.size == 1) "it" else "them"} on in Settings → Features.")
        }
        val facts = buildString {
            found.forEachIndexed { at, guide ->
                appendLine("${guide.title}: ${guide.intro}")
                bestSteps(guide, asked, index, if (at == 0) 5 else 2).forEach { appendLine("- $it") }
            }
            if (off.isNotEmpty()) appendLine("${listText(off.map { "\"${it.title}\"" })} is an extra that is off now. It is turned on in Settings → Features.")
        }.trim()
        val actions = buildList {
            off.forEach { add(turnOn(it)) }
            if (off.isEmpty()) {
                newThing(question, state)?.let { add(it) }
                guideRoute(first.key)?.let { route -> add(AskAction.Open(placeLabel(route), route)) }
            }
        }
        return AskAnswer(AskKind.HOW_TO, text, facts = facts, guides = found, actions = actions)
    }

    /** The screen a guide is about, where it is one that opens with nothing more to pick. */
    fun guideRoute(key: String): String? = if (key.startsWith(FEATURE)) "features/" + key.removePrefix(FEATURE) else when (key) {
        "TODAY" -> "today"
        "GOALS" -> "goals"
        "SHOP", "REWARD_EDITOR" -> "shop"
        "REVIEW" -> "review"
        "BUDGET", "MONEY_ENTRY", "BUDGET_GAME" -> "budget"
        "SETTINGS", "REMINDERS", "PRIVACY", "BACKUP", "DIFFICULTY", "STREAKS" -> "settings"
        "FEATURES", "AI" -> "features"
        "RULES" -> "rules"
        "APPEARANCE", "WIDGETS" -> "appearance"
        "BALANCE_CHECK" -> "balance-check"
        "HISTORY" -> "history"
        "CHARACTER", "PATHS" -> "character"
        "BADGES" -> "badges"
        "MOOD_GOALS" -> "mood-goals"
        "MOOD_JOURNAL" -> "mood-journal"
        "PLAN" -> "planner"
        "WEEK_PLAN" -> "plan-week"
        "DONE_LIST" -> "done"
        "REMOVED_GOALS" -> "removed"
        "PAY_AND_HOURS" -> "budget-pay"
        "CATEGORIES" -> "budget-categories"
        "BILLS", "BILL_EDITOR" -> "budget-bills"
        "BUDGET_REPORT" -> "budget-report"
        "HABIT_EDITOR", "PROJECT_EDITOR", "TODO_EDITOR" -> "goals"
        else -> null
    }

    /** What the button to a screen says: the screen it really opens, not the guide's name. */
    fun placeLabel(route: String): String = if (route.startsWith("features/")) "Open Features" else when (route) {
        "today" -> "Open Today"
        "goals" -> "Open Goals"
        "shop" -> "Open the Shop"
        "review" -> "Open the Review"
        "budget" -> "Open Budget"
        "settings" -> "Open Settings"
        "features" -> "Open Features"
        "rules" -> "Open Points and rules"
        "appearance" -> "Open Appearance"
        "balance-check" -> "Open the balance check"
        "history" -> "Open the points history"
        "character" -> "Open your character"
        "badges" -> "Open Badges"
        "mood-goals" -> "Mood and your goals"
        "mood-journal" -> "Mood journal"
        "planner" -> "Open the planner"
        "plan-week" -> "Plan your week"
        "done" -> "Open the Done list"
        "removed" -> "Open removed goals"
        "budget-pay" -> "Open pay and hours"
        "budget-categories" -> "Categories and limits"
        "budget-bills" -> "Open bills"
        "budget-report" -> "Open the month's report"
        else -> "Open it"
    }

    /** "Why did I lose points?": what came off this week, and what takes points off at all. */
    fun lost(state: AskState): AskAnswer {
        val cost = state.guides.firstOrNull { it.key == "SKIPS_AND_MISSES" }?.steps?.firstOrNull { "cost" in it.lowercase() && ("%" in it || "half" in it) }
        val why = "Points come off when a skip or miss goes past a goal's free allowance, and when a tick is taken back." +
            (cost?.let { " $it" } ?: "") + " Buying a reward spends points too. The points history lists every change, with what it was for."
        val from = guides(state, "SKIPS_AND_MISSES", "HISTORY")
        val history = AskAction.Open("Open the points history", "history")
        if (state.locks.review) {
            return AskAnswer(
                AskKind.WEEK, "$why This week's numbers are behind the privacy lock.", facts = why, guides = from,
                actions = listOf(history, AskAction.Unlock), locked = true,
            )
        }
        val week = state.week
        val missed = week?.goals.orEmpty().filter { it.missed > 0 }.sortedByDescending { it.missed }
        val lines = buildList {
            if (week != null) {
                add(if (week.lost > 0) "This week ${week.lost} came off as penalties." else "Nothing has come off as a penalty this week.")
                if (week.spent > 0 && !state.locks.shop) add("${week.spent} went on rewards.")
                if (missed.isNotEmpty()) add("Missed this week: ${listText(missed.take(4).map { "${it.goal.name} (${it.missed})" })}.")
            }
            add(why)
        }
        val figures = buildList {
            add(AskFigure("Balance", PointsName.current.amount(state.balance)))
            if (week != null) {
                add(AskFigure("Lost this week", week.lost.toString()))
                if (week.spent > 0 && !state.locks.shop) add(AskFigure("Spent this week", week.spent.toString()))
            }
        }
        return withPrivate(state, AskAnswer(AskKind.WEEK, lines.joinToString(" "), facts = lines.joinToString("\n"), figures = figures, guides = from, actions = listOf(history, AskAction.Open("Open the Review", "review"))))
    }

    /** "What is my weekly target?": how far along it is, when one is set. */
    private fun target(state: AskState): AskAnswer? {
        val target = state.target?.takeIf { it.target > 0 } ?: return null
        val text = (if (target.reached) "This week's target of ${target.target} is reached, with ${target.earned} earned." else "This week's target: ${target.earned} of ${target.target}, ${target.left} to go.") +
            " It counts what you earn from Monday to Sunday, and is set under \"Weekly target\" in Settings."
        return AskAnswer(
            AskKind.WEEK, text, facts = text, figures = listOf(AskFigure("Weekly target", "${target.earned} of ${target.target}")),
            guides = guides(state, "SETTINGS"), actions = listOf(AskAction.Open("Open Settings", "settings")),
        )
    }

    /** A greeting, or "what can you do?": what Ask is for. */
    private fun hello(state: AskState): AskAnswer = AskAnswer(
        if (guides(state, "ASK").isEmpty()) AskKind.UNKNOWN else AskKind.HOW_TO,
        "Hello! Ask me what's next or left today, how you're doing, how a goal or a reward is going, how to get better at something, " +
            "or how any part of the app works, like \"What does a skip cost?\".",
        guides = guides(state, "ASK"), actions = listOf(AskAction.Open("Open Help", "help")),
    )

    private fun unknown(state: AskState): AskAnswer = AskAnswer(
        AskKind.UNKNOWN,
        "I don't know that one. I can tell you what's next or left today, how you're doing, how a goal or a reward is going, " +
            "and how any part of the app works. Every guide is in Help.",
        actions = listOf(AskAction.Open("Open Help", "help")),
    )

    // Suggested questions

    /** Questions to offer as chips, picked from where the user stands. At most 6. */
    fun suggestions(state: AskState): List<String> = buildList {
        add(if (state.open.isNotEmpty()) "What's left today?" else "How am I doing?")
        if (state.open.size > 1) add("What's next?")
        if (!state.locks.review) {
            val checked = state.checks.filter { !it.goal.private }
            (checked.minByOrNull { it.share } ?: checked.firstOrNull())?.let { add("How is ${it.goal.name} going?") }
                ?: state.goals.firstOrNull { it.kind == GoalKind.HABIT && !it.private }?.let { add("How is ${it.name} going?") }
        }
        if (Feature.CHARACTER in state.features) state.character?.let { add("How do I get better at ${Ideas.weakest(it).label}?") }
        if (!state.locks.shop) state.rewards.firstOrNull { it.id == state.starredId }?.let { add("How close is ${it.name}?") }
        add("What does a skip cost?")
        if (Feature.BUDGET in state.features && !state.locks.budget) add("How is my budget this month?")
        if (Feature.MOOD in state.features && !state.locks.moods) add("What goes with a better mood?")
        add("How do I keep my streak?")
        add("How do I back up?")
    }.distinct().take(6)

    // Asking the AI

    /**
     * What the AI is sent for [question]: the [answer]'s facts, trimmed to what the small model
     * can take, the turn or two before when they fit, and the question. Null when there are no
     * facts for it to word, so the app's own answer stands.
     */
    fun prompt(question: String, answer: AskAnswer, earlier: List<AskTurn> = emptyList()): String? {
        if (answer.facts.isBlank()) return null
        val facts = trim(answer.facts, MAX_FACTS)
        val before = StringBuilder()
        for (turn in earlier.takeLast(2).reversed()) {
            val text = "They asked: ${turn.question.take(120)}\nYou said: ${turn.said.take(200)}\n"
            if (before.length + text.length > MAX_EARLIER) break
            before.insert(0, text)
        }
        return buildString {
            appendLine("Notes:")
            appendLine(facts)
            if (before.isNotEmpty()) {
                appendLine()
                appendLine("Earlier in this chat:")
                append(before)
            }
            appendLine()
            appendLine("Question: ${question.trim().replace(Regex("\\s+"), " ").take(MAX_QUESTION)}")
            append("Answer:")
        }
    }

    /** For "Say it in words": the facts of an answer, to be rewritten as a paragraph. */
    fun wordsPrompt(answer: AskAnswer): String? = answer.facts.takeIf { it.isNotBlank() }?.let { "Notes:\n${trim(it, MAX_FACTS)}\n\nTo the user:" }

    /** [text] cut to [length] at the end of a line where it can be. */
    private fun trim(text: String, length: Int): String {
        if (text.length <= length) return text
        val cut = text.take(length)
        val at = cut.lastIndexOf('\n')
        return (if (at > length / 2) cut.take(at) else cut).trim()
    }

    /**
     * The AI's [said] as plain sentences, at most 5. Null when there is nothing usable, or when
     * it holds a number that isn't in the [facts] it was given (or the [question]): then the
     * app's own answer is shown instead, so a slip can't mislead.
     */
    fun clean(said: String, facts: String, question: String = ""): String? {
        val text = said.replace(Regex("(?s)<think>.*?</think>"), " ")
            .replace(Regex("[*#_`>]"), "")
            .replace(Regex("(?m)^\\s*([-•]|\\d+[.)])\\s+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (text.length < 12) return null
        val known = Regex("\\d+").findAll("$facts $question").map { it.value }.toSet()
        if (Regex("\\d+").findAll(text).any { it.value !in known }) return null
        // Notes about mood only say what goes together; an answer that turns that into a cause isn't shown.
        if ("mood" in facts.lowercase() && causes.containsMatchIn(text.lowercase().replace("what causes what", ""))) return null
        val sentences = Regex("[^.!?]+[.!?]+").findAll(text).map { it.value.trim() }.toList()
        return (if (sentences.isEmpty()) listOf(text) else sentences.take(5)).joinToString(" ").take(700).trim()
    }

    private val causes = Regex("\\b(because|causes?|caused|causing|makes? you|made you|making you|leads? to|led to|improves?|improved|boosts?|boosted|lifts?|lifted|thanks to|helps?|helped|results? in|due to)\\b")

    // Small words

    internal fun days(n: Int): String = if (n == 1) "1 day" else "$n days"

    internal fun listText(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        2 -> "${items[0]} and ${items[1]}"
        else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
    }

    internal fun oneDecimal(value: Double): String {
        val tenths = kotlin.math.round(value * 10).toLong()
        return "${tenths / 10}.${tenths % 10}"
    }
}
