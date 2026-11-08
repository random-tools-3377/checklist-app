package io.github.randomtools3377.checklist.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Dates are stored as epoch days, moments as epoch milliseconds, and kinds and periods by name.
//
// Every version's layout is exported to app/schemas. A later version that changes a table must
// add a migration; the owner's data is never dropped.

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val icon: String,
    val points: Int,
    val period: String?,
    val target: Int,
    val bonus: Int,
    val deadline: Long?,
    val archived: Boolean,
    val position: Int,
    val createdAt: Long,
    /** When it is due, as JSON (see RepeatText). Null for a project. Added in version 2. */
    val repeat: String? = null,
    /** How long one tick takes, in minutes. Added in version 2. */
    val minutes: Int? = null,
    /** Clock time to do it at, in minutes after midnight. Added in version 2. */
    val atMinute: Int? = null,
    /** Time to finish it by, in minutes after midnight. Added in version 2. */
    val finishByMinute: Int? = null,
    /** For "times per": the day of its week, month or year to finish by. Added in version 2. */
    val finishDay: Int? = null,
    /** Notification settings, as JSON (see RepeatText). Null means the defaults. Added in version 3. */
    val reminder: String? = null,
    /** Skips and misses allowed per [allowancePer]. Null means the default. Added in version 4. */
    val allowance: Int? = null,
    val allowancePer: String? = null,
    /** Kept behind the privacy lock. Added in version 4. */
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false,
)

@Entity(tableName = "steps", indices = [Index("goalId")])
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val name: String,
    val points: Int,
    val position: Int,
    val archived: Boolean,
)

@Entity(tableName = "ticks", indices = [Index("goalId", "periodStart"), Index("stepId")])
data class TickEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val stepId: Long?,
    val at: Long,
    val day: Long,
    val period: String?,
    val periodStart: Long,
)

/** The points ledger. The balance is the sum of [amount]. */
@Entity(tableName = "entries", indices = [Index("tickId"), Index("day")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val day: Long,
    val kind: String,
    val amount: Int,
    val goalId: Long?,
    val tickId: Long?,
    val rewardId: Long?,
    val purchaseId: Long?,
    val label: String,
    /** The skip or miss a penalty or refund belongs to. Added in version 4. */
    val lapseId: Long? = null,
)

@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val price: Int,
    val archived: Boolean,
    val position: Int,
    val createdAt: Long,
)

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rewardId: Long,
    val name: String,
    val icon: String,
    val price: Int,
    val at: Long,
    val usedAt: Long?,
    /** When it was returned for a refund. Added in version 2. */
    val returnedAt: Long? = null,
)

/** Skips and misses. Added in version 4. */
@Entity(tableName = "lapses", indices = [Index("goalId", "day")])
data class LapseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val goalId: Long,
    val kind: String,
    val day: Long,
    val period: String?,
    val periodStart: Long,
    val at: Long,
    val overLimit: Boolean,
    val cost: Int,
    val reason: String?,
    val note: String?,
    val forgotten: Boolean,
    val label: String,
    /** The gap it falls in. Added in version 5. */
    val gapId: Long? = null,
)

/** Stretches when it looks like the user stopped for a while. Added in version 5. */
@Entity(tableName = "gaps")
data class GapEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromDay: Long,
    val toDay: Long,
    val kind: String,
    val createdAt: Long,
    val reason: String?,
    val note: String?,
)

/** Penalty tasks owed for going over the limit. Added in version 4. */
@Entity(tableName = "penalties", indices = [Index("lapseId")])
data class PenaltyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lapseId: Long?,
    val createdAt: Long,
    val choice: String?,
    val goalId: Long?,
    val taskId: Long?,
    val label: String?,
    val doneAt: Long?,
    val cancelledAt: Long?,
    /** The gap it covers, for a gap check-in. Added in version 5. */
    val gapId: Long? = null,
)

/** The user's own penalty tasks. Added in version 4. */
@Entity(tableName = "penalty_tasks")
data class PenaltyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val archived: Boolean,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey @ColumnInfo(name = "name") val name: String,
    val value: String,
)

