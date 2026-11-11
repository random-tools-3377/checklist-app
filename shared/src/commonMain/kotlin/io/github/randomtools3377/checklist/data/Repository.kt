package io.github.randomtools3377.checklist.data

import io.github.randomtools3377.checklist.core.Amounts
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Categories
import io.github.randomtools3377.checklist.core.Celebration
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
import io.github.randomtools3377.checklist.core.PayPlan
import io.github.randomtools3377.checklist.core.WorkDay
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RewardSize
import io.github.randomtools3377.checklist.core.Effort
import io.github.randomtools3377.checklist.core.Mood
import io.github.randomtools3377.checklist.core.Moods
import io.github.randomtools3377.checklist.core.Challenge
import io.github.randomtools3377.checklist.core.RepeatText
import io.github.randomtools3377.checklist.core.RepeatKind
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
/** "0" turns off the daily mood check-in; and the day it was put off ("Not today"). */
const val SETTING_MOOD = "mood_checkin"
const val SETTING_MOOD_SKIPPED = "mood_skipped"
/** The Monday of the last week planned, or put off, from the weekly planning card. */
const val SETTING_WEEK_PLANNED = "week_planned"
/** "done" once the welcome card on Today was answered (see GuideScreen). */
const val SETTING_GUIDE = "guide"
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
    /** As typed; tidied on saving (see Categories). */
    val category: String? = null,
    val color: Int? = null,
    /** How hard its steps are, which suggests their points. */
    val effort: Effort? = null,
    /** Its skill area for the character; null to guess it. */
    val skill: io.github.randomtools3377.checklist.core.SkillArea? = null,
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
)

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

    /** Deletes picked GIFs that no goal uses any more. */
    private suspend fun pruneCelebrations() {
        celebrations.keepOnly(dao.allGoals().mapNotNull { Celebration.ownFile(it.celebration) }.toSet())
    }

    suspend fun goal(id: Long): Goal? = dao.goal(id)?.toCore()

    /** What the user typed for a category, tidied, joining one already in use when it matches. */
    private suspend fun category(typed: String?, goalId: Long): String? =
        Categories.tidy(typed, Categories.of(dao.allGoals().filter { it.id != goalId }.map { it.toCore() }))

    suspend fun steps(goalId: Long): List<Step> = dao.activeSteps(goalId).map { it.toCore() }

    suspend fun reward(id: Long): Reward? = dao.reward(id)?.toCore()

    suspend fun saveHabit(draft: HabitDraft): Unit = db.withTransaction {
        val existing = if (draft.id != 0L) dao.goal(draft.id) else null
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
            unit = draft.unit?.trim()?.take(Amounts.UNIT_LENGTH)?.takeIf { it.isNotEmpty() && draft.amount != null },
            effort = draft.effort?.name,
            quit = draft.quit,
            challengeDays = draft.challenge?.days,
            challengeBonus = draft.challenge?.bonus,
            challengeFrom = draft.challenge?.from?.toEpochDay(),
            afterGoal = draft.after?.takeIf { it != draft.id },
            skill = draft.skill?.name,
        )
        val goalId = if (existing == null) dao.insertGoal(row) else row.id.also { dao.updateGoal(row) }
        // A routine's checklist: removed items are archived, so their past ticks still make sense.
        if (draft.items.isNotEmpty() || dao.activeSteps(goalId).isNotEmpty()) saveSteps(goalId, draft.items)
        pruneCelebrations()
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
        pruneCelebrations()
    }

    /** Saves a quick to-do. Returns its id. */
    suspend fun saveTodo(draft: TodoDraft): Long = db.withTransaction {
        val existing = if (draft.id != 0L) dao.goal(draft.id)?.takeIf { it.kind == GoalKind.TODO.name } else null
        val made = Todos.of(draft.name, draft.points, draft.date, draft.icon.ifBlank { Todos.ICON })
        val reminder = draft.remindAt?.takeIf { draft.date != null }?.let { Reminder(onTime = true, at = it) }
        val goalId = if (existing == null) {
            dao.insertGoal(made.copy(private = draft.private, reminder = reminder, effort = draft.effort, position = dao.nextGoalPosition(), createdAt = nowMillis()).toEntity())
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
     * two rewards. They are ordinary goals the user can change or remove.
     */
    suspend fun addExamples(today: LocalDate): Unit = db.withTransaction {
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
    }

    /** How the user feels today, from the check-in on Today. */
    suspend fun setMood(day: LocalDate, score: Int, note: String?) {
        val old = dao.allMoods().firstOrNull { it.day == day.toEpochDay() }
        dao.putMood(MoodEntity(day.toEpochDay(), score.coerceIn(1, 5), note?.trim()?.take(Moods.NOTE_LENGTH)?.takeIf { it.isNotEmpty() } ?: old?.note?.takeIf { note == null }, nowMillis()))
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

    /** A copy of a goal, with its steps or checklist, named "… (copy)", at the end of the list. Returns its id. */
    suspend fun duplicateGoal(id: Long): Long? = db.withTransaction {
        val goal = dao.goal(id) ?: return@withTransaction null
        val copyId = dao.insertGoal(
            goal.copy(id = 0, name = (goal.name.take(53) + " (copy)"), archived = false, position = dao.nextGoalPosition(), createdAt = nowMillis()),
        )
        dao.activeSteps(id).forEach { step -> dao.insertStep(step.copy(id = 0, goalId = copyId)) }
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

    /** Brings a removed goal back, at the end of the list. Its past stays as it was. */
    suspend fun restoreGoal(id: Long) = db.withTransaction {
        dao.goal(id)?.let { dao.updateGoal(it.copy(archived = false, position = dao.nextGoalPosition())) }
    }

    /** Removes a goal from the lists. Points it already paid stay in the balance. */
    suspend fun removeGoal(id: Long) {
        dao.goal(id)?.let { dao.updateGoal(it.copy(archived = true)) }
    }

    /** Moves a goal one place up ([by] = -1) or down (+1) among the goals of the same kind. */
    suspend fun moveGoal(id: Long, by: Int): Unit = db.withTransaction {
        val goal = dao.goal(id) ?: return@withTransaction
        val all = dao.activeGoals()
        val sameKind = all.filter { it.kind == goal.kind }.toMutableList()
        val from = sameKind.indexOfFirst { it.id == id }
        val to = from + by
        if (from < 0 || to !in sameKind.indices) return@withTransaction
        sameKind.add(to, sameKind.removeAt(from))
        // Put the reordered kind back into the same slots, then number everything in order.
        val reordered = sameKind.iterator()
        all.map { if (it.kind == goal.kind) reordered.next() else it }
            .forEachIndexed { i, row -> if (row.position != i) dao.updateGoal(row.copy(position = i)) }
    }

    suspend fun saveReward(draft: RewardDraft): Unit = db.withTransaction {
        val existing = if (draft.id != 0L) dao.reward(draft.id) else null
        if (existing == null) {
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
        }
        Unit
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

    /** This week's boss and the items in hand. Items count from the first time this is asked. */
    suspend fun bossView(day: LocalDate): io.github.randomtools3377.checklist.core.BossView {
        val B = io.github.randomtools3377.checklist.core.Bosses
        val since = dao.setting(B.ITEMS_FROM)?.toLongOrNull()
            ?: nowMillis().also { dao.putSetting(SettingEntity(B.ITEMS_FROM, it.toString())) }
        val goals = dao.allGoals().map { it.toCore() }
        val monday = B.monday(day)
        val boss = B.of(monday)
        val fight = io.github.randomtools3377.checklist.core.BossFight(monday, boss, B.health(B.expectedTicks(goals, monday)), B.damageIn(dao.setting(B.FIGHT), monday))
        val items = B.inventory(dao.allTicks().map { it.toCore() }, goals, since, B.decodeUsed(dao.setting(B.USED)))
        return io.github.randomtools3377.checklist.core.BossView(fight, items)
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
        dao.putSetting(SettingEntity(B.FIGHT, "${view.fight.week}:$damage"))
        val beaten = damage >= view.fight.health
        var paid = 0
        if (beaten && dao.setting(B.PAID) != view.fight.week.toString()) {
            val entry = io.github.randomtools3377.checklist.core.Entry(
                at = nowMillis(), day = day, kind = io.github.randomtools3377.checklist.core.EntryKind.BONUS,
                amount = B.REWARD, label = "Boss beaten: ${view.fight.boss.title}",
            )
            dao.insertEntry(entry.toEntity())
            dao.putSetting(SettingEntity(B.PAID, view.fight.week.toString()))
            paid = B.REWARD
        }
        io.github.randomtools3377.checklist.core.Attack(dealt, beaten, paid)
    }

    /** What a lucky tick paid, so undoing the tick can take it back. */
    data class LuckPaid(val entryId: Long, val points: Int, val double: Boolean)

    /**
     * If the tick of [goalId] just made on [day] is lucky, pays its luck and says what; null if
     * it isn't. The same tick (the goal, the day, which tick of the day) is always as lucky.
     */
    suspend fun luckyTick(goalId: Long, day: LocalDate, points: Int): LuckPaid? = db.withTransaction {
        val nth = dao.allTicks().count { it.goalId == goalId && it.day == day.toEpochDay() } - 1
        val luck = io.github.randomtools3377.checklist.core.Lucky.of(io.github.randomtools3377.checklist.core.luckSeed(goalId, day, nth), points)
            ?: return@withTransaction null
        val (amount, label, double) = when (luck) {
            is io.github.randomtools3377.checklist.core.Lucky.Luck.Double -> Triple(luck.points, "Lucky tick: the points again", true)
            is io.github.randomtools3377.checklist.core.Lucky.Luck.Chest -> Triple(luck.points, "Lucky chest", false)
        }
        val entry = io.github.randomtools3377.checklist.core.Entry(at = nowMillis(), day = day, kind = io.github.randomtools3377.checklist.core.EntryKind.BONUS, amount = amount, label = label)
        LuckPaid(dao.insertEntry(entry.toEntity()), amount, double)
    }

    suspend fun removeEntry(id: Long) = dao.deleteEntry(id)

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

    /** Everything in the app as a backup file's text. */
    suspend fun exportBackup(): String {
        val backup = db.withTransaction {
            Backup(
                exportedAt = nowMillis(),
                goals = dao.allGoals().map { it.toCore() },
                steps = dao.allSteps().map { it.toCore() },
                ticks = dao.allTicks().map { it.toCore() },
                entries = dao.allEntries().map { it.toCore() },
                rewards = dao.allRewards().map { it.toCore() },
                purchases = dao.allPurchases().map { it.toCore() },
                settings = dao.allSettings().associate { it.name to it.value },
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
            )
        }
        // The GIFs the user picked go in the file too, so a restore brings them back.
        val files = backup.goals.mapNotNull { Celebration.ownFile(it.celebration) }.toSet()
            .mapNotNull { name -> celebrations.read(name)?.let { name to Base64.Default.encode(it) } }
            .toMap()
        val photo = background?.read()?.let { Base64.Default.encode(it) }
        return Backup.encode(backup.copy(files = files, background = photo))
    }

    /** Replaces everything in the app with [backup]. All or nothing, then the picked GIFs. */
    suspend fun restore(backup: Backup) {
        restoreData(backup)
        val used = backup.goals.mapNotNull { Celebration.ownFile(it.celebration) }.toSet()
        celebrations.keepOnly(emptySet())
        for ((name, data) in backup.files) {
            if (name !in used) continue
            val bytes = try {
                Base64.Default.decode(data)
            } catch (e: IllegalArgumentException) {
                continue
            }
            celebrations.write(name, bytes)
        }
        background?.let { photo ->
            val bytes = backup.background?.let { data -> runCatching { Base64.Default.decode(data) }.getOrNull() }
            if (bytes != null) photo.write(bytes) else photo.delete()
        }
    }

    private suspend fun restoreData(backup: Backup): Unit = db.withTransaction {
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
        dao.insertGoals(backup.goals.map { it.toEntity() })
        dao.insertSteps(backup.steps.map { it.toEntity() })
        dao.insertTicks(backup.ticks.map { it.toEntity() })
        dao.insertEntries(backup.entries.map { it.toEntity() })
        dao.insertRewards(backup.rewards.map { it.toEntity() })
        dao.insertPurchases(backup.purchases.map { it.toEntity() })
        dao.insertSettings(backup.settings.map { (name, value) -> SettingEntity(name, value) })
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
    }

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
        val name = bill.name.trim().take(40).ifEmpty { "Bill" }
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

    /** Counts each bill as spent on each day it fell due up to [today], once. */
    suspend fun countBills(today: LocalDate): Unit = db.withTransaction {
        for (bill in dao.allBills().map { it.toCore() }) {
            if (bill.archived) continue
            val days = Bills.toCount(bill, today, bill.first)
            days.forEach { day ->
                dao.insertMoney(
                    MoneyEntry(
                        kind = MoneyKind.SPENT, cents = bill.cents, categoryId = bill.categoryId, note = bill.name, day = day,
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

    /** Removes a saving goal from the list; what went in and out stays in the history. */
    suspend fun removeSavingGoal(id: Long) {
        dao.allSavingGoals().firstOrNull { it.id == id }?.let { dao.updateSavingGoal(it.copy(archived = true)) }
    }

    /**
     * Pays what the budget game owes: each day from when it started up to yesterday, months that
     * ended within every limit, and saving goals reached, each once. Returns what it paid.
     */
    suspend fun settleMoneyGame(today: LocalDate): List<MoneyGame.Payout> = db.withTransaction {
        val from = dao.setting(MoneyGame.FROM)?.toLongOrNull()?.toDay()
            ?: today.also { dao.putSetting(SettingEntity(MoneyGame.FROM, it.toEpochDay().toString())) }
        val paidTo = dao.setting(MoneyGame.TO)?.toLongOrNull()?.toDay() ?: from.minusDays(1)
        val plan = PayPlan.decode(dao.setting(PayPlan.SETTING))
        val entries = dao.allMoney().map { it.toCore() }
        val categories = dao.moneyCategories().map { it.toCore() }
        val changes = dao.allWorkDays().map { it.toCore() }.associateBy { it.day }
        val shares = mutableMapOf<LocalDate, Double?>()
        val shareOf = { d: LocalDate -> shares.getOrPut(Budget.monthOf(d)) { MoneyGame.share(plan, Budget.monthOf(d), changes, entries) } }
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

    /** Money from the start of last month on, for the budget game's streak. */
    suspend fun moneyThisAndLastMonth(today: LocalDate): List<MoneyEntry> =
        dao.observeMoney(Budget.monthOf(Budget.monthOf(today).minusDays(1)).toEpochDay(), today.toEpochDay()).first().map { it.toCore() }

    /** The day the budget game started, if it has. */
    suspend fun moneyGameFrom(): LocalDate? = dao.setting(MoneyGame.FROM)?.toLongOrNull()?.toDay()

    /** Puts [cents] into a saving goal, or takes it out when [out]. */
    suspend fun moveSaving(goalId: Long, cents: Long, out: Boolean, day: LocalDate, name: String) {
        if (cents <= 0) return
        dao.insertMoney(
            MoneyEntry(
                kind = if (out) MoneyKind.TAKEN_OUT else MoneyKind.SAVED, cents = cents, note = name, day = day,
                at = nowMillis(), savingId = goalId,
            ).toEntity(),
        )
    }

    suspend fun savePay(plan: PayPlan) = setSetting(PayPlan.SETTING, PayPlan.encode(plan))

    /** Saves money spent or received: a new entry when [entry]'s id is 0. */
    suspend fun saveMoney(entry: MoneyEntry) {
        val row = entry.copy(cents = entry.cents.coerceAtLeast(0), note = entry.note.trim().take(80), at = entry.at.takeIf { it > 0 } ?: nowMillis()).toEntity()
        if (entry.id == 0L) dao.insertMoney(row) else dao.updateMoney(row)
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
    suspend fun rewardBought(purchaseId: Long, name: String, value: Double, day: LocalDate): Unit = db.withTransaction {
        startBudget()
        val rewards = dao.moneyCategories().firstOrNull { it.role == CategoryRole.REWARDS.name }
        dao.deleteMoneyForPurchase(purchaseId)
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
