package io.github.randomtools3377.checklist.core

/*
 * "How am I doing?": a short summary from the app's own numbers, for Today, the review and each
 * goal's page. What is done and left, how this week stands beside the last, the streak, the
 * goal going best and the one that could use a hand, then one next step with a button. It needs
 * no AI; with the AI on, the same facts can be put into a friendly paragraph.
 */

/** The one thing to do next, with the button that opens it. */
data class NextStep(val text: String, val action: AskAction?)

data class HowDoing(val lines: List<String>, val figures: List<AskFigure>, val next: NextStep?) {
    companion object {
        /** The summary of [state]. Locked parts aren't in the state, so they aren't in here. */
        fun of(state: AskState): HowDoing {
            val due = state.today?.dueToday.orEmpty()
            val done = due.count { it.done }
            val open = state.open
            val lines = mutableListOf<String>()
            val figures = mutableListOf<AskFigure>()
            lines += when {
                state.today == null -> "Today's list isn't ready yet."
                due.isEmpty() -> "Nothing is due today."
                open.isEmpty() -> "Everything due today is done."
                else -> "Today: $done of ${due.size} done, ${open.size} left."
            }
            if (due.isNotEmpty()) figures += AskFigure("Done today", "$done of ${due.size}")
            if (!state.locks.review) {
                lines += weekLines(state)
                state.week?.let { week ->
                    val weekDue = week.goals.sumOf { it.due }
                    if (weekDue > 0) figures += AskFigure("This week", "${week.goals.sumOf { it.done }} of $weekDue")
                    figures += AskFigure("Earned this week", PointsName.current.amount(week.earned))
                }
            }
            state.streaks?.let { streaks ->
                lines += if (streaks.done > 0) {
                    "Your main streak is ${Asker.days(streaks.done)}" + (state.bestStreak?.takeIf { it > streaks.done }?.let { " (best ${Asker.days(it)})" } ?: "") + "."
                } else {
                    "Your main streak isn't running right now."
                }
                figures += AskFigure("Main streak", Asker.days(streaks.done))
            }
            if (!state.locks.review) {
                val (best, hand) = bestAndHand(state)
                best?.let { lines += "Going best: ${it.goal.name}, ${it.done} of ${it.due}." }
                hand?.let { lines += "Could use a hand: ${it.goal.name}, ${it.done} of ${it.due}." }
            }
            val next = nextStep(state)
            next?.let { lines += "Next step: ${it.text}" }
            return HowDoing(lines, figures, next)
        }

        /** The goal done most of the time and the one done least, over the last 4 weeks or else this week. */
        private fun bestAndHand(state: AskState): Pair<GoalCheck?, GoalCheck?> {
            val checks = state.checks.filter { !it.goal.private }.ifEmpty {
                state.week?.goals.orEmpty().filter { it.due >= 2 && !it.goal.private }.map { GoalCheck(it.goal, it.due, it.done, null, null) }
            }
            val best = checks.filter { it.done > 0 }.maxWithOrNull(compareBy({ it.share }, { it.due }))
            val hand = checks.filter { it.done < it.due && it.goal.id != best?.goal?.id }.minWithOrNull(compareBy({ it.share }, { -it.due }))
            return best to hand
        }

        /** This week so far, last week beside it, and the weekly target. */
        fun weekLines(state: AskState): List<String> = buildList {
            val week = state.week ?: return@buildList
            val due = week.goals.sumOf { it.due }
            val done = week.goals.sumOf { it.done }
            if (due > 0) add("This week so far: $done of $due done (${done * 100 / due}%), ${PointsName.current.amount(week.earned)} earned" + (if (week.lost > 0) ", ${week.lost} lost" else "") + ".")
            else if (week.earned > 0) add("This week so far: ${PointsName.current.amount(week.earned)} earned.")
            state.lastWeek?.let { last ->
                val lastDue = last.goals.sumOf { it.due }
                val lastDone = last.goals.sumOf { it.done }
                if (lastDue > 0) add("Last week ended at $lastDone of $lastDue (${lastDone * 100 / lastDue}%), ${PointsName.current.amount(last.earned)} earned.")
            }
            state.target?.takeIf { it.target > 0 }?.let {
                add(if (it.reached) "This week's target of ${it.target} is reached." else "This week's target: ${it.earned} of ${it.target}, ${it.left} to go.")
            }
            val missed = week.goals.sumOf { it.missed }
            val skipped = week.goals.sumOf { it.skipped }
            if (missed > 0 || skipped > 0) {
                add(listOfNotNull(missed.takeIf { it > 0 }?.let { "$it missed" }, skipped.takeIf { it > 0 }?.let { "$it skipped" }).joinToString(" and ") + " this week.")
            }
        }

        /** How one goal is going: this week, the last 4 weeks, today, and what could help. */
        fun goalLines(state: AskState, goal: Goal): List<String> = buildList {
            val inWeek = state.week?.goals?.firstOrNull { it.goal.id == goal.id }
            val check = state.checks.firstOrNull { it.goal.id == goal.id }
            inWeek?.takeIf { it.due > 0 }?.let {
                add("${goal.name}: done ${it.done} of ${it.due} this week" + (if (it.currentRun >= 2) ", ${it.currentRun} in a row now" else "") + ".")
            }
            check?.let { add("Over the last 4 weeks ${goal.name} got done ${it.done} of ${it.due} times (${it.done * 100 / it.due}%).") }
            val row = state.today?.let { it.dueToday + it.thisWeek + it.thisMonth + it.thisYear }?.firstOrNull { it.goal.id == goal.id }
            row?.let {
                add(
                    when {
                        it.done -> "It's done" + (if (it.window.period == Period.DAILY) " today." else " for now.")
                        it.skipped -> "It's skipped" + (if (it.window.period == Period.DAILY) " today." else " for now.")
                        goal.target > 1 -> "It stands at ${it.count} of ${goal.target}, with ${it.left} to go."
                        else -> "It's still open" + (if (it.window.period == Period.DAILY) " today." else ".")
                    },
                )
            }
            if (isNotEmpty() || check != null) add("It pays ${PointsName.current.amount(goal.points)} each time.")
            when (check?.hint) {
                GoalHint.PAY_MORE -> add("It gets done half the time or less. A smaller version, or a set time with a reminder, makes it easier to start" + (check.suggested?.let { "; it could also pay $it instead of ${goal.points}" } ?: "") + ".")
                GoalHint.PAY_LESS -> {
                    val step = Ideas.stepUps(listOf(goal), SkillArea.of(goal)).firstOrNull()
                    val to = step?.let { s ->
                        listOfNotNull(
                            s.goal.minutes?.let { "$it min" },
                            s.goal.amount?.let { Amounts.format(it) + (s.goal.unit?.let { u -> " $u" } ?: "") },
                            s.goal.timesPerWeek?.let { "$it times a week" },
                        ).firstOrNull()
                    }
                    add("It's done nearly every time." + (if (to != null) " A next step, if you like: $to instead of ${step.from}." else ""))
                }
                null -> {}
            }
            if (goal.name in state.moodLifts) add("It goes with a better mood for you. These go together; it doesn't say what causes what.")
        }

        /** The one next step: what's next today, else the app's suggestion from the record, else a near win. */
        fun nextStep(state: AskState): NextStep? {
            val open = state.open
            val row = open.firstOrNull { it.goal.id == state.nextId } ?: open.firstOrNull()
            if (row != null) {
                return NextStep("${row.goal.name} is next on today's list.", AskAction.Open("Open Today", "today"))
            }
            if (!state.locks.review) {
                state.week?.let { WeekAdvices.pick(it, state.checks) }?.let { advice ->
                    val idea = advice.idea
                    return NextStep(advice.text, if (advice.button != null && idea != null) AskAction.Fill(advice.button, idea) else null)
                }
            }
            return NearWins.of(state).firstOrNull()?.let { NextStep(it, null) }
        }
    }
}