data class DayAmount(val day: Long, val amount: Int)

@Dao
interface ChecklistDao {
    // Goals and steps

    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY position, id")
    fun observeGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun goal(id: Long): GoalEntity?

    @Insert
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM goals")
    suspend fun nextGoalPosition(): Int

    @Query("SELECT * FROM steps WHERE archived = 0 ORDER BY goalId, position, id")
    fun observeSteps(): Flow<List<StepEntity>>

    @Query("SELECT * FROM steps WHERE goalId = :goalId AND archived = 0 ORDER BY position, id")
    suspend fun activeSteps(goalId: Long): List<StepEntity>

    @Query("SELECT * FROM steps WHERE id = :id")
    suspend fun step(id: Long): StepEntity?

    @Insert
    suspend fun insertStep(step: StepEntity): Long

    @Update
    suspend fun updateStep(step: StepEntity)

    // Ticks

    @Query("SELECT * FROM ticks WHERE stepId IS NULL AND periodStart >= :since")
    fun observeHabitTicksSince(since: Long): Flow<List<TickEntity>>

    @Query("SELECT * FROM ticks WHERE stepId IS NOT NULL")
    fun observeStepTicks(): Flow<List<TickEntity>>

    @Query("SELECT * FROM ticks WHERE stepId IS NULL AND periodStart >= :since")
    suspend fun habitTicksSince(since: Long): List<TickEntity>

    @Query("SELECT * FROM ticks WHERE goalId = :goalId AND stepId IS NULL AND periodStart = :periodStart ORDER BY id")
    suspend fun habitTicks(goalId: Long, periodStart: Long): List<TickEntity>

    @Query("SELECT * FROM ticks WHERE stepId = :stepId ORDER BY id LIMIT 1")
    suspend fun stepTick(stepId: Long): TickEntity?

    @Query("SELECT * FROM ticks WHERE goalId = :goalId AND stepId IS NOT NULL ORDER BY id")
    suspend fun stepTicks(goalId: Long): List<TickEntity>

    @Insert
    suspend fun insertTick(tick: TickEntity): Long

    @Query("DELETE FROM ticks WHERE id = :id")
    suspend fun deleteTick(id: Long)

    // Ledger

    @Insert
    suspend fun insertEntry(entry: EntryEntity): Long

