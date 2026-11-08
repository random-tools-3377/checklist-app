package io.github.randomtools3377.checklist.data

import androidx.room.withTransaction
import io.github.randomtools3377.checklist.core.Backup
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Reminder
import io.github.randomtools3377.checklist.core.Repeat
import io.github.randomtools3377.checklist.core.RepeatText
import io.github.randomtools3377.checklist.core.Reward
import io.github.randomtools3377.checklist.core.Step
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

const val SETTING_DEFAULT_FINISH = "default_finish_minute"
const val SETTING_TODAY_ORDER = "today_order"
const val ORDER_RECOMMENDED = "recommended"
const val ORDER_OWN = "own"

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
)

/** What the reward editor saves. */
data class RewardDraft(val id: Long, val name: String, val icon: String, val price: Int)

/** Adding, editing, ordering and removing goals and rewards; settings; export and import. */
class Repository(private val db: AppDatabase) {
    private val dao = db.dao()

    suspend fun goal(id: Long): Goal? = dao.goal(id)?.toCore()

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
            finishByMinute = draft.finishBy?.toMinuteOfDay(),
            finishDay = draft.finishDay,
            reminder = draft.reminder?.let { RepeatText.encodeReminder(it) },
        )
        if (existing == null) dao.insertGoal(row) else dao.updateGoal(row)
        Unit
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
        Unit
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

    suspend fun setTodayOrder(order: String) {
        dao.putSetting(SettingEntity(SETTING_TODAY_ORDER, order))
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
            )
        }
        return Backup.encode(backup)
    }

    /** Replaces everything in the app with [backup]. All or nothing. */
    suspend fun restore(backup: Backup): Unit = db.withTransaction {
        dao.deleteTicks()
        dao.deleteEntries()
        dao.deletePurchases()
        dao.deleteSteps()
        dao.deleteGoals()
        dao.deleteRewards()
        dao.deleteSettings()
        dao.insertGoals(backup.goals.map { it.toEntity() })
        dao.insertSteps(backup.steps.map { it.toEntity() })
        dao.insertTicks(backup.ticks.map { it.toEntity() })
        dao.insertEntries(backup.entries.map { it.toEntity() })
        dao.insertRewards(backup.rewards.map { it.toEntity() })
        dao.insertPurchases(backup.purchases.map { it.toEntity() })
        dao.insertSettings(backup.settings.map { (name, value) -> SettingEntity(name, value) })
    }
}
