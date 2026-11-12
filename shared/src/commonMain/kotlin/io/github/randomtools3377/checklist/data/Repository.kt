package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Amounts
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Categories
import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.core.AtMost
import io.github.randomtools3377.checklist.core.CelebrationSounds
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.Moves
import io.github.randomtools3377.checklist.core.Retimes
import io.github.randomtools3377.checklist.core.Todos
import io.github.randomtools3377.checklist.core.Mystery
import io.github.randomtools3377.checklist.core.MoneyPrices
import io.github.randomtools3377.checklist.core.Budget
import io.github.randomtools3377.checklist.core.Bill
import io.github.randomtools3377.checklist.core.Bills
import io.github.randomtools3377.checklist.core.minusDays
import io.github.randomtools3377.checklist.core.SavingGoal
import io.github.randomtools3377.checklist.core.SavingProgress
import io.github.randomtools3377.checklist.core.MoneyGame
import io.github.randomtools3377.checklist.core.plusDays
import io.github.randomtools3377.checklist.core.CategoryRole
import io.github.randomtools3377.checklist.core.MoneyCategory
import io.github.randomtools3377.checklist.core.MoneyEntry
import io.github.randomtools3377.checklist.core.MoneyKind
import io.github.randomtools3377.checklist.core.Pay
import io.github.randomtools3377.checklist.core.PayPlan
import io.github.randomtools3377.checklist.core.PayPlans
import io.github.randomtools3377.checklist.core.WorkDay
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.SavingFor
import io.github.randomtools3377.checklist.core.RewardSize
import io.github.randomtools3377.checklist.core.Effort
import io.github.randomtools3377.checklist.core.FocusSession
import io.github.randomtools3377.checklist.core.FocusTime
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.MoodFactors
import io.github.randomtools3377.checklist.core.Moods
import io.github.randomtools3377.checklist.core.Challenge
import io.github.randomtools3377.checklist.core.RepeatText
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.core.Days
import io.github.randomtools3377.checklist.core.LapseKind
import io.github.randomtools3377.checklist.core.Window
import io.github.randomtools3377.checklist.core.Reward
import io.github.randomtools3377.checklist.core.Step
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalTime
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

const val SETTING_DEFAULT_FINISH = "default_finish_minute"
const val SETTING_DAY_START = "day_start_minute"
const val SETTING_MORNING_PLAN = "morning_plan"
const val SETTING_SNOOZE = "snooze_minutes"
const val SETTING_WEEKLY = "weekly_summary"
const val SETTING_OWN_REASONS = "own_reasons"
/** Points to earn each week; 0 or missing for none. */
const val SETTING_WEEKLY_TARGET = "weekly_target"
/** Badges earned, with the day each was first earned (see Badges in the shared code). */
const val SETTING_BADGES = "badges"
/** The badges the user has already been shown. */
const val SETTING_BADGES_SEEN = "badges_seen"
/** Light, dark, phone or black (see ThemeMode). */
const val SETTING_THEME = "theme"
/** The colour the app is built from, as "#RRGGBB"; missing to match the wallpaper. */
const val SETTING_COLOR = "color"
/** Colours picked lately, as "#RRGGBB" separated by commas. */
const val SETTING_RECENT_COLORS = "recent_colors"
/** The app's own text size, card size and Today's layout (see Appearance). */
const val SETTING_TEXT_SIZE = "text_size"
const val SETTING_CARD_SIZE = "card_size"
const val SETTING_TODAY_LAYOUT = "today_layout"
/** What is behind Today, how much a photo is dimmed, and when the photo was picked. */
const val SETTING_BACKDROP = "backdrop"
const val SETTING_BACKDROP_DIM = "backdrop_dim"
const val SETTING_PHOTO_STAMP = "photo_stamp"
/** Today's sections in order, hidden ones marked (see Sections in the shared code). */
const val SETTING_TODAY_SECTIONS = "today_sections"
/** The tabs hidden from the bottom bar, and the one the app opens on. */
const val SETTING_HIDDEN_TABS = "hidden_tabs"
const val SETTING_TAB_ORDER = "tab_order"
const val SETTING_FIRST_TAB = "first_tab"
/** What points are called, as "coin|coins|🪙" (see PointsName in the shared code). */
const val SETTING_POINTS_NAME = "points_name"
/** The app's font (see FontChoice). */
const val SETTING_FONT = "font"
/** Celebrations: sound and buzz ("1" or "0"), how much confetti, and a GIF for every goal. */
const val SETTING_CELEBRATE_SOUND = "celebrate_sound"
const val SETTING_CELEBRATE_BUZZ = "celebrate_buzz"
const val SETTING_CONFETTI = "confetti"
const val SETTING_DEFAULT_GIF = "default_gif"
/**
 * "0" turns off the glow round the screen's edges at a celebration; "1" also lights the edges for
 * a tick made away from the app (Android); and "1" lights up the screen for reminders (Android).
 * The user's own celebration sounds are in CelebrationSounds' settings.
 */
const val SETTING_CELEBRATE_GLOW = "celebrate_glow"
const val SETTING_CELEBRATE_GLOW_AWAY = "celebrate_glow_away"
const val SETTING_REMINDER_LIGHT = "reminder_light"
/** The app's home-screen icon (see AppIcon), and the widgets' style (see WidgetStyle). */
const val SETTING_APP_ICON = "app_icon"
/** The running Start and Stop timing, as "goalId|startedAt" in epoch milliseconds. */
const val SETTING_STOPWATCH = "stopwatch"
const val SETTING_WIDGET_STYLE = "widget_style"
/** What the user would spend on treats in a normal week, in their own money, for suggested prices. */
const val SETTING_TREAT_BUDGET = "treat_budget"
/** "1" when the phone's calendar is shown in the planner and on Today; which calendars, by id ("" for all). */
const val SETTING_CALENDAR = "phone_calendar"
const val SETTING_CALENDAR_IDS = "phone_calendar_ids"

/**
 * The key for backup files protected with a passphrase (see BackupKey), made from the passphrase
 * and kept with the app's own data on this device. Empty or missing while files are plain.
 */
const val SETTING_BACKUP_KEY = "backup_key"

/**
 * Settings that only make sense on the phone that set them, so they stay out of the backup file:
 * the running Start and Stop timing, which of the phone's calendars are shown (by its own ids),
 * and the key for protected backup files. An import keeps this device's calendars and key.
 */
val PHONE_ONLY_SETTINGS = setOf(SETTING_STOPWATCH, SETTING_CALENDAR_IDS, SETTING_BACKUP_KEY)

/** "0" turns off the daily mood check-in; and the day it was put off ("Not today"). */
const val SETTING_MOOD = "mood_checkin"
const val SETTING_MOOD_SKIPPED = "mood_skipped"
/** The day the check-in once everything is done was put off ("Not now"), or its answer closed. */
const val SETTING_MOOD_AFTER_CLOSED = "mood_after_closed"
/** The day the notes under the morning's mood were closed ("Done"). */
const val SETTING_MOOD_MORE_CLOSED = "mood_more_closed"
/** The day whose first check-in came after everything was done, so it is that day's only mood. */
const val SETTING_MOOD_ONLY = "mood_only_day"
/** The Monday of the last week planned, or put off, from the weekly planning card. */
const val SETTING_WEEK_PLANNED = "week_planned"
/** "done" once the welcome card on Today was answered, or the examples added (see GuideScreen). */
const val SETTING_GUIDE = "guide"

/** What the tour's examples add: habits and projects, to-dos, and rewards (see Repository.addExamples). */
const val EXAMPLE_GOALS = 5
const val EXAMPLE_TODOS = 1
const val EXAMPLE_REWARDS = 2

/** The goals and rewards the examples added, by id, to take away again. */
data class AddedExamples(val goals: List<Long>, val rewards: List<Long>)
const val DEFAULT_SNOOZE_MINUTES = 10
const val SETTING_TODAY_ORDER = "today_order"
const val ORDER_RECOMMENDED = "recommended"
const val ORDER_OWN = "own"
const val SETTING_STREAK_TARGET = "streak_target"
const val SETTING_STREAK_VALUE = "streak_value"
const val LOCK_HISTORY = "lock_history"
const val LOCK_SHOP = "lock_shop"
const val LOCK_PRIVATE = "lock_private"
const val LOCK_REASONS = "lock_reasons"
const val LOCK_GAPS = "lock_gaps"
const val LOCK_REVIEW = "lock_review"
const val LOCK_BUDGET = "lock_budget"

/** What the habit editor saves. */
data class HabitDraft(
    val id: Long,
    val name: String,
    val icon: String,
    val points: Int,
    val repeat: Repeat,
    val target: Int,
    val bonus: Int,
    val minutes: Int?,
    val at: LocalTime?,
    val finishBy: LocalTime?,
    val finishDay: Int?,
    val reminder: Reminder? = null,
    val allowance: Int? = null,
    val allowancePer: Period? = null,
    val private: Boolean = false,
    val celebration: String? = null,
    /** Its own celebration sound, already copied in (see CelebrationSounds); null for the usual one. */
    val sound: String? = null,
    /** A time for each one, for a habit done several times a day. */
    val times: List<LocalTime> = emptyList(),
    /** A routine's checklist. Empty for a plain habit. */
    val items: List<StepDraft> = emptyList(),
    /** As typed; tidied on saving (see Categories). */
    val category: String? = null,
    val color: Int? = null,
    /** How much of [unit] makes it done each time, like 20 pages; null for a goal that is simply ticked. */
    val amount: Double? = null,
    val unit: String? = null,
    /** How hard it is, which suggests its points. */
    val effort: Effort? = null,
    /** A goal to quit, ticked each day it was avoided. */
    val quit: Boolean = false,
    /**
     * An "at most" goal: the most a day can have, in [unit] if it has one (see AtMost in the
     * shared code); null for every other habit.
     */
    val limit: Double? = null,
    /** A challenge on it, like 30 days in a row with a bonus at the end. */
    val challenge: Challenge? = null,
    /** The goal it comes after, in a habit chain. */
    val after: Long? = null,
    /** Its skill area for the character; null to guess it. */
    val skill: io.github.randomtools3377.checklist.core.SkillArea? = null,
)

