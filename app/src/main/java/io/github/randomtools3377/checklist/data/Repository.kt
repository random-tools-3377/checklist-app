package io.github.randomtools3377.checklist.data

import androidx.room.withTransaction
import io.github.randomtools3377.checklist.core.Amounts
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Categories
import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.Moves
import io.github.randomtools3377.checklist.core.Todos
import io.github.randomtools3377.checklist.core.Mystery
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatText
import io.github.randomtools3377.checklist.core.RepeatKind
import io.github.randomtools3377.checklist.core.Reward
import io.github.randomtools3377.checklist.core.Step
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.Base64

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
)

/** What the reward editor saves. */
data class RewardDraft(
    val id: Long,
    val name: String,
    val icon: String,
    val price: Int,
    /** For a mystery reward, what can come out of it. Empty for a plain reward. */
    val prizes: List<String> = emptyList(),
)

/** Adding, editing, ordering and removing goals and rewards; settings; export and import. */
class Repository(private val db: AppDatabase, private val celebrations: CelebrationFiles, private val background: BackgroundPhoto? = null) {
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
                draft.repeat.copy(moves = Moves.keep(existing?.repeat?.let { RepeatText.decode(it).moves }.orEmpty(), nowLocal().date)),
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
                ),
            )
        } else {
            dao.updateGoal(
                existing.copy(
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
            dao.insertGoal(made.copy(private = draft.private, reminder = reminder, position = dao.nextGoalPosition(), createdAt = nowMillis()).toEntity())
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
                ),
            )
        } else {
            dao.updateReward(existing.copy(name = draft.name, icon = draft.icon, price = draft.price, prizes = Mystery.encode(draft.prizes)))
        }
        Unit
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
            )
        }
        // The GIFs the user picked go in the file too, so a restore brings them back.
        val files = backup.goals.mapNotNull { Celebration.ownFile(it.celebration) }.toSet()
            .mapNotNull { name -> celebrations.read(name)?.let { name to Base64.getEncoder().encodeToString(it) } }
            .toMap()
        val photo = background?.read()?.let { Base64.getEncoder().encodeToString(it) }
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
                Base64.getDecoder().decode(data)
            } catch (e: IllegalArgumentException) {
                continue
            }
            celebrations.write(name, bytes)
        }
        background?.let { photo ->
            val bytes = backup.background?.let { data -> runCatching { Base64.getDecoder().decode(data) }.getOrNull() }
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
    }
}
