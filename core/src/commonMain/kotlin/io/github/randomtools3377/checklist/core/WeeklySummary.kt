package io.github.randomtools3377.checklist.core

/** What the weekly summary says, from the week's review. */
data class WeeklySummary(
    val due: Int,
    val done: Int,
    val missed: Int,
    val skipped: Int,
    val earned: Int,
    val lost: Int,
    /** The goal done most consistently this week, if any was due. */
    val best: GoalSummary?,
) {
    val percent: Int get() = if (due == 0) 0 else done * 100 / due

    /**
     * "18 of 21 done (85%) · +320 points · 2 missed · best: 💧 Water". With a weekly points
     * [target], whether it was reached.
     */
    fun describe(target: Int = 0): String = buildList {
        add(if (due == 0) "Nothing was due" else "$done of $due done ($percent%)")
        add("+" + PointsName.current.amount(earned))
        if (target > 0) add(if (earned >= target) "🎯 target reached" else "🎯 $earned of $target")
        if (lost > 0) add("−$lost lost")
        if (missed > 0) add(if (missed == 1) "1 missed" else "$missed missed")
        if (skipped > 0) add(if (skipped == 1) "1 skipped" else "$skipped skipped")
        best?.takeIf { it.done > 0 }?.let { add("best: ${it.goal.icon} ${it.goal.name}") }
    }.joinToString(" · ")

    companion object {
        /** The weekly summary sent on Sunday evening. */
        val AT: kotlinx.datetime.LocalTime = kotlinx.datetime.LocalTime(18, 0)

        /**
         * The summary of [review]. With [hidePrivate] (the privacy lock covers private goals),
         * the best goal is picked from the others, so a private one is never named.
         */
        fun of(review: ReviewSummary, hidePrivate: Boolean = false): WeeklySummary = WeeklySummary(
            due = review.goals.sumOf { it.due },
            done = review.goals.sumOf { it.done },
            missed = review.goals.sumOf { it.missed },
            skipped = review.goals.sumOf { it.skipped },
            earned = review.earned,
            lost = review.lost,
            best = if (hidePrivate) review.copy(goals = review.goals.filter { !it.goal.private }).mostConsistent else review.mostConsistent,
        )
    }
}