/** One step in the project editor. [id] is 0 for a new step. */
data class StepDraft(val id: Long, val name: String, val points: Int)

/** What the project editor saves. */
data class ProjectDraft(
    val id: Long,
    val name: String,
    val icon: String,
    val steps: List<StepDraft>,
    val bonus: Int,
    val deadline: LocalDate?,
    val finishBy: LocalTime?,
    val allowance: Int? = null,
    val private: Boolean = false,
    val celebration: String? = null,
    /** Its own celebration sound, already copied in (see CelebrationSounds); null for the usual one. */
    val sound: String? = null,
    /** As typed; tidied on saving (see Categories). */
    val category: String? = null,
    val color: Int? = null,
    /** How hard its steps are, which suggests their points. */
    val effort: Effort? = null,
    /** Its skill area for the character; null to guess it. */
    val skill: io.github.randomtools3377.checklist.core.SkillArea? = null,
    /**
     * Its deadline reminder (see Alerts.deadlines): null reminds when the user's day starts,
     * [Reminder.at] is a time picked for it, and [Reminder.onTime] off is no reminder.
     */
    val reminder: Reminder? = null,
)

/** What the to-do editor and the quick add on Today save. [id] is 0 for a new to-do. */
data class TodoDraft(
    val id: Long,
    val name: String,
    val icon: String = Todos.ICON,
    val points: Int = Todos.POINTS,
    /** The day it is due; null for one that waits until it is done and is never missed. */
    val date: LocalDate? = null,
    val private: Boolean = false,
    /** Its steps, if it has any: it is done when every one is ticked. Steps pay nothing. */
    val steps: List<StepDraft> = emptyList(),
    /** A reminder on its date, for a to-do with one. */
    val remindAt: LocalTime? = null,
    val effort: Effort? = null,
    /** As typed; tidied on saving (see Categories). */
    val category: String? = null,
)

/** What the reward editor saves. */
data class RewardDraft(
    val id: Long,
    val name: String,
    val icon: String,
    val price: Int,
    /** For a mystery reward, what can come out of it. Empty for a plain reward. */
    val prizes: List<String> = emptyList(),
    /** How big a treat it is, or what it costs in money, which suggest its price. */
    val size: RewardSize? = null,
    val value: Double? = null,
    /** Whether its price follows [value] (see MoneyPrices). */
    val followsMoney: Boolean = false,
    /** Whether it is the reward being saved for (see SavingFor); null leaves the star where it is. */
    val savingFor: Boolean? = null,
)

/** Today in the budget game: today's share (null with nothing to share), spent today in cents, and good days in a row before today. */
data class MoneyGameToday(val share: Double?, val spentCents: Long, val streakBefore: Int)

/** Adding, editing, ordering and removing goals and rewards; settings; export and import. */
/** Picked celebration GIFs, kept by the app in its own storage (see CelebrationFiles on Android). */
interface GifStore {
    fun read(name: String): ByteArray?
    fun write(name: String, bytes: ByteArray)

    /** Deletes every GIF not named in [names]. */
    fun keepOnly(names: Set<String>)
}

/** The photo behind Today, kept by the app in its own storage (see BackgroundPhoto on Android). */
interface PhotoStore {
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
    fun delete()
}

@OptIn(ExperimentalEncodingApi::class)
class Repository(private val db: AppDatabase, private val celebrations: GifStore, private val background: PhotoStore? = null) {
    private val dao = db.dao()

    /** Deletes picked GIFs that no goal uses any more, and picked sounds nothing uses any more. */
    private suspend fun pruneCelebrations() {
        val goals = dao.allGoals()
        celebrations.keepOnly(goals.mapNotNull { Celebration.ownFile(it.celebration) }.toSet() + soundsUsed(goals.map { it.id }.toSet()))
    }

    /** The picked sound files the settings still use, for the goals in [goalIds]. */
    private suspend fun soundsUsed(goalIds: Set<Long>): Set<String> =
        CelebrationSounds.used(dao.allSettings().associate { it.name to it.value }, goalIds)

    /** The user's own sound for one kind of celebration, already copied in; null puts the app's sound back. */
    suspend fun setCelebrationSound(kind: CelebrationSounds.Kind, file: String?) {
        dao.putSetting(SettingEntity(kind.setting, file?.takeIf { CelebrationSounds.isSafeName(it) }.orEmpty()))
        pruneCelebrations()
    }

    /** A goal's own celebration sound, if it has one. */
    suspend fun goalSound(goalId: Long): String? = CelebrationSounds.goals(dao.setting(CelebrationSounds.GOALS_SETTING))[goalId]

    private suspend fun setGoalSound(goalId: Long, file: String?) {
        val now = dao.setting(CelebrationSounds.GOALS_SETTING)
        val next = CelebrationSounds.withGoal(now, goalId, file)
        if (next != now.orEmpty()) dao.putSetting(SettingEntity(CelebrationSounds.GOALS_SETTING, next))
    }

    suspend fun goal(id: Long): Goal? = dao.goal(id)?.toCore()

    /** What the user typed for a category, tidied, joining one already in use when it matches. */
    private suspend fun category(typed: String?, goalId: Long): String? =
        Categories.tidy(typed, Categories.of(dao.allGoals().filter { it.id != goalId }.map { it.toCore() }))

    suspend fun steps(goalId: Long): List<Step> = dao.activeSteps(goalId).map { it.toCore() }

    suspend fun reward(id: Long): Reward? = dao.reward(id)?.toCore()

    suspend fun saveHabit(draft: HabitDraft, today: LocalDate = nowLocal().date): Unit = db.withTransaction {
        val existing = if (draft.id != 0L) dao.goal(draft.id) else null
        // An "at most" goal: its limit is kept in a setting, by its id (see AtMost). It is for
        // days only, and a goal with an amount can't become one (what was entered toward its
        // amount would turn into what was used). It is nothing else at the same time.
        val limit = draft.limit?.let { Amounts.tidy(it) }?.takeIf { AtMost.fits(draft.repeat) && existing?.amount == null }
        @Suppress("NAME_SHADOWING") val draft = if (limit == null) draft else draft.copy(
            target = 1, bonus = 0, minutes = null, at = null, times = emptyList(), finishBy = null, finishDay = null,
            reminder = Reminder(onTime = false), items = emptyList(), amount = null, quit = false, challenge = null, after = null,
        )
        val challenge = draft.challenge?.takeIf { draft.repeat.kind != RepeatKind.TIMES_PER }
        val base = existing ?: GoalEntity(
            kind = GoalKind.HABIT.name, name = "", icon = "", points = 0, period = null, target = 1, bonus = 0,
            deadline = null, archived = false, position = dao.nextGoalPosition(), createdAt = nowMillis(),
        )
        val row = base.copy(
            name = draft.name,
            icon = draft.icon,
            points = draft.points,
            period = draft.repeat.windowPeriod.name,
            target = draft.target,
            bonus = draft.bonus,
            // Days moved to another day stay moved when the schedule is edited.
            repeat = RepeatText.encode(
                draft.repeat.copy(
                    moves = Moves.keep(existing?.repeat?.let { RepeatText.decode(it).moves }.orEmpty(), nowLocal().date),
                    retimes = existing?.repeat?.let { RepeatText.decode(it).retimes }.orEmpty(),
                    // A late "since last done" goal stays late when edited, so it isn't missed twice.
                    lateSince = existing?.repeat?.let { RepeatText.decode(it) }
                        ?.takeIf { it.kind == RepeatKind.AFTER_DONE && draft.repeat.kind == RepeatKind.AFTER_DONE && it.start == draft.repeat.start }
                        ?.lateSince,
                ),
            ),
            minutes = draft.minutes,
            atMinute = draft.at?.toMinuteOfDay(),
            times = encodeTimes(draft.times),
            finishByMinute = draft.finishBy?.toMinuteOfDay(),
            finishDay = draft.finishDay,
            reminder = draft.reminder?.let { RepeatText.encodeReminder(it) },
            allowance = draft.allowance,
            allowancePer = draft.allowancePer?.name,
            isPrivate = draft.private,
            celebration = draft.celebration?.takeIf { Celebration.isValid(it) },
            category = category(draft.category, draft.id),
            color = draft.color,
            amount = draft.amount?.let { Amounts.tidy(it) },
            unit = draft.unit?.trim()?.take(Amounts.UNIT_LENGTH)?.takeIf { it.isNotEmpty() && (draft.amount != null || limit != null) },
            effort = draft.effort?.name,
            quit = draft.quit,
            // A challenge is only on habits not counted per week, month or year.
            challengeDays = challenge?.days,
            challengeBonus = challenge?.bonus,
            challengeFrom = challenge?.from?.toEpochDay(),
            afterGoal = draft.after?.takeIf { it != draft.id },
            skill = draft.skill?.name,
        )
        val goalId = if (existing == null) dao.insertGoal(row) else row.id.also { dao.updateGoal(row) }
        // Switched between single days and "so many times a week, month or year": what was
        // already ticked or skipped in the window open today moves over to it, so it still counts.
        if (existing?.period != null && existing.period != row.period) {
            row.repeat?.let { RepeatText.decode(it) }?.window(today)?.let { window ->
                moveIntoWindow(goalId, window)
            }
        }
        // A routine's checklist: removed items are archived, so their past ticks still make sense.
        if (draft.items.isNotEmpty() || dao.activeSteps(goalId).isNotEmpty()) saveSteps(goalId, draft.items)
        setGoalSound(goalId, draft.sound)
        setLimit(goalId, limit)
        pruneCelebrations()
    }

    /** Sets, or with null takes away, [goalId]'s "at most" limit (see AtMost), where it differs. */
    private suspend fun setLimit(goalId: Long, limit: Double?) {
        val now = dao.setting(AtMost.SETTING)
        val next = AtMost.with(now, goalId, limit)
        if (next != now.orEmpty()) dao.putSetting(SettingEntity(AtMost.SETTING, next))
        AtMostLimits.load(dao)
    }