    @Query("SELECT * FROM entries WHERE tickId IN (:tickIds)")
    suspend fun entriesForTicks(tickIds: List<Long>): List<EntryEntity>

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM entries")
    suspend fun balance(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM entries")
    fun observeBalance(): Flow<Int>

    @Query("SELECT day, SUM(amount) AS amount FROM entries WHERE kind IN ('TICK', 'BONUS') AND day >= :since GROUP BY day")
    fun observeEarnedByDay(since: Long): Flow<List<DayAmount>>

    @Query("SELECT MIN(day) FROM entries WHERE kind IN ('TICK', 'BONUS')")
    fun observeFirstEarningDay(): Flow<Long?>

    // Shop

    @Query("SELECT * FROM rewards WHERE archived = 0 ORDER BY position, id")
    fun observeRewards(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun reward(id: Long): RewardEntity?

    @Insert
    suspend fun insertReward(reward: RewardEntity): Long

    @Update
    suspend fun updateReward(reward: RewardEntity)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM rewards")
    suspend fun nextRewardPosition(): Int

    @Query("SELECT * FROM purchases WHERE usedAt IS NULL AND returnedAt IS NULL ORDER BY at DESC, id DESC")
    fun observeUnusedPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun purchase(id: Long): PurchaseEntity?

    @Insert
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Query("UPDATE purchases SET usedAt = :at WHERE id = :id")
    suspend fun markUsed(id: Long, at: Long)

    @Query("UPDATE purchases SET returnedAt = :at WHERE id = :id")
    suspend fun markReturned(id: Long, at: Long)

    // Points history

    @Query("SELECT * FROM entries ORDER BY at DESC, id DESC")
    fun observeEntries(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM rewards WHERE archived = 0 ORDER BY position, id")
    suspend fun activeRewards(): List<RewardEntity>

    @Query("SELECT * FROM goals WHERE archived = 0 ORDER BY position, id")
    suspend fun activeGoals(): List<GoalEntity>

    // Backup: everything, including removed goals and rewards, which history still refers to.

    @Query("SELECT * FROM goals") suspend fun allGoals(): List<GoalEntity>
    @Query("SELECT * FROM steps") suspend fun allSteps(): List<StepEntity>
    @Query("SELECT * FROM ticks") suspend fun allTicks(): List<TickEntity>
    @Query("SELECT * FROM entries") suspend fun allEntries(): List<EntryEntity>
    @Query("SELECT * FROM rewards") suspend fun allRewards(): List<RewardEntity>
    @Query("SELECT * FROM purchases") suspend fun allPurchases(): List<PurchaseEntity>
    @Query("SELECT * FROM settings") suspend fun allSettings(): List<SettingEntity>

    @Query("DELETE FROM goals") suspend fun deleteGoals()
    @Query("DELETE FROM steps") suspend fun deleteSteps()
    @Query("DELETE FROM ticks") suspend fun deleteTicks()
    @Query("DELETE FROM entries") suspend fun deleteEntries()
    @Query("DELETE FROM rewards") suspend fun deleteRewards()
    @Query("DELETE FROM purchases") suspend fun deletePurchases()
    @Query("DELETE FROM settings") suspend fun deleteSettings()

    @Insert suspend fun insertGoals(rows: List<GoalEntity>)
    @Insert suspend fun insertSteps(rows: List<StepEntity>)
    @Insert suspend fun insertTicks(rows: List<TickEntity>)
    @Insert suspend fun insertEntries(rows: List<EntryEntity>)
    @Insert suspend fun insertRewards(rows: List<RewardEntity>)
    @Insert suspend fun insertPurchases(rows: List<PurchaseEntity>)
    @Insert suspend fun insertSettings(rows: List<SettingEntity>)

    // Settings

    @Query("SELECT value FROM settings WHERE name = :name")
    suspend fun setting(name: String): String?

    @Query("SELECT value FROM settings WHERE name = :name")
    fun observeSetting(name: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSetting(setting: SettingEntity)

    // Skips, misses and penalties

    @Query("SELECT * FROM lapses WHERE goalId = :goalId AND day >= :from AND day <= :to ORDER BY day, id")
    suspend fun lapses(goalId: Long, from: Long, to: Long): List<LapseEntity>

    @Query("SELECT * FROM lapses WHERE id = :id")
    suspend fun lapse(id: Long): LapseEntity?

    @Insert
    suspend fun insertLapse(lapse: LapseEntity): Long

    @Update
    suspend fun updateLapse(lapse: LapseEntity)

    @Query("SELECT * FROM lapses WHERE day >= :since ORDER BY day, id")
    fun observeLapsesSince(since: Long): Flow<List<LapseEntity>>

    @Query("SELECT * FROM lapses WHERE day >= :since ORDER BY day, id")
    suspend fun lapsesSince(since: Long): List<LapseEntity>

    /** Misses still waiting for a reason. Those inside a gap get the gap's one reason instead. */
    @Query("SELECT * FROM lapses WHERE reason IS NULL AND forgotten = 0 AND kind = 'MISS' AND gapId IS NULL ORDER BY day, id")
    fun observeUnexplained(): Flow<List<LapseEntity>>

    // Gaps and the review

    @Query("SELECT * FROM lapses WHERE day >= :from AND day <= :to ORDER BY day, id")
    suspend fun allLapses(from: Long, to: Long): List<LapseEntity>

    @Query("SELECT * FROM entries WHERE day >= :from AND day <= :to ORDER BY day, id")
    suspend fun entries(from: Long, to: Long): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE day >= :from AND day <= :to ORDER BY day, id")
    fun observeEntries(from: Long, to: Long): Flow<List<EntryEntity>>

    @Query("SELECT DISTINCT day FROM ticks WHERE day >= :from AND day <= :to")
    suspend fun tickedDays(from: Long, to: Long): List<Long>

    @Query("SELECT * FROM gaps ORDER BY fromDay, id")
    suspend fun gaps(): List<GapEntity>

    @Query("SELECT * FROM gaps ORDER BY fromDay, id")
    fun observeGaps(): Flow<List<GapEntity>>

    @Query("SELECT * FROM gaps WHERE reason IS NULL ORDER BY fromDay, id")
    fun observeUnansweredGaps(): Flow<List<GapEntity>>

    @Query("SELECT * FROM gaps WHERE id = :id")
    suspend fun gap(id: Long): GapEntity?

    @Insert
    suspend fun insertGap(gap: GapEntity): Long

    @Update
    suspend fun updateGap(gap: GapEntity)

    @Query("SELECT * FROM gaps") suspend fun allGaps(): List<GapEntity>
    @Query("DELETE FROM gaps") suspend fun deleteGaps()
    @Insert suspend fun insertGaps(rows: List<GapEntity>)

    @Query("SELECT * FROM entries WHERE lapseId = :lapseId")
    suspend fun entriesForLapse(lapseId: Long): List<EntryEntity>

    @Insert
    suspend fun insertPenalty(penalty: PenaltyEntity): Long

    @Update
    suspend fun updatePenalty(penalty: PenaltyEntity)

    @Query("SELECT * FROM penalties WHERE id = :id")
    suspend fun penalty(id: Long): PenaltyEntity?

    @Query("SELECT * FROM penalties WHERE lapseId = :lapseId ORDER BY id LIMIT 1")
    suspend fun penaltyForLapse(lapseId: Long): PenaltyEntity?

    @Query("SELECT * FROM penalties WHERE doneAt IS NULL AND cancelledAt IS NULL ORDER BY createdAt, id")
    suspend fun openPenalties(): List<PenaltyEntity>

    @Query("SELECT * FROM penalties WHERE doneAt IS NULL AND cancelledAt IS NULL ORDER BY createdAt, id")
    fun observeOpenPenalties(): Flow<List<PenaltyEntity>>

    @Query("SELECT * FROM penalty_tasks WHERE archived = 0 ORDER BY id")
    suspend fun penaltyTasks(): List<PenaltyTaskEntity>

    @Query("SELECT * FROM penalty_tasks WHERE archived = 0 ORDER BY id")
    fun observePenaltyTasks(): Flow<List<PenaltyTaskEntity>>

    @Insert
    suspend fun insertPenaltyTask(task: PenaltyTaskEntity): Long

    @Query("UPDATE penalty_tasks SET archived = 1 WHERE id = :id")
    suspend fun removePenaltyTask(id: Long)

    @Query("SELECT * FROM lapses") suspend fun allLapses(): List<LapseEntity>
    @Query("SELECT * FROM penalties") suspend fun allPenalties(): List<PenaltyEntity>
    @Query("SELECT * FROM penalty_tasks") suspend fun allPenaltyTasks(): List<PenaltyTaskEntity>
    @Query("DELETE FROM lapses") suspend fun deleteLapses()
    @Query("DELETE FROM penalties") suspend fun deletePenalties()
    @Query("DELETE FROM penalty_tasks") suspend fun deletePenaltyTasks()
    @Insert suspend fun insertLapses(rows: List<LapseEntity>)
    @Insert suspend fun insertPenalties(rows: List<PenaltyEntity>)
    @Insert suspend fun insertPenaltyTasks(rows: List<PenaltyTaskEntity>)
}

@Database(
    entities = [
        GoalEntity::class,
        StepEntity::class,
        TickEntity::class,
        EntryEntity::class,
        RewardEntity::class,
        PurchaseEntity::class,
        SettingEntity::class,
        LapseEntity::class,
        PenaltyEntity::class,
        PenaltyTaskEntity::class,
        GapEntity::class,
    ],
    version = 5,
    exportSchema = true,
    // Versions 4 and 5 only add tables and columns, so Room works the migrations out from the layouts.
    autoMigrations = [AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ChecklistDao

    companion object {
        const val FILE_NAME = "checklist.db"

        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