/** Things that are close: a streak milestone, the weekly target, a reward, a level. */
object NearWins {
    private val milestones = listOf(7, 30, 100, 365)

    /** "2 more days make it 7 in a row." when a milestone is 3 days away or less. */
    fun streak(days: Int): String? {
        val next = milestones.firstOrNull { it > days } ?: return null
        val left = next - days
        if (days == 0 || left > 3) return null
        return if (left == 1) "One more day makes it $next in a row." else "$left more days make it $next in a row."
    }

    fun of(state: AskState): List<String> = buildList {
        val open = state.open
        if (open.size == 1) add("Only ${open[0].goal.name} is left for everything due today to be done.")
        state.streaks?.let { streak(it.done) }?.let { add(it) }
        state.target?.takeIf { it.target > 0 && !it.reached && it.left * 4 <= it.target }?.let { add("This week's target is ${it.left} away.") }
        if (!state.locks.shop) {
            state.rewards.firstOrNull { it.id == state.starredId }?.let { reward ->
                val left = reward.price - state.balance
                val perDay = state.perDay
                if (left <= 0) add("You can buy ${reward.name} now.")
                else if (perDay != null && perDay > 0 && left <= perDay * 2) add("${reward.name} is only $left away.")
            }
        }
        if (Feature.CHARACTER in state.features) {
            state.character?.let { sheet ->
                SkillArea.entries.map { it to sheet.skillLevel(it) }.filter { (_, level) -> level.span > 0 && level.toNext * 5 <= level.span && level.toNext > 0 }
                    .minByOrNull { it.second.toNext }?.let { (area, level) -> add("${area.label} is ${level.toNext} experience from level ${level.level + 1}.") }
            }
        }
    }
}
