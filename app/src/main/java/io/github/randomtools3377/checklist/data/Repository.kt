package io.github.randomtools3377.checklist.data

import androidx.room.withTransaction
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Categories
import io.github.randomtools3377.checklist.core.Celebration
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatText
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
)

/** What the reward editor saves. */
data class RewardDraft(val id: Long, val name: String, val icon: String, val price: Int)

/** Adding, editing, ordering and removing goals and rewards; settings; export and import. */
class Repository(private val db: AppDatabase, private val celebrations: CelebrationFiles) {
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
            repeat = RepeatText.encode(draft.repeat),
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
                ),
            )
        } else {
            dao.updateReward(existing.copy(name = draft.name, icon = draft.icon, price = draft.price))
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
        return Backup.encode(backup.copy(files = files))
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
