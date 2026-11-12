package io.github.randomtools3377.checklist.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/*
 * Paths and titles, for the character (part of the "Character and skills" extra). At level 10
 * the user chooses a path: a title, and a little more experience in its skill. More titles are
 * earned from the way the user plays, and the user picks which one shows.
 */

/** A path the character can follow, one for each skill area. */
enum class Path(val area: SkillArea, val title: String, val icon: String) {
    ATHLETE(SkillArea.FITNESS, "Athlete", "🏅"),
    HEALER(SkillArea.HEALTH, "Healer", "🌿"),
    SCHOLAR(SkillArea.MIND, "Scholar", "🦉"),
    HOMEMAKER(SkillArea.HOME, "Homemaker", "🏡"),
    PROFESSIONAL(SkillArea.WORK, "Professional", "📈"),
    MAKER(SkillArea.CREATIVE, "Maker", "🛠️"),
}

object Paths {
    /** The setting the path is kept in, by name. */
    const val SETTING = "character_path"

    /** The level a path can be chosen at. */
    const val AT_LEVEL = 10

    /** How much more experience the path's skill gets, in percent (see [Rules]). */
    val BONUS_PERCENT: Int get() = Rules.current.pathBonusPercent

    fun decode(text: String?): Path? = Path.entries.firstOrNull { it.name == text }
}

/** A title earned from the way the user plays. */
enum class Title(val icon: String, val label: String, val how: String) {
    EARLY_BIRD("🐦", "Early bird", "Tick 20 goals before 8 am"),
    NIGHT_OWL("🌙", "Night owl", "Tick 20 goals after 10 pm"),
    WEEKEND_WARRIOR("⚔️", "Weekend warrior", "Do everything due, with nothing skipped, on 8 Saturdays or Sundays"),
    UNSTOPPABLE("🚂", "Unstoppable", "Keep the main streak going for 50 days"),
    ALL_ROUNDER("🌈", "All-rounder", "Reach level 5 in every skill"),
    BOSS_SLAYER("🗡️", "Boss slayer", "Beat 4 bosses"),
    QUEST_HUNTER("🧭", "Quest hunter", "Finish 30 quests"),
    LUCKY_STAR("🍀", "Lucky star", "Have 10 lucky ticks"),
}

object Titles {
    /** The title shown: a title's name, [FROM_PATH] for the path's, or empty for none. */
    const val SETTING = "character_title"
    const val FROM_PATH = "PATH"

    /** The titles earned so far, kept once earned like badges: "EARLY_BIRD=2026-11-09,UNSTOPPABLE=2027-01-03". */
    const val EARNED = "character_titles_earned"

    /**
     * Every title the history shows. [timeOf] gives a tick's time of day, on the user's day that
     * ends at [dayEndHour]: a tick after midnight that still belongs to the day counts as late
     * (toward Night owl), not early.
     */
    fun earned(
        ticks: List<Tick>,
        entries: List<Entry>,
        records: List<DayRecord>,
        sheet: CharacterSheet,
        streak: StreakSettings,
        timeOf: (Long) -> LocalTime,
        dayEndHour: Int = 0,
    ): Set<Title> {
        val found = mutableSetOf<Title>()
        val times = ticks.map { Days.secondOfDay(timeOf(it.at), dayEndHour) }
        if (times.count { it < 8 * 60 * 60 } >= 20) found += Title.EARLY_BIRD
        if (times.count { it >= 22 * 60 * 60 } >= 20) found += Title.NIGHT_OWL
        val weekendDays = records.count { r ->
            (r.day.dayOfWeek == DayOfWeek.SATURDAY || r.day.dayOfWeek == DayOfWeek.SUNDAY) && r.perfect
        }
        if (weekendDays >= 8) found += Title.WEEKEND_WARRIOR
        if (longestMainStreak(records, streak) >= 50) found += Title.UNSTOPPABLE
        if (SkillArea.entries.all { sheet.skillLevel(it).level >= 5 }) found += Title.ALL_ROUNDER
        // Game payouts are bonuses with no goal; a goal's own entries carry its name, which could start the same way.
        val payouts = entries.filter { it.kind == EntryKind.BONUS && it.goalId == null }
        if (payouts.count { it.label.startsWith("Boss beaten:") } >= 4) found += Title.BOSS_SLAYER
        if (payouts.count { it.label.startsWith("Quest:") } >= 30) found += Title.QUEST_HUNTER
        if (payouts.count { it.label.startsWith("Lucky") } >= 10) found += Title.LUCKY_STAR
        return found
    }

    /** Titles once earned stay, from the day they were first earned; new ones join them. */
    fun keep(kept: Map<Title, LocalDate>, now: Set<Title>, day: LocalDate): Map<Title, LocalDate> =
        kept + now.filter { it !in kept }.associateWith { day }

    fun encodeKept(titles: Map<Title, LocalDate>): String =
        titles.entries.sortedBy { it.key.ordinal }.joinToString(",") { "${it.key.name}=${it.value}" }

    fun decodeKept(text: String?): Map<Title, LocalDate> = text.orEmpty().split(",").mapNotNull { part ->
        val (name, day) = part.split("=").takeIf { it.size == 2 } ?: return@mapNotNull null
        val title = Title.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
        val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return@mapNotNull null
        title to date
    }.toMap()

    /** The longest main streak in [records]: days with nothing due neither count nor break it. */
    fun longestMainStreak(records: List<DayRecord>, streak: StreakSettings): Int {
        var best = 0
        var run = 0
        for (record in records.sortedBy { it.day }) {
            val ok = StreakCounter.mainDay(record, streak) ?: continue
            if (ok) {
                run++
                best = maxOf(best, run)
            } else {
                run = 0
            }
        }
        return best
    }

    /** The title to show under the level: the one picked, or the path's, if any. */
    fun shown(setting: String?, path: Path?, earned: Set<Title>): String? {
        val picked = Title.entries.firstOrNull { it.name == setting }?.takeIf { it in earned }
        return when {
            picked != null -> "${picked.icon} ${picked.label}"
            setting == "" -> null
            path != null -> "${path.icon} ${path.title}"
            else -> null
        }
    }
}