    /** Gives [goalId]'s ticks and skips made on the days of [window] that window's period, so they count toward it. */
    private suspend fun moveIntoWindow(goalId: Long, window: Window) {
        val from = window.start.toEpochDay()
        val to = window.end.toEpochDay()
        val period = window.period.name
        dao.habitTicksSince(Days.earliestOpenDay(window.start).toEpochDay())
            // What was added to an "at most" goal has no period, and stays that way.
            .filter { it.goalId == goalId && it.period != null && it.day in from..to && (it.period != period || it.periodStart != from) }
            .forEach { dao.updateTick(it.copy(period = period, periodStart = from)) }
        dao.lapses(goalId, from, to)
            .filter { it.kind == LapseKind.SKIP.name && (it.period != period || it.periodStart != from) }
            .forEach { dao.updateLapse(it.copy(period = period, periodStart = from)) }
    }

    /** Saves a goal's steps in order: new ones added, changed ones updated, left-out ones archived. */
    private suspend fun saveSteps(goalId: Long, steps: List<StepDraft>) {
        val kept = steps.map { it.id }.filter { it != 0L }.toSet()
        dao.activeSteps(goalId).filter { it.id !in kept }.forEach { dao.updateStep(it.copy(archived = true)) }
        steps.forEachIndexed { position, step ->
            val old = if (step.id != 0L) dao.step(step.id) else null
            if (old == null || old.goalId != goalId) {
                dao.insertStep(StepEntity(goalId = goalId, name = step.name, points = step.points, position = position, archived = false))
            } else {
                dao.updateStep(old.copy(name = step.name, points = step.points, position = position))
            }
        }
    }

    suspend fun saveProject(draft: ProjectDraft): Unit = db.withTransaction {
        val existing = if (draft.id != 0L) dao.goal(draft.id) else null
        val goalId = if (existing == null) {
            dao.insertGoal(
                GoalEntity(
                    kind = GoalKind.PROJECT.name,
                    name = draft.name,
                    icon = draft.icon,
                    points = 0,
                    period = null,
                    target = 1,
                    bonus = draft.bonus,
                    deadline = draft.deadline?.toEpochDay(),
                    archived = false,
                    position = dao.nextGoalPosition(),
                    createdAt = nowMillis(),
                    finishByMinute = draft.finishBy?.toMinuteOfDay(),
                    allowance = draft.allowance,
                    isPrivate = draft.private,
                    celebration = draft.celebration?.takeIf { Celebration.isValid(it) },
                    category = category(draft.category, 0),
                    color = draft.color,
                    effort = draft.effort?.name,
                    skill = draft.skill?.name,
                    reminder = draft.reminder?.let { RepeatText.encodeReminder(it) },
                ),
            )
        } else {
            dao.updateGoal(
                existing.copy(
                    skill = draft.skill?.name,
                    name = draft.name,
                    icon = draft.icon,
                    bonus = draft.bonus,
                    deadline = draft.deadline?.toEpochDay(),
                    finishByMinute = draft.finishBy?.toMinuteOfDay(),
                    allowance = draft.allowance,
                    isPrivate = draft.private,
                    celebration = draft.celebration?.takeIf { Celebration.isValid(it) },
                    category = category(draft.category, existing.id),
                    color = draft.color,
                    effort = draft.effort?.name,
                    reminder = draft.reminder?.let { RepeatText.encodeReminder(it) },
                ),
            )
            existing.id
        }

        // Steps left out of the draft are removed from the project. Points they already paid stay.
        val kept = draft.steps.map { it.id }.filter { it != 0L }.toSet()
        dao.activeSteps(goalId).filter { it.id !in kept }.forEach { dao.updateStep(it.copy(archived = true)) }

        draft.steps.forEachIndexed { position, step ->
            val old = if (step.id != 0L) dao.step(step.id) else null
            if (old == null || old.goalId != goalId) {
                dao.insertStep(StepEntity(goalId = goalId, name = step.name, points = step.points, position = position, archived = false))
            } else {
                dao.updateStep(old.copy(name = step.name, points = step.points, position = position))
            }
        }
        setGoalSound(goalId, draft.sound)
        pruneCelebrations()
    }

    /** Saves a quick to-do. Returns its id. */
    suspend fun saveTodo(draft: TodoDraft): Long = db.withTransaction {
        val existing = if (draft.id != 0L) dao.goal(draft.id)?.takeIf { it.kind == GoalKind.TODO.name } else null
        val made = Todos.of(draft.name, draft.points, draft.date, draft.icon.ifBlank { Todos.ICON })
        val reminder = draft.remindAt?.takeIf { draft.date != null }?.let { Reminder(onTime = true, at = it) }
        val goalId = if (existing == null) {
            dao.insertGoal(
                made.copy(
                    private = draft.private, reminder = reminder, effort = draft.effort, category = category(draft.category, 0),
                    position = dao.nextGoalPosition(), createdAt = nowMillis(),
                ).toEntity(),
            )
        } else {
            val repeat = made.repeat
            dao.updateGoal(
                existing.copy(
                    name = made.name,
                    icon = made.icon,
                    points = made.points,
                    repeat = repeat?.let { RepeatText.encode(it) },
                    period = repeat?.windowPeriod?.name,
                    isPrivate = draft.private,
                    reminder = reminder?.let { RepeatText.encodeReminder(it) },
                    effort = draft.effort?.name,
                    category = category(draft.category, existing.id),
                ),
            )
            existing.id
        }
        val steps = draft.steps.filter { it.name.isNotBlank() }.map { it.copy(name = it.name.trim().take(60), points = 0) }
        if (steps.isNotEmpty() || dao.activeSteps(goalId).isNotEmpty()) saveSteps(goalId, steps)
        goalId
    }

    /**
     * Example goals and rewards from the guide, to try the app with: one of each kind of goal, and
     * two rewards ([EXAMPLE_GOALS] habits and projects, [EXAMPLE_TODOS] to-do and [EXAMPLE_REWARDS] rewards).
     * They are ordinary goals the user can change or remove. Returns what was added, for Undo.
     */
    suspend fun addExamples(today: LocalDate): AddedExamples = db.withTransaction {
        val goalsBefore = dao.allGoals().map { it.id }.toSet()
        val rewardsBefore = dao.allRewards().map { it.id }.toSet()
        val daily = Repeat(RepeatKind.EVERY_N_DAYS, today)
        fun habit(name: String, icon: String, points: Int, repeat: Repeat = daily, target: Int = 1, bonus: Int = 0) = HabitDraft(
            id = 0, name = name, icon = icon, points = points, repeat = repeat, target = target, bonus = bonus,
            minutes = null, at = null, finishBy = null, finishDay = null,
        )
        saveHabit(habit("Drink water", "💧", 1, target = 8, bonus = 2))
        saveHabit(habit("Read", "📖", 5).copy(amount = 20.0, unit = "pages"))
        saveHabit(habit("Exercise", "🏃", 10, repeat = Repeat(RepeatKind.TIMES_PER, today, per = Period.WEEKLY), target = 3))
        saveHabit(
            habit("Morning", "☀️", 5).copy(
                items = listOf(StepDraft(0, "Glass of water", 0), StepDraft(0, "Stretch", 0), StepDraft(0, "Make the bed", 0)),
            ),
        )
        saveProject(
            ProjectDraft(
                id = 0, name = "Sort out a cupboard", icon = "🧹",
                steps = listOf(StepDraft(0, "Empty it", 3), StepDraft(0, "Throw out what isn't needed", 3), StepDraft(0, "Put it back tidy", 3)),
                bonus = 5, deadline = null, finishBy = null,
            ),
        )
        saveTodo(TodoDraft(id = 0, name = "Tick this to-do"))
        saveReward(RewardDraft(id = 0, name = "A fancy coffee", icon = "☕", price = 30))
        saveReward(RewardDraft(id = 0, name = "A film night", icon = "🎬", price = 100))
        dao.putSetting(SettingEntity(SETTING_GUIDE, "done"))
        AddedExamples(
            goals = dao.allGoals().map { it.id }.filter { it !in goalsBefore },
            rewards = dao.allRewards().map { it.id }.filter { it !in rewardsBefore },
        )
    }

    /**
     * Undo after adding the examples: they go again. One already used (ticked, skipped, or a
     * reward bought) is removed the usual way instead, so its history stays.
     */
    suspend fun removeExamples(added: AddedExamples): Unit = db.withTransaction {
        for (id in added.goals) {
            if (dao.goalHistoryCount(id) == 0) {
                dao.deleteStepsOf(id)
                dao.deleteGoal(id)
                setLimit(id, null)
            } else {
                removeGoal(id)
            }
        }
        for (id in added.rewards) {
            if (dao.purchasesOf(id).isEmpty()) dao.deleteReward(id) else removeReward(id)
        }
    }

    /**
     * How the user feels today, from the check-in on Today. A null [note] keeps the one there;
     * blank clears it. The mood once everything was done, its note and the day's factors stay.
     */
    suspend fun setMood(day: LocalDate, score: Int, note: String?, afterAllDone: Boolean = false) {
        val old = dao.mood(day.toEpochDay())
        val text = if (note == null) old?.note else Moods.tidyNote(note)
        dao.putMood(
            old?.copy(score = score.coerceIn(1, 5), note = text, at = nowMillis())
                ?: MoodEntity(day.toEpochDay(), score.coerceIn(1, 5), text, nowMillis()),
        )
        // The day's first check-in, with everything already done: its only mood (see MoodInsights.asksLater).
        if (old == null && afterAllDone) setSetting(SETTING_MOOD_ONLY, day.toString())
    }

    /**
     * A day filled in from the mood journal, for [today] or earlier: its mood, and whatever else
     * was given with it. False when the day is still to come or already has a mood.
     */
    suspend fun addMoodDay(mood: Mood, today: LocalDate): Boolean = db.withTransaction {
        if (!io.github.randomtools3377.checklist.core.MoodJournal.canAdd(mood.day, today)) return@withTransaction false
        if (dao.mood(mood.day.toEpochDay()) != null) return@withTransaction false
        val after = mood.after?.coerceIn(1, 5)
        dao.putMood(
            MoodEntity(
                day = mood.day.toEpochDay(),
                score = mood.score.coerceIn(1, 5),
                note = Moods.tidyNote(mood.note),
                at = nowMillis(),
                afterScore = after,
                afterAt = after?.let { nowMillis() },
                afterNote = Moods.tidyNote(mood.afterNote),
                factors = MoodFactors.encode(mood.factors),
            ),
        )
        true
    }

