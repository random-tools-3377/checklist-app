package io.github.randomtools3377.checklist.core

/** The parts of Today the user can show, hide and put in their own order. */
enum class TodaySection(val label: String) {
    /** The mood check-ins: in the morning, what's behind it, and once the day's done. */
    MOOD("Mood check-in"),
    BALANCE("Balance"),

    /** The character's card, with its check-ins, and the game's cards after it. */
    CHARACTER("Your character"),
    QUESTS("Daily quests"),
    RACE("Race with last week"),
    BETS("Bets"),
    BOSS("Boss of the week"),

    /** Today's pay filling up, while Budget is on and pay is set. */
    MONEY("Today's pay"),
    WEEKLY_TARGET("Weekly target"),
    STREAKS("Streaks"),
    PACE("Pace and order"),
    DUE_TODAY("Due today"),
    TODOS("To-dos"),
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    THIS_YEAR("This year"),
    PROJECTS("Projects"),
}

data class SectionChoice(val section: TodaySection, val shown: Boolean)

/**
 * Today's sections in the user's order, each shown or hidden. Stored as names in order, with a
 * "-" in front of hidden ones: "BALANCE,-STREAKS,DUE_TODAY". Sections a newer version adds are
 * shown, just after the one before them in the usual order, or first if that one isn't there.
 */
object Sections {
    val DEFAULT: List<SectionChoice> = TodaySection.entries.map { SectionChoice(it, shown = true) }

    fun decode(text: String?): List<SectionChoice> {
        val stored = text.orEmpty().split(",").mapNotNull { part ->
            val hidden = part.startsWith("-")
            val section = TodaySection.entries.firstOrNull { it.name == part.removePrefix("-") } ?: return@mapNotNull null
            SectionChoice(section, shown = !hidden)
        }.distinctBy { it.section }
        val all = stored.toMutableList()
        TodaySection.entries.filter { s -> stored.none { it.section == s } }.forEach { added ->
            val before = TodaySection.entries.take(added.ordinal).lastOrNull { s -> all.any { it.section == s } }
            all.add(before?.let { s -> all.indexOfFirst { it.section == s } + 1 } ?: 0, SectionChoice(added, true))
        }
        return all
    }

    fun encode(choices: List<SectionChoice>): String =
        choices.joinToString(",") { (if (it.shown) "" else "-") + it.section.name }

    /** Moves the section at [index] up (-1) or down (+1). */
    fun move(choices: List<SectionChoice>, index: Int, by: Int): List<SectionChoice> {
        val to = index + by
        if (index !in choices.indices || to !in choices.indices) return choices
        return choices.toMutableList().also { val moved = it.removeAt(index); it.add(to, moved) }
    }

    fun toggle(choices: List<SectionChoice>, section: TodaySection): List<SectionChoice> =
        choices.map { if (it.section == section) it.copy(shown = !it.shown) else it }
}

/** The tabs along the bottom. Today is always there; the others can be hidden. */
enum class AppTabKind(val label: String) { TODAY("Today"), PLAN("Plan"), GOALS("Goals"), SHOP("Shop"), REVIEW("Review"), BUDGET("Budget") }

/**
 * Which tabs show, in the user's order, and which one the app opens on. Stored as "SHOP,REVIEW"
 * for hidden ones, a tab name, and the order as names ("GOALS,TODAY,…"); tabs a newer version
 * adds go at the end.
 */
data class TabChoice(
    val hidden: Set<AppTabKind> = emptySet(),
    val first: AppTabKind = AppTabKind.TODAY,
    val order: List<AppTabKind> = AppTabKind.entries,
) {
    val shown: List<AppTabKind> get() = order.filter { it !in hidden }

    /** Moves [tab] one place earlier (-1) or later (+1) in the order. */
    fun moved(tab: AppTabKind, by: Int): List<AppTabKind> {
        val from = order.indexOf(tab)
        val to = from + by
        if (from < 0 || to !in order.indices) return order
        return order.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    /** The first tab, or Today if the first one is hidden. */
    val opensOn: AppTabKind get() = if (first in hidden) AppTabKind.TODAY else first

    companion object {
        fun decode(hidden: String?, first: String?, order: String? = null): TabChoice = TabChoice(
            hidden = hidden.orEmpty().split(",").mapNotNull { name -> AppTabKind.entries.firstOrNull { it.name == name } }
                .filter { it != AppTabKind.TODAY }.toSet(),
            first = AppTabKind.entries.firstOrNull { it.name == first } ?: AppTabKind.TODAY,
            order = order.orEmpty().split(",").mapNotNull { name -> AppTabKind.entries.firstOrNull { it.name == name } }.distinct()
                .let { stored -> stored + AppTabKind.entries.filter { it !in stored } },
        )

        fun encodeOrder(order: List<AppTabKind>): String = order.joinToString(",") { it.name }

        fun encodeHidden(hidden: Set<AppTabKind>): String = hidden.filter { it != AppTabKind.TODAY }.sortedBy { it.ordinal }.joinToString(",") { it.name }
    }
}
