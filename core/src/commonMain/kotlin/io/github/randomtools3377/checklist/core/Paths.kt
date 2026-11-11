package io.github.randomtools3377.checklist.core

import kotlinx.datetime.DayOfWeek
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

    /** How much more experience the path's skill gets, in percent. */
    const val BONUS_PERCENT = 10

    fun decode(text: String?): Path? = Path.entries.firstOrNull { it.name == text }
}

/** A title earned from the way the user plays. */
enum class Title(val icon: String, val label: String, val how: String) {
    EARLY_BIRD("🐦", "Early bird", "Tick 20 goals before 8 am"),
    NIGHT_OWL("🌙", "Night owl", "Tick 20 goals after 10 pm"),
    WEEKEND_WARRIOR("⚔️", "Weekend warrior", "Do everything due on 8 Saturdays or Sundays"),
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

    /** Every title the history shows. [timeOf] gives a tick's time of day. */
    fun earned(
        ticks: List<Tick>,
        entries: List<Entry>,
        records: List<DayRecord>,
        sheet: CharacterSheet,
        streak: StreakSettings,
        timeOf: (Long) -> LocalTime,
    ): Set<Title> {
        val found = mutableSetOf<Title>()
        val times = ticks.map { timeOf(it.at) }
        if (times.count { it < LocalTime(8, 0) } >= 20) found += Title.EARLY_BIRD
        if (times.count { it >= LocalTime(22, 0) } >= 20) found += Title.NIGHT_OWL
        val weekendDays = records.count { r ->
            (r.day.dayOfWeek == DayOfWeek.SATURDAY || r.day.dayOfWeek == DayOfWeek.SUNDAY) && r.due > 0 && r.done >= r.due
        }
        if (weekendDays >= 8) found += Title.WEEKEND_WARRIOR
        if (longestMainStreak(records, streak) >= 50) found += Title.UNSTOPPABLE
        if (SkillArea.entries.all { sheet.skillLevel(it).level >= 5 }) found += Title.ALL_ROUNDER
        if (entries.count { it.label.startsWith("Boss beaten:") } >= 4) found += Title.BOSS_SLAYER
        if (entries.count { it.label.startsWith("Quest:") } >= 30) found += Title.QUEST_HUNTER
        if (entries.count { it.label.startsWith("Lucky") } >= 10) found += Title.LUCKY_STAR
        return found
    }

    /** The longest main streak in [records]: days with nothing due neither count nor break it. */
    fun longestMainStreak(records: List<DayRecord>, streak: StreakSettings): Int {
        var best = 0
        var run = 0
        for (record in records.sortedBy { it.day }) {
            if (record.due == 0 && record.misses == 0 && record.skips == 0) continue
            if (StreakCounter.meetsTarget(record, streak)) {
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