    /**
     * Takes a day out of the mood journal: its moods, notes and noted things. What Today
     * remembered about that day's check-ins goes too, so today's can be asked afresh.
     */
    suspend fun deleteMoodDay(day: LocalDate): Unit = db.withTransaction {
        dao.deleteMood(day.toEpochDay())
        for (name in listOf(SETTING_MOOD_ONLY, SETTING_MOOD_AFTER_CLOSED, SETTING_MOOD_MORE_CLOSED, SETTING_MOOD_SKIPPED)) {
            if (dao.setting(name) == day.toString()) setSetting(name, "")
        }
    }

    /**
     * "Pay N points" on "Mood and your goals": the goal pays [points] from now on, and what it
     * paid before is remembered for Undo (see MoodRaises). False when the goal is gone.
     */
    suspend fun raiseGoalPoints(id: Long, points: Int): Boolean = db.withTransaction {
        val goal = dao.goal(id) ?: return@withTransaction false
        val raises = io.github.randomtools3377.checklist.core.MoodRaises
        setSetting(raises.SETTING, raises.encode(raises.raised(raises.decode(dao.setting(raises.SETTING)), goal.toCore(), points.coerceAtLeast(0))))
        dao.updateGoal(goal.copy(points = points.coerceAtLeast(0)))
        true
    }

    /**
     * Undo after "Pay N points": the goal pays what it did before. False, and nothing changes,
     * when it no longer pays what it was raised to (its points were changed somewhere else).
     */
    suspend fun undoRaise(id: Long): Boolean = db.withTransaction {
        val raises = io.github.randomtools3377.checklist.core.MoodRaises
        val all = raises.decode(dao.setting(raises.SETTING))
        val raise = all.firstOrNull { it.goalId == id } ?: return@withTransaction false
        setSetting(raises.SETTING, raises.encode(raises.without(all, id)))
        val goal = dao.goal(id) ?: return@withTransaction false
        if (goal.points != raise.new) return@withTransaction false
        dao.updateGoal(goal.copy(points = raise.old))
        true
    }

    /**
     * How the user feels once everything due on [day] is done, from the second check-in. It goes
     * beside the morning's mood, so it needs one; false when there is none.
     */
    suspend fun setMoodAfter(day: LocalDate, score: Int): Boolean {
        val old = dao.mood(day.toEpochDay()) ?: return false
        dao.putMood(old.copy(afterScore = score.coerceIn(1, 5), afterAt = nowMillis()))
        return true
    }

    /**
     * The note from the morning's check-in, or with [after] the one from the check-in once
     * everything was done; blank clears it. False when the day has no mood to go with.
     */
    suspend fun setMoodNote(day: LocalDate, note: String, after: Boolean = false): Boolean {
        val old = dao.mood(day.toEpochDay()) ?: return false
        val text = Moods.tidyNote(note)
        dao.putMood(if (after) old.copy(afterNote = text) else old.copy(note = text))
        return true
    }

    /** Picks [factor] for [day]'s mood, or takes it off if it was picked. Both check-ins share them. */
    suspend fun toggleMoodFactor(day: LocalDate, factor: String): Boolean = db.withTransaction {
        val old = dao.mood(day.toEpochDay()) ?: return@withTransaction false
        val now = MoodFactors.toggle(MoodFactors.decode(old.factors), factor)
        dao.putMood(old.copy(factors = MoodFactors.encode(now)))
        true
    }

    /**
     * A day changed in the mood journal: its moods, notes and factors. The moods keep when they
     * were given, unless one is new. False when the day has no mood.
     */
    suspend fun editMoodDay(mood: Mood): Boolean = db.withTransaction {
        val old = dao.mood(mood.day.toEpochDay()) ?: return@withTransaction false
        val after = mood.after?.coerceIn(1, 5)
        dao.putMood(
            old.copy(
                score = mood.score.coerceIn(1, 5),
                note = Moods.tidyNote(mood.note),
                afterScore = after,
                afterAt = if (after == null) null else old.afterAt ?: nowMillis(),
                afterNote = Moods.tidyNote(mood.afterNote),
                factors = MoodFactors.encode(mood.factors),
            ),
        )
        true
    }

    /** The things offered at the mood check-ins (see MoodFactors). */
    suspend fun moodFactorList(): List<String> = MoodFactors.list(dao.setting(MoodFactors.SETTING))

    /** Changes the things offered at the mood check-ins. Days that had one keep it. */
    suspend fun setMoodFactorList(names: List<String>) {
        setSetting(MoodFactors.SETTING, MoodFactors.encodeList(names))
    }

    /** Adds the user's own thing to the list, and picks it for [day]'s mood when there is one. */
    suspend fun addMoodFactor(name: String, day: LocalDate?): Unit = db.withTransaction {
        val clean = MoodFactors.tidy(name)
        if (clean.isEmpty()) return@withTransaction
        setMoodFactorList(MoodFactors.add(moodFactorList(), clean))
        val old = day?.let { dao.mood(it.toEpochDay()) } ?: return@withTransaction
        val picked = MoodFactors.decode(old.factors)
        if (!MoodFactors.has(picked, clean)) dao.putMood(old.copy(factors = MoodFactors.encode(picked + clean)))
    }

    /** Puts a habit right after another in a habit chain, unless that would make a loop. False if it can't. */
    suspend fun setGoalAfter(id: Long, after: Long): Boolean = db.withTransaction {
        val row = dao.goal(id) ?: return@withTransaction false
        val goals = dao.allGoals().map { it.toCore() }
        if (io.github.randomtools3377.checklist.core.Chains.choices(row.toCore(), goals).none { it.id == after }) return@withTransaction false
        dao.updateGoal(row.copy(afterGoal = after))
        true
    }

    /** Gives a goal a clock time for [day] only (from the timeline), or back to its usual time with null. */
    suspend fun retimeGoal(goalId: Long, day: LocalDate, minute: Int?, today: LocalDate): Unit = db.withTransaction {
        val row = dao.goal(goalId) ?: return@withTransaction
        val repeat = row.repeat?.let { RepeatText.decode(it) } ?: return@withTransaction
        dao.updateGoal(row.copy(repeat = RepeatText.encode(Retimes.set(repeat, day, minute, today))))
    }

    /** Moves one day's goal to another day of the same week; false if that move isn't allowed. */
    suspend fun moveGoal(goalId: Long, from: LocalDate, to: LocalDate, today: LocalDate): Boolean = db.withTransaction {
        val row = dao.goal(goalId) ?: return@withTransaction false
        val goal = row.toCore()
        val repeat = goal.repeat ?: return@withTransaction false
        val backHome = Moves.movedFrom(repeat, from) == to
        if (!backHome && to !in Moves.choices(goal, from, today)) return@withTransaction false
        dao.updateGoal(row.copy(repeat = RepeatText.encode(Moves.move(repeat, from, to))))
        true
    }

    /**
     * A copy of a goal, with its steps or checklist, named "… (copy)", at the end of the list.
     * The copy is a new goal from [today]: its repeat starts today at the earliest, on the same
     * days as the original (a copy of an "every 2nd day" goal falls on the original's days),
     * without the original's moved or retimed days, lateness or challenge. Returns its id.
     */
    suspend fun duplicateGoal(id: Long, today: LocalDate = nowLocal().date): Long? = db.withTransaction {
        val goal = dao.goal(id) ?: return@withTransaction null
        val repeat = goal.repeat?.let { RepeatText.decode(it) }?.let { decoded ->
            val r = decoded.copy(moves = emptyList(), retimes = emptyList(), lateSince = null)
            // The first day from today it would be due, so an interval keeps its days; today if none.
            val start = if (r.start >= today) r.start else r.nextDue(today) ?: today
            RepeatText.encode(r.copy(start = start, end = r.end?.takeIf { it >= start }))
        }
        val copyId = dao.insertGoal(
            goal.copy(
                id = 0, name = (goal.name.take(53) + " (copy)"), archived = false, position = dao.nextGoalPosition(), createdAt = nowMillis(),
                repeat = repeat, challengeDays = null, challengeBonus = null, challengeFrom = null,
            ),
        )
        dao.activeSteps(id).forEach { step -> dao.insertStep(step.copy(id = 0, goalId = copyId)) }
        // The copy celebrates like the one it was made from, its own sound included.
        goalSound(id)?.let { setGoalSound(copyId, it) }
        // A copy of an "at most" goal is one too, with the same limit.
        AtMost.decode(dao.setting(AtMost.SETTING))[id]?.let { setLimit(copyId, it) }
        copyId
    }

    /** One category for several goals, tidied like a typed one; empty takes them out of any. */
    suspend fun setCategories(ids: List<Long>, typed: String): Unit = db.withTransaction {
        val first = ids.firstOrNull() ?: return@withTransaction
        val category = category(typed.takeIf { it.isNotBlank() }, first)
        ids.forEach { id -> dao.goal(id)?.let { dao.updateGoal(it.copy(category = category)) } }
    }

    /** How hard several goals are, which suggests their points. */
    suspend fun setEfforts(ids: List<Long>, effort: Effort?): Unit = db.withTransaction {
        ids.forEach { id -> dao.goal(id)?.let { dao.updateGoal(it.copy(effort = effort?.name)) } }
    }

