package io.github.randomtools3377.checklist.data

import androidx.room.withTransaction
import io.github.randomtools3377.checklist.core.Goal
import io.github.randomtools3377.checklist.core.GoalKind
import io.github.randomtools3377.checklist.core.Period
import io.github.randomtools3377.checklist.core.Reward
import io.github.randomtools3377.checklist.core.Step
import java.time.Clock
import java.time.LocalDate

/** What the habit editor saves. */
data class HabitDraft(
    val id: Long,
    val name: String,
    val icon: String,
    val points: Int,
    val period: Period,
    val target: Int,
    val bonus: Int,
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
)

/** What the reward editor saves. */
data class RewardDraft(val id: Long, val name: String, val icon: String, val price: Int)

/** Adding, editing and removing goals and rewards, and the settings. */
class Repository(private val db: AppDatabase, private val clock: Clock) {
    private val dao = db.dao()

    suspend fun goal(id: Long): Goal? = dao.goal(id)?.toCore()

    suspend fun steps(goalId: Long): List<Step> = dao.activeSteps(goalId).map { it.toCore() }

    suspend fun reward(id: Long): Reward? = dao.reward(id)?.toCore()

    suspend fun saveHabit(draft: HabitDraft): Unit = db.withTransaction {
        val existing = if (draft.id != 0L) dao.goal(draft.id) else null
        if (existing == null) {
            dao.insertGoal(
                GoalEntity(
                    kind = GoalKind.HABIT.name,
                    name = draft.name,
                    icon = draft.icon,
                    points = draft.points,
                    period = draft.period.name,
                    target = draft.target,
                    bonus = draft.bonus,
                    deadline = null,
                    archived = false,
                    position = dao.nextGoalPosition(),
                    createdAt = clock.millis(),
                ),
            )
        } else {
            dao.updateGoal(
                existing.copy(
                    name = draft.name,
                    icon = draft.icon,
                    points = draft.points,
                    period = draft.period.name,
                    target = draft.target,
                    bonus = draft.bonus,
                ),
            )
        }
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
                    createdAt = clock.millis(),
                ),
            )
        } else {
            dao.updateGoal(
                existing.copy(
                    name = draft.name,
                    icon = draft.icon,
                    bonus = draft.bonus,
                    deadline = draft.deadline?.toEpochDay(),
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
                    createdAt = clock.millis(),
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

    suspend fun setDayEndHour(hour: Int) {
        dao.putSetting(SettingEntity(SETTING_DAY_END_HOUR, parseDayEndHour(hour.toString()).toString()))
    }
}