    /**
     * Brings a removed goal back, at the end of the list. Its past stays as it was: with
     * [freshStart], it counts in streaks and records from now, so the days it was away aren't
     * days not done. The Undo bar puts it back as it was, with [freshStart] false. A "since last
     * done" goal whose next day passed while it was away is due [today].
     */
    suspend fun restoreGoal(id: Long, today: LocalDate = nowLocal().date, freshStart: Boolean = true) = db.withTransaction {
        dao.goal(id)?.let { row ->
            val repeat = row.repeat?.let { RepeatText.decode(it) }
                ?.takeIf { it.kind == RepeatKind.AFTER_DONE && it.start < today }
                ?.let { RepeatText.encode(it.copy(start = today)) }
                ?: row.repeat
            dao.updateGoal(
                row.copy(
                    archived = false, position = dao.nextGoalPosition(), repeat = repeat,
                    createdAt = if (freshStart) nowMillis() else row.createdAt,
                ),
            )
        }
    }

    /** Removes a goal from the lists. Points it already paid stay in the balance. */
    suspend fun removeGoal(id: Long) {
        dao.goal(id)?.let { dao.updateGoal(it.copy(archived = true)) }
    }

    /** Moves a goal one place up ([by] = -1) or down (+1) among the goals of the same kind. */
    suspend fun moveGoal(id: Long, by: Int): Unit = db.withTransaction {
        val goal = dao.goal(id) ?: return@withTransaction
        val sameKind = dao.activeGoals().filter { it.kind == goal.kind }
        val from = sameKind.indexOfFirst { it.id == id }
        sameKind.getOrNull(from + by)?.takeIf { from >= 0 }?.let { placeGoal(goal, it.id) }
    }

    /**
     * Moves a goal to the place of [besideId], a goal of the same kind: the one shown next to it.
     * The list leaves some goals out (past to-dos, locked private goals), so a move by one place
     * among all of them could swap with one the user can't see and look like nothing happened.
     */
    suspend fun moveGoalTo(id: Long, besideId: Long): Unit = db.withTransaction {
        dao.goal(id)?.let { placeGoal(it, besideId) }
    }

    private suspend fun placeGoal(goal: GoalEntity, besideId: Long) {
        val all = dao.activeGoals()
        val sameKind = all.filter { it.kind == goal.kind }.toMutableList()
        val from = sameKind.indexOfFirst { it.id == goal.id }
        val to = sameKind.indexOfFirst { it.id == besideId }
        if (from < 0 || to < 0 || from == to) return
        sameKind.add(to, sameKind.removeAt(from))
        // Put the reordered kind back into the same slots, then number everything in order.
        val reordered = sameKind.iterator()
        all.map { if (it.kind == goal.kind) reordered.next() else it }
            .forEachIndexed { i, row -> if (row.position != i) dao.updateGoal(row.copy(position = i)) }
    }

    suspend fun saveReward(draft: RewardDraft): Unit = db.withTransaction {
        val existing = if (draft.id != 0L) dao.reward(draft.id) else null
        val id = if (existing == null) {
            dao.insertReward(
                RewardEntity(
                    name = draft.name,
                    icon = draft.icon,
                    price = draft.price,
                    archived = false,
                    position = dao.nextRewardPosition(),
                    createdAt = nowMillis(),
                    prizes = Mystery.encode(draft.prizes),
                    size = draft.size?.name,
                    value = draft.value?.takeIf { it > 0 },
                    followsMoney = draft.followsMoney && (draft.value ?: 0.0) > 0,
                ),
            )
        } else {
            dao.updateReward(
                existing.copy(
                    name = draft.name, icon = draft.icon, price = draft.price, prizes = Mystery.encode(draft.prizes),
                    size = draft.size?.name, value = draft.value?.takeIf { it > 0 },
                    followsMoney = draft.followsMoney && (draft.value ?: 0.0) > 0,
                ),
            )
            existing.id
        }
        draft.savingFor?.let { star(id, it) }
        Unit
    }

    /**
     * Stars a reward as the one being saved for, or with [on] false takes its star off. There is
     * one star: starring another reward moves it.
     */
    suspend fun setSavingFor(rewardId: Long, on: Boolean): Unit = db.withTransaction { star(rewardId, on) }

    private suspend fun star(rewardId: Long, on: Boolean) {
        val starred = SavingFor.starred(dao.setting(SavingFor.SETTING))
        if (on && starred != rewardId) dao.putSetting(SettingEntity(SavingFor.SETTING, rewardId.toString()))
        if (!on && starred == rewardId) dao.putSetting(SettingEntity(SavingFor.SETTING, ""))
    }

    /**
     * Keeps the prices of rewards that follow money in step with what the goals pay: works out the
     * money rate from the goals and the weekly treat budget, keeps the one in use unless it has
     * drifted (see MoneyPrices), and reprices those rewards. Ones already bought keep what they cost.
     */
    suspend fun keepMoneyPricesInStep(today: LocalDate): Unit = db.withTransaction {
        val budget = dao.setting(SETTING_TREAT_BUDGET)?.toDoubleOrNull()?.takeIf { it > 0 }
        val stored = MoneyPrices.decode(dao.setting(MoneyPrices.RATE))
        val rate = MoneyPrices.next(stored, MoneyPrices.fresh(dao.allGoals().map { it.toCore() }, today, budget))
            ?: return@withTransaction
        if (rate != stored) dao.putSetting(SettingEntity(MoneyPrices.RATE, MoneyPrices.encode(rate)))
        val rows = dao.activeRewards()
        val changes = MoneyPrices.changes(rows.map { it.toCore() }, rate)
        rows.filter { it.id in changes }.forEach { dao.updateReward(it.copy(price = changes.getValue(it.id))) }
    }

    /** A new price for a reward, from the balance check. Ones already bought keep what they cost. */
    suspend fun setRewardPrice(id: Long, price: Int) {
        dao.reward(id)?.let { dao.updateReward(it.copy(price = price.coerceAtLeast(1))) }
    }

    /** New points for a goal, from the balance check. It applies from now on. */
    suspend fun setGoalPoints(id: Long, points: Int) {
        dao.goal(id)?.let { dao.updateGoal(it.copy(points = points.coerceAtLeast(0))) }
    }

    /** Removes a reward from the shop. Bought ones stay in the inventory. */
    suspend fun removeReward(id: Long) {
        dao.reward(id)?.let { dao.updateReward(it.copy(archived = true)) }
    }

    /** Moves a reward one place up ([by] = -1) or down (+1) in the shop. */
    suspend fun moveReward(id: Long, by: Int): Unit = db.withTransaction {
        val list = dao.activeRewards().toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in list.indices) return@withTransaction
        list.add(to, list.removeAt(from))
        list.forEachIndexed { i, row -> if (row.position != i) dao.updateReward(row.copy(position = i)) }
    }

    suspend fun setDayEndHour(hour: Int) {
        dao.putSetting(SettingEntity(SETTING_DAY_END_HOUR, parseDayEndHour(hour.toString()).toString()))
    }

    /** The default time to finish the day's goals by. Null clears it, which turns today's pace off. */
    suspend fun setDefaultFinish(time: LocalTime?) {
        dao.putSetting(SettingEntity(SETTING_DEFAULT_FINISH, time?.toMinuteOfDay()?.toString() ?: ""))
    }

    suspend fun setDayStart(time: LocalTime?) {
        dao.putSetting(SettingEntity(SETTING_DAY_START, time?.toMinuteOfDay()?.toString() ?: ""))
    }

    suspend fun setTodayOrder(order: String) {
        dao.putSetting(SettingEntity(SETTING_TODAY_ORDER, order))
    }

    suspend fun setSetting(name: String, value: String) {
        dao.putSetting(SettingEntity(name, value))
    }

    suspend fun addPenaltyTask(name: String) {
        dao.insertPenaltyTask(PenaltyTaskEntity(name = name, archived = false))
    }

    suspend fun removePenaltyTask(id: Long) = dao.removePenaltyTask(id)

    /**
     * This week's boss and the items in hand. Items count from the first time this is asked.
     * The boss's health is worked out the first time the week is seen and kept for the week,
     * so changing goals or rules mid-week doesn't move it.
     */
    suspend fun bossView(day: LocalDate): io.github.randomtools3377.checklist.core.BossView {
        val B = io.github.randomtools3377.checklist.core.Bosses
        val since = dao.setting(B.ITEMS_FROM)?.toLongOrNull()
            ?: nowMillis().also { dao.putSetting(SettingEntity(B.ITEMS_FROM, it.toString())) }
        val goals = dao.allGoals().map { it.toCore() }
        val monday = B.monday(day)
        val boss = B.of(monday)
        val kept = dao.setting(B.FIGHT)
        val damage = B.damageIn(kept, monday)
        val health = B.healthIn(kept, monday)
            ?: B.health(B.expectedTicks(goals, monday)).also { dao.putSetting(SettingEntity(B.FIGHT, B.encodeFight(monday, damage, it))) }
        val fight = io.github.randomtools3377.checklist.core.BossFight(monday, boss, health, damage)
        // Beaten before its health was kept (it used to move with the goals), but never paid: it pays now.
        if (fight.beaten && fight.damage > 0) payBoss(fight, day)
        val items = B.inventory(dao.allTicks().map { it.toCore() }, goals, since, B.decodeUsed(dao.setting(B.USED)))
        return io.github.randomtools3377.checklist.core.BossView(fight, items)
    }

    /** Pays for beating this week's boss, once a week. What it paid. */
    private suspend fun payBoss(fight: io.github.randomtools3377.checklist.core.BossFight, day: LocalDate): Int = db.withTransaction {
        val B = io.github.randomtools3377.checklist.core.Bosses
        if (dao.setting(B.PAID) == fight.week.toString()) return@withTransaction 0
        val entry = io.github.randomtools3377.checklist.core.Entry(
            at = nowMillis(), day = day, kind = io.github.randomtools3377.checklist.core.EntryKind.BONUS,
            amount = B.REWARD, label = "Boss beaten: ${fight.boss.title}",
        )
        dao.insertEntry(entry.toEntity())
        dao.putSetting(SettingEntity(B.PAID, fight.week.toString()))
        B.REWARD
    }

    /** Uses every item in hand on this week's boss. A boss beaten pays its bonus once. Null when there is nothing to do. */
    suspend fun attackBoss(day: LocalDate): io.github.randomtools3377.checklist.core.Attack? = db.withTransaction {
        val B = io.github.randomtools3377.checklist.core.Bosses
        val view = bossView(day)
        if (view.fight.beaten || view.items.isEmpty()) return@withTransaction null
        val dealt = view.wouldDeal
        val used = B.decodeUsed(dao.setting(B.USED)).toMutableMap()
        view.items.forEach { (item, count) -> used[item] = (used[item] ?: 0) + count }
        dao.putSetting(SettingEntity(B.USED, B.encodeUsed(used)))
        val damage = view.fight.damage + dealt
        dao.putSetting(SettingEntity(B.FIGHT, B.encodeFight(view.fight.week, damage, view.fight.health)))
        val after = view.fight.copy(damage = damage)
        val paid = if (after.beaten) payBoss(after, day) else 0
        io.github.randomtools3377.checklist.core.Attack(dealt, after.beaten, paid)
    }

    /** What a lucky tick paid, so undoing the tick can take it back. */
    data class LuckPaid(val entryId: Long, val points: Int, val double: Boolean)

    /**
     * If the tick of [goalId] just made on [day] is lucky, pays its luck and says what; null if
     * it isn't. The same tick (the goal, the day, which tick of the day) is always as lucky.
     * The extra is linked to its tick, so taking the tick back in any way takes the extra too;
     * it has no goal, so it isn't experience.
     */
    suspend fun luckyTick(goalId: Long, day: LocalDate, points: Int): LuckPaid? = db.withTransaction {
        val todays = dao.allTicks().filter { it.goalId == goalId && it.day == day.toEpochDay() }
        val nth = todays.size - 1
        val luck = io.github.randomtools3377.checklist.core.Lucky.of(io.github.randomtools3377.checklist.core.luckSeed(goalId, day, nth), points)
            ?: return@withTransaction null
        val (amount, label, double) = when (luck) {
            is io.github.randomtools3377.checklist.core.Lucky.Luck.Double -> Triple(luck.points, "Lucky tick: the points again", true)
            is io.github.randomtools3377.checklist.core.Lucky.Luck.Chest -> Triple(luck.points, "Lucky chest", false)
        }
        val tickId = todays.filter { it.stepId == null }.maxByOrNull { it.id }?.id
        val entry = io.github.randomtools3377.checklist.core.Entry(at = nowMillis(), day = day, kind = io.github.randomtools3377.checklist.core.EntryKind.BONUS, amount = amount, tickId = tickId, label = label)
        LuckPaid(dao.insertEntry(entry.toEntity()), amount, double)
    }

    suspend fun removeEntry(id: Long) = dao.deleteEntry(id)

    /**
     * The day's quests: made from [due] the first time they are asked for on [day], then kept
     * for the rest of the day, so adding, moving or reordering goals doesn't change them.
     */
    suspend fun questsOf(day: LocalDate, due: List<Goal>): List<io.github.randomtools3377.checklist.core.Quest> = db.withTransaction {
        val Q = io.github.randomtools3377.checklist.core.Quests
        Q.decodeDay(dao.setting(Q.SETTING_DAY), day) ?: Q.of(day, due).also { made ->
            if (made.isNotEmpty()) dao.putSetting(SettingEntity(Q.SETTING_DAY, Q.encodeDay(day, made)))
        }
    }

    /** Pays a finished quest once: its bonus, and a note that it is paid. False if it already was. */
    suspend fun payQuest(day: LocalDate, quest: io.github.randomtools3377.checklist.core.Quest): Boolean = db.withTransaction {
        val paid = io.github.randomtools3377.checklist.core.Quests.paidOn(dao.setting(io.github.randomtools3377.checklist.core.Quests.SETTING_PAID), day)
        if (quest.id in paid) return@withTransaction false
        val entry = io.github.randomtools3377.checklist.core.Entry(
            at = nowMillis(), day = day, kind = io.github.randomtools3377.checklist.core.EntryKind.BONUS, amount = quest.reward, label = "Quest: ${quest.text}",
        )
        dao.insertEntry(entry.toEntity())
        dao.putSetting(SettingEntity(io.github.randomtools3377.checklist.core.Quests.SETTING_PAID, io.github.randomtools3377.checklist.core.Quests.encodePaid(day, paid + quest.id)))
        true
    }

    /** The points history as a spreadsheet (CSV), with the notes on ticks. */
    suspend fun historySpreadsheet(): String {
        val (entries, notes) = db.withTransaction {
            dao.allEntries().map { it.toCore() } to
                dao.allTicks().map { it.toCore() }.filter { !it.note.isNullOrBlank() }.associate { it.id to it.note!! }
        }
        return io.github.randomtools3377.checklist.core.Spreadsheet.csv(entries, notes) { it.toLocalDateTime() }
    }

    /** The goal record as a spreadsheet (CSV): each goal each day, done, skipped or missed, with the reason. */
    suspend fun goalRecordSpreadsheet(today: LocalDate): String {
        val backup = db.withTransaction {
            listOf(dao.allGoals().map { it.toCore() }, dao.allSteps().map { it.toCore() }, dao.allTicks().map { it.toCore() }, dao.allLapses().map { it.toCore() })
        }
        @Suppress("UNCHECKED_CAST")
        return io.github.randomtools3377.checklist.core.GoalRecord.csv(
            goals = backup[0] as List<io.github.randomtools3377.checklist.core.Goal>,
            steps = backup[1] as List<io.github.randomtools3377.checklist.core.Step>,
            ticks = backup[2] as List<io.github.randomtools3377.checklist.core.Tick>,
            lapses = backup[3] as List<io.github.randomtools3377.checklist.core.Lapse>,
            today = today,
        )
    }

    /** The mood journal as a spreadsheet (CSV). */
    suspend fun moodSpreadsheet(): String = io.github.randomtools3377.checklist.core.MoodSheet.csv(dao.allMoods().map { it.toCore() })

    /** The budget of the month starting on [first] as a spreadsheet (CSV), with its pay by [now]. */
    suspend fun budgetSpreadsheet(first: LocalDate, now: kotlinx.datetime.LocalDateTime): String {
        val entries = dao.allMoney().map { it.toCore() }
        val categories = dao.moneyCategories().map { it.toCore() }
        val plan = PayPlans.decode(dao.setting(PayPlan.SETTING))
        val changes = dao.allWorkDays().map { it.toCore() }.associateBy { it.day }
        val month = Budget.month(Budget.monthOf(first), plan, changes, entries, categories, now)
        return io.github.randomtools3377.checklist.core.BudgetSheet.csv(Budget.monthOf(first), entries, categories, month.pay, now)
    }

    /** Everything in the database as a backup, without the picked files. */
    private suspend fun snapshot(): Backup = db.withTransaction {
        Backup(
            exportedAt = nowMillis(),
            goals = dao.allGoals().map { it.toCore() },
            steps = dao.allSteps().map { it.toCore() },
            ticks = dao.allTicks().map { it.toCore() },
            entries = dao.allEntries().map { it.toCore() },
            rewards = dao.allRewards().map { it.toCore() },
            purchases = dao.allPurchases().map { it.toCore() },
            settings = dao.allSettings().filter { it.name !in PHONE_ONLY_SETTINGS }.associate { it.name to it.value },
            lapses = dao.allLapses().map { it.toCore() },
            penalties = dao.allPenalties().map { it.toCore() },
            penaltyTasks = dao.allPenaltyTasks().map { it.toCore() },
            gaps = dao.allGaps().map { it.toCore() },
            checks = dao.allChecks().map { it.toCore() },
            moods = dao.allMoods().map { it.toCore() },
            money = dao.allMoney().map { it.toCore() },
            moneyCategories = dao.moneyCategories().map { it.toCore() },
            workDays = dao.allWorkDays().map { it.toCore() },
            bills = dao.allBills().map { it.toCore() },
            savingGoals = dao.allSavingGoals().map { it.toCore() },
            focusSessions = dao.allFocusSessions().map { it.toCore() },
        )
    }

    /** What this device holds now, counted, to set beside a backup file before it replaces it. */
    suspend fun summary(): io.github.randomtools3377.checklist.core.BackupSummary =
        io.github.randomtools3377.checklist.core.BackupSummary.of(snapshot())

    /** Everything in the app as a backup file's text. */
    suspend fun exportBackup(): String {
        val backup = snapshot()
        // The GIFs the user picked go in the file too, so a restore brings them back.
        // So do the sounds the user picked for celebrations, in the same part of the file.
        val sounds = CelebrationSounds.used(backup.settings, backup.goals.map { it.id }.toSet())
        val files = (backup.goals.mapNotNull { Celebration.ownFile(it.celebration) }.toSet() + sounds)
            .mapNotNull { name -> celebrations.read(name)?.let { name to Base64.Default.encode(it) } }
            .toMap()
        val photo = background?.read()?.let { Base64.Default.encode(it) }
        return Backup.encode(backup.copy(files = files, background = photo))
    }

    /** Replaces everything in the app with [backup]. All or nothing, then the picked GIFs and sounds. */
    suspend fun restore(backup: Backup) {
        restoreData(backup)
        val used = backup.goals.mapNotNull { Celebration.ownFile(it.celebration) }.toSet() +
            CelebrationSounds.used(backup.settings, backup.goals.map { it.id }.toSet())
        celebrations.keepOnly(emptySet())
        for ((name, data) in backup.files) {
            if (name !in used) continue
            val bytes = try {
                Base64.Default.decode(data)
            } catch (e: IllegalArgumentException) {
                continue
            }
            if (CelebrationSounds.isSafeName(name) && bytes.size > CelebrationSounds.MAX_BYTES) continue
            celebrations.write(name, bytes)
        }
        background?.let { photo ->
            val bytes = backup.background?.let { data -> runCatching { Base64.Default.decode(data) }.getOrNull() }
            if (bytes != null) photo.write(bytes) else photo.delete()
        }
    }

    private suspend fun restoreData(backup: Backup): Unit = db.withTransaction {
        // This phone keeps its own calendar choice. A running timing stops: its goal may now be another one.
        val calendarIds = dao.setting(SETTING_CALENDAR_IDS)
        val backupKey = dao.setting(SETTING_BACKUP_KEY)
        deleteEverything()
        dao.insertGoals(backup.goals.map { it.toEntity() })
        dao.insertSteps(backup.steps.map { it.toEntity() })
        dao.insertTicks(backup.ticks.map { it.toEntity() })
        dao.insertEntries(backup.entries.map { it.toEntity() })
        dao.insertRewards(backup.rewards.map { it.toEntity() })
        dao.insertPurchases(backup.purchases.map { it.toEntity() })
        dao.insertSettings(
            backup.settings.filterKeys { it !in PHONE_ONLY_SETTINGS }.map { (name, value) -> SettingEntity(name, value) } +
                listOfNotNull(
                    calendarIds?.let { SettingEntity(SETTING_CALENDAR_IDS, it) },
                    backupKey?.let { SettingEntity(SETTING_BACKUP_KEY, it) },
                ),
        )
        dao.insertLapses(backup.lapses.map { it.toEntity() })
        dao.insertPenalties(backup.penalties.map { it.toEntity() })
        dao.insertPenaltyTasks(backup.penaltyTasks.map { it.toEntity() })
        dao.insertGaps(backup.gaps.map { it.toEntity() })
        dao.insertChecks(backup.checks.map { it.toEntity() })
        dao.insertMoods(backup.moods.map { it.toEntity() })
        dao.insertMoneyRows(backup.money.map { it.toEntity() })
        dao.insertMoneyCategories(backup.moneyCategories.map { it.toEntity() })
        dao.insertWorkDays(backup.workDays.map { it.toEntity() })
        dao.insertBills(backup.bills.map { it.toEntity() })
        dao.insertSavingGoals(backup.savingGoals.map { it.toEntity() })
        dao.insertFocusSessions(backup.focusSessions.filter { it.minutes > 0 }.map { it.toEntity() })
        // "At most" limits only for goals that came with the backup.
        dao.setting(AtMost.SETTING)?.let { text ->
            val kept = AtMost.kept(text, backup.goals.map { it.id }.toSet())
            if (kept != text) dao.putSetting(SettingEntity(AtMost.SETTING, kept))
        }
        AtMostLimits.load(dao)
    }

    /** Empties every table. Inside a transaction. */
    private suspend fun deleteEverything() {
        dao.deleteTicks()
        dao.deleteEntries()
        dao.deletePurchases()
        dao.deleteSteps()
        dao.deleteGoals()
        dao.deleteRewards()
        dao.deleteSettings()
        dao.deleteLapses()
        dao.deletePenalties()
        dao.deletePenaltyTasks()
        dao.deleteGaps()
        dao.deleteChecks()
        dao.deleteMoods()
        dao.deleteAllMoney()
        dao.deleteMoneyCategories()
        dao.deleteWorkDays()
        dao.deleteBills()
        dao.deleteSavingGoals()
        dao.deleteFocusSessions()
    }

    /**
     * "Erase everything and start fresh": every table is emptied, settings included, so the app
     * is as it was when first installed; then the picked GIFs, sounds and photo go. Backup files
     * the user saved are not the app's to touch, and stay where they are.
     */
    suspend fun eraseEverything() {
        db.withTransaction { deleteEverything() }
        celebrations.keepOnly(emptySet())
        background?.delete()
        AtMostLimits.load(dao)
    }

    // Focus time.

    /**
     * A focus timer ended at [now] (it ran out, "Done now", or it was stopped): keeps the minutes
     * really focused, pauses left out, against its goal and the user's day it ended. Nothing is
     * kept for a break, or for under half a minute. Call it once for each timer taken.
     */
    suspend fun recordFocus(state: io.github.randomtools3377.checklist.reminders.FocusState, now: Long = nowMillis()): FocusSession? {
        // A timer found after its time was up ended when it ran out, not when it was found.
        val endedAt = if (state.running) minOf(now, state.endsAt) else now
        val focused = state.focused(now)
        val day = Days.dayOf(endedAt.toLocalDateTime(), parseDayEndHour(dao.setting(SETTING_DAY_END_HOUR)))
        val session = FocusTime.session(
            goalId = state.goalId,
            isBreak = state.isBreak,
            totalMillis = state.totalMillis,
            leftMillis = state.left(now),
            startedAt = state.startedAt.takeIf { it in 1..endedAt } ?: (endedAt - focused),
            day = day,
        ) ?: return null
        return session.copy(id = dao.insertFocusSession(session.toEntity()))
    }

    /** The minutes focused on a goal from [from] to [to], both days included: for the goal's own page. */
    suspend fun focusMinutes(goalId: Long, from: LocalDate, to: LocalDate): Int =
        dao.focusMinutes(goalId, from.toEpochDay(), to.toEpochDay())

    /** The same, kept up to date. */
    fun observeFocusMinutes(goalId: Long, from: LocalDate, to: LocalDate): kotlinx.coroutines.flow.Flow<Int> =
        dao.observeFocusMinutes(goalId, from.toEpochDay(), to.toEpochDay())

    /** How long the break offered after a focus session is, in minutes; 0 while it is off. */
    suspend fun focusBreakMinutes(): Int = FocusTime.breakMinutes(dao.setting(FocusTime.BREAK_SETTING))

    // The budget.

    /** Puts in the starting categories, the first time the budget is used. */
    suspend fun startBudget(): Unit = db.withTransaction {
        if (dao.moneyCategories().isEmpty()) dao.insertMoneyCategories(Budget.STARTER.map { it.toEntity() })
    }

    /**
     * Saves a bill. A new one is counted from today on: what fell due before is left for the
     * user to add if it should be. Changing one keeps what was already counted.
     */
    suspend fun saveBill(bill: Bill, today: LocalDate): Unit = db.withTransaction {
        val name = bill.name.trim().take(40).ifEmpty { if (bill.moneyIn) "Money in" else "Bill" }
        if (bill.id == 0L) {
            dao.insertBill(bill.copy(name = name, countedTo = today.minusDays(1)).toEntity())
        } else {
            val stored = dao.bill(bill.id)?.toCore()
            dao.updateBill(bill.copy(name = name, countedTo = stored?.countedTo ?: today.minusDays(1)).toEntity())
        }
        Unit
    }

    suspend fun bill(id: Long): Bill? = dao.bill(id)?.toCore()

    /** Stops a bill; what it already counted stays in the history. */
    suspend fun removeBill(id: Long) {
        dao.bill(id)?.let { dao.updateBill(it.copy(archived = true)) }
    }

    /** Counts each bill as spent, and regular money in as received, on each day it fell due up to [today], once. */
    suspend fun countBills(today: LocalDate): Unit = db.withTransaction {
        for (bill in dao.allBills().map { it.toCore() }) {
            if (bill.archived) continue
            val days = Bills.toCount(bill, today, bill.first)
            days.forEach { day ->
                dao.insertMoney(
                    MoneyEntry(
                        kind = if (bill.moneyIn) MoneyKind.RECEIVED else MoneyKind.SPENT, cents = bill.cents,
                        categoryId = bill.categoryId.takeIf { !bill.moneyIn }, note = bill.name, day = day,
                        at = nowMillis(), billId = bill.id,
                    ).toEntity(),
                )
            }
            val counted = bill.countedTo
            if (counted == null || counted < today) dao.updateBill(bill.copy(countedTo = today).toEntity())
        }
    }

    /** Saves a saving goal: a new one at the end when its id is 0. */
    suspend fun saveSavingGoal(goal: SavingGoal): Unit = db.withTransaction {
        val name = goal.name.trim().take(40).ifEmpty { "Saving" }
        if (goal.id == 0L) {
            dao.insertSavingGoal(goal.copy(name = name, position = dao.allSavingGoals().size, createdAt = nowMillis()).toEntity())
        } else {
            dao.updateSavingGoal(goal.copy(name = name).toEntity())
        }
        Unit
    }

    /**
     * Removes a saving goal from the list; what went in and out stays in the history. With
     * [takeOutOn], what is still in it is taken out on that day first, so it isn't left counted
     * as saved.
     */
    suspend fun removeSavingGoal(id: Long, takeOutOn: LocalDate? = null): Unit = db.withTransaction {
        val goal = dao.allSavingGoals().firstOrNull { it.id == id } ?: return@withTransaction
        if (takeOutOn != null) moveSaving(id, savedIn(id), out = true, day = takeOutOn, name = goal.name)
        dao.updateSavingGoal(goal.copy(archived = true))
    }

    /**
     * What is in saving goal [id]: put in, less taken out, in cents. A plain query, not a Flow,
     * as it is also read inside a transaction (removing a goal with what's in it taken out).
     */
    private suspend fun savedIn(id: Long): Long =
        dao.allMoney().map { it.toCore() }.filter { it.savingId == id }.sumOf {
            when (it.kind) {
                MoneyKind.SAVED -> it.cents
                MoneyKind.TAKEN_OUT -> -it.cents
                else -> 0L
            }
        }

    /**
     * Pays what the budget game owes: each day from when it started up to yesterday, months that
     * ended within every limit, and saving goals reached, each once. Returns what it paid.
     */
    suspend fun settleMoneyGame(today: LocalDate): List<MoneyGame.Payout> = db.withTransaction {
        val from = dao.setting(MoneyGame.FROM)?.toLongOrNull()?.toDay()
            ?: today.also { dao.putSetting(SettingEntity(MoneyGame.FROM, it.toEpochDay().toString())) }
        val paidTo = dao.setting(MoneyGame.TO)?.toLongOrNull()?.toDay() ?: from.minusDays(1)
        val entries = dao.allMoney().map { it.toCore() }
        val categories = dao.moneyCategories().map { it.toCore() }
        val shareOf = moneyGameShares(entries)
        val yesterday = today.minusDays(1)
        val days = if (paidTo < yesterday) MoneyGame.settle(maxOf(paidTo.plusDays(1), from), yesterday, shareOf, entries, categories) else emptyList()
        val paidGoals = MoneyGame.decodeIds(dao.setting(MoneyGame.GOALS))
        val reached = MoneyGame.savedUp(SavingProgress.of(dao.allSavingGoals().map { it.toCore() }, entries), paidGoals)
        val payouts = days + reached.map { MoneyGame.Payout(today, MoneyGame.SAVED_UP, "Money: saved up for ${it.goal.name}") }
        payouts.forEach { payout ->
            dao.insertEntry(
                io.github.randomtools3377.checklist.core.Entry(
                    at = nowMillis(), day = payout.day, kind = io.github.randomtools3377.checklist.core.EntryKind.BONUS,
                    amount = payout.points, label = payout.label,
                ).toEntity(),
            )
        }
        if (paidTo < yesterday) dao.putSetting(SettingEntity(MoneyGame.TO, yesterday.toEpochDay().toString()))
        if (reached.isNotEmpty()) dao.putSetting(SettingEntity(MoneyGame.GOALS, MoneyGame.encodeIds(paidGoals + reached.map { it.goal.id })))
        payouts
    }

    /**
     * Each day's share in the budget game, worked out from [entries] (all the money), every
     * changed work day and the bills, the same way for the days paid and for the streak shown.
     */
    private suspend fun moneyGameShares(entries: List<MoneyEntry>): (LocalDate) -> Double? {
        // The user's own daily amount, when set, is the line for every day still to be paid.
        dao.setting(MoneyGame.DAILY)?.toLongOrNull()?.takeIf { it > 0 }?.let { own -> return { _: LocalDate -> own / 100.0 } }
        val plan = PayPlans.decode(dao.setting(PayPlan.SETTING))
        val changes = dao.allWorkDays().map { it.toCore() }.associateBy { it.day }
        val bills = dao.allBills().map { it.toCore() }
        val shares = mutableMapOf<LocalDate, Double?>()
        return { d: LocalDate -> shares.getOrPut(Budget.monthOf(d)) { MoneyGame.share(plan, Budget.monthOf(d), changes, entries, bills) } }
    }

    /**
     * Today in the budget game: today's share, what's spent today, and good money days in a row
     * up to yesterday, from the day the game started.
     */
    suspend fun moneyGameToday(today: LocalDate): MoneyGameToday {
        val from = dao.setting(MoneyGame.FROM)?.toLongOrNull()?.toDay() ?: today
        val entries = dao.observeMoney(Budget.monthOf(minOf(from, today)).toEpochDay(), Pay.monthDays(today).last().toEpochDay())
            .first().map { it.toCore() }
        val shareOf = moneyGameShares(entries)
        return MoneyGameToday(shareOf(today), MoneyGame.spent(today, entries), MoneyGame.streak(from, today.minusDays(1), shareOf, entries))
    }

    /**
     * Starts the budget game again from [today] when it, or Budget, is turned back on after days
     * it was off: those days aren't paid, and the money streak counts from today. Nothing changes
     * when it never started or nothing was missed.
     */
    suspend fun restartMoneyGame(today: LocalDate): Unit = db.withTransaction {
        val from = dao.setting(MoneyGame.FROM)?.toLongOrNull()?.toDay() ?: return@withTransaction
        val paidTo = dao.setting(MoneyGame.TO)?.toLongOrNull()?.toDay() ?: from.minusDays(1)
        val yesterday = today.minusDays(1)
        if (paidTo >= yesterday) return@withTransaction
        dao.putSetting(SettingEntity(MoneyGame.FROM, today.toEpochDay().toString()))
        dao.putSetting(SettingEntity(MoneyGame.TO, yesterday.toEpochDay().toString()))
    }

    /**
     * Moves what was left at the end of a month into savings, dated [day] (that month's last day):
     * into [goalId], or with none into a plain "Savings" account, made the first time, so the
     * money can be seen growing while it isn't spent.
     */
    suspend fun saveLeftover(goalId: Long?, cents: Long, day: LocalDate, note: String): Unit = db.withTransaction {
        if (cents <= 0) return@withTransaction
        val into = goalId
            ?: dao.allSavingGoals().firstOrNull { !it.archived && it.name == SAVINGS_ACCOUNT && it.targetCents == 0L }?.id
            ?: dao.insertSavingGoal(SavingGoal(name = SAVINGS_ACCOUNT, icon = "💰", targetCents = 0, position = dao.allSavingGoals().size, createdAt = nowMillis()).toEntity())
        dao.insertMoney(MoneyEntry(kind = MoneyKind.SAVED, cents = cents, note = note, day = day, at = nowMillis(), savingId = into).toEntity())
        Unit
    }

    /** Puts [cents] into a saving goal, or takes it out when [out], never more than is in it. */
    suspend fun moveSaving(goalId: Long, cents: Long, out: Boolean, day: LocalDate, name: String) {
        val amount = if (out) minOf(cents, savedIn(goalId)) else cents
        if (amount <= 0) return
        dao.insertMoney(
            MoneyEntry(
                kind = if (out) MoneyKind.TAKEN_OUT else MoneyKind.SAVED, cents = amount, note = name, day = day,
                at = nowMillis(), savingId = goalId,
            ).toEntity(),
        )
    }

    /**
     * Saves the pay, applying from [from] on: the days before keep the pay they had, so past
     * months and reports stay as they were. With null it applies from the start, to every day.
     */
    suspend fun savePay(plan: PayPlan, from: LocalDate? = null): Unit = db.withTransaction {
        val stored = PayPlans.decode(dao.setting(PayPlan.SETTING))
        val plans = if (stored == null) PayPlans.of(plan) else stored.changed(plan, from)
        dao.putSetting(SettingEntity(PayPlan.SETTING, PayPlans.encode(plans)))
    }

    /**
     * Saves money spent or received: a new entry when [entry]'s id is 0. Spending paid from a
     * saving goal ([MoneyEntry.spentFromSavings]) comes out of that goal when it holds enough,
     * and is no part of the month's spending; when it doesn't, it is plain spending.
     */
    suspend fun saveMoney(entry: MoneyEntry): Unit = db.withTransaction {
        var saved = entry.copy(cents = entry.cents.coerceAtLeast(0), note = entry.note.trim().take(80), at = entry.at.takeIf { it > 0 } ?: nowMillis())
        if (saved.spentFromSavings) {
            val goal = saved.savingId
            // What this entry already took from the goal counts as still in it while it is changed.
            val own = dao.money(saved.id)?.toCore()?.takeIf { it.kind == MoneyKind.TAKEN_OUT && it.savingId == goal }?.cents ?: 0
            saved = if (goal != null && savedIn(goal) + own >= saved.cents) {
                saved.copy(kind = MoneyKind.TAKEN_OUT)
            } else {
                saved.copy(kind = MoneyKind.SPENT, savingId = null, spentFromSavings = false)
            }
        }
        if (saved.id == 0L) dao.insertMoney(saved.toEntity()) else dao.updateMoney(saved.toEntity())
        Unit
    }

    suspend fun removeMoney(id: Long) = dao.deleteMoney(id)

    suspend fun moneyEntry(id: Long): MoneyEntry? = dao.money(id)?.toCore()

    /** Saves a category: a new one at the end when [category]'s id is 0. */
    suspend fun saveMoneyCategory(category: MoneyCategory): Unit = db.withTransaction {
        val name = category.name.trim().take(30).ifEmpty { "Category" }
        if (category.id == 0L) {
            dao.insertMoneyCategory(category.copy(name = name, position = dao.moneyCategories().size, archived = false, role = null).toEntity())
        } else {
            dao.updateMoneyCategory(category.copy(name = name).toEntity())
        }
        Unit
    }

    /** Moves a category one place up ([by] = -1) or down (+1) among those in use. */
    suspend fun moveMoneyCategory(id: Long, by: Int): Unit = db.withTransaction {
        val list = dao.moneyCategories().filter { !it.archived }.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in list.indices) return@withTransaction
        list.add(to, list.removeAt(from))
        list.forEachIndexed { i, row -> if (row.position != i) dao.updateMoneyCategory(row.copy(position = i)) }
    }

    /** Removes a category from the list; what was spent in it stays in the history. Rewards stays. */
    suspend fun removeMoneyCategory(id: Long) {
        dao.moneyCategories().firstOrNull { it.id == id && it.role == null }?.let { dao.updateMoneyCategory(it.copy(archived = true)) }
    }

    /** Changes one work day: other hours, or a day off with or without pay. */
    suspend fun setWorkDay(change: WorkDay) = dao.putWorkDay(change.toEntity())

    suspend fun usualWorkDay(day: LocalDate) = dao.deleteWorkDay(day.toEpochDay())

    /**
     * A reward bought in the shop with a money value counts as spent, under Rewards, on the day
     * it was bought. Returning it takes it off again (see [rewardReturned]).
     */
    suspend fun rewardBought(purchaseId: Long, name: String, value: Double, day: LocalDate, fromSaving: Long? = null): Unit = db.withTransaction {
        startBudget()
        val rewards = dao.moneyCategories().firstOrNull { it.role == CategoryRole.REWARDS.name }
        dao.deleteMoneyForPurchase(purchaseId)
        // Paid from a saving goal that holds enough: the money comes out of the goal instead of
        // counting as this month's spending. Returning the reward puts it back.
        if (fromSaving != null && savedIn(fromSaving) >= Budget.cents(value)) {
            dao.insertMoney(
                MoneyEntry(
                    kind = MoneyKind.TAKEN_OUT, cents = Budget.cents(value), note = name, day = day,
                    at = nowMillis(), purchaseId = purchaseId, savingId = fromSaving,
                ).toEntity(),
            )
            return@withTransaction
        }
        dao.insertMoney(
            MoneyEntry(
                kind = MoneyKind.SPENT, cents = Budget.cents(value), categoryId = rewards?.id, note = name, day = day,
                at = nowMillis(), purchaseId = purchaseId,
            ).toEntity(),
        )
        Unit
    }

    suspend fun rewardReturned(purchaseId: Long) = dao.deleteMoneyForPurchase(purchaseId)
}

/** The plain savings account that money left over goes into when no saving goal is picked. */
const val SAVINGS_ACCOUNT = "Savings"

/** The setting holding the last month (its first day) whose leftover was moved into savings or let go. */
const val SETTING_LEFTOVER_DONE = "budget_leftover_done"
